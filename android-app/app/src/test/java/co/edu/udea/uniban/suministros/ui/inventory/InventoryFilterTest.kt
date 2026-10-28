package co.edu.udea.uniban.suministros.ui.inventory

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import co.edu.udea.uniban.suministros.data.local.InventoryItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** HU_11: búsqueda y categoría combinadas sobre el inventario local, sin red. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class, qualifiers = "w411dp-h891dp")
class InventoryFilterTest {
    @get:Rule val compose = createComposeRule()

    private fun show() {
        compose.setContent {
            InventoryScreen(
                state = InventoryUiState(loading = false, items = listOf(
                    InventoryItem("1", "Bolsa azul", "Empaque", "unidad", "Bodega", 10f),
                    InventoryItem("2", "Caja", "Empaque", "unidad", "Bodega", 5f),
                    InventoryItem("3", "Bolsa de urea", "Fertilizantes", "kg", "Bodega", 20f),
                )),
                onRetry = {}, onSelect = {}, onNewEntry = {}, onLogout = {},
            )
        }
    }

    private fun selectCategory(category: String) {
        compose.onNodeWithTag("inventory-category-filter").performScrollTo().performClick()
        compose.onNode(hasText(category) and hasAnyAncestor(isPopup())).performClick()
    }

    @Test fun filtraPorCategoriaYPermiteVolverATodas() {
        show()
        selectCategory("Empaque")
        compose.onNodeWithText("Inventario principal · 2 insumos").assertIsDisplayed()
        compose.onNodeWithText("Bolsa azul").assertIsDisplayed()
        compose.onNodeWithText("Bolsa de urea").assertDoesNotExist()
        selectCategory("Todas las categorías")
        compose.onNodeWithText("Inventario principal · 3 insumos").assertIsDisplayed()
    }

    @Test fun combinaTextoYCategoriaYConservaLaBusquedaAlQuitarElFiltro() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("  BOLSA  ")
        selectCategory("Empaque")
        compose.onNodeWithText("Bolsa azul").assertIsDisplayed()
        compose.onNodeWithText("Bolsa de urea").assertDoesNotExist()
        compose.onNodeWithText("Caja").assertDoesNotExist()
        selectCategory("Todas las categorías")
        compose.onNodeWithText("Inventario principal · 2 insumos").assertIsDisplayed()
        compose.onNodeWithText("Caja").assertDoesNotExist()
    }

    @Test fun muestraMensajeSinResultadosYSeRecuperaAlCambiarLaBusqueda() {
        show()
        selectCategory("Fertilizantes")
        compose.onNode(hasSetTextAction()).performTextInput("caja")
        compose.onNodeWithText("No se encontraron insumos.").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("Bolsa de urea").assertIsDisplayed()
        compose.onNodeWithText("Caja").assertDoesNotExist()
    }
}
