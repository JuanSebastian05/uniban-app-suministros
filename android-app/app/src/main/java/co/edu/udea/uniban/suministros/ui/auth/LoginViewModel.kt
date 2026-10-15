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
import kotlinx.coroutines.launch

sealed interface LoginError {
    data object EmptyFields : LoginError
    data object InvalidEmail : LoginError
    data class Auth(val error: AuthError) : LoginError
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: LoginError? = null,
    val firebaseConfigured: Boolean = true,
    val demoAvailable: Boolean = false,
)

class LoginViewModel(
    private val repository: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    // El correo sobrevive a la recreación del proceso; la contraseña nunca se persiste.
    var state by mutableStateOf(
        LoginUiState(
            email = savedState["email"] ?: "",
            firebaseConfigured = repository.isConfigured,
            demoAvailable = repository.demoAvailable,
        )
    )
        private set

    fun changeEmail(value: String) {
        savedState["email"] = value
        state = state.copy(email = value, error = null)
    }

    fun changePassword(value: String) {
        state = state.copy(password = value, error = null)
    }

    fun togglePasswordVisible() {
        state = state.copy(passwordVisible = !state.passwordVisible)
    }

    fun submit() {
        if (state.isLoading) return
        val email = state.email.trim()
        val password = state.password
        val validation = when {
            email.isEmpty() || password.isEmpty() -> LoginError.EmptyFields
            !EMAIL_PATTERN.matches(email) -> LoginError.InvalidEmail
            else -> null
        }
        if (validation != null) {
            state = state.copy(error = validation)
            return
        }
        state = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.signIn(email, password).fold(
                // La navegación reacciona al cambio de sesión; isLoading sigue activo para evitar doble envío.
                onSuccess = {},
                onFailure = { failure ->
                    val error = (failure as? AuthException)?.error ?: AuthError.Unknown
                    state = state.copy(isLoading = false, error = LoginError.Auth(error))
                },
            )
        }
    }

    fun continueAsDemo() = repository.startDemo()

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
