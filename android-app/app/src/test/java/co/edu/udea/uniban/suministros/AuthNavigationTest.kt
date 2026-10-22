package co.edu.udea.uniban.suministros

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.data.auth.AuthException
import co.edu.udea.uniban.suministros.data.auth.ProfileState
import co.edu.udea.uniban.suministros.data.auth.SessionState
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
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
class AuthNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: InventoryDatabase
    private lateinit var repository: InventoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        repository = InventoryRepository(database)
    }

    @After
    fun tearDown() { database.close() }

    private fun launch(auth: FakeAuthRepository) {
        compose.setContent { UnibanTheme { AppNavHost({ repository }, auth) } }
    }

    private fun awaitTag(tag: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertAbsent(tag: String) = compose.onNodeWithTag(tag).assertDoesNotExist()

    @Test
    fun withoutSessionStartsAtLogin() {
        launch(FakeAuthRepository())
        awaitTag("login-submit")
        assertAbsent("inventory-list")
    }

    @Test
    fun withSessionStartsAtInventory() {
        launch(FakeAuthRepository(SessionState.Authenticated("uid-1", "ana@unibanagro.com")))
        awaitTag("inventory-list")
        assertAbsent("login-submit")
    }

    @Test
    fun loadingSessionShowsSplashThenResolves() {
        val auth = FakeAuthRepository(SessionState.Loading)
        launch(auth)
        awaitTag("session-loading")
        auth.setSession(SessionState.Authenticated("uid-1", null))
        awaitTag("inventory-list")
    }

    @Test
    fun signingInMovesToInventoryAndLoginIsRemovedFromBackStack() {
        val auth = FakeAuthRepository()
        launch(auth)
        awaitTag("login-submit")
        compose.onNodeWithTag("login-email").performTextInput("ana@unibanagro.com")
        compose.onNodeWithTag("login-password").performTextInput("secreto")
        compose.onNodeWithTag("login-submit").performClick()
        awaitTag("inventory-list")
        assertEquals(listOf("ana@unibanagro.com" to "secreto"), auth.signInCalls)
        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertAbsent("login-submit")
    }

    @Test
    fun createAccountOpensRegistrationAndBackReturnsToLogin() {
        launch(FakeAuthRepository())
        awaitTag("login-submit")
        compose.onNodeWithTag("login-register").performClick()
        awaitTag("register-submit")
        compose.onNodeWithTag("register-back").performScrollTo().performClick()
        awaitTag("login-submit")
        assertAbsent("register-submit")
    }

    @Test
    fun registeringMovesToInventoryAndAuthScreensLeaveTheBackStack() {
        val auth = FakeAuthRepository()
        launch(auth)
        awaitTag("login-submit")
        compose.onNodeWithTag("login-register").performClick()
        awaitTag("register-submit")
        compose.onNodeWithTag("register-name").performTextInput("Ana Pérez")
        compose.onNodeWithTag("register-document").performTextInput("1020304050")
        compose.onNodeWithTag("register-email").performTextInput("ana@unibanagro.com")
        compose.onNodeWithTag("register-password").performTextInput("secreto1")
        compose.onNodeWithTag("register-confirm").performTextInput("secreto1")
        compose.onNodeWithTag("register-submit").performScrollTo().performClick()
        awaitTag("inventory-list")
        assertEquals("ana@unibanagro.com", auth.registerCalls.single().first.email)
        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertAbsent("register-submit")
        assertAbsent("login-submit")
    }

    @Test
    fun logoutAfterConfirmationReturnsToLoginAndBackDoesNotReopenInventory() {
        val auth = FakeAuthRepository(SessionState.Authenticated("uid-1", "ana@unibanagro.com"))
        launch(auth)
        awaitTag("inventory-list")
        compose.onNodeWithContentDescription("Cerrar sesión").performClick()
        // Cancelar no cierra la sesión.
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(0, auth.signOutCalls)
        compose.onNodeWithContentDescription("Cerrar sesión").performClick()
        compose.onNode(hasText("Cerrar sesión") and hasClickAction()).performClick()
        awaitTag("login-submit")
        assertEquals(1, auth.signOutCalls)
        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertAbsent("inventory-list")
    }

    @Test
    fun eachAccountOpensItsOwnInventory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val luisDatabase = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        val requested = mutableListOf<String>()
        val repositories = mapOf(
            "uid-ana" to InventoryRepository(database, "uid-ana"),
            "uid-luis" to InventoryRepository(luisDatabase, "uid-luis"),
        )
        val auth = FakeAuthRepository(SessionState.Authenticated("uid-ana", "ana@unibanagro.com"))
        compose.setContent {
            UnibanTheme { AppNavHost({ uid -> requested += uid; repositories.getValue(uid) }, auth) }
        }
        try {
            awaitTag("inventory-list")
            auth.setSession(SessionState.Unauthenticated)
            awaitTag("login-submit")
            auth.setSession(SessionState.Authenticated("uid-luis", "luis@unibanagro.com"))
            awaitTag("inventory-list")
            compose.waitUntil(15_000) {
                runBlocking { luisDatabase.inventoryDao().observeInventory().first().isNotEmpty() }
            }
            assertEquals(listOf("uid-ana", "uid-luis"), requested)
        } finally {
            luisDatabase.close()
        }
    }

    // --- P-02: cuenta de Firebase sin perfil en el backend (registro interrumpido) ---

    @Test
    fun accountWithoutProfileMustCompleteItAndThenReturnsToInventory() {
        val auth = FakeAuthRepository(SessionState.Authenticated("uid-1", "ana@unibanagro.com"))
        auth.profileOnRefresh = ProfileState.Missing
        launch(auth)
        awaitTag("complete-profile-submit")
        assertAbsent("inventory-list")
        compose.onNodeWithTag("complete-profile-name").performTextInput("Ana Pérez")
        compose.onNodeWithTag("complete-profile-document").performTextInput("1020304050")
        compose.onNodeWithTag("complete-profile-submit").performScrollTo().performClick()
        awaitTag("inventory-list")
        assertEquals(listOf(Triple("Ana Pérez", "1020304050", null)), auth.completeProfileCalls)
        // Atrás no reabre la pantalla de completar el perfil.
        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertAbsent("complete-profile-submit")
    }

    @Test
    fun completingTheProfileFailsKeepsTheFormWithItsDataAndExplainsWhy() {
        val auth = FakeAuthRepository(SessionState.Authenticated("uid-1", "ana@unibanagro.com"))
        auth.profileOnRefresh = ProfileState.Missing
        auth.onCompleteProfile = { _, _, _ -> Result.failure(AuthException(AuthError.DocumentInUse)) }
        launch(auth)
        awaitTag("complete-profile-submit")
        compose.onNodeWithTag("complete-profile-name").performTextInput("Ana Pérez")
        compose.onNodeWithTag("complete-profile-document").performTextInput("1020304050")
        compose.onNodeWithTag("complete-profile-submit").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Ya existe una cuenta con este documento", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("complete-profile-name").assertTextContains("Ana Pérez")
        assertAbsent("inventory-list")
    }

    @Test
    fun accountWithoutProfileCanSignOutInsteadOfCompletingIt() {
        val auth = FakeAuthRepository(SessionState.Authenticated("uid-1", "ana@unibanagro.com"))
        auth.profileOnRefresh = ProfileState.Missing
        launch(auth)
        awaitTag("complete-profile-sign-out")
        compose.onNodeWithTag("complete-profile-sign-out").performScrollTo().performClick()
        awaitTag("login-submit")
        assertEquals(1, auth.signOutCalls)
        assertAbsent("complete-profile-submit")
    }

    @Test
    fun unknownProfileNeverBlocksTheOfflineUseOfTheApp() {
        // Sin red (o servidor dormido) el backend no responde: la app sigue con los datos locales.
        val auth = FakeAuthRepository(SessionState.Authenticated("uid-1", "ana@unibanagro.com"))
        launch(auth)
        awaitTag("inventory-list")
        compose.waitUntil(15_000) { auth.refreshProfileCalls >= 1 }
        assertAbsent("complete-profile-submit")
    }

    @Test
    fun profileIsOnlyAskedWhenThereIsASession() {
        val auth = FakeAuthRepository()
        launch(auth)
        awaitTag("login-submit")
        assertEquals(0, auth.refreshProfileCalls)
        auth.setSession(SessionState.Authenticated("uid-1", "ana@unibanagro.com"))
        awaitTag("inventory-list")
        compose.waitUntil(15_000) { auth.refreshProfileCalls == 1 }
    }

    @Test
    fun sessionRevokedWhileOpenRedirectsToLogin() {
        val auth = FakeAuthRepository(SessionState.Authenticated("uid-1", "ana@unibanagro.com"))
        launch(auth)
        awaitTag("inventory-list")
        auth.setSession(SessionState.Unauthenticated)
        awaitTag("login-submit")
        assertAbsent("inventory-list")
    }
}
