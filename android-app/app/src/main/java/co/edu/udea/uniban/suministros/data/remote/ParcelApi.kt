package co.edu.udea.uniban.suministros.data.remote

import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Parcelas del usuario autenticado (`/api/v1/parcels`, HU_14).
 *
 * POST: 201 = creada, 200 = ya existía idéntica (idempotente por UUID), 409 = `detail.code`
 * DUPLICATE_NAME o ID_CONFLICT, 422 = datos inválidos, 401/403 = sesión no válida o sin perfil.
 */
interface ParcelApi {
    @POST("parcels")
    suspend fun create(
        @Header("Authorization") authorization: String,
        @Body parcel: ParcelDto,
    ): Response<ParcelResponseDto>

    @GET("parcels")
    suspend fun list(@Header("Authorization") authorization: String): Response<List<ParcelResponseDto>>
}

data class CycleDto(val id: String, val startDate: String, val status: String)

/** [cycle] es opcional: Gson omite el campo cuando es nulo. */
data class ParcelDto(val id: String, val name: String, val areaHa: BigDecimal, val cycle: CycleDto?)

data class CycleResponseDto(val id: String, val startDate: String, val endDate: String?, val status: String)

data class ParcelResponseDto(
    val id: String,
    val name: String,
    val areaHa: BigDecimal,
    val activeCycle: CycleResponseDto?,
)

/** Cuerpo de un 409: `{"detail": {"code": "...", "message": "..."}}`. */
data class ApiErrorDto(val detail: ApiErrorDetailDto?)

data class ApiErrorDetailDto(val code: String?, val message: String?)
