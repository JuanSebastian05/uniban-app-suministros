package co.edu.udea.uniban.suministros.data.auth

import android.content.Context
import co.edu.udea.uniban.suministros.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Autenticación con Firebase Auth. Firebase persiste la sesión en disco, así que
 * `currentUser` está disponible sin conexión y el estado inicial es inmediato.
 * Si no hay `google-services.json`, Firebase no se inicializa y no se toca `FirebaseAuth`.
 */
class FirebaseAuthRepository(context: Context) : AuthRepository {
    private val auth: FirebaseAuth? =
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAuth.getInstance()

    override val isConfigured: Boolean = auth != null
    override val demoAvailable: Boolean = BuildConfig.DEBUG && auth == null

    private val state = MutableStateFlow(auth?.currentUser.toSession())
    override val sessionState: StateFlow<SessionState> = state.asStateFlow()

    init {
        // El repositorio vive tanto como la aplicación; no hace falta quitar el listener.
        auth?.addAuthStateListener { state.value = it.currentUser.toSession() }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        val firebase = auth ?: return Result.failure(AuthException(AuthError.NotConfigured))
        return try {
            firebase.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(AuthException(mapFirebaseError(error)))
        }
    }

    override fun signOut() {
        if (auth != null) auth.signOut() else state.value = SessionState.Unauthenticated
    }

    override fun startDemo() {
        if (demoAvailable) state.value = SessionState.Authenticated(DEMO_UID, null)
    }

    private fun com.google.firebase.auth.FirebaseUser?.toSession(): SessionState =
        if (this == null) SessionState.Unauthenticated else SessionState.Authenticated(uid, email)

    private companion object {
        const val DEMO_UID = "demo"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val failure = task.exception
        when {
            failure != null -> continuation.resumeWithException(failure)
            task.isCanceled -> continuation.cancel()
            else -> continuation.resume(task.result)
        }
    }
}
