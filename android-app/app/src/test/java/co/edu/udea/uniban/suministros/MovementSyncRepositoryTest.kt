package co.edu.udea.uniban.suministros

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.MovementSyncRepository
import co.edu.udea.uniban.suministros.data.local.DemoInventory
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.SyncStatus
import co.edu.udea.uniban.suministros.data.remote.MovementApi
import co.edu.udea.uniban.suministros.data.remote.MovementDto
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class MovementSyncRepositoryTest {
    private class FakeApi : MovementApi {
        val received = mutableListOf<MovementDto>()
        var respond: suspend (MovementDto) -> Response<Unit> = { Response.success(Unit) }
        override suspend fun send(movement: MovementDto): Response<Unit> {
            received += movement
            return respond(movement)
        }
    }

    private lateinit var database: InventoryDatabase
    private lateinit var inventory: InventoryRepository
    private lateinit var sync: MovementSyncRepository
    private val api = FakeApi()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        inventory = InventoryRepository(database)
        sync = MovementSyncRepository(database, api)
        inventory.initializeDemo()
    }

    @After
    fun tearDown() { database.close() }

    private suspend fun register(quantity: String = "1,25"): String {
        val item = inventory.inventory.first().first()
        val id = UUID.randomUUID().toString()
        inventory.registerEntry(id, item.id, quantity, LocalDate.of(2026, 9, 29), "Compra")
        return id
    }

    private suspend fun statusOf(id: String) = database.inventoryDao().findMovement(id)!!.syncStatus

    private fun error(code: Int): Response<Unit> =
        Response.error(code, "{}".toResponseBody("application/json".toMediaType()))

    @Test
    fun pendingMovementIsSentWithItsContextAndMarkedSynced() = runBlocking {
        val id = register()
        val item = inventory.inventory.first().first()
        val demo = DemoInventory.inventory.first { it.id == item.id }

        val summary = sync.syncPending()

        assertEquals(1, summary.synced)
        assertFalse(summary.retryLater)
        assertEquals(SyncStatus.SYNCED, statusOf(id))
        val dto = api.received.single()
        assertEquals(id, dto.id)
        assertEquals("ENTRADA", dto.type)
        assertEquals("2026-09-29", dto.date)
        assertEquals("Compra", dto.observation)
        assertEquals(0, BigDecimal("1.25").compareTo(dto.quantity))
        assertEquals(item.id, dto.inventory.id)
        assertEquals(0, BigDecimal(demo.initialQuantity.toString()).compareTo(dto.inventory.initialQuantity))
        assertEquals(item.name, dto.inventory.supply.name)
        assertEquals(item.unit, dto.inventory.supply.unit)
        assertEquals(DemoInventory.location.name, dto.inventory.location.name)
        assertEquals(DemoInventory.producer.id, dto.inventory.location.producer.id)
    }

    @Test
    fun networkFailureMarksErrorKeepsLocalDataAndIsRetriedLater() = runBlocking {
        val id = register()
        val stockBefore = inventory.inventory.first().first().quantity
        api.respond = { throw IOException("sin red") }

        val failed = sync.syncPending()

        assertEquals(1, failed.failed)
        assertTrue(failed.retryLater)
        assertEquals(SyncStatus.ERROR, statusOf(id))
        assertEquals(stockBefore, inventory.inventory.first().first().quantity, 0.001f)

        api.respond = { Response.success(Unit) }
        val retried = sync.syncPending()

        assertEquals(1, retried.synced)
        assertEquals(SyncStatus.SYNCED, statusOf(id))
        assertEquals(listOf(id, id), api.received.map { it.id })
    }

    @Test
    fun networkFailureStopsTheBatchAndLeavesTheRestPending() = runBlocking {
        val first = register("1")
        val second = register("2")
        api.respond = { throw IOException("sin red") }

        sync.syncPending()

        assertEquals(SyncStatus.ERROR, statusOf(first))
        assertEquals(SyncStatus.PENDING, statusOf(second))
        assertEquals(1, api.received.size)
    }

    @Test
    fun serverRejectionMarksErrorWithoutAskingForAutomaticRetry() = runBlocking {
        val id = register()
        api.respond = { error(409) }

        val summary = sync.syncPending()

        assertEquals(1, summary.failed)
        assertFalse(summary.retryLater)
        assertEquals(SyncStatus.ERROR, statusOf(id))
    }

    @Test
    fun serverErrorsAreRetriedLater() = runBlocking {
        val id = register()
        api.respond = { error(503) }

        assertTrue(sync.syncPending().retryLater)
        assertEquals(SyncStatus.ERROR, statusOf(id))
    }

    @Test
    fun interruptedSyncingIsResumedWithoutLoss() = runBlocking {
        val id = register()
        assertEquals(1, database.inventoryDao().markSyncing(id))

        val summary = sync.syncPending()

        assertEquals(1, summary.synced)
        assertEquals(SyncStatus.SYNCED, statusOf(id))
        assertEquals(1, api.received.size)
    }

    @Test
    fun savingAnEntryRequestsASyncOncePerMovement() = runBlocking {
        var requests = 0
        val observed = InventoryRepository(database, onMovementSaved = { requests++ })
        val item = observed.inventory.first().first()
        observed.registerEntry(UUID.randomUUID().toString(), item.id, "1", LocalDate.now(), "")
        assertEquals(1, requests)
        assertTrue(runCatching {
            observed.registerEntry(UUID.randomUUID().toString(), item.id, "0", LocalDate.now(), "")
        }.isFailure)
        assertEquals(1, requests)
    }

    @Test
    fun syncedMovementsAreNotSentAgain() = runBlocking {
        register()
        sync.syncPending()
        val second = sync.syncPending()

        assertEquals(0, second.synced)
        assertEquals(1, api.received.size)
    }

    @Test
    fun cancellationWhileSendingReturnsTheMovementToPending() = runBlocking {
        val id = register()
        val started = CompletableDeferred<Unit>()
        api.respond = {
            started.complete(Unit)
            awaitCancellation()
        }

        val job = launch { sync.syncPending() }
        started.await()
        assertEquals(SyncStatus.SYNCING, statusOf(id))
        job.cancelAndJoin()

        assertEquals(SyncStatus.PENDING, statusOf(id))
    }

    @Test
    fun syncingDoesNotChangeStockOrMovementContent() = runBlocking {
        val id = register()
        val before = database.inventoryDao().findMovement(id)!!
        val stock = inventory.inventory.first().first().quantity

        sync.syncPending()

        val after = database.inventoryDao().findMovement(id)!!
        assertEquals(before.copy(syncStatus = SyncStatus.SYNCED), after)
        assertEquals(stock, inventory.inventory.first().first().quantity, 0.001f)
    }
}
