package co.edu.udea.uniban.suministros.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Recomendaciones de la parcela (`/api/v1/parcels/{id}/recommendation`, HU_12). La app solo habla con nuestro backend
 * (RT_11): es él quien consulta al Equipo 2.
 *
 * POST pide una recomendación nueva (síncrono: puede tardar minutos si el servicio del Equipo 2 está dormido, por eso se
 * usa un cliente con espera larga): 201 = guardada; 409 `detail.code` NO_AFORAMIENTO = la parcela aún no tiene aforamiento;
 * 502/503 = el servicio del Equipo 2 no respondió bien; 404 = la parcela no existe; 401/403 = sesión no válida o sin perfil.
 * GET devuelve la última guardada (404 `detail.code` NO_RECOMMENDATION si aún no hay).
 */
interface RecommendationApi {
    @POST("parcels/{id}/recommendation")
    suspend fun request(
        @Header("Authorization") authorization: String,
        @Path("id") parcelId: String,
    ): Response<RecommendationDto>

    @GET("parcels/{id}/recommendation")
    suspend fun latest(
        @Header("Authorization") authorization: String,
        @Path("id") parcelId: String,
    ): Response<RecommendationDto>
}

/** Recomendación tal como la entrega el backend (camelCase). */
data class RecommendationDto(
    val id: String,
    val parcelId: String,
    val period: String,
    val generatedAt: String,
    val sourceVersion: String,
    val volumeBunches: Double,
    val cutDeadline: String,
    val meetsWindow: Boolean,
    val plan: RecommendationPlanDto?,
    val supplies: List<RecommendationSupplyDto>?,
    val metrics: RecommendationMetricsDto? = null,
)

/** Plan completo enviado por el Equipo 2; valores nulos permiten leer recomendaciones locales antiguas. */
data class RecommendationPlanDto(
    /** Motivo informado por el servicio, por ejemplo, por qué no cumple la ventana. */
    val reason: String?,
    val parcelId: String? = null,
    val cutDate: String? = null,
    val vehicleId: String? = null,
    val order: Int? = null,
    val estimatedLoadBunches: Int? = null,
    val meetsWindow: Boolean? = null,
)

data class RecommendationMetricsDto(
    val windowCompliance: Double? = null,
    val capacityUse: Double? = null,
)

data class RecommendationSupplyDto(
    val name: String,
    val date: String,
    val requiredQuantity: Double,
    val availableQuantity: Double,
    val deficit: Double,
)
