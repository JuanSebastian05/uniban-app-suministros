package co.edu.udea.uniban.suministros.data.auth

import android.content.Context
import co.edu.udea.uniban.suministros.BuildConfig
import co.edu.udea.uniban.suministros.data.remote.UserApi
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Autenticación con Firebase Auth. Firebase persiste la sesión en disco, así que
 * `currentUser` está disponible sin conexión y el estado inicial es inmediato.
 * Si no hay `google-services.json`, Firebase no se inicializa y no se toca `FirebaseAuth`.
 *
 * El registro crea la cuenta en Firebase y luego el perfil en el backend ([UserApi]); mientras
 * tanto la sesión no se publica, para que la app no entre al inventario con un perfil a medias.
 */
class FirebaseAuthRepository(context: Context, private val userApi: UserApi) : AuthRepository {
    private val auth: FirebaseAuth? =
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAuth.getInstance()

    override val isConfigured: Boolean = auth != null
    override val demoAvailable: Boolean = BuildConfig.DEBUG && auth == null

    private val state = MutableStateFlow(auth?.currentUser.toSession())
    override val sessionState: StateFlow<SessionState> = state.asStateFlow()

    @Volatile private var registering = false

    init {
        // El repositorio vive tanto como la aplicación; no hace falta quitar el listener.
        auth?.addAuthStateListener { if (!registering) state.value = it.currentUser.toSession() }
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

    override suspend fun register(registration: Registration, password: String): Result<Unit> {
        val firebase = auth ?: return Result.failure(AuthException(AuthError.NotConfigured))
        registering = true
        try {
            val user = try {
                firebase.createUserWithEmailAndPassword(registration.email, password).await().user
                    ?: return Result.failure(AuthException(AuthError.Unknown))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return Result.failure(AuthException(mapFirebaseError(error)))
            }
            val profile = try {
                val token = user.getIdToken(false).await().token.orEmpty()
                sendProfile(userApi, token, registration)
            } catch (error: CancellationException) {
                discard(firebase, user)
                throw error
            } catch (error: Exception) {
                Result.failure(AuthException(mapFirebaseError(error)))
            }
            if (profile.isFailure) discard(firebase, user)
            return profile
        } finally {
            registering = false
            state.value = firebase.currentUser.toSession()
        }
    }

    /** Deshace una cuenta sin perfil en el backend; si no se puede borrar, al menos cierra la sesión. */
    private suspend fun discard(firebase: FirebaseAuth, user: FirebaseUser) = withContext(NonCancellable) {
        runCatching { user.delete().await() }
        firebase.signOut()
    }

    override suspend fun idToken(uid: String): String? {
        val user = auth?.currentUser?.takeIf { it.uid == uid } ?: return null
        return user.getIdToken(false).await().token
    }

    override fun signOut() {
        if (auth != null) auth.signOut() else state.value = SessionState.Unauthenticated
    }

    override fun startDemo() {
        if (demoAvailable) state.value = SessionState.Authenticated(DEMO_UID, null)
    }

    private fun FirebaseUser?.toSession(): SessionState =
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
