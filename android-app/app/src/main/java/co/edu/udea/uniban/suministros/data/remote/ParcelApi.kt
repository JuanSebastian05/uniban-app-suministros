package co.edu.udea.uniban.suministros.data.remote

import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

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

    /** Aforamiento más reciente de la parcela (HU_09): 200 = lo trae, 404 = la parcela aún no tiene (o no existe). */
    @GET("parcels/{id}/aforamiento")
    suspend fun aforamiento(
        @Header("Authorization") authorization: String,
        @Path("id") parcelId: String,
    ): Response<AforamientoDto>
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
    /** Aforamiento más reciente del Equipo 1 (HU_09); nulo si la parcela no tiene o el servidor es anterior. */
    val aforamiento: AforamientoDto? = null,
)

/** Aforamiento del Equipo 1 tal como lo entrega el backend; [confidence] es una fracción entre 0 y 1 (opcional). */
data class AforamientoDto(
    val id: String,
    val parcelId: String,
    val date: String,
    val areaHa: BigDecimal,
    val estimatedPlants: Int,
    val confidence: BigDecimal?,
)

/** Cuerpo de un 409: `{"detail": {"code": "...", "message": "..."}}`. */
data class ApiErrorDto(val detail: ApiErrorDetailDto?)

data class ApiErrorDetailDto(val code: String?, val message: String?)
