package co.edu.udea.uniban.suministros

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.ui.auth.RegisterError
import co.edu.udea.uniban.suministros.ui.auth.RegisterScreen
import co.edu.udea.uniban.suministros.ui.auth.RegisterUiState
import co.edu.udea.uniban.suministros.ui.theme.UnibanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pantalla de registro (HU_13): mensajes de error visibles y datos conservados. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
class RegisterScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: RegisterUiState) {
        compose.setContent {
            UnibanTheme {
                RegisterScreen(
                    state = state,
                    onNameChange = {}, onDocumentChange = {}, onPhoneChange = {}, onEmailChange = {},
                    onPasswordChange = {}, onConfirmPasswordChange = {}, onTogglePassword = {},
                    onSubmit = {}, onBack = {},
                )
            }
        }
    }

    @Test
    fun duplicateDocumentShowsItsMessage() {
        show(RegisterUiState(document = "1020304050", error = RegisterError.Auth(AuthError.DocumentInUse)))
        compose.onNodeWithText("Ya existe una cuenta con este documento. Verifique el número o inicie sesión.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun duplicateEmailStillShowsItsOwnMessage() {
        show(RegisterUiState(error = RegisterError.Auth(AuthError.EmailInUse)))
        compose.onNodeWithText("Ya existe una cuenta con este correo. Inicie sesión.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun whileCreatingTheAccountItWarnsThatTheServerMayBeWakingUp() {
        show(RegisterUiState(isLoading = true))
        compose.onNodeWithTag("server-wakeup-hint").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noWakeupHintWhenIdle() {
        show(RegisterUiState())
        compose.onNodeWithTag("server-wakeup-hint").assertDoesNotExist()
    }
}
