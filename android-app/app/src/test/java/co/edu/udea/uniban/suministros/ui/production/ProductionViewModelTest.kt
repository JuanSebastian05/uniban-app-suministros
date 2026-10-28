package co.edu.udea.uniban.suministros.ui.production

import androidx.lifecycle.SavedStateHandle
import co.edu.udea.uniban.suministros.data.*
import co.edu.udea.uniban.suministros.data.local.*
import co.edu.udea.uniban.suministros.data.remote.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

class FakeProductionSource : ProductionSource {
    val calls = mutableListOf<String>()
    var fail = false
    var lastException = false
    var lastQuantity = 0
    val state = MutableStateFlow(productionFixture())
    override fun observe(parcelId: String) = state
    override suspend fun refresh(parcelId: String) = true
    override suspend fun harvest(id: String, planId: String, quantity: Int, date: LocalDate, observation: String, exceptionConfirmed: Boolean) {
        calls += id; lastQuantity = quantity; lastException = exceptionConfirmed
        if (fail) throw IllegalArgumentException("No se pudo guardar")
    }
    override suspend fun dispatch(id: String, planId: String, quantity: Int, at: OffsetDateTime, vehicle: String, order: Int, observation: String) { calls += id }
}

fun productionFixture(): ProductionOverview {
    val plan = RecommendationDto("r1", "p1", "2026-W44", "2026-10-28T12:00:00Z", "test", 145.6,
        "2026-10-28T16:00:00", true, RecommendationPlanDto(null, "p1", "2026-10-28T08:00:00", "V-3", 1, 146, true),
        listOf(RecommendationSupplyDto("cajas", "2026-10-28", 100.0, 0.0, 100.0)), aforamientoId = "af1", cycleId = "c1")
    val reference = requireNotNull(plan.productionPlan(Gson()))
    return ProductionOverview(loading = false, periods = listOf(ProductionPeriod(reference, plan, 100, 0)))
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionViewModelTest {
    @Before fun before() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun after() = Dispatchers.resetMain()

    @Test fun reintentoTrasRecrearFormularioConservaIdentidadYCantidad() {
        val source = FakeProductionSource().apply { fail = true }
        val saved = SavedStateHandle(mapOf("parcelId" to "p1"))
        val first = ProductionViewModel(source, saved)
        first.selectPlan(source.state.value.periods.single())
        first.change(first.form.copy(quantity = "46", exceptionConfirmed = true))
        first.save()
        val restored = ProductionViewModel(source, saved)
        assertEquals("46", restored.form.quantity)
        assertTrue(restored.form.exceptionConfirmed)
        restored.save()
        assertEquals(2, source.calls.size)
        assertEquals(source.calls[0], source.calls[1])
    }
    @Test fun rechazaFraccionesAntesDeLlamarAlRepositorio() {
        val source = FakeProductionSource()
        val vm = ProductionViewModel(source, SavedStateHandle())
        vm.selectPlan(source.state.value.periods.single())
        vm.change(vm.form.copy(quantity = "1.5"))
        vm.save()
        assertTrue(source.calls.isEmpty())
        assertNotNull(vm.error)
    }
    @Test fun guardadoExitosoLimpiaFormularioYCreaIdentidadParaLaSiguienteOperacion() {
        val source = FakeProductionSource()
        val vm = ProductionViewModel(source, SavedStateHandle())
        vm.selectPlan(source.state.value.periods.single())
        vm.save()
        assertEquals(45, source.lastQuantity)
        assertEquals("", vm.form.quantity)
        vm.change(vm.form.copy(quantity = "1"))
        vm.save()
        assertNotEquals(source.calls[0], source.calls[1])
    }
}
