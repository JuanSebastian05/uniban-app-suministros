package co.edu.udea.uniban.suministros

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.InventoryEntity
import co.edu.udea.uniban.suministros.data.local.MovementEntity
import co.edu.udea.uniban.suministros.data.local.LossReason
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.data.local.StarterInventory
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class InventoryRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: InventoryDatabase
    private lateinit var repository: InventoryRepository
    private val databaseName = "inventory-test.db"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        openDatabase()
        repository.initializeDemo()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, InventoryDatabase::class.java, databaseName).build()
        repository = InventoryRepository(database)
    }

    @Test
    fun entryUpdatesStockAndRemainsPendingAfterReopening() = runBlocking {
        val item = repository.inventory.first().first()
        val id = UUID.randomUUID().toString()
        repository.registerEntry(id, item.id, "1,25", LocalDate.of(2026, 9, 29), "Compra")

        database.close()
        openDatabase()
        repository.initializeDemo()

        assertEquals(9, repository.inventory.first().size)
        val updated = repository.inventory.first().first { it.id == item.id }
        assertEquals(item.quantity + 1.25f, updated.quantity, 0.001f)
        val movement = repository.movements.first().single()
        assertEquals(id, movement.id)
        assertEquals("PENDING", movement.syncStatus)
        assertEquals("ENTRADA", movement.type)
        assertEquals("2026-09-29", movement.date)
        assertEquals("Compra", movement.observation)
        assertTrue(movement.createdAt.isNotBlank())
        assertEquals(movement.createdAt, movement.updatedAt)
        assertEquals(id, repository.findSavedEntry(id)?.id)
    }

    @Test
    fun packagingStartsAtZeroWithTheAgreedNamesAndUnits() = runBlocking {
        val packaging = repository.inventory.first().filter { it.category == "Empaque" }
        assertEquals(setOf("cajas", "carton", "bolsas"), packaging.map { it.name }.toSet())
        assertEquals(3, packaging.size)
        assertTrue(packaging.all { it.unit == "unidad" && it.quantity == 0f })
        assertTrue(repository.movements.first().isEmpty())
        assertEquals(0, repository.pendingCount.first())
    }

    @Test
    fun existingAccountGetsPackagingWithoutChangingItsStockOrHistory() = runBlocking {
        val legacyDb = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        try {
            val starter = StarterInventory("legacy-owner")
            val dao = legacyDb.inventoryDao()
            val agriculturalSupplies = starter.supplies.filter { it.category != "Empaque" }
            val agriculturalIds = agriculturalSupplies.map { it.id }.toSet()
            dao.insertProducer(starter.producer)
            dao.insertLocation(starter.location)
            dao.insertSupplies(agriculturalSupplies)
            dao.insertInventory(starter.inventory.filter { it.supplyId in agriculturalIds })
            val legacy = InventoryRepository(legacyDb, "legacy-owner")
            val originalItem = legacy.inventory.first().first()
            legacy.registerEntry(UUID.randomUUID().toString(), originalItem.id, "5", LocalDate.now(), "Compra anterior")
            val balancesBefore = legacy.balances.first()
            val movementsBefore = legacy.movements.first()

            // Abrir la cuenta repetidas veces no debe duplicar materiales ni cambiar sus datos anteriores.
            repeat(3) { legacy.initializeDemo() }

            val balancesAfter = legacy.balances.first().associateBy { it.id }
            balancesBefore.forEach { assertEquals(it, balancesAfter[it.id]) }
            assertEquals(9, legacy.inventory.first().size)
            assertEquals(movementsBefore, legacy.movements.first())
            assertEquals(1, legacy.pendingCount.first())
            assertTrue(legacy.inventory.first().filter { it.category == "Empaque" }.all { it.quantity == 0f })
        } finally {
            legacyDb.close()
        }
    }

    @Test
    fun packagingMovementsAndBalancesSurviveReopeningWithoutDuplicates() = runBlocking {
        val packaging = repository.inventory.first().filter { it.category == "Empaque" }
        for (item in packaging) {
            repository.registerEntry(UUID.randomUUID().toString(), item.id, "100", LocalDate.now(), "Compra de empaque")
            repository.registerMovement(UUID.randomUUID().toString(), MovementType.PERDIDA, item.id, "5",
                LocalDate.now(), "", lossReason = LossReason.MAL_MANEJO)
        }
        val movementsBefore = repository.movements.first()
        database.close()
        openDatabase()
        repeat(2) { repository.initializeDemo() }

        val packagingAfter = repository.inventory.first().filter { it.category == "Empaque" }
        assertEquals(packaging.map { it.id }.toSet(), packagingAfter.map { it.id }.toSet())
        assertEquals(3, packagingAfter.size)
        assertTrue(packagingAfter.all { it.quantity == 95f })
        assertEquals(movementsBefore, repository.movements.first())
        assertEquals(6, repository.pendingCount.first())
    }

    @Test
    fun concurrentRetriesOfSameEntryOnlyIncreaseStockOnce() = runBlocking {
        val item = repository.inventory.first().first()
        val id = UUID.randomUUID().toString()
        coroutineScope {
            List(5) {
                async { repository.registerEntry(id, item.id, "0.25", LocalDate.now(), "") }
            }.awaitAll()
        }
        assertEquals(1, repository.movements.first().size)
        assertEquals(item.quantity + 0.25f,
            repository.inventory.first().first { it.id == item.id }.quantity, 0.001f)
    }

    @Test
    fun stockUpdateFailureRollsBackTheMovement() = runBlocking {
        val item = repository.inventory.first().first()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_stock BEFORE UPDATE ON inventory BEGIN SELECT RAISE(ABORT, 'test'); END",
        )
        val result = runCatching {
            repository.registerEntry(UUID.randomUUID().toString(), item.id, "1.25", LocalDate.now(), "")
        }
        assertTrue(result.isFailure)
        assertTrue(repository.movements.first().isEmpty())
        assertEquals(item.quantity, repository.inventory.first().first { it.id == item.id }.quantity, 0.001f)
    }

    @Test
    fun invalidInputsDoNotChangeStock() = runBlocking {
        val item = repository.inventory.first().first()
        listOf("0", "-1", "1.234", "NaN", "99999.99").forEach { quantity ->
            assertTrue(runCatching {
                repository.registerEntry(UUID.randomUUID().toString(), item.id, quantity, LocalDate.now(), "")
            }.isFailure)
        }
        assertTrue(runCatching {
            repository.registerEntry(UUID.randomUUID().toString(), "missing", "1", LocalDate.now(), "")
        }.isFailure)
        assertTrue(repository.movements.first().isEmpty())
        assertEquals(item.quantity, repository.inventory.first().first { it.id == item.id }.quantity, 0.001f)
    }

    @Test
    fun databaseRejectsMissingParentsAndDuplicateStockRows() = runBlocking {
        val dao = database.inventoryDao()
        assertTrue(runCatching {
            dao.insertMovement(MovementEntity(UUID.randomUUID().toString(), "missing", 1f,
                "2026-09-29", "", "now", "now"))
        }.isFailure)
        val cursor = database.openHelper.readableDatabase.query("SELECT * FROM inventory LIMIT 1")
        val duplicate = cursor.use {
            check(it.moveToFirst())
            InventoryEntity(
                UUID.randomUUID().toString(),
                it.getString(it.getColumnIndexOrThrow("locationId")),
                it.getString(it.getColumnIndexOrThrow("supplyId")), 1f, 1f,
            )
        }
        assertTrue(runCatching { dao.insertInventory(listOf(duplicate)) }.isFailure)
    }
}

