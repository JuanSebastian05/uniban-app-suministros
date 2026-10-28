package co.edu.udea.uniban.suministros

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.ParcelRepository
import co.edu.udea.uniban.suministros.data.auth.SessionState
import co.edu.udea.uniban.suministros.data.local.*
import co.edu.udea.uniban.suministros.data.remote.ParcelApi
import co.edu.udea.uniban.suministros.ui.navigation.AppNavHost
import co.edu.udea.uniban.suministros.ui.theme.UnibanTheme
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
class HomeNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: InventoryDatabase
    private lateinit var repository: InventoryRepository
    private val auth = FakeAuthRepository(SessionState.Authenticated("demo", "juan@example.com", "Juan José"))

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InventoryDatabase::class.java).build()
        repository = InventoryRepository(database)
        repository.initializeDemo()
        database.parcelDao().upsertParcels(listOf(
            ParcelEntity("p1", "Producción", "2", "PRODUCCION", "c1"),
            ParcelEntity("p2", "Descanso", "1", "DESCANSO", "c2"),
        ))
        database.parcelDao().upsertCycles(listOf(ProductionCycleEntity("c1", "p1", "2026-01-01", null, "PRODUCCION")))
    }

    @After fun tearDown() { database.close() }

    private fun launch() {
        // La sesión demo debe trabajar sin llamar a ninguna operación de red.
        val api = Proxy.newProxyInstance(ParcelApi::class.java.classLoader, arrayOf(ParcelApi::class.java)) { _, method, _ ->
            error("Inicio demo intentó llamar a la red: ${method.name}")
        } as ParcelApi
        val parcels = ParcelRepository(database, api, StarterInventory.DEMO_OWNER) { null }
        compose.setContent { UnibanTheme { AppNavHost({ repository }, auth, parcelRepositoryFor = { parcels }) } }
        awaitTag("home-count-total")
    }

    private fun awaitTag(tag: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun abreInicioYPermiteVisitarLasCuatroPestanas() {
        launch()
        compose.onNodeWithText("Hola, Juan").assertIsDisplayed()
        compose.onNodeWithTag("tab-activities").performClick()
        awaitTag("activity-list")
        compose.onNodeWithTag("tab-inventory").performClick()
        awaitTag("inventory-list")
        compose.onNodeWithTag("tab-parcels").performClick()
        awaitTag("parcel-list")
        compose.onNodeWithTag("tab-home").performClick()
        awaitTag("home-list")
        compose.onNodeWithTag("tab-home").assertIsSelected()
    }

    @Test fun tarjetaDeEstadoAbreParcelasFiltradasYPermiteQuitarElFiltro() {
        launch()
        compose.onNodeWithTag("home-count-PRODUCCION").performClick()
        awaitTag("parcel-list")
        compose.onNodeWithTag("parcel-p1").assertExists()
        compose.onNodeWithTag("parcel-p2").assertDoesNotExist()
        compose.onNodeWithText("Ver todas").performClick()
        compose.onNodeWithTag("parcel-p2").assertExists()
    }

    @Test fun movimientoDesdeInicioRegresaAlInventarioTrasGuardar() {
        launch()
        compose.onNodeWithText("Registrar movimiento").performScrollTo().performClick()
        compose.onNodeWithText("Insumo").performClick()
        compose.onNodeWithText("Fertilizante 15-15-15").performClick()
        compose.onNodeWithText("Cantidad").performScrollTo().performTextInput("1")
        compose.onNodeWithText("Guardar entrada").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Movimiento guardado").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Volver al inventario").performClick()
        awaitTag("inventory-list")
        compose.onNodeWithTag("tab-home").performClick()
        awaitTag("home-list")
    }

    @Test fun cerrarSesionDesdeInicioRetiraElGrafoPrivado() {
        launch()
        compose.onNodeWithTag("home-account").performClick()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNode(hasText("Cerrar sesión") and hasClickAction()).performClick()
        awaitTag("login-submit")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithTag("home-list").assertDoesNotExist()
    }
}
