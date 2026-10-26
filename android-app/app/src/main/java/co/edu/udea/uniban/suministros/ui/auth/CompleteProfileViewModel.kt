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
import co.edu.udea.uniban.suministros.data.auth.SessionState
import kotlinx.coroutines.launch

sealed interface CompleteProfileError {
    data object EmptyFields : CompleteProfileError
    data class Auth(val error: AuthError) : CompleteProfileError
}

data class CompleteProfileUiState(
    val email: String = "",
    val name: String = "",
    val document: String = "",
    val phone: String = "",
    val isLoading: Boolean = false,
    val error: CompleteProfileError? = null,
)

/**
 * Cuenta de Firebase sin perfil en el backend (registro interrumpido): pide los datos que faltan
 * y los envía con el token de la sesión abierta. Al terminar, la navegación reacciona a
 * `AuthRepository.profileState` y devuelve al inventario.
 */
class CompleteProfileViewModel(
    private val repository: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    var state by mutableStateOf(
        CompleteProfileUiState(
            email = (repository.sessionState.value as? SessionState.Authenticated)?.email.orEmpty(),
            name = savedState[KEY_NAME] ?: "",
            document = savedState[KEY_DOCUMENT] ?: "",
            phone = savedState[KEY_PHONE] ?: "",
        )
    )
        private set

    fun changeName(value: String) = update(KEY_NAME, value) { copy(name = value) }
    fun changeDocument(value: String) = update(KEY_DOCUMENT, value) { copy(document = value) }
    fun changePhone(value: String) = update(KEY_PHONE, value) { copy(phone = value) }

    fun submit() {
        if (state.isLoading) return
        val name = state.name.trim()
        val document = state.document.trim()
        val phone = state.phone.trim().ifEmpty { null }
        if (name.isEmpty() || document.isEmpty()) {
            state = state.copy(error = CompleteProfileError.EmptyFields)
            return
        }
        state = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.completeProfile(name, document, phone).fold(
                // La navegación reacciona al perfil completo; el botón sigue bloqueado hasta salir.
                onSuccess = {},
                onFailure = { failure ->
                    val error = (failure as? AuthException)?.error ?: AuthError.Unknown
                    state = state.copy(isLoading = false, error = CompleteProfileError.Auth(error))
                },
            )
        }
    }

    fun signOut() = repository.signOut()

    private inline fun update(key: String, value: String, change: CompleteProfileUiState.() -> CompleteProfileUiState) {
        savedState[key] = value
        state = state.change().copy(error = null)
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_DOCUMENT = "document"
        const val KEY_PHONE = "phone"
    }
}
