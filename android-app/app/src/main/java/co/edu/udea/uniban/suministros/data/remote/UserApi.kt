package co.edu.udea.uniban.suministros.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Registro del perfil en el backend (`/api/v1/users/register`, HU_01).
 *
 * El backend toma el uid y el correo del token de Firebase. 201 = creado,
 * 200 = la cuenta ya estaba registrada (idempotente), 401 = token inválido.
 */
interface UserApi {
    @POST("users/register")
    suspend fun register(
        @Header("Authorization") authorization: String,
        @Body profile: RegisterProfileDto,
    ): Response<Unit>

    /**
     * Perfil de la cuenta del token (`/api/v1/users/me`): 200 = tiene perfil, 404 = la cuenta
     * existe en Firebase pero nunca se registró en el backend (cuenta huérfana, P-02).
     */
    @GET("users/me")
    suspend fun me(@Header("Authorization") authorization: String): Response<Unit>
}

data class RegisterProfileDto(val name: String, val document: String, val phone: String?)
