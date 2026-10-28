package co.edu.udea.uniban.suministros.data

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.local.ActivityType
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.data.local.ParcelEntity
import co.edu.udea.uniban.suministros.data.local.ProductionCycleEntity
import co.edu.udea.uniban.suministros.data.local.SyncStatus
import co.edu.udea.uniban.suministros.data.remote.ActivityApi
import co.edu.udea.uniban.suministros.data.remote.ActivityDto
import co.edu.udea.uniban.suministros.data.remote.MovementApi
import co.edu.udea.uniban.suministros.data.remote.MovementDto
import co.edu.udea.uniban.suministros.ui.activities.ActivityFormViewModel
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Response

/** Actividades productivas (HU_08): se guardan con sus consumos en una transacción y se sincronizan primero. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ActivityTest {
    private lateinit var database: InventoryDatabase
    private lateinit var repository: InventoryRepository
    private lateinit var ureaId: String // 80 kg
    private lateinit var otherId: String
    private val today = LocalDate.of(2026, 10, 24)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InventoryDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = InventoryRepository(database)
        runBlocking {
            repository.initializeDemo()
            val items = repository.inventory.first()
            ureaId = items.first { it.name == "Urea 46%" }.id
            otherId = items.first { it.id != ureaId }.id
            database.parcelDao().upsertParcels(listOf(
                ParcelEntity("p1", "La Esperanza", "2.40", "PRODUCCION", "c1"), ParcelEntity("p2", "El Mirador", "1.50"),
            ))
            database.parcelDao().upsertCycles(listOf(ProductionCycleEntity("c1", "p1", "2026-01-15", null, "PRODUCCION")))
        }
    }

    @After fun tearDown() = database.close()

    private fun id() = UUID.randomUUID().toString()
    private suspend fun stock(inventoryId: String = ureaId) = repository.inventory.first().first { it.id == inventoryId }.quantity
    private suspend fun register(
        activityId: String = id(), parcelId: String = "p1", type: ActivityType = ActivityType.FERTILIZACION,
        observation: String = "Primera aplicación", supplies: List<ActivitySupply> = emptyList(),
    ) = repository.registerActivity(activityId, parcelId, type, today, observation, supplies)

    // --- Registro ---

    @Test fun guardaLaActividadConSuTipoFechaCicloYEstadoPendiente() = runTest {
        database.parcelDao().updateActiveCycleStatus("p1", "EN_PREPARACION")
        val activityId = id()
        val saved = register(activityId, type = ActivityType.SIEMBRA, observation = "  Lote norte ")
        assertEquals(ActivityType.SIEMBRA, saved.type)
        assertEquals("c1", saved.cycleId)
        val entity = database.activityDao().findActivity(activityId)!!
        assertEquals("p1", entity.parcelId)
        assertEquals("c1", entity.cycleId)
        assertEquals("SIEMBRA", entity.type)
        assertEquals(today.toString(), entity.date)
        assertEquals("Lote norte", entity.observation)
        assertEquals("PENDING", entity.syncStatus)
    }

    @Test fun laObservacionEsOpcional() = runTest {
        val activityId = id()
        register(activityId, observation = "")
        assertEquals("", database.activityDao().findActivity(activityId)!!.observation)
    }

    @Test fun exigeUnCicloIniciado() = runTest {
        val activityId = id()
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { register(activityId, parcelId = "p2") }
        }
        assertTrue(error.message!!.contains("ciclo productivo"))
        assertNull(database.activityDao().findActivity(activityId))
    }

    @Test fun exigeUnaParcelaExistente() = runTest {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { register(parcelId = "no-existe") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { register(parcelId = "") } }
    }

    @Test fun observacionDeMasDe500CaracteresSeRechaza() = runTest {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { register(observation = "x".repeat(501)) } }
    }

    // --- Consumos en el mismo flujo ---

    @Test fun losInsumosUsadosSeGuardanComoConsumosDeLaActividad() = runTest {
        val activityId = id()
        val saved = register(activityId, supplies = listOf(
            ActivitySupply(ureaId, "12,5"), ActivitySupply(otherId, "1"),
        ))
        assertEquals(2, saved.consumptions)
        assertEquals(67.5f, stock())
        val consumptions = database.inventoryDao().observeMovements().first().filter { it.activityId == activityId }
        assertEquals(2, consumptions.size)
        consumptions.forEach {
            assertEquals(MovementType.CONSUMO, it.type)
            assertEquals("p1", it.parcelId)
            assertEquals("c1", it.cycleId)
            assertEquals("PENDING", it.syncStatus)
            assertEquals(today.toString(), it.date)
        }
    }

    @Test fun sinInsumosSoloSeGuardaLaActividad() = runTest {
        register()
        assertEquals(80f, stock())
        assertTrue(database.inventoryDao().observeMovements().first().isEmpty())
    }

    @Test fun siUnInsumoNoAlcanzaNoSeGuardaNada() = runTest {
        val activityId = id()
        val error = assertThrows(ActivityStockException::class.java) {
            runBlocking {
                register(activityId, supplies = listOf(ActivitySupply(otherId, "1"), ActivitySupply(ureaId, "80,01")))
            }
        }
        assertEquals(ureaId, error.inventoryId)
        assertEquals(80f, error.available)
        assertEquals(80f, stock())
        assertNull(database.activityDao().findActivity(activityId))
        assertTrue(database.inventoryDao().observeMovements().first().isEmpty())
    }

    @Test fun noPermiteRepetirElMismoInsumo() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { register(supplies = listOf(ActivitySupply(ureaId, "1"), ActivitySupply(ureaId, "2"))) }
        }
        assertEquals(80f, stock())
    }

    @Test fun cantidadInvalidaNoGuardaNada() = runTest {
        val activityId = id()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { register(activityId, supplies = listOf(ActivitySupply(ureaId, "0"))) }
        }
        assertNull(database.activityDao().findActivity(activityId))
    }

    @Test fun reintentarLaMismaActividadNoDuplicaNiResta() = runTest {
        val activityId = id()
        val supplies = listOf(ActivitySupply(ureaId, "10"))
        register(activityId, supplies = supplies)
        register(activityId, supplies = supplies)
        assertEquals(70f, stock())
        assertEquals(1, database.inventoryDao().observeMovements().first().size)
        assertEquals(1, database.activityDao().observeActivities().first().size)
    }

    @Test fun mismoIdConOtroContenidoSeRechaza() = runTest {
        val activityId = id()
        register(activityId, type = ActivityType.FERTILIZACION)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { register(activityId, type = ActivityType.COSECHA) }
        }
    }

    @Test fun elConsumoSueltoDelInventarioSigueFuncionando() = runTest {
        val id = id()
        repository.registerMovement(id, MovementType.CONSUMO, ureaId, "5", today, "", parcelId = "p1")
        assertNull(database.inventoryDao().findMovement(id)!!.activityId)
        assertEquals(75f, stock())
    }

    @Test fun elConsumoDeUnaActividadUsaElCicloDeLaActividadAunqueLaParcelaYaTengaOtro() = runTest {
        val activityId = id()
        register(activityId)
        // El ciclo c1 se cierra y se inicia c2 mientras tanto.
        database.parcelDao().upsertCycles(listOf(
            ProductionCycleEntity("c1", "p1", "2026-01-15", "2026-10-20", "CERRADO"),
            ProductionCycleEntity("c2", "p1", "2026-10-21", null, "EN_PREPARACION"),
        ))
        database.parcelDao().updateLifecycleState("p1", "EN_PREPARACION", "c2")
        val movementId = id()
        repository.registerMovement(
            movementId, MovementType.CONSUMO, ureaId, "1", today, "", parcelId = "p1", activityId = activityId,
        )
        assertEquals("c1", database.inventoryDao().findMovement(movementId)!!.cycleId)
    }

    @Test fun unConsumoNoPuedeApuntarALaActividadDeOtraParcela() = runTest {
        val activityId = id()
        register(activityId)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.registerMovement(id(), MovementType.CONSUMO, ureaId, "1", today, "", parcelId = "p2", activityId = activityId)
            }
        }
    }

    // --- Consulta: detalle de parcela, lista, historial y pendientes ---

    @Test fun lasActividadesSeListanPorParcelaConSusConsumos() = runTest {
        val first = id()
        register(first, supplies = listOf(ActivitySupply(ureaId, "2")))
        val parcelActivities = database.activityDao().observeParcelActivities("p1").first()
        assertEquals(1, parcelActivities.size)
        assertEquals(first, parcelActivities.single().id)
        assertEquals(1, parcelActivities.single().consumptionCount)
        assertTrue(database.activityDao().observeParcelActivities("p2").first().isEmpty())
        val all = database.activityDao().observeActivities().first()
        assertEquals("La Esperanza", all.single().parcelName)
    }

    @Test fun elHistorialMuestraLaActividadDeCadaConsumo() = runTest {
        register(type = ActivityType.APLICACION_INSUMO, supplies = listOf(ActivitySupply(ureaId, "2")))
        repository.registerMovement(id(), MovementType.CONSUMO, ureaId, "1", today, "", parcelId = "p1")
        val history = repository.history.first().filter { it.type == MovementType.CONSUMO }
        assertEquals(setOf("APLICACION_INSUMO", null), history.map { it.activityType }.toSet())
    }

    @Test fun elContadorDePendientesSumaActividadesYMovimientos() = runTest {
        assertEquals(0, repository.pendingCount.first())
        register(supplies = listOf(ActivitySupply(ureaId, "2")))
        assertEquals(2, repository.pendingCount.first()) // la actividad y su consumo
        register()
        assertEquals(3, repository.pendingCount.first())
    }

    @Test fun refrescarLasParcelasNoBorraLasActividades() = runTest {
        val activityId = id()
        register(activityId)
        database.parcelDao().deleteAllParcels() // lo que hace ParcelRepository.refresh()
        assertNotNull(database.activityDao().findActivity(activityId))
        assertNull(database.activityDao().observeActivities().first().single().parcelName)
    }

    // --- Sincronización: la actividad viaja antes que sus consumos ---

    private class Recorder(
        var activityCode: Int = 201,
        var movementCode: Int = 201,
    ) : ActivityApi, MovementApi {
        val calls = mutableListOf<String>()
        val activities = mutableListOf<ActivityDto>()
        val movements = mutableListOf<MovementDto>()

        override suspend fun send(authorization: String, activity: ActivityDto): Response<Unit> {
            calls += "A:${activity.id}"
            activities += activity
            return response(activityCode)
        }

        override suspend fun send(authorization: String, movement: MovementDto): Response<Unit> {
            calls += "M:${movement.id}"
            movements += movement
            return response(movementCode)
        }

        private fun response(code: Int): Response<Unit> =
            if (code in 200..299) Response.success(code, Unit)
            else Response.error(code, "{}".toResponseBody())
    }

    private fun syncRepo(api: Recorder?, withActivities: Boolean = true) =
        MovementSyncRepository(database, api ?: Recorder(), if (withActivities) api else null) { "token" }

    @Test fun laSincronizacionEnviaPrimeroLaActividadYLuegoSusConsumos() = runTest {
        val activityId = id()
        register(activityId, type = ActivityType.FERTILIZACION, observation = "Lote 1", supplies = listOf(ActivitySupply(ureaId, "3")))
        val api = Recorder()
        val summary = syncRepo(api).syncPending()
        assertEquals(2, summary.synced)
        assertTrue(api.calls.first().startsWith("A:"))
        val sent = api.activities.single()
        assertEquals(activityId, sent.id)
        assertEquals("p1", sent.parcelId)
        assertEquals("c1", sent.cycleId)
        assertEquals("FERTILIZACION", sent.type)
        assertEquals("Lote 1", sent.observation)
        assertEquals(activityId, api.movements.single().activityId)
        assertEquals(SyncStatus.SYNCED, database.activityDao().findActivity(activityId)!!.syncStatus)
        assertEquals(0, repository.pendingCount.first())
    }

    @Test fun siLaActividadNoLlegaSusConsumosEsperan() = runTest {
        register(supplies = listOf(ActivitySupply(ureaId, "3")))
        val api = Recorder(activityCode = 503)
        val summary = syncRepo(api).syncPending()
        assertTrue(summary.retryLater)
        assertTrue(api.movements.isEmpty())
        assertEquals(1, database.inventoryDao().observeMovements().first().count { it.syncStatus != SyncStatus.SYNCED })
    }

    @Test fun siElServidorRechazaLaActividadSusConsumosNoSeEnvian() = runTest {
        val activityId = id()
        register(activityId, supplies = listOf(ActivitySupply(ureaId, "3")))
        val api = Recorder(activityCode = 409)
        syncRepo(api).syncPending()
        assertEquals(SyncStatus.ERROR, database.activityDao().findActivity(activityId)!!.syncStatus)
        assertTrue(api.movements.isEmpty())
    }

    @Test fun losConsumosSueltosSiguenSincronizandoSinActividades() = runTest {
        repository.registerMovement(id(), MovementType.CONSUMO, ureaId, "1", today, "", parcelId = "p1")
        val api = Recorder()
        assertEquals(1, syncRepo(api).syncPending().synced)
        assertTrue(api.activities.isEmpty())
    }

    @Test fun sinApiDeActividadesLosConsumosDeUnaActividadEsperan() = runTest {
        register(supplies = listOf(ActivitySupply(ureaId, "3")))
        val api = Recorder()
        assertEquals(0, syncRepo(api, withActivities = false).syncPending().synced)
        assertTrue(api.movements.isEmpty())
    }

    @Test fun unReintentoRetomaLoPendiente() = runTest {
        register(supplies = listOf(ActivitySupply(ureaId, "3")))
        val api = Recorder(activityCode = 503)
        syncRepo(api).syncPending()
        api.activityCode = 201
        assertEquals(2, syncRepo(api).syncPending().synced)
    }
}
