package co.edu.udea.uniban.suministros.data.auth

import co.edu.udea.uniban.suministros.data.remote.RegisterProfileDto
import co.edu.udea.uniban.suministros.data.remote.UserApi
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Envía el perfil al backend con el token de la cuenta recién creada; traduce el resultado a [AuthError]. */
internal suspend fun sendProfile(api: UserApi, idToken: String, registration: Registration): Result<Unit> =
    try {
        val response = api.register(
            "Bearer $idToken",
            RegisterProfileDto(registration.name, registration.document, registration.phone),
        )
        if (response.isSuccessful) Result.success(Unit) else Result.failure(AuthException(AuthError.Server))
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        Result.failure(AuthException(AuthError.Network))
    } catch (_: Exception) {
        Result.failure(AuthException(AuthError.Server))
    }
