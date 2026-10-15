package co.edu.udea.uniban.suministros.data.auth

import kotlinx.coroutines.flow.StateFlow

/** Errores de autenticación ya traducidos; la UI nunca ve excepciones de Firebase. */
enum class AuthError {
    InvalidCredentials,
    Network,
    TooManyRequests,
    AccountDisabled,
    NotConfigured,
    /** Registro: el correo ya tiene una cuenta. */
    EmailInUse,
    /** Registro: Firebase rechazó la contraseña por débil. */
    WeakPassword,
    /** Registro: la cuenta se creó pero el backend no guardó el perfil. */
    Server,
    Unknown,
}

class AuthException(val error: AuthError) : Exception(error.name)

/** Datos de una cuenta nueva. La contraseña viaja aparte y nunca se guarda. */
data class Registration(
    val name: String,
    val document: String,
    val phone: String?,
    val email: String,
)

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

    /**
     * Crea la cuenta en Firebase y su perfil en el backend; al terminar, la sesión queda abierta.
     * Si el backend falla, la cuenta de Firebase se elimina para que el registro se pueda repetir.
     * Falla con [AuthException].
     */
    suspend fun register(registration: Registration, password: String): Result<Unit>

    /**
     * Token de Firebase de la sesión abierta, solo si es la cuenta [uid]; `null` si la sesión es
     * de otra cuenta o de demostración. Falla si no se puede renovar (p. ej. sin red).
     */
    suspend fun idToken(uid: String): String?

    fun signOut()

    /** Abre una sesión local de demostración. No hace nada si [demoAvailable] es `false`. */
    fun startDemo()
}
