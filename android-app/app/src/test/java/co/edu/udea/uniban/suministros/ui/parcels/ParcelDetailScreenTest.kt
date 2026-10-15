package co.edu.udea.uniban.suministros.ui.parcels

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import co.edu.udea.uniban.suministros.data.local.ParcelConsumption
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Detalle de parcela (HU_02): ciclo activo con fechas y etapa, o aviso explícito sin ciclo. */
@RunWith(RobolectricTestRunner::class)
class ParcelDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    private val withCycle = ParcelSummary("p1", "La Esperanza", "2.40", "c1", "2024-01-15", "PRODUCCION")
    private val withoutCycle = ParcelSummary("p2", "El Mirador", "1.50", null, null, null)

    private fun show(
        parcel: ParcelSummary,
        offline: Boolean = false,
        consumptions: List<ParcelConsumption> = emptyList(),
    ) = compose.setContent {
        ParcelDetailScreen(
            parcelId = parcel.id,
            state = ParcelsUiState(loading = false, parcels = listOf(withCycle, withoutCycle), offline = offline),
            pendingCount = 0,
            onBack = {},
            onRetry = {},
            today = LocalDate.of(2024, 1, 24),
            consumptions = consumptions,
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
        compose.onNodeWithText("Sin conexión: datos guardados en este dispositivo.").assertIsDisplayed()
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
}
