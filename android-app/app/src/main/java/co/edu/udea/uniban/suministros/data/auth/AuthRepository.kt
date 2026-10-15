package co.edu.udea.uniban.suministros.data.auth

import kotlinx.coroutines.flow.StateFlow

/** Errores de autenticación ya traducidos; la UI nunca ve excepciones de Firebase. */
enum class AuthError {
    InvalidCredentials,
    Network,
    TooManyRequests,
    AccountDisabled,
    NotConfigured,
    Unknown,
}

class AuthException(val error: AuthError) : Exception(error.name)

/**
 * Contrato de identidad (RT_05, RT_13): autenticar no equivale a autorizar; los roles
 * los valida el backend. La contraseña nunca se guarda.
 */
interface AuthRepository {
    /** Estado actual; disponible sin red porque Firebase persiste la sesión en disco. */
    val sessionState: StateFlow<SessionState>

    /** `false` cuando falta `google-services.json` y Firebase no se inicializó. */
    val isConfigured: Boolean

    /** Acceso de demostración: solo builds debug sin Firebase configurado. */
    val demoAvailable: Boolean

    /** Falla con [AuthException] cuando no se puede autenticar. */
    suspend fun signIn(email: String, password: String): Result<Unit>

    fun signOut()

    /** Abre una sesión local de demostración. No hace nada si [demoAvailable] es `false`. */
    fun startDemo()
}
