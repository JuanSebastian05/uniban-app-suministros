package co.edu.udea.uniban.suministros.ui.home

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import co.edu.udea.uniban.suministros.data.SyncIssue
import co.edu.udea.uniban.suministros.data.local.HomeRecord
import co.edu.udea.uniban.suministros.data.local.InventoryItem
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import co.edu.udea.uniban.suministros.ui.inventory.InventoryUiState
import co.edu.udea.uniban.suministros.ui.inventory.SyncStatusUiState
import co.edu.udea.uniban.suministros.ui.navigation.MainBottomBar
import co.edu.udea.uniban.suministros.ui.navigation.MainTab
import co.edu.udea.uniban.suministros.ui.parcels.ParcelsUiState
import co.edu.udea.uniban.suministros.ui.theme.UnibanTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val parcels = listOf(
        ParcelSummary("p1", "El Porvenir", "2", "c1", "2026-01-01", "PRODUCCION"),
        ParcelSummary("p2", "La Unión", "1", "c2", "2026-01-01", "EN_PREPARACION"),
        ParcelSummary("p3", "La Esperanza", "3", null, null, null, "DESCANSO", "c3"),
    )
    private var selectedStatus: String? = "unset"
    private var activityCalls = 0
    private var movementCalls = 0
    private var syncCalls = 0
    private var parcelCalls = 0
    private var signOutCalls = 0

    private fun show(
        parcelState: ParcelsUiState = ParcelsUiState(loading = false, parcels = parcels),
        sync: SyncStatusUiState = SyncStatusUiState(loading = false),
        online: Boolean? = true,
        recent: HomeRecordsState = HomeRecordsState(loading = false),
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            UnibanTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    HomeScreen(
                        name = "Juan José", email = "juan@example.com", parcels = parcelState,
                        inventory = InventoryUiState(loading = false, items = listOf(InventoryItem("i1", "Urea", "Fertilizantes", "kg", "Bodega", 50f))),
                        sync = sync, recent = recent, online = online,
                        onParcels = { selectedStatus = it }, onNewParcel = { parcelCalls++ }, onParcel = { parcelCalls++ },
                        onNewActivity = { activityCalls++ }, onNewMovement = { movementCalls++ },
                        onActivities = {}, onHistory = {}, onRecord = {}, onSync = { syncCalls++ },
                        onRetryParcels = {}, onLogout = { signOutCalls++ },
                        navigationBar = { MainBottomBar(MainTab.HOME, onSelect = {}) }, today = LocalDate.of(2026, 10, 28),
                    )
                }
            }
        }
    }

    @Test fun muestraResumenYLasCuatroPestanas() {
        show(sync = SyncStatusUiState(loading = false, lastSuccessfulAtEpochMillis = 1793205600000),
            recent = HomeRecordsState(loading = false, records = listOf(
                HomeRecord("a1", "ACTIVITY", "FERTILIZACION", "El Porvenir", null, null, "2026-10-28", "2026-10-28T13:30:00Z", "p1", null),
                HomeRecord("m1", "MOVEMENT", "ENTRADA", "Urea", 50f, "kg", "2026-10-28", "2026-10-28T12:30:00Z", null, "i1"),
                HomeRecord("a2", "ACTIVITY", "COSECHA", "La Unión", null, null, "2026-10-27", "2026-10-27T12:30:00Z", "p2", null),
            )))
        compose.onNodeWithText("Hola, Juan").assertIsDisplayed()
        compose.onNodeWithTag("home-count-total").assertTextContains("3")
        compose.onNodeWithTag("home-count-PRODUCCION").performClick()
        assertEquals("PRODUCCION", selectedStatus)
        listOf("home", "parcels", "inventory", "activities").forEach {
            compose.onNodeWithTag("tab-$it").assertIsDisplayed()
        }
        compose.onNodeWithText("Atención necesaria").assertDoesNotExist()
        compose.onNodeWithText("Iniciar primer ciclo").assertDoesNotExist()
        capture("inicio")
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Todo sincronizado"))
        capture("inicio-registros")
    }

    private fun capture(name: String) {
        val file = File("build/reports/home/$name.png").apply { parentFile?.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun accionesRapidasFuncionanSinConexion() {
        show(online = false)
        compose.onNodeWithText("Sin conexión · Mostrando datos guardados").assertIsDisplayed()
        compose.onNodeWithText("Registrar actividad").performScrollTo().performClick()
        compose.onNodeWithText("Registrar movimiento").performScrollTo().performClick()
        assertEquals(1, activityCalls)
        assertEquals(1, movementCalls)
    }

    @Test fun cuentaNuevaInvitaARegistrarPrimeraParcela() {
        show(parcelState = ParcelsUiState(loading = false))
        compose.onNodeWithTag("home-first-parcel").performClick()
        assertEquals(1, parcelCalls)
        compose.onNodeWithTag("home-count-total").assertDoesNotExist()
        compose.onNodeWithText("Registrar actividad").performScrollTo().assertIsNotEnabled()
    }

    @Test fun proponePrimerCicloSoloParaParcelasQueNoHanTenidoUno() {
        show(parcelState = ParcelsUiState(loading = false, parcels = parcels + ParcelSummary("p4", "Nueva", "1", null, null, null)))
        compose.onNodeWithText("Iniciar primer ciclo").performScrollTo().performClick()
        assertEquals(1, parcelCalls)
        compose.onAllNodesWithText("Iniciar primer ciclo").assertCountEquals(1)
    }

    @Test fun erroresYPendientesSeDistinguenSinRepetirElResumen() {
        show(online = false, sync = SyncStatusUiState(loading = false, pendingCount = 3,
            issues = listOf(SyncIssue("m1", "Entrada", "2026-10-28", "Urea", "Error"))))
        compose.onNodeWithText("1 error de envío").performScrollTo().performClick()
        assertEquals(1, syncCalls)
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("3 registros pendientes"))
        compose.onNodeWithText("3 registros pendientes").assertIsDisplayed()
        compose.onAllNodesWithText("3 registros pendientes").assertCountEquals(1)
        compose.onNodeWithText("Todo sincronizado").assertDoesNotExist()
    }

    @Test fun sinHistorialDeEnvioNoAfirmaQueTodoEstaSincronizado() {
        show()
        compose.onNodeWithText("Sin registros pendientes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Todo sincronizado").assertDoesNotExist()
    }

    @Test fun muestraFechaDelRegistroSinInventarUnaHoraYAdmiteTextoGrande() {
        show(fontScale = 1.6f, recent = HomeRecordsState(loading = false, records = listOf(
            HomeRecord("a1", "ACTIVITY", "FERTILIZACION", "El Porvenir", null, null, "2026-10-28", "2026-10-28T12:00:00Z", "p1", null),
        )))
        compose.onNodeWithText("Registrar movimiento").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Fertilización"))
        compose.onNodeWithText("Fertilización").assertIsDisplayed()
        compose.onNodeWithText("Hoy").assertIsDisplayed()
        compose.onNodeWithTag("tab-activities").assertIsDisplayed()
    }

    @Test fun permiteCerrarSesionConConfirmacionDesdeInicio() {
        show()
        compose.onNodeWithTag("home-account").performClick()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(0, signOutCalls)
        compose.onNodeWithTag("home-account").performClick()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNode(hasText("Cerrar sesión") and hasClickAction()).performClick()
        assertEquals(1, signOutCalls)
    }
}
