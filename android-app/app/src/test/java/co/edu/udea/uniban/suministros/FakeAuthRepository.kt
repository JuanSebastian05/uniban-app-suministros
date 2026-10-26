package co.edu.udea.uniban.suministros

import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.data.auth.AuthException
import co.edu.udea.uniban.suministros.data.auth.AuthRepository
import co.edu.udea.uniban.suministros.data.auth.ProfileState
import co.edu.udea.uniban.suministros.data.auth.Registration
import co.edu.udea.uniban.suministros.data.auth.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Repositorio de identidad en memoria para pruebas. */
class FakeAuthRepository(
    initial: SessionState = SessionState.Unauthenticated,
    override val isConfigured: Boolean = true,
    override val demoAvailable: Boolean = false,
) : AuthRepository {
    private val state = MutableStateFlow(initial)
    override val sessionState: StateFlow<SessionState> = state

    /** Comportamiento de [signIn]; por defecto, éxito que abre sesión. */
    var onSignIn: suspend (String, String) -> Result<Unit> = { email, _ ->
        state.value = SessionState.Authenticated("uid-1", email)
        Result.success(Unit)
    }
    val signInCalls = mutableListOf<Pair<String, String>>()

    /** Comportamiento de [register]; por defecto, éxito que abre sesión con el correo nuevo. */
    var onRegister: suspend (Registration, String) -> Result<Unit> = { registration, _ ->
        state.value = SessionState.Authenticated("uid-new", registration.email)
        Result.success(Unit)
    }
    val registerCalls = mutableListOf<Pair<Registration, String>>()
    var signOutCalls = 0

    fun failWith(error: AuthError) {
        onSignIn = { _, _ -> Result.failure(AuthException(error)) }
    }

    fun setSession(session: SessionState) { state.value = session }

    private val profile = MutableStateFlow(ProfileState.Unknown)
    override val profileState: StateFlow<ProfileState> = profile

    /** Lo que «responde el backend» cuando la app pregunta por el perfil; `null` = no responde (sin red). */
    var profileOnRefresh: ProfileState? = null
    var refreshProfileCalls = 0

    /** Comportamiento de [completeProfile]; por defecto, éxito que deja el perfil completo. */
    var onCompleteProfile: suspend (String, String, String?) -> Result<Unit> = { _, _, _ ->
        profile.value = ProfileState.Complete
        Result.success(Unit)
    }
    val completeProfileCalls = mutableListOf<Triple<String, String, String?>>()

    fun setProfile(value: ProfileState) { profile.value = value }

    override suspend fun refreshProfile() {
        refreshProfileCalls++
        profileOnRefresh?.let { profile.value = it }
    }

    override suspend fun completeProfile(name: String, document: String, phone: String?): Result<Unit> {
        completeProfileCalls += Triple(name, document, phone)
        return onCompleteProfile(name, document, phone)
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        signInCalls += email to password
        return onSignIn(email, password)
    }

    override suspend fun register(registration: Registration, password: String): Result<Unit> {
        registerCalls += registration to password
        return onRegister(registration, password)
    }

    /** Token que entrega [idToken] a la cuenta con sesión abierta. */
    var token: String? = "token-de-prueba"

    override suspend fun idToken(uid: String): String? =
        token.takeIf { (state.value as? SessionState.Authenticated)?.uid == uid }

    override fun signOut() {
        signOutCalls++
        state.value = SessionState.Unauthenticated
    }

    override fun startDemo() {
        if (demoAvailable) state.value = SessionState.Authenticated("demo", null)
    }
}
