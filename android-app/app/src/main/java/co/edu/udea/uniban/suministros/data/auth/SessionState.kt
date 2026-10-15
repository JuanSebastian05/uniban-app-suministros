package co.edu.udea.uniban.suministros.data.auth

/** Estado de la sesión del usuario. Solo identidad: ningún dato de dominio (RT_05). */
sealed interface SessionState {
    data object Loading : SessionState
    data class Authenticated(val uid: String, val email: String?) : SessionState
    data object Unauthenticated : SessionState
}
