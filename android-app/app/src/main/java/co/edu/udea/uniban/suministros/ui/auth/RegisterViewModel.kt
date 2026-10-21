package co.edu.udea.uniban.suministros.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.data.auth.AuthException
import co.edu.udea.uniban.suministros.data.auth.AuthRepository
import co.edu.udea.uniban.suministros.data.auth.Registration
import kotlinx.coroutines.launch

sealed interface RegisterError {
    data object EmptyFields : RegisterError
    data object InvalidEmail : RegisterError
    data object ShortPassword : RegisterError
    data object PasswordMismatch : RegisterError
    data class Auth(val error: AuthError) : RegisterError
}

data class RegisterUiState(
    val name: String = "",
    val document: String = "",
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: RegisterError? = null,
    val firebaseConfigured: Boolean = true,
)

class RegisterViewModel(
    private val repository: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    // Los datos del perfil sobreviven a la recreación del proceso; las contraseñas nunca se persisten.
    var state by mutableStateOf(
        RegisterUiState(
            name = savedState[KEY_NAME] ?: "",
            document = savedState[KEY_DOCUMENT] ?: "",
            phone = savedState[KEY_PHONE] ?: "",
            email = savedState[KEY_EMAIL] ?: "",
            firebaseConfigured = repository.isConfigured,
        )
    )
        private set

    fun changeName(value: String) = update(KEY_NAME, value) { copy(name = value) }
    fun changeDocument(value: String) = update(KEY_DOCUMENT, value) { copy(document = value) }
    fun changePhone(value: String) = update(KEY_PHONE, value) { copy(phone = value) }
    fun changeEmail(value: String) = update(KEY_EMAIL, value) { copy(email = value) }

    fun changePassword(value: String) {
        state = state.copy(password = value, error = null)
    }

    fun changeConfirmPassword(value: String) {
        state = state.copy(confirmPassword = value, error = null)
    }

    fun togglePasswordVisible() {
        state = state.copy(passwordVisible = !state.passwordVisible)
    }

    fun submit() {
        if (state.isLoading) return
        val registration = Registration(
            name = state.name.trim(),
            document = state.document.trim(),
            phone = state.phone.trim().ifEmpty { null },
            email = state.email.trim(),
        )
        val password = state.password
        val validation = when {
            registration.name.isEmpty() || registration.document.isEmpty() || registration.email.isEmpty() ||
                password.isEmpty() || state.confirmPassword.isEmpty() -> RegisterError.EmptyFields
            !EMAIL_PATTERN.matches(registration.email) -> RegisterError.InvalidEmail
            password.length < MIN_PASSWORD_LENGTH -> RegisterError.ShortPassword
            password != state.confirmPassword -> RegisterError.PasswordMismatch
            else -> null
        }
        if (validation != null) {
            state = state.copy(error = validation)
            return
        }
        state = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.register(registration, password).fold(
                // Igual que en el login: la navegación reacciona a la sesión y el botón sigue bloqueado.
                onSuccess = {},
                onFailure = { failure ->
                    val error = (failure as? AuthException)?.error ?: AuthError.Unknown
                    state = state.copy(isLoading = false, error = RegisterError.Auth(error))
                },
            )
        }
    }

    private inline fun update(key: String, value: String, change: RegisterUiState.() -> RegisterUiState) {
        savedState[key] = value
        state = state.change().copy(error = null)
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_DOCUMENT = "document"
        const val KEY_PHONE = "phone"
        const val KEY_EMAIL = "email"

        /** Mínimo que exige Firebase Authentication. */
        const val MIN_PASSWORD_LENGTH = 6
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
