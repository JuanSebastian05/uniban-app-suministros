package co.edu.udea.uniban.suministros

import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.data.auth.AuthException
import co.edu.udea.uniban.suministros.data.auth.AuthRepository
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
    var signOutCalls = 0

    fun failWith(error: AuthError) {
        onSignIn = { _, _ -> Result.failure(AuthException(error)) }
    }

    fun setSession(session: SessionState) { state.value = session }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        signInCalls += email to password
        return onSignIn(email, password)
    }

    override fun signOut() {
        signOutCalls++
        state.value = SessionState.Unauthenticated
    }

    override fun startDemo() {
        if (demoAvailable) state.value = SessionState.Authenticated("demo", null)
    }
}
