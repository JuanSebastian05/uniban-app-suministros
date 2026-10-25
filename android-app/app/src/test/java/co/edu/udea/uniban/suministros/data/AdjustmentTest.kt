package co.edu.udea.uniban.suministros.data

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.local.AdjustDirection
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.data.remote.MovementApi
import co.edu.udea.uniban.suministros.data.remote.MovementDto
import co.edu.udea.uniban.suministros.ui.inventory.EntryViewModel
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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

/** Ajustes (correcciones): suman o restan, exigen motivo, no dejan la existencia negativa y se sincronizan. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class AdjustmentTest {
    private lateinit var database: InventoryDatabase
    private lateinit var repository: InventoryRepository
    private lateinit var itemId: String
    private val today = LocalDate.of(2026, 10, 24)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InventoryDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = InventoryRepository(database)
        runBlocking {
            repository.initializeDemo()
            itemId = repository.inventory.first().first { it.name == "Urea 46%" }.id // 80 kg
        }
    }

    @After fun tearDown() = database.close()

    private fun id() = UUID.randomUUID().toString()
    private suspend fun stock() = repository.inventory.first().first { it.id == itemId }.quantity

    private suspend fun adjust(direction: AdjustDirection?, quantity: String, reason: String = "Conteo físico", movementId: String = id()) =
        repository.registerMovement(
            movementId, MovementType.AJUSTE, itemId, quantity, today, reason, adjustDirection = direction,
        )

    @Test fun ajusteDeAumentoSumaYGuardaElSentido() = runTest {
        val movementId = id()
        val saved = adjust(AdjustDirection.AUMENTO, "2,5", movementId = movementId)
        assertEquals(MovementType.AJUSTE, saved.type)
        assertEquals(82.5f, stock())
        val movement = database.inventoryDao().findMovement(movementId)!!
        assertEquals("AUMENTO", movement.adjustDirection)
        assertEquals("Conteo físico", movement.observation)
        assertEquals("PENDING", movement.syncStatus)
    }

    @Test fun ajusteDeDisminucionResta() = runTest {
        adjust(AdjustDirection.DISMINUCION, "5")
        assertEquals(75f, stock())
    }

    @Test fun ajusteDeDisminucionPuedeDejarLaExistenciaEnCero() = runTest {
        adjust(AdjustDirection.DISMINUCION, "80")
        assertEquals(0f, stock())
    }

    @Test fun ajusteDeDisminucionNoDejaExistenciaNegativa() = runTest {
        val movementId = id()
        assertThrows(InsufficientStockException::class.java) {
            runBlocking { adjust(AdjustDirection.DISMINUCION, "80,01", movementId = movementId) }
        }
        assertEquals(80f, stock())
        assertNull(database.inventoryDao().findMovement(movementId))
    }

    @Test fun exigeSentidoYMotivo() = runTest {
        listOf(
            suspend { adjust(null, "1") },
            suspend { adjust(AdjustDirection.AUMENTO, "1", reason = "") },
            suspend { adjust(AdjustDirection.AUMENTO, "1", reason = "   ") },
            suspend { adjust(AdjustDirection.AUMENTO, "0") },
        ).forEach { attempt -> assertThrows(IllegalArgumentException::class.java) { runBlocking { attempt() } } }
        assertEquals(80f, stock())
    }

    @Test fun reintentoNoAplicaElAjusteDosVeces() = runTest {
        val movementId = id()
        adjust(AdjustDirection.DISMINUCION, "10", movementId = movementId)
        adjust(AdjustDirection.DISMINUCION, "10", movementId = movementId)
        assertEquals(70f, stock())
    }

    @Test fun mismoIdConOtroSentidoSeRechaza() = runTest {
        val movementId = id()
        adjust(AdjustDirection.DISMINUCION, "10", movementId = movementId)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { adjust(AdjustDirection.AUMENTO, "10", movementId = movementId) }
        }
        assertEquals(70f, stock())
    }

    @Test fun otrosTiposIgnoranElSentido() = runTest {
        val movementId = id()
        repository.registerMovement(
            movementId, MovementType.ENTRADA, itemId, "1", today, "", adjustDirection = AdjustDirection.DISMINUCION,
        )
        assertEquals(81f, stock())
        assertNull(database.inventoryDao().findMovement(movementId)!!.adjustDirection)
    }

    @Test fun lasumaSignadaDelHistorialCuadraConLaExistencia() = runTest {
        repository.registerMovement(id(), MovementType.ENTRADA, itemId, "20", today, "Compra")
        adjust(AdjustDirection.DISMINUCION, "7,5")
        adjust(AdjustDirection.AUMENTO, "2")
        val balance = repository.balances.first().first { it.id == itemId }
        val summary = MovementHistory.summary(balance, repository.history.first())
        assertEquals(-5.5f, summary.others)
        assertEquals(94.5f, summary.expected)
        assertEquals(94.5f, summary.actual)
        assertTrue(summary.matches)
        val ajuste = repository.history.first().first { it.type == MovementType.AJUSTE && it.quantity == 7.5f }
        assertEquals("DISMINUCION", ajuste.adjustDirection)
    }

    @Test fun elHistorialPermiteFiltrarPorAjuste() = runTest {
        repository.registerMovement(id(), MovementType.ENTRADA, itemId, "1", today, "")
        adjust(AdjustDirection.AUMENTO, "1")
        val filtered = MovementHistory.filter(repository.history.first(), HistoryFilter(types = setOf(MovementType.AJUSTE)))
        assertEquals(1, filtered.size)
    }

    @Test fun laSincronizacionEnviaElSentido() = runTest {
        val up = id()
        val down = id()
        adjust(AdjustDirection.AUMENTO, "3", movementId = up)
        adjust(AdjustDirection.DISMINUCION, "1", movementId = down)
        val sent = mutableListOf<MovementDto>()
        val api = object : MovementApi {
            override suspend fun send(authorization: String, movement: MovementDto): Response<Unit> {
                sent += movement
                return Response.success(201, Unit)
            }
        }
        MovementSyncRepository(database, api) { "token" }.syncPending()
        assertEquals("AUMENTO", sent.single { it.id == up }.adjustDirection)
        assertEquals("DISMINUCION", sent.single { it.id == down }.adjustDirection)
        assertEquals("AJUSTE", sent.first().type)
    }

    // --- Formulario ---

    /**
     * Room responde en otro hilo y el ViewModel retoma en el hilo principal de Robolectric: se vacía su
     * looper hasta que termine de restaurar o de guardar (sin tocar `Dispatchers.Main`, que comparten
     * las pruebas de pantalla).
     */
    private fun EntryViewModel.settle() {
        val deadline = System.currentTimeMillis() + 5_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (!state.restoring && !state.saving) return
            Thread.sleep(10)
        } while (System.currentTimeMillis() < deadline)
    }

    private fun form() = EntryViewModel(repository, SavedStateHandle()).also {
        it.settle()
        it.changeType(MovementType.AJUSTE)
        it.selectItem(itemId)
        it.changeQuantity("4")
    }

    @Test fun elFormularioOfreceAjusteComoTipo() {
        assertTrue(MovementType.AJUSTE in MovementType.REGISTRABLE)
    }

    @Test fun formularioExigeSentidoYMotivo() = runTest {
        val vm = form()
        vm.save()
        vm.settle()
        assertNotNull(vm.state.reasonError)
        assertEquals(80f, stock())
        vm.selectAdjustDirection(AdjustDirection.DISMINUCION)
        vm.save()
        vm.settle()
        assertEquals("Describe el motivo del ajuste en la observación.", vm.state.reasonError)
        assertNull(vm.state.saved)
    }

    @Test fun formularioGuardaElAjuste() = runTest {
        val vm = form()
        vm.selectAdjustDirection(AdjustDirection.DISMINUCION)
        vm.changeObservation("Se derramó un bulto")
        vm.save()
        vm.settle()
        assertNotNull(vm.state.saved)
        assertEquals(76f, stock())
    }

    @Test fun formularioMuestraElErrorDeExistenciaInsuficiente() = runTest {
        val vm = form()
        vm.changeQuantity("90")
        vm.selectAdjustDirection(AdjustDirection.DISMINUCION)
        vm.changeObservation("Conteo")
        vm.save()
        vm.settle()
        assertNull(vm.state.saved)
        assertNotNull(vm.state.quantityError)
        assertEquals(80f, stock())
    }
}
