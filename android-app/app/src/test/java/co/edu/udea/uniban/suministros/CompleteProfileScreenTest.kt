package co.edu.udea.uniban.suministros

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.ui.auth.CompleteProfileError
import co.edu.udea.uniban.suministros.ui.auth.CompleteProfileScreen
import co.edu.udea.uniban.suministros.ui.auth.CompleteProfileUiState
import co.edu.udea.uniban.suministros.ui.theme.UnibanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pantalla para completar el perfil (P-02): mensaje con la cuenta, errores y aviso de servidor dormido. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
class CompleteProfileScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: CompleteProfileUiState) {
        compose.setContent {
            UnibanTheme {
                CompleteProfileScreen(
                    state = state,
                    onNameChange = {}, onDocumentChange = {}, onPhoneChange = {},
                    onSubmit = {}, onSignOut = {},
                )
            }
        }
    }

    @Test
    fun explainsWhichAccountIsMissingItsProfile() {
        show(CompleteProfileUiState(email = "ana@unibanagro.com"))
        compose.onNodeWithText(
            "Su cuenta ana@unibanagro.com aún no tiene perfil en Unibán Suministros. Complete sus datos para continuar.",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("complete-profile-submit").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("complete-profile-sign-out").performScrollTo().assertIsEnabled()
    }

    @Test
    fun duplicateDocumentShowsItsMessage() {
        show(CompleteProfileUiState(error = CompleteProfileError.Auth(AuthError.DocumentInUse)))
        compose.onNodeWithText("Ya existe una cuenta con este documento. Verifique el número o inicie sesión.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun offlineAndServerFailuresHaveTheirOwnMessage() {
        show(CompleteProfileUiState(error = CompleteProfileError.Auth(AuthError.Network)))
        compose.onNodeWithText("Sin conexión. Se necesita internet para guardar su perfil.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun missingFieldsShowTheRequiredFieldsMessage() {
        show(CompleteProfileUiState(error = CompleteProfileError.EmptyFields))
        compose.onNodeWithText("Complete los campos obligatorios.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun whileSavingItWarnsThatTheServerMayBeWakingUpAndLocksTheForm() {
        show(CompleteProfileUiState(name = "Ana", document = "1", isLoading = true))
        compose.onNodeWithTag("server-wakeup-hint").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("complete-profile-submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("complete-profile-sign-out").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun hintIsHiddenWhenNothingIsBeingSaved() {
        show(CompleteProfileUiState())
        compose.onNodeWithTag("server-wakeup-hint").assertDoesNotExist()
    }
}
