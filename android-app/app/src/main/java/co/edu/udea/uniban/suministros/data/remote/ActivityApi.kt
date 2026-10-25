package co.edu.udea.uniban.suministros.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Sincronización de actividades productivas (`/api/v1/activities`, HU_08).
 *
 * Igual que los movimientos: exige el token de Firebase; 201 = guardada, 200 = ya existía idéntica,
 * 401/403 = token o perfil, 409/422 = rechazada.
 */
interface ActivityApi {
    @POST("activities")
    suspend fun send(
        @Header("Authorization") authorization: String,
        @Body activity: ActivityDto,
    ): Response<Unit>
}

data class ActivityDto(
    val id: String,
    val parcelId: String,
    val cycleId: String,
    val type: String,
    val date: String,
    val observation: String,
    val createdAt: String,
    val updatedAt: String,
)
