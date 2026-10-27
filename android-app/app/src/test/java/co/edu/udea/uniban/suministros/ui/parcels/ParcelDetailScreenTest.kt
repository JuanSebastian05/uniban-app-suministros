package co.edu.udea.uniban.suministros.ui.parcels

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import android.app.Application
import co.edu.udea.uniban.suministros.data.ParcelRecommendation
import co.edu.udea.uniban.suministros.data.RecommendationSupply
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
        recommendation: RecommendationUiState? = null,
        onRequestRecommendation: () -> Unit = {},
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
            recommendation = recommendation,
            onRequestRecommendation = onRequestRecommendation,
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

    // --- HU_12: recomendaciones ---

    private val recommendation = ParcelRecommendation(
        parcelId = "p1", period = "2026-W44", generatedAt = "2026-10-26T19:00:00Z", sourceVersion = "v0.3.1",
        volumeBunches = 1850.0, cutDeadline = "2026-11-02T14:30:00", meetsWindow = true, reason = null,
        supplies = listOf(
            RecommendationSupply("cajas", "2026-11-01", 120.0, 80.0, 40.0),
            RecommendationSupply("bolsas", "2026-11-01", 50.0, 90.0, 0.0),
        ),
    )

    private fun withData(r: ParcelRecommendation = recommendation) =
        RecommendationUiState(loading = false, recommendation = r)

    @Test fun sinParametroDeRecomendacionesNoMuestraLaSeccion() {
        show(withCycle)
        compose.onNodeWithText("RECOMENDACIONES").assertDoesNotExist()
    }

    @Test fun muestraVolumenFechaLimiteEIndicadorDeVentana() {
        show(withCycle, recommendation = withData())
        compose.onNodeWithText("RECOMENDACIONES").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recommendation-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Volumen estimado").assertIsDisplayed()
        compose.onNodeWithText("1.850 racimos").assertIsDisplayed()
        compose.onNodeWithText("Fecha límite de corte").assertIsDisplayed()
        compose.onNodeWithText("02/11/2026 14:30").assertIsDisplayed()
        compose.onNodeWithTag("window-meets").assertIsDisplayed()
        compose.onNodeWithText("Cumple la ventana de despacho").assertIsDisplayed()
        compose.onNodeWithTag("window-fails").assertDoesNotExist()
    }

    @Test fun siNoCumpleLaVentanaLoIndicaYMuestraLaObservacion() {
        show(withCycle, recommendation = withData(recommendation.copy(meetsWindow = false, reason = "Llega fuera de ventana")))
        compose.onNodeWithTag("window-fails").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No cumple la ventana de despacho").assertIsDisplayed()
        compose.onNodeWithTag("recommendation-reason").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Observación: Llega fuera de ventana").assertIsDisplayed()
        compose.onNodeWithTag("window-meets").assertDoesNotExist()
    }

    @Test fun sinObservacionNoLaMuestra() {
        show(withCycle, recommendation = withData())
        compose.onNodeWithTag("recommendation-card").performScrollTo()
        compose.onNodeWithTag("recommendation-reason").assertDoesNotExist()
    }

    @Test fun cadaInsumoMuestraRequeridoDisponibleFechaYElDeficitResaltado() {
        show(withCycle, recommendation = withData())
        compose.onNodeWithTag("supply-cajas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Requerido 120 · Disponible 80").assertIsDisplayed()
        compose.onAllNodesWithText("Para el 01/11/2026", useUnmergedTree = true).assertCountEquals(2) // una por insumo
        compose.onNodeWithTag("deficit-cajas").assertIsDisplayed()
        compose.onNodeWithText("Déficit: 40").assertIsDisplayed()
        // Sin déficit no se resalta nada.
        compose.onNodeWithTag("supply-bolsas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("no-deficit-bolsas").assertIsDisplayed()
        compose.onNodeWithTag("deficit-bolsas").assertDoesNotExist()
    }

    @Test fun sinInsumosLoIndica() {
        show(withCycle, recommendation = withData(recommendation.copy(supplies = emptyList())))
        compose.onNodeWithTag("recommendation-no-supplies").performScrollTo().assertIsDisplayed()
    }

    @Test fun muestraLaFechaDeGeneracionYElPeriodo() {
        show(withCycle, recommendation = withData(recommendation.copy(generatedAt = "2026-10-26T19:00:00-05:00")))
        compose.onNodeWithTag("recommendation-generated").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("periodo 2026-W44", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Generada el", substring = true).assertIsDisplayed()
    }

    @Test fun recomendacionesMientrasConsultaMuestraCargando() {
        show(withCycle, recommendation = RecommendationUiState(loading = true))
        compose.onNodeWithTag("recommendation-loading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recommendation-empty").assertDoesNotExist()
    }

    @Test fun sinRecomendacionAunInvitaAPedirla() {
        var requests = 0
        show(withCycle, recommendation = RecommendationUiState(loading = false), onRequestRecommendation = { requests++ })
        compose.onNodeWithTag("recommendation-empty").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin recomendación aún").assertIsDisplayed()
        compose.onNodeWithText("Solicitar recomendación").assertIsDisplayed()
        compose.onNodeWithTag("recommendation-request").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, requests)
    }

    @Test fun conRecomendacionElBotonDiceActualizar() {
        show(withCycle, recommendation = withData())
        compose.onNodeWithTag("recommendation-request").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Actualizar recomendación").assertIsDisplayed()
    }

    @Test fun mientrasPideAvisaQuePuedeTardarYBloqueaElBoton() {
        show(withCycle, recommendation = RecommendationUiState(loading = false, requesting = true))
        compose.onNodeWithTag("recommendation-requesting").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pidiendo la recomendación… puede tardar hasta un minuto.").assertIsDisplayed()
        compose.onNodeWithTag("recommendation-request").assertIsNotEnabled()
    }

    @Test fun mientrasActualizaConservaLaCopiaVisible() {
        show(withCycle, recommendation = withData().copy(requesting = true))
        compose.onNodeWithTag("recommendation-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recommendation-requesting").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recommendation-request").performScrollTo().assertIsNotEnabled()
    }

    private fun problemShown(problem: RecommendationProblem) {
        show(withCycle, recommendation = RecommendationUiState(loading = false, problem = problem))
        compose.onNodeWithTag("recommendation-problem").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(problemMessage(problem)).assertIsDisplayed()
        compose.onNodeWithTag("recommendation-request").assertIsEnabled() // se puede reintentar
    }

    @Test fun problemaServicioNoDisponible() = problemShown(RecommendationProblem.SERVICE_UNAVAILABLE)

    @Test fun problemaSinConexion() = problemShown(RecommendationProblem.OFFLINE)

    @Test fun problemaDeSesion() = problemShown(RecommendationProblem.SESSION)

    @Test fun problemaGenerico() = problemShown(RecommendationProblem.OTHER)

    @Test fun elProblemaNoOcultaLaUltimaCopia() {
        show(withCycle, recommendation = withData().copy(problem = RecommendationProblem.OFFLINE))
        compose.onNodeWithTag("recommendation-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recommendation-problem").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin conexión. Conéctate a internet para pedir la recomendación.").assertIsDisplayed()
    }

    @Test fun sinAforamientoElMensajeEsClaro() {
        show(withCycle, recommendation = RecommendationUiState(loading = false, problem = RecommendationProblem.NO_AFORAMIENTO))
        compose.onNodeWithTag("recommendation-problem").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Esta parcela todavía no tiene aforamiento. Cuando se reciba, podrás pedir la recomendación.",
        ).assertIsDisplayed()
    }

    @Test fun losMensajesDeProblemaSonDistintosYNoMencionanDetallesTecnicos() {
        val messages = RecommendationProblem.entries.map(::problemMessage)
        assertEquals(messages.size, messages.toSet().size)
        messages.forEach { assertEquals(it, it.trim()) }
    }

    // --- Formato ---

    @Test fun formatoDeCantidadesFechasYGeneracion() {
        assertEquals("1.850", formatAmount(1850.0))
        assertEquals("12,5", formatAmount(12.5))
        assertEquals("0", formatAmount(0.0))
        assertEquals("1.234,57", formatAmount(1234.567))
        assertEquals("02/11/2026 14:30", formatDateTime("2026-11-02T14:30:00"))
        assertEquals("fecha rara", formatDateTime("fecha rara"))
        assertEquals("01/11/2026", formatShortDate("2026-11-01"))
        // 19:00 UTC = 14:00 en Bogotá (UTC-5), sin importar la zona del equipo que corre la prueba.
        assertEquals("26/10/2026 14:00", formatGenerated("2026-10-26T19:00:00Z", java.time.ZoneId.of("America/Bogota")))
        assertEquals("26/10/2026 14:00", formatGenerated("2026-10-26T19:00:00.123456Z", java.time.ZoneId.of("America/Bogota")))
        assertEquals("no es fecha", formatGenerated("no es fecha"))
    }
}
