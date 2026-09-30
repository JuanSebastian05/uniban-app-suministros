package co.edu.udea.uniban.suministros

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.MovementEntity
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.data.local.SyncStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Consultas del inventario sin conexión (HU_03) y consistencia del saldo (RT_10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class InventoryQueryTest {
    private lateinit var context: Context
    private lateinit var database: InventoryDatabase
    private lateinit var repository: InventoryRepository
    private val databaseName = "inventory-query-test.db"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, InventoryDatabase::class.java, databaseName).build()
        repository = InventoryRepository(database)
        repository.initializeDemo()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    /** RT_10: existencia = inicial + entradas + ajustes − consumos − salidas − pérdidas. */
    @Test
    fun stockMatchesInitialQuantityPlusItsMovements() = runBlocking {
        val item = repository.inventory.first().first()
        listOf("1,25", "0,50", "10", "0,01").forEach { quantity ->
            repository.registerEntry(
                UUID.randomUUID().toString(), item.id, quantity, LocalDate.of(2026, 9, 29), "",
            )
        }

        val initial = requireNotNull(database.inventoryDao().findSyncContext(item.id)).initialQuantity
        val movements = repository.recentMovements(item.id).first()
        val current = repository.inventory.first().first { it.id == item.id }.quantity

        assertEquals(4, movements.size)
        assertEquals(reconcile(initial, movements).toFloat(), current, 0.001f)
        // El saldo se movió de verdad: la prueba no pasaría con una lista de movimientos vacía.
        assertTrue(current > initial)
    }

    @Test
    fun recentMovementsOnlyReturnsTheRequestedSupplyNewestFirst() = runBlocking {
        val items = repository.inventory.first()
        val target = items[0]
        val other = items[1]
        listOf(
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 9, 28),
            LocalDate.of(2026, 9, 24),
        ).forEach { date ->
            repository.registerEntry(UUID.randomUUID().toString(), target.id, "1", date, "")
        }
        repository.registerEntry(
            UUID.randomUUID().toString(), other.id, "5", LocalDate.of(2026, 9, 29), "",
        )

        val recent = repository.recentMovements(target.id, limit = 2).first()
        assertEquals(2, recent.size)
        assertTrue(recent.all { it.inventoryId == target.id })
        assertEquals(listOf("2026-09-28", "2026-09-24"), recent.map { it.date })

        assertEquals(1, repository.recentMovements(other.id).first().size)
    }

    @Test
    fun pendingCountIgnoresSyncedMovements() = runBlocking {
        val item = repository.inventory.first().first()
        val id = UUID.randomUUID().toString()
        repository.registerEntry(id, item.id, "2", LocalDate.of(2026, 9, 29), "")
        assertEquals(1, repository.pendingCount.first())

        val dao = database.inventoryDao()
        dao.markSyncing(id)
        dao.finishSyncing(id, SyncStatus.SYNCED)
        assertEquals(0, repository.pendingCount.first())
    }

    private fun reconcile(initial: Float, movements: List<MovementEntity>): BigDecimal =
        movements.fold(BigDecimal(initial.toString())) { total, movement ->
            val amount = BigDecimal(movement.quantity.toString())
            if (MovementType.subtracts(movement.type)) total.subtract(amount) else total.add(amount)
        }
}
