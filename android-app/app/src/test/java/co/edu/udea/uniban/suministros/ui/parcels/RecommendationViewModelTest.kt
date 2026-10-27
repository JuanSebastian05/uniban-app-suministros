package co.edu.udea.uniban.suministros.ui.parcels

import co.edu.udea.uniban.suministros.data.ParcelRecommendation
import co.edu.udea.uniban.suministros.data.RecommendationRefresh
import co.edu.udea.uniban.suministros.data.RecommendationResult
import co.edu.udea.uniban.suministros.data.RecommendationSource
import co.edu.udea.uniban.suministros.data.RecommendationSupply
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

/** ViewModel de las recomendaciones (HU_12) en JVM puro: repositorio falso y `viewModelScope` sobre un dispatcher de prueba. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationViewModelTest {
    private val source = FakeSource()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val sample = ParcelRecommendation(
        parcelId = "p1", period = "2026-W44", generatedAt = "2026-10-26T19:00:00Z", sourceVersion = "v1",
        volumeBunches = 1850.0, cutDeadline = "2026-11-02T14:30:00", meetsWindow = true, reason = null,
        supplies = listOf(RecommendationSupply("cajas", "2026-11-01", 120.0, 80.0, 40.0)),
    )

    @Test fun alAbrirConsultaLaUltimaGuardadaYMuestraCargandoMientrasTanto() {
        source.refreshGate = CompletableDeferred()
        val vm = RecommendationViewModel(source, "p1")

        assertTrue(vm.state.value.loading)
        assertNull(vm.state.value.recommendation)
        assertEquals(1, source.refreshCalls)

        source.refreshGate!!.complete(RecommendationRefresh.NONE)
        assertFalse(vm.state.value.loading)
    }

    @Test fun sinRecomendacionNiProblemaEsSinRecomendacionAun() {
        val state = RecommendationViewModel(source, "p1").state.value

        assertNull(state.recommendation)
        assertFalse(state.loading)
        assertFalse(state.requesting)
        assertNull(state.problem)
    }

    @Test fun laCopiaLocalSeMuestraDeInmediatoSinEsperarAlServidor() {
        source.local.value = sample
        source.refreshGate = CompletableDeferred()

        val state = RecommendationViewModel(source, "p1").state.value

        assertEquals(sample, state.recommendation)
        assertFalse(state.loading)
    }

    @Test fun laConsultaInicialTraeLaDelServidor() {
        source.onRefresh = {
            source.local.value = sample
            RecommendationRefresh.UPDATED
        }
        assertEquals(sample, RecommendationViewModel(source, "p1").state.value.recommendation)
    }

    @Test fun siFallaLaConsultaInicialSeQuedaConLaCopiaYNoMuestraProblema() {
        source.local.value = sample
        source.onRefresh = { RecommendationRefresh.FAILED }
        val state = RecommendationViewModel(source, "p1").state.value

        assertEquals(sample, state.recommendation)
        assertNull(state.problem)
        assertFalse(state.loading)
    }

    @Test fun unaExcepcionEnLaConsultaInicialNoRompeNada() {
        source.onRefresh = { throw IllegalStateException("boom") }
        val state = RecommendationViewModel(source, "p1").state.value
        assertFalse(state.loading)
        assertNull(state.problem)
    }

    @Test fun pedirMuestraEsperandoYAlTerminarMuestraLaNueva() {
        val vm = RecommendationViewModel(source, "p1")
        source.requestGate = CompletableDeferred()

        vm.request()
        assertTrue(vm.state.value.requesting)
        assertEquals(1, source.requestCalls)

        source.local.value = sample
        source.requestGate!!.complete(RecommendationResult.Saved)
        assertFalse(vm.state.value.requesting)
        assertEquals(sample, vm.state.value.recommendation)
        assertNull(vm.state.value.problem)
    }

    @Test fun mientrasEspiraNoSePideDeNuevo() {
        val vm = RecommendationViewModel(source, "p1")
        source.requestGate = CompletableDeferred()
        vm.request()
        vm.request()
        vm.request()
        assertEquals(1, source.requestCalls)
        source.requestGate!!.complete(RecommendationResult.Saved)
        vm.request()
        assertEquals(2, source.requestCalls)
    }

    @Test fun cadaResultadoFallidoSeTraduceAUnProblema() {
        val expected = mapOf(
            RecommendationResult.NoAforamiento to RecommendationProblem.NO_AFORAMIENTO,
            RecommendationResult.ServiceUnavailable to RecommendationProblem.SERVICE_UNAVAILABLE,
            RecommendationResult.Offline to RecommendationProblem.OFFLINE,
            RecommendationResult.SessionInvalid to RecommendationProblem.SESSION,
            RecommendationResult.Failed to RecommendationProblem.OTHER,
        )
        expected.forEach { (result, problem) ->
            val fresh = FakeSource().apply { onRequest = { result } }
            val vm = RecommendationViewModel(fresh, "p1")
            vm.request()
            assertEquals("$result", problem, vm.state.value.problem)
            assertFalse(vm.state.value.requesting)
        }
    }

    @Test fun unProblemaNoOcultaLaUltimaCopia() {
        source.local.value = sample
        source.onRequest = { RecommendationResult.Offline }
        val vm = RecommendationViewModel(source, "p1")
        vm.request()

        assertEquals(sample, vm.state.value.recommendation)
        assertEquals(RecommendationProblem.OFFLINE, vm.state.value.problem)
    }

    @Test fun reintentarLimpiaElProblemaYPuedeFuncionar() {
        source.onRequest = { RecommendationResult.ServiceUnavailable }
        val vm = RecommendationViewModel(source, "p1")
        vm.request()
        assertEquals(RecommendationProblem.SERVICE_UNAVAILABLE, vm.state.value.problem)

        source.requestGate = CompletableDeferred()
        vm.request()
        assertNull(vm.state.value.problem)
        assertTrue(vm.state.value.requesting)

        source.local.value = sample
        source.requestGate!!.complete(RecommendationResult.Saved)
        assertEquals(sample, vm.state.value.recommendation)
        assertNull(vm.state.value.problem)
    }

    @Test fun unaExcepcionAlPedirEsUnProblemaGenericoYLiberaElBoton() {
        source.onRequest = { throw IllegalStateException("boom") }
        val vm = RecommendationViewModel(source, "p1")
        vm.request()

        assertEquals(RecommendationProblem.OTHER, vm.state.value.problem)
        assertFalse(vm.state.value.requesting)
    }

    @Test fun usaLaParcelaIndicada() {
        val vm = RecommendationViewModel(source, "otra-parcela")
        vm.request()
        assertEquals("otra-parcela", source.lastRefreshParcel)
        assertEquals("otra-parcela", source.lastRequestParcel)
    }

    @Test fun losMensajesDeProblemaSonSencillosYEnEspanol() {
        RecommendationProblem.entries.forEach {
            val text = problemMessage(it)
            assertTrue(it.name, text.isNotBlank())
            assertFalse(it.name, text.contains("503") || text.contains("502") || text.contains("HTTP") || text.contains("Equipo 2"))
        }
    }

    private class FakeSource : RecommendationSource {
        val local = MutableStateFlow<ParcelRecommendation?>(null)
        var onRefresh: suspend () -> RecommendationRefresh = { RecommendationRefresh.NONE }
        var onRequest: suspend () -> RecommendationResult = { RecommendationResult.Saved }
        var refreshGate: CompletableDeferred<RecommendationRefresh>? = null
        var requestGate: CompletableDeferred<RecommendationResult>? = null
        var refreshCalls = 0
        var requestCalls = 0
        var lastRefreshParcel: String? = null
        var lastRequestParcel: String? = null

        override fun observeRecommendation(parcelId: String): Flow<ParcelRecommendation?> = local

        override suspend fun refreshRecommendation(parcelId: String): RecommendationRefresh {
            refreshCalls++
            lastRefreshParcel = parcelId
            return refreshGate?.await() ?: onRefresh()
        }

        override suspend fun requestRecommendation(parcelId: String): RecommendationResult {
            requestCalls++
            lastRequestParcel = parcelId
            return requestGate?.await() ?: onRequest()
        }
    }
}
