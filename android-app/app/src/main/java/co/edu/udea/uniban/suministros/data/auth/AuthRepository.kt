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
    /** Registro (HU_13): el backend rechazó el perfil porque otra cuenta ya usa ese documento. */
    DocumentInUse,
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
 * Si la cuenta de la sesión tiene perfil en el backend. Es independiente de [SessionState] para no
 * bloquear el uso sin conexión: mientras no se sepa, la app trabaja como siempre.
 */
enum class ProfileState {
    /** Aún no se consultó, o no se pudo (sin red, servidor dormido o caído): no se bloquea nada. */
    Unknown,

    /** El backend confirmó el perfil. */
    Complete,

    /** El backend no conoce la cuenta (huérfana): hay que completar el perfil antes de usar la app. */
    Missing,
}

/**
 * Contrato de identidad (RT_05, RT_13): autenticar no equivale a autorizar; los roles
 * los valida el backend. La contraseña nunca se guarda.
 */
interface AuthRepository {
    /** Estado actual; disponible sin red porque Firebase persiste la sesión en disco. */
    val sessionState: StateFlow<SessionState>

    /** Perfil de la cuenta con sesión abierta; vuelve a [ProfileState.Unknown] al cambiar de cuenta. */
    val profileState: StateFlow<ProfileState>

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
     * Pregunta al backend si la cuenta abierta tiene perfil y actualiza [profileState]. Sin red, con el
     * servidor caído o sin sesión deja [ProfileState.Unknown]. No hace nada si el perfil ya está confirmado.
     */
    suspend fun refreshProfile()

    /**
     * Crea el perfil de una cuenta de Firebase que no lo tiene (cuenta huérfana). Si sale bien,
     * [profileState] pasa a [ProfileState.Complete]. Falla con [AuthException].
     */
    suspend fun completeProfile(name: String, document: String, phone: String?): Result<Unit>

    /**
     * Token de Firebase de la sesión abierta, solo si es la cuenta [uid]; `null` si la sesión es
     * de otra cuenta o de demostración. Falla si no se puede renovar (p. ej. sin red).
     */
    suspend fun idToken(uid: String): String?

    fun signOut()

    /** Abre una sesión local de demostración. No hace nada si [demoAvailable] es `false`. */
    fun startDemo()
}
