package co.edu.udea.uniban.suministros.ui.parcels

import co.edu.udea.uniban.suministros.data.AforamientoRefresh
import co.edu.udea.uniban.suministros.data.AforamientoSource
import co.edu.udea.uniban.suministros.data.local.AforamientoEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** ViewModel del aforamiento (HU_09) en JVM puro: repositorio falso y `viewModelScope` sobre un dispatcher de prueba. */
@OptIn(ExperimentalCoroutinesApi::class)
class AforamientoViewModelTest {
    private val source = FakeSource()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val sample = AforamientoEntity("p1", "a1", "2026-10-29", "2.41", 1850, "0.870")

    @Test fun consultaAlAbrirYMuestraCargandoMientrasTanto() {
        source.gate = CompletableDeferred()
        val vm = AforamientoViewModel(source, "p1")

        assertTrue(vm.state.value.loading)
        assertNull(vm.state.value.aforamiento)
        assertEquals(1, source.refreshCalls)

        source.gate!!.complete(AforamientoRefresh.NONE)
        assertFalse(vm.state.value.loading)
    }

    @Test fun conAforamientoNuevoDelServidorLoMuestra() {
        source.onRefresh = {
            source.local.value = sample
            AforamientoRefresh.UPDATED
        }
        val state = AforamientoViewModel(source, "p1").state.value

        assertEquals(sample, state.aforamiento)
        assertFalse(state.loading)
        assertFalse(state.refreshFailed)
    }

    @Test fun sinAforamientosLoIndica() {
        source.onRefresh = { AforamientoRefresh.NONE }
        val state = AforamientoViewModel(source, "p1").state.value

        assertNull(state.aforamiento)
        assertFalse(state.loading)
        assertFalse(state.refreshFailed) // → «Sin aforamiento recibido»
    }

    @Test fun sinConexionYConCopiaMuestraLaUltimaCopia() {
        source.local.value = sample
        source.onRefresh = { AforamientoRefresh.FAILED }
        val state = AforamientoViewModel(source, "p1").state.value

        assertEquals(sample, state.aforamiento)
        assertFalse(state.loading)
        assertTrue(state.refreshFailed)
    }

    @Test fun laCopiaLocalSeMuestraDeInmediatoSinEsperarAlServidor() {
        source.local.value = sample
        source.gate = CompletableDeferred()
        val state = AforamientoViewModel(source, "p1").state.value

        assertEquals(sample, state.aforamiento)
        assertFalse(state.loading)
    }

    @Test fun sinConexionYSinCopiaEsUnError() {
        source.onRefresh = { AforamientoRefresh.FAILED }
        val state = AforamientoViewModel(source, "p1").state.value

        assertNull(state.aforamiento)
        assertFalse(state.loading)
        assertTrue(state.refreshFailed)
    }

    @Test fun unaExcepcionInesperadaSeTrataComoFallo() {
        source.onRefresh = { throw IllegalStateException("boom") }
        val state = AforamientoViewModel(source, "p1").state.value
        assertTrue(state.refreshFailed)
    }

    @Test fun reintentarVuelveACargarYSePuedeRecuperar() {
        source.onRefresh = { AforamientoRefresh.FAILED }
        val vm = AforamientoViewModel(source, "p1")
        assertTrue(vm.state.value.refreshFailed)

        source.gate = CompletableDeferred()
        vm.reload()
        assertTrue(vm.state.value.loading)
        assertFalse(vm.state.value.refreshFailed)

        source.local.value = sample
        source.gate!!.complete(AforamientoRefresh.UPDATED)
        assertEquals(sample, vm.state.value.aforamiento)
        assertFalse(vm.state.value.refreshFailed)
        assertEquals(2, source.refreshCalls)
    }

    @Test fun pideLaParcelaCorrecta() {
        AforamientoViewModel(source, "otra-parcela")
        assertEquals("otra-parcela", source.lastParcelId)
    }

    private class FakeSource : AforamientoSource {
        val local = MutableStateFlow<AforamientoEntity?>(null)
        var onRefresh: suspend () -> AforamientoRefresh = { AforamientoRefresh.NONE }
        var gate: CompletableDeferred<AforamientoRefresh>? = null
        var refreshCalls = 0
        var lastParcelId: String? = null

        override fun observeAforamiento(parcelId: String): Flow<AforamientoEntity?> = local

        override suspend fun refreshAforamiento(parcelId: String): AforamientoRefresh {
            refreshCalls++
            lastParcelId = parcelId
            return gate?.await() ?: onRefresh()
        }
    }
}
