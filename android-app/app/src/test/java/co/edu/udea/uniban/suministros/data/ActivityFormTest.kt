package co.edu.udea.uniban.suministros.data

import androidx.lifecycle.SavedStateHandle
import co.edu.udea.uniban.suministros.data.local.ActivityType
import co.edu.udea.uniban.suministros.ui.activities.ActivityFormViewModel
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Registrador en memoria: anota las llamadas y responde lo que cada prueba disponga. */
class FakeActivityRegistrar : ActivityRegistrar {
    data class Call(
        val id: String, val parcelId: String, val type: ActivityType, val date: LocalDate,
        val observation: String, val supplies: List<ActivitySupply>,
    )

    val calls = mutableListOf<Call>()
    var answer: suspend (Call) -> SavedActivity = { SavedActivity(it.id, it.type, "c1", it.supplies.size) }

    override suspend fun registerActivity(
        id: String, parcelId: String, type: ActivityType, date: LocalDate, observation: String,
        supplies: List<ActivitySupply>,
    ): SavedActivity {
        val call = Call(id, parcelId, type, date, observation, supplies)
        calls += call
        return answer(call)
    }
}

/**
 * Formulario «Registrar actividad» (HU_08): validaciones y llamada al repositorio. JVM puro, como los demás
 * ViewModel del proyecto; la transacción con Room se prueba en [ActivityTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityFormTest {
    private val registrar = FakeActivityRegistrar()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun form(parcelId: String = "p1", saved: SavedStateHandle = SavedStateHandle()) =
        ActivityFormViewModel(registrar, saved).also { if (parcelId.isNotEmpty()) it.selectParcel(parcelId) }

    @Test fun exigeParcelaYTipo() {
        val vm = ActivityFormViewModel(registrar, SavedStateHandle())
        vm.save()
        assertEquals("Selecciona la parcela.", vm.state.parcelError)
        assertEquals("Selecciona el tipo de actividad.", vm.state.typeError)
        assertTrue(registrar.calls.isEmpty())
    }

    @Test fun laParcelaInicialSeTomaDeLosArgumentosDeNavegacion() {
        assertEquals("p9", ActivityFormViewModel(registrar, SavedStateHandle(mapOf("parcelId" to "p9"))).state.parcelId)
        assertEquals("p7", ActivityFormViewModel(registrar, SavedStateHandle(), initialParcelId = "p7").state.parcelId)
    }

    @Test fun validaLasFilasDeInsumos() {
        val vm = form().also { it.selectType(ActivityType.FERTILIZACION); it.addRow(); it.addRow(); it.addRow() }
        val (first, second, third) = vm.state.rows
        vm.selectRowItem(first.key, "urea")
        vm.selectRowItem(second.key, "urea")
        vm.changeRowQuantity(first.key, "0")
        vm.changeRowQuantity(second.key, "2")
        vm.save()
        assertEquals("Este insumo ya está en la lista.", vm.state.rows[0].itemError)
        assertEquals("Este insumo ya está en la lista.", vm.state.rows[1].itemError)
        assertNotNull(vm.state.rows[0].quantityError) // 0
        assertNull(vm.state.rows[1].quantityError)
        assertEquals("Selecciona un insumo.", vm.state.rows[2].itemError)
        assertNotNull(vm.state.rows[2].quantityError) // vacía
        assertEquals(third.key, vm.state.rows[2].key)
        assertTrue(registrar.calls.isEmpty())
    }

    @Test fun guardaLaActividadConSusInsumos() {
        val vm = form().also { it.selectType(ActivityType.FERTILIZACION); it.changeObservation("Con lluvia"); it.addRow() }
        val key = vm.state.rows.single().key
        vm.selectRowItem(key, "urea")
        vm.changeRowQuantity(key, "4")
        vm.changeDate(LocalDate.of(2026, 10, 24))
        vm.save()
        val call = registrar.calls.single()
        assertEquals("p1", call.parcelId)
        assertEquals(ActivityType.FERTILIZACION, call.type)
        assertEquals(LocalDate.of(2026, 10, 24), call.date)
        assertEquals("Con lluvia", call.observation)
        assertEquals(listOf(ActivitySupply("urea", "4")), call.supplies)
        assertNotNull(vm.state.saved)
        assertNull(vm.state.error)
    }

    @Test fun losInsumosSonOpcionales() {
        val vm = form().also { it.selectType(ActivityType.SIEMBRA) }
        vm.save()
        assertTrue(registrar.calls.single().supplies.isEmpty())
        assertNotNull(vm.state.saved)
    }

    @Test fun marcaLaFilaQueNoAlcanzaYNoDaPorGuardada() {
        registrar.answer = { throw ActivityStockException("urea", 80f) }
        val vm = form().also { it.selectType(ActivityType.FERTILIZACION); it.addRow(); it.addRow() }
        val (urea, other) = vm.state.rows
        vm.selectRowItem(urea.key, "urea"); vm.changeRowQuantity(urea.key, "90")
        vm.selectRowItem(other.key, "otro"); vm.changeRowQuantity(other.key, "1")
        vm.save()
        assertNull(vm.state.saved)
        assertFalse(vm.state.saving)
        assertNotNull(vm.state.rows[0].quantityError)
        assertNull(vm.state.rows[1].quantityError)
    }

    @Test fun muestraElErrorQueDevuelveElRepositorio() {
        registrar.answer = { throw IllegalArgumentException("La parcela no tiene un ciclo activo.") }
        val vm = form("p2").also { it.selectType(ActivityType.SIEMBRA) }
        vm.save()
        assertEquals("La parcela no tiene un ciclo activo.", vm.state.error)
        assertNull(vm.state.saved)
        assertFalse(vm.state.saving)
    }

    @Test fun unFalloInesperadoConservaLosDatos() {
        registrar.answer = { throw IllegalStateException("boom") }
        val vm = form().also { it.selectType(ActivityType.COSECHA); it.changeObservation("Lote 1") }
        vm.save()
        assertEquals("No se pudo guardar. Tus datos siguen aquí; intenta de nuevo.", vm.state.error)
        assertEquals("Lote 1", vm.state.observation)
    }

    @Test fun elBotonSeBloqueaMientrasGuarda() {
        val gate = CompletableDeferred<SavedActivity>()
        registrar.answer = { gate.await() }
        val vm = form().also { it.selectType(ActivityType.COSECHA) }
        vm.save()
        vm.save()
        assertEquals(1, registrar.calls.size)
        assertTrue(vm.state.saving)
        gate.complete(SavedActivity("x", ActivityType.COSECHA, "c1", 0))
        assertFalse(vm.state.saving)
        assertNotNull(vm.state.saved)
    }

    @Test fun reintentarUsaElMismoIdentificador() {
        registrar.answer = { throw IllegalStateException("sin red") }
        val vm = form().also { it.selectType(ActivityType.COSECHA) }
        vm.save()
        vm.save()
        assertEquals(2, registrar.calls.size)
        assertEquals(registrar.calls[0].id, registrar.calls[1].id)
    }

    @Test fun guardadoUnaVezNoSeVuelveAGuardar() {
        val vm = form().also { it.selectType(ActivityType.COSECHA) }
        vm.save()
        vm.save()
        assertEquals(1, registrar.calls.size)
    }

    @Test fun permiteQuitarFilas() {
        val vm = form().also { it.addRow(); it.addRow() }
        vm.removeRow(vm.state.rows.first().key)
        assertEquals(1, vm.state.rows.size)
    }

    @Test fun elTipoLaFechaYLaObservacionSobrevivenALaRecreacion() {
        val saved = SavedStateHandle()
        form(saved = saved).also {
            it.selectType(ActivityType.COSECHA); it.changeDate(LocalDate.of(2026, 10, 20)); it.changeObservation("Lote 2")
        }
        val restored = form(parcelId = "", saved = saved).state
        assertEquals("p1", restored.parcelId)
        assertEquals(ActivityType.COSECHA, restored.type)
        assertEquals(LocalDate.of(2026, 10, 20), restored.date)
        assertEquals("Lote 2", restored.observation)
    }

    @Test fun laObservacionSeLimitaA500Caracteres() {
        val vm = form()
        vm.changeObservation("x".repeat(600))
        assertEquals(500, vm.state.observation.length)
    }
}
