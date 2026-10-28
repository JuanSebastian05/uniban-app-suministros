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
    private val names = context.applicationContext.getSharedPreferences("profile_names", Context.MODE_PRIVATE)
    private val auth: FirebaseAuth? =
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAuth.getInstance()

    override val isConfigured: Boolean = auth != null
    override val demoAvailable: Boolean = BuildConfig.DEBUG && auth == null

    private val state = MutableStateFlow(auth?.currentUser.toSession())
    override val sessionState: StateFlow<SessionState> = state.asStateFlow()

    private val profile = MutableStateFlow(ProfileState.Unknown)
    override val profileState: StateFlow<ProfileState> = profile.asStateFlow()

    @Volatile private var registering = false

    init {
        // El repositorio vive tanto como la aplicación; no hace falta quitar el listener.
        auth?.addAuthStateListener { if (!registering) publish(it.currentUser.toSession()) }
    }

    /** Publica la sesión; al cambiar de cuenta, el perfil de la anterior deja de valer. */
    private fun publish(session: SessionState) {
        if (session.uid() != state.value.uid()) profile.value = ProfileState.Unknown
        state.value = session
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
        var saved = false
        try {
            val user = try {
                firebase.createUserWithEmailAndPassword(registration.email, password).await().user
                    ?: return Result.failure(AuthException(AuthError.Unknown))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return Result.failure(AuthException(mapFirebaseError(error)))
            }
            val result = try {
                val token = user.getIdToken(false).await().token.orEmpty()
                sendProfile(userApi, token, registration)
            } catch (error: CancellationException) {
                discard(firebase, user)
                throw error
            } catch (error: Exception) {
                Result.failure(AuthException(mapFirebaseError(error)))
            }
            if (result.isFailure) discard(firebase, user) else {
                names.edit().putString(user.uid, registration.name).apply()
                saved = true
            }
            return result
        } finally {
            registering = false
            publish(firebase.currentUser.toSession())
            // Recién registrada: el perfil existe, no hace falta preguntarlo al backend.
            if (saved) profile.value = ProfileState.Complete
        }
    }

    override suspend fun refreshProfile() {
        val firebase = auth ?: return
        val user = firebase.currentUser ?: return
        if (profile.value == ProfileState.Complete) return
        var fetchedName: String? = null
        val result = try {
            fetchProfileState(userApi, user.getIdToken(false).await().token.orEmpty()) { fetchedName = it }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ProfileState.Unknown
        }
        // La cuenta pudo cambiar mientras llegaba la respuesta (el servidor tarda al despertar).
        if (firebase.currentUser?.uid == user.uid) {
            fetchedName?.let { names.edit().putString(user.uid, it).apply() }
            publish(user.toSession())
            profile.value = result
        }
    }

    override suspend fun completeProfile(name: String, document: String, phone: String?): Result<Unit> {
        val firebase = auth ?: return Result.failure(AuthException(AuthError.NotConfigured))
        val user = firebase.currentUser ?: return Result.failure(AuthException(AuthError.Unknown))
        val result = try {
            val token = user.getIdToken(false).await().token.orEmpty()
            sendProfile(userApi, token, Registration(name, document, phone, user.email.orEmpty()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(AuthException(mapFirebaseError(error)))
        }
        if (result.isSuccess && firebase.currentUser?.uid == user.uid) {
            names.edit().putString(user.uid, name).apply()
            publish(user.toSession())
            profile.value = ProfileState.Complete
        }
        return result
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
        if (auth != null) auth.signOut() else publish(SessionState.Unauthenticated)
    }

    override fun startDemo() {
        if (demoAvailable) publish(SessionState.Authenticated(DEMO_UID, null, "Productor"))
    }

    private fun FirebaseUser?.toSession(): SessionState =
        if (this == null) SessionState.Unauthenticated
        else SessionState.Authenticated(uid, email, names.getString(uid, null) ?: displayName)

    private fun SessionState.uid(): String? = (this as? SessionState.Authenticated)?.uid

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
