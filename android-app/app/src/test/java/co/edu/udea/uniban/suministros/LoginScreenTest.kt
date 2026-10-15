package co.edu.udea.uniban.suministros

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.ui.auth.LoginError
import co.edu.udea.uniban.suministros.ui.auth.LoginScreen
import co.edu.udea.uniban.suministros.ui.auth.LoginUiState
import co.edu.udea.uniban.suministros.ui.theme.UnibanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
class LoginScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(initial: LoginUiState) {
        compose.setContent {
            UnibanTheme {
                var state by remember { mutableStateOf(initial) }
                LoginScreen(
                    state = state,
                    onEmailChange = { state = state.copy(email = it) },
                    onPasswordChange = { state = state.copy(password = it) },
                    onTogglePassword = { state = state.copy(passwordVisible = !state.passwordVisible) },
                    onSubmit = {},
                    onContinueDemo = {},
                )
            }
        }
    }

    @Test
    fun showsBrandFieldsAndButton() {
        show(LoginUiState())
        compose.onNodeWithText("Unibán Suministros").assertExists()
        compose.onNodeWithText("Gestión agrícola de productores").assertExists()
        compose.onNodeWithText("Correo electrónico").assertExists()
        compose.onNodeWithText("Contraseña").assertExists()
        compose.onNodeWithTag("login-email").assertExists()
        compose.onNodeWithTag("login-password").assertExists()
        compose.onNodeWithTag("login-submit").assertIsEnabled()
        compose.onNodeWithText("¿Problemas para ingresar? Contacte a su asesor Unibán.").assertExists()
        compose.onNodeWithTag("login-demo").assertDoesNotExist()
    }

    @Test
    fun showsErrorBanner() {
        show(LoginUiState(error = LoginError.Auth(AuthError.InvalidCredentials)))
        compose.onNodeWithText("Correo o contraseña incorrectos.").assertExists()
    }

    @Test
    fun loadingDisablesSubmitAndShowsProgressText() {
        show(LoginUiState(isLoading = true))
        compose.onNodeWithTag("login-submit").assertIsNotEnabled()
        compose.onNodeWithText("Ingresando…").assertExists()
    }

    @Test
    fun passwordCanBeShownAndHidden() {
        show(LoginUiState())
        compose.onNodeWithContentDescription("Mostrar contraseña").performClick()
        compose.onNodeWithContentDescription("Ocultar contraseña").assertExists()
        compose.onNodeWithContentDescription("Ocultar contraseña").performClick()
        compose.onNodeWithContentDescription("Mostrar contraseña").assertExists()
    }

    @Test
    fun demoButtonAndConfigNoticeAppearWhenRequested() {
        show(LoginUiState(firebaseConfigured = false, demoAvailable = true))
        compose.onNodeWithText("Firebase no está configurado en este dispositivo. Ver README.").assertExists()
        compose.onNodeWithTag("login-demo").assertExists()
    }
}
