package co.edu.udea.uniban.suministros

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.ui.navigation.AppNavHost
import co.edu.udea.uniban.suministros.ui.theme.UnibanTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class EntryFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: InventoryDatabase
    private lateinit var repository: InventoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        repository = InventoryRepository(database)
        compose.setContent { UnibanTheme { AppNavHost(repository) } }
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun entryFromDetailValidatesAndUpdatesInventory() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("inventory-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("inventory-list").performScrollToNode(hasText("Fertilizante 15-15-15"))
        compose.onNodeWithText("Fertilizante 15-15-15").performClick()
        compose.onNodeWithText("+ Registrar entrada").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Cantidad").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Guardar entrada").performClick()
        compose.onNodeWithText("Ingresa una cantidad con máximo dos decimales.").assertExists()
        compose.onNodeWithText("Cantidad").performTextInput("1,50")
        compose.onNodeWithText("Guardar entrada").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Movimiento guardado").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Pendiente de sincronización").assertExists()
        compose.onNodeWithText("Entrada · 1,50 kg").assertExists()
        compose.onNodeWithText("Volver al inventario").performClick()
        compose.onNodeWithTag("inventory-list").performScrollToNode(hasText("Fertilizante 15-15-15"))
        compose.onNodeWithText("121,50 kg").assertExists()
        compose.onNodeWithText("1 pendiente").assertExists()
        runBlocking { assertEquals(1, repository.movements.first().size) }
    }
}

