package co.edu.udea.uniban.suministros.ui.parcels

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import android.app.Application
import co.edu.udea.uniban.suministros.data.local.AforamientoEntity
import co.edu.udea.uniban.suministros.data.local.ParcelConsumption
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Detalle de parcela (HU_02): ciclo activo con fechas y etapa, o aviso explícito sin ciclo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
class ParcelDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    private val withCycle = ParcelSummary("p1", "La Esperanza", "2.40", "c1", "2024-01-15", "PRODUCCION")
    private val withoutCycle = ParcelSummary("p2", "El Mirador", "1.50", null, null, null)

    private fun show(
        parcel: ParcelSummary,
        offline: Boolean = false,
        consumptions: List<ParcelConsumption> = emptyList(),
        aforamiento: AforamientoUiState = AforamientoUiState(loading = false),
        onRetryAforamiento: () -> Unit = {},
    ) = compose.setContent {
        ParcelDetailScreen(
            parcelId = parcel.id,
            state = ParcelsUiState(loading = false, parcels = listOf(withCycle, withoutCycle), offline = offline),
            pendingCount = 0,
            onBack = {},
            onRetry = {},
            today = LocalDate.of(2024, 1, 24),
            consumptions = consumptions,
            aforamiento = aforamiento,
            onRetryAforamiento = onRetryAforamiento,
        )
    }

    @Test fun muestraElCicloActivoConFechasYEtapa() {
        show(withCycle)
        compose.onNodeWithTag("active-cycle").assertIsDisplayed()
        compose.onNodeWithText("2,4 ha").assertIsDisplayed()
        compose.onNodeWithText("Día 10 del ciclo").assertIsDisplayed()
        compose.onNodeWithText("En curso").assertIsDisplayed()
    }

    @Test fun indicaQueNoHayCicloActivo() {
        show(withoutCycle)
        compose.onNodeWithTag("no-active-cycle").assertIsDisplayed()
        compose.onNodeWithText("Esta parcela no tiene un ciclo productivo activo.").assertIsDisplayed()
    }

    @Test fun sinConexionMuestraLosDatosGuardados() {
        show(withCycle, offline = true)
        compose.onNodeWithText("Sin conexión: datos guardados en este dispositivo.").performScrollTo().assertIsDisplayed()
    }

    @Test fun parcelaInexistente() {
        compose.setContent {
            ParcelDetailScreen("otra", ParcelsUiState(loading = false), 0, {}, {})
        }
        compose.onNodeWithText("La parcela ya no está disponible.").assertIsDisplayed()
    }

    @Test fun muestraLosInsumosConsumidos() {
        show(withCycle, consumptions = listOf(
            ParcelConsumption("m1", "Fertilizante 15-15-15", "kg", 15f, "2024-04-12", "c1", "SYNCED"),
            ParcelConsumption("m2", "Urea 46%", "kg", 2.5f, "2023-11-02", "c0", "PENDING"),
        ))
        compose.onNodeWithText("Fertilizante 15-15-15").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Consumo · 15,00 kg · 12/04/2024").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ciclo anterior").performScrollTo().assertIsDisplayed()
    }

    @Test fun sinConsumosLoIndica() {
        show(withCycle)
        compose.onNodeWithTag("no-consumptions").performScrollTo().assertIsDisplayed()
    }

    // --- HU_09: aforamiento ---

    private val aforamiento = AforamientoEntity("p1", "a1", "2026-10-29", "2.41", 1850, "0.870")

    @Test fun muestraElAforamientoConFechaAreaPlantasYConfianza() {
        show(withCycle, aforamiento = AforamientoUiState(loading = false, aforamiento = aforamiento))
        compose.onNodeWithText("AFORAMIENTO").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("aforamiento-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("29/10/2026").assertIsDisplayed()
        compose.onNodeWithText("Área aforada").assertIsDisplayed()
        compose.onNodeWithText("2,41 ha").assertIsDisplayed()
        compose.onNodeWithText("Plantas estimadas").assertIsDisplayed()
        compose.onNodeWithText("1.850").assertIsDisplayed()
        compose.onNodeWithText("Confianza").assertIsDisplayed()
        compose.onNodeWithText("87 %").assertIsDisplayed()
        // El área de la parcela sigue aparte, en el encabezado.
        compose.onNodeWithText("2,4 ha").assertIsDisplayed()
    }

    @Test fun sinConfianzaNoMuestraEsaFila() {
        show(withCycle, aforamiento = AforamientoUiState(loading = false, aforamiento = aforamiento.copy(confidence = null)))
        compose.onNodeWithTag("aforamiento-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confianza").assertDoesNotExist()
    }

    @Test fun sinAforamientoLoIndica() {
        show(withCycle)
        compose.onNodeWithTag("aforamiento-empty").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin aforamiento recibido").assertIsDisplayed()
        compose.onNodeWithTag("aforamiento-card").assertDoesNotExist()
    }

    @Test fun mientrasConsultaMuestraCargando() {
        show(withCycle, aforamiento = AforamientoUiState(loading = true))
        compose.onNodeWithTag("aforamiento-loading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin aforamiento recibido").assertDoesNotExist()
    }

    @Test fun sinConexionYConCopiaMuestraLaCopiaYElAviso() {
        show(withCycle, aforamiento = AforamientoUiState(loading = false, aforamiento = aforamiento, refreshFailed = true))
        compose.onNodeWithTag("aforamiento-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("aforamiento-stale").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("aforamiento-error").assertDoesNotExist()
    }

    @Test fun sinCopiaYConFalloMuestraErrorYPermiteReintentar() {
        var retries = 0
        show(withCycle, aforamiento = AforamientoUiState(loading = false, refreshFailed = true), onRetryAforamiento = { retries++ })
        compose.onNodeWithTag("aforamiento-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin aforamiento recibido").assertDoesNotExist()
        compose.onNodeWithTag("aforamiento-retry").performClick()
        assertEquals(1, retries)
    }

    @Test fun elAforamientoTambienSeMuestraEnUnaParcelaSinCiclo() {
        show(withoutCycle, aforamiento = AforamientoUiState(loading = false, aforamiento = aforamiento.copy(parcelId = "p2")))
        compose.onNodeWithTag("aforamiento-card").performScrollTo().assertIsDisplayed()
    }

    // --- Formato ---

    @Test fun formatoDeConfianzaYPlantas() {
        assertEquals("87 %", formatConfidence("0.870"))
        assertEquals("87,5 %", formatConfidence("0.875"))
        assertEquals("100 %", formatConfidence("1.000"))
        assertEquals("0 %", formatConfidence("0.000"))
        assertEquals("texto", formatConfidence("texto"))
        assertEquals("1.850", formatPlants(1850))
        assertEquals("0", formatPlants(0))
        assertEquals("1.234.567", formatPlants(1234567))
    }
}
