package co.edu.udea.uniban.suministros.ui.activities

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import co.edu.udea.uniban.suministros.data.FakeActivityRegistrar
import co.edu.udea.uniban.suministros.data.local.ActivityListItem
import co.edu.udea.uniban.suministros.data.local.InventoryItem
import co.edu.udea.uniban.suministros.data.local.ParcelActivity
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import co.edu.udea.uniban.suministros.ui.inventory.InventoryUiState
import co.edu.udea.uniban.suministros.ui.parcels.ParcelDetailScreen
import co.edu.udea.uniban.suministros.ui.parcels.ParcelsUiState
import co.edu.udea.uniban.suministros.ui.theme.UnibanTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pantallas de actividades (HU_08): lista, formulario y sección en el detalle de la parcela. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
class ActivityScreensTest {
    @get:Rule val compose = createComposeRule()
    private val viewModel = ActivityFormViewModel(FakeActivityRegistrar(), SavedStateHandle())

    private val withCycle = ParcelSummary("p1", "La Esperanza", "2.40", "c1", "2026-01-15", "PRODUCCION")
    private val withoutCycle = ParcelSummary("p2", "El Mirador", "1.50", null, null, null)
    private val urea = InventoryItem("i1", "Urea 46%", "Fertilizante", "kg", "Inventario principal", 80f)

    private fun showForm(parcels: List<ParcelSummary> = listOf(withCycle, withoutCycle)) = compose.setContent {
        UnibanTheme {
            ActivityFormScreen(
                inventoryState = InventoryUiState(loading = false, items = listOf(urea)),
                parcels = parcels, viewModel = viewModel,
                onBack = {}, onDone = {}, onRetryInventory = {},
            )
        }
    }

    // --- Lista ---

    @Test fun laListaIndicaQueNoHayActividades() {
        compose.setContent { UnibanTheme { ActivitiesScreen(emptyList(), 0, {}, {}) } }
        compose.onNodeWithTag("no-activities").assertIsDisplayed()
    }

    @Test fun laListaMuestraTipoParcelaFechaInsumosYEstado() {
        val item = ActivityListItem("a1", "p1", "La Esperanza", "FERTILIZACION", "2026-10-24", "Lote norte", "PENDING", 2)
        compose.setContent { UnibanTheme { ActivitiesScreen(listOf(item), 1, {}, {}) } }
        compose.onNodeWithText("Fertilización").assertIsDisplayed()
        compose.onNodeWithText("La Esperanza · 24/10/2026").assertIsDisplayed()
        compose.onNodeWithText("2 insumos usados").assertIsDisplayed()
        compose.onNodeWithText("Lote norte").assertIsDisplayed()
        compose.onNodeWithText("Pendiente de sincronización").assertIsDisplayed()
    }

    @Test fun elBotonDeLaListaAbreElFormulario() {
        var opened = false
        compose.setContent { UnibanTheme { ActivitiesScreen(emptyList(), 0, { opened = true }, {}) } }
        compose.onNodeWithText("+ Registrar actividad").performClick()
        assertEquals(true, opened)
    }

    // --- Detalle de la parcela ---

    private fun showDetail(parcel: ParcelSummary, activities: List<ParcelActivity>, onNew: (() -> Unit)? = {}) =
        compose.setContent {
            UnibanTheme {
                ParcelDetailScreen(
                    parcelId = parcel.id,
                    state = ParcelsUiState(loading = false, parcels = listOf(withCycle, withoutCycle)),
                    pendingCount = 0, onBack = {}, onRetry = {}, today = LocalDate.of(2026, 10, 24),
                    activities = activities, onNewActivity = onNew,
                )
            }
        }

    @Test fun elDetalleDeLaParcelaListaSusActividades() {
        showDetail(withCycle, listOf(ParcelActivity("a1", "SIEMBRA", "2026-10-20", "Lote sur", "c1", "SYNCED", 1)))
        compose.onNodeWithText("ACTIVIDADES").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Siembra").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("20/10/2026 · 1 insumo usado").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sincronizado").performScrollTo().assertIsDisplayed()
    }

    @Test fun elDetalleIndicaQueNoHayActividades() {
        showDetail(withCycle, emptyList())
        compose.onNodeWithTag("no-activities").performScrollTo().assertIsDisplayed()
    }

    @Test fun elDetalleMarcaLasActividadesDeUnCicloAnterior() {
        showDetail(withCycle, listOf(ParcelActivity("a1", "COSECHA", "2025-12-01", "", "viejo", "SYNCED", 0)))
        compose.onNodeWithText("Ciclo anterior").performScrollTo().assertIsDisplayed()
    }

    @Test fun elDetalleOfreceRegistrarUnaActividadSoloConCicloActivo() {
        var opened = false
        showDetail(withCycle, emptyList(), onNew = { opened = true })
        compose.onNodeWithTag("new-activity").performScrollTo().performClick()
        assertEquals(true, opened)
    }

    @Test fun sinCicloActivoElDetalleExplicaPorQueNoSePuedeRegistrar() {
        showDetail(withoutCycle, emptyList())
        compose.onNodeWithText("Inicia un ciclo productivo para registrar actividades en esta parcela.")
            .performScrollTo().assertIsDisplayed()
    }

    // --- Formulario ---

    @Test fun elFormularioMuestraLosSeisTiposDeActividad() {
        showForm()
        listOf("Fertilización", "Siembra", "Cosecha", "Aplicación de insumo", "Control fitosanitario", "Otra actividad")
            .forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
    }

    @Test fun elFormularioPideElTipoYLaParcelaAlGuardar() {
        showForm()
        compose.onNodeWithText("Guardar actividad").assertIsEnabled().performClick()
        compose.onNodeWithText("Selecciona la parcela.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Selecciona el tipo de actividad.").performScrollTo().assertIsDisplayed()
    }

    @Test fun elFormularioPermiteAgregarUnInsumoUsado() {
        showForm()
        compose.onNodeWithTag("add-supply").performScrollTo().performClick()
        compose.onNodeWithTag("supply-quantity-0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cantidad usada").performScrollTo().assertIsDisplayed()
    }

    @Test fun sinParcelasElFormularioLoExplica() {
        showForm(parcels = emptyList())
        compose.onNodeWithText("Aún no tienes parcelas. Regístralas en la pestaña Parcelas.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun laParcelaElegidaSinCicloMuestraElAviso() {
        viewModel.selectParcel("p2")
        showForm()
        compose.onNodeWithText("Esta parcela aún no tiene ciclos para asociar actividades. Inicia uno primero.")
            .performScrollTo().assertIsDisplayed()
    }
}
