package co.edu.udea.uniban.suministros.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.LossReason
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.data.local.ParcelEntity
import co.edu.udea.uniban.suministros.data.local.ProductionCycleEntity
import co.edu.udea.uniban.suministros.data.remote.MovementApi
import co.edu.udea.uniban.suministros.data.remote.MovementDto
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

/** Consumos y pérdidas (HU_05): restan sin dejar la existencia negativa y guardan parcela, ciclo o motivo. */
@RunWith(RobolectricTestRunner::class)
class ConsumptionLossTest {
    private lateinit var database: InventoryDatabase
    private lateinit var repository: InventoryRepository
    private lateinit var itemId: String
    private val today = LocalDate.of(2026, 10, 15)

    @Before fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InventoryDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = InventoryRepository(database)
        repository.initializeDemo()
        itemId = repository.inventory.first().first { it.name == "Urea 46%" }.id // 80 kg
        database.parcelDao().upsertParcels(listOf(ParcelEntity("p1", "La Esperanza", "2.40"), ParcelEntity("p2", "El Mirador", "1.50")))
        database.parcelDao().upsertCycles(listOf(ProductionCycleEntity("c1", "p1", "2026-01-15", null, "PRODUCCION")))
    }

    @After fun tearDown() = database.close()

    private fun id() = UUID.randomUUID().toString()
    private suspend fun stock() = repository.inventory.first().first { it.id == itemId }.quantity

    @Test fun consumoRestaYSeAsociaAlCicloActivo() = runTest {
        val movementId = id()
        val saved = repository.registerMovement(movementId, MovementType.CONSUMO, itemId, "12,5", today, "", parcelId = "p1")
        assertEquals(MovementType.CONSUMO, saved.type)
        assertEquals(67.5f, stock())
        val movement = database.inventoryDao().findMovement(movementId)!!
        assertEquals("p1", movement.parcelId)
        assertEquals("c1", movement.cycleId)
        assertNull(movement.lossReason)
        assertEquals("PENDING", movement.syncStatus)
    }

    @Test fun consumoEnParcelaSinCicloSoloGuardaLaParcela() = runTest {
        val movementId = id()
        repository.registerMovement(movementId, MovementType.CONSUMO, itemId, "1", today, "", parcelId = "p2")
        val movement = database.inventoryDao().findMovement(movementId)!!
        assertEquals("p2", movement.parcelId)
        assertNull(movement.cycleId)
    }

    @Test fun perdidaRestaYGuardaElMotivo() = runTest {
        val movementId = id()
        repository.registerMovement(movementId, MovementType.PERDIDA, itemId, "5", today, "", lossReason = LossReason.CLIMA)
        assertEquals(75f, stock())
        assertEquals("CLIMA", database.inventoryDao().findMovement(movementId)!!.lossReason)
    }

    @Test fun puedeDejarLaExistenciaEnCero() = runTest {
        repository.registerMovement(id(), MovementType.PERDIDA, itemId, "80", today, "", lossReason = LossReason.DESPERDICIO)
        assertEquals(0f, stock())
    }

    @Test fun noPermiteExistenciaNegativa() = runTest {
        val movementId = id()
        assertThrows(InsufficientStockException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.registerMovement(movementId, MovementType.CONSUMO, itemId, "80,01", today, "", parcelId = "p1")
            }
        }
        assertEquals(80f, stock())
        assertNull(database.inventoryDao().findMovement(movementId))
    }

    @Test fun datosObligatoriosPorTipo() = runTest {
        listOf(
            suspend { repository.registerMovement(id(), MovementType.CONSUMO, itemId, "1", today, "") },
            suspend { repository.registerMovement(id(), MovementType.PERDIDA, itemId, "1", today, "") },
            suspend { repository.registerMovement(id(), MovementType.PERDIDA, itemId, "1", today, " ", lossReason = LossReason.OTRO) },
            suspend { repository.registerMovement(id(), MovementType.SALIDA, itemId, "1", today, "") },
            suspend { repository.registerMovement(id(), MovementType.CONSUMO, itemId, "1", today, "", parcelId = "no-existe") },
        ).forEach { attempt ->
            assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { attempt() } }
        }
        assertEquals(80f, stock())
    }

    @Test fun reintentoNoRestaDosVeces() = runTest {
        val movementId = id()
        repository.registerMovement(movementId, MovementType.CONSUMO, itemId, "10", today, "", parcelId = "p1")
        repository.registerMovement(movementId, MovementType.CONSUMO, itemId, "10", today, "", parcelId = "p1")
        assertEquals(70f, stock())
    }

    @Test fun laSincronizacionEnviaParcelaCicloYMotivo() = runTest {
        val consumption = id()
        val loss = id()
        repository.registerMovement(consumption, MovementType.CONSUMO, itemId, "2", today, "", parcelId = "p1")
        repository.registerMovement(loss, MovementType.PERDIDA, itemId, "1", today, "", lossReason = LossReason.MAL_MANEJO)
        val api = RecordingMovementApi()
        MovementSyncRepository(database, api) { "token" }.syncPending()

        val sentConsumption = api.sent.single { it.id == consumption }
        assertEquals("CONSUMO", sentConsumption.type)
        assertEquals("p1", sentConsumption.parcelId)
        assertEquals("c1", sentConsumption.cycleId)
        assertNull(sentConsumption.lossReason)
        val sentLoss = api.sent.single { it.id == loss }
        assertEquals("MAL_MANEJO", sentLoss.lossReason)
        assertNull(sentLoss.parcelId)
    }
}

private class RecordingMovementApi : MovementApi {
    val sent = mutableListOf<MovementDto>()
    override suspend fun send(authorization: String, movement: MovementDto): Response<Unit> {
        sent += movement
        return Response.success(201, Unit)
    }
}
