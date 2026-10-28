package co.edu.udea.uniban.suministros.data.auth

import co.edu.udea.uniban.suministros.data.remote.ApiErrorDto
import co.edu.udea.uniban.suministros.data.remote.RegisterProfileDto
import co.edu.udea.uniban.suministros.data.remote.UserApi
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/** Código del 409 con el que el backend avisa que el documento ya pertenece a otra cuenta (HU_13). */
internal const val DUPLICATE_DOCUMENT = "DUPLICATE_DOCUMENT"

/** Envía el perfil al backend con el token de la cuenta recién creada; traduce el resultado a [AuthError]. */
internal suspend fun sendProfile(api: UserApi, idToken: String, registration: Registration): Result<Unit> =
    try {
        val response = api.register(
            "Bearer $idToken",
            RegisterProfileDto(registration.name, registration.document, registration.phone),
        )
        when {
            response.isSuccessful -> Result.success(Unit)
            response.code() == 409 && errorCode(response) == DUPLICATE_DOCUMENT ->
                Result.failure(AuthException(AuthError.DocumentInUse))
            else -> Result.failure(AuthException(AuthError.Server))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        Result.failure(AuthException(AuthError.Network))
    } catch (_: Exception) {
        Result.failure(AuthException(AuthError.Server))
    }

/**
 * Consulta si la cuenta del token tiene perfil: 200 = [ProfileState.Complete], 404 = [ProfileState.Missing].
 * Cualquier otra cosa (sin red, 401, 5xx, servidor dormido) es [ProfileState.Unknown]: no se debe
 * bloquear al productor por un fallo que no prueba que le falte el perfil.
 */
internal suspend fun fetchProfileState(api: UserApi, idToken: String, onName: (String) -> Unit = {}): ProfileState =
    try {
        val response = api.me("Bearer $idToken")
        when {
            response.isSuccessful -> {
                response.body()?.name?.takeIf { it.isNotBlank() }?.let(onName)
                ProfileState.Complete
            }
            response.code() == 404 -> ProfileState.Missing
            else -> ProfileState.Unknown
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        ProfileState.Unknown
    }

private fun errorCode(response: Response<*>): String? = runCatching {
    Gson().fromJson(response.errorBody()?.charStream(), ApiErrorDto::class.java)?.detail?.code
}.getOrNull()
