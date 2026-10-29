package co.edu.udea.uniban.suministros.ui.production

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import co.edu.udea.uniban.suministros.ui.theme.UnibanTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProductionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val source = FakeProductionSource()
    private var entryCalls = 0
    private fun show() {
        val vm = ProductionViewModel(source, SavedStateHandle(mapOf("parcelId" to "p1")))
        compose.setContent { UnibanTheme {
            ProductionScreen(vm, listOf(ParcelSummary("p1", "La Esperanza", "2", "c1", "2026-01-01", "PRODUCCION")),
                onBack = {}, onParcel = {}, onEntry = { entryCalls++ })
        } }
    }
    @Test fun distingueEstimacionSemanalCargaYSaldoReal() {
        show()
        compose.onNodeWithText("Carga entera del plan: 146 racimos").assertIsDisplayed()
        compose.onNodeWithText("Disponibles: 100 racimos").assertIsDisplayed()
        compose.onNodeWithText("Estimación semanal:", substring = true).assertIsDisplayed()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/production/overview.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onNodeWithTag("production-quantity").performScrollTo().assertTextContains("45")
    }
    @Test fun muestraFaltanteYAccesoAEntradaDeEmpaques() {
        show()
        compose.onNodeWithText("Despacho", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Registrar entrada de empaques").performScrollTo().performClick()
        assertEquals(1, entryCalls)
    }
    @Test fun confirmaExcepcionYEnviaCantidadReal() {
        show()
        compose.onNodeWithTag("production-quantity").performScrollTo().performTextReplacement("46")
        compose.onNodeWithTag("production-exception").performScrollTo().performClick()
        compose.onNodeWithTag("production-save").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(46, source.lastQuantity)
        assertTrue(source.lastException)
        compose.onNodeWithTag("production-saved-dialog").assertIsDisplayed()
        compose.onNodeWithText("Cosecha guardada").assertIsDisplayed()
        compose.onNodeWithText("Aceptar").performClick()
        compose.onNodeWithTag("production-saved-dialog").assertDoesNotExist()
    }

    @Test fun elDespachoMuestraConfirmacionAunqueElFormularioEsteDesplazado() {
        source.state.value = source.state.value.copy(inventory = listOf(
            co.edu.udea.uniban.suministros.data.local.InventoryItem("i1", "cajas", "Empaque", "unidad", "Bodega", 500f),
        ))
        show()
        compose.onNodeWithText("Despacho", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("production-save").performScrollTo().performClick()
        compose.onNodeWithText("Despacho guardado").assertIsDisplayed()
        compose.onNodeWithText("Se guardaron 100 racimos.", substring = true).assertIsDisplayed()
    }

    @Test fun unErrorDeGuardadoSeHaceVisibleSinMostrarConfirmacion() {
        show()
        compose.onNodeWithTag("production-quantity").performScrollTo().performTextReplacement("1.5")
        compose.onNodeWithTag("production-save").performScrollTo().performClick()
        compose.onNodeWithTag("production-error").assertIsDisplayed()
        compose.onNodeWithTag("production-saved-dialog").assertDoesNotExist()
        assertTrue(source.calls.isEmpty())
    }
}
