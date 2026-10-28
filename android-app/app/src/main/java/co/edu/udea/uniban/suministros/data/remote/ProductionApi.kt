package co.edu.udea.uniban.suministros.data.remote

import retrofit2.Response
import retrofit2.http.*

data class HarvestDto(
    val id: String, val parcelId: String, val cycleId: String, val recommendationId: String,
    val aforamientoId: String, val date: String, val quantity: Int, val observation: String,
    val exceptionConfirmed: Boolean, val createdAt: String,
)
data class DispatchDto(
    val id: String, val parcelId: String, val cycleId: String, val recommendationId: String,
    val dispatchedAt: String, val quantity: Int, val vehicleId: String, val order: Int,
    val observation: String, val createdAt: String, val consumptions: List<MovementDto>,
)
data class ProductionHistoryDto(
    val harvests: List<HarvestDto>, val dispatches: List<DispatchDto>, val recommendations: List<RecommendationDto>,
)
interface ProductionApi {
    @POST("harvests") suspend fun harvest(@Header("Authorization") authorization: String, @Body body: HarvestDto): Response<Unit>
    @POST("dispatches") suspend fun dispatch(@Header("Authorization") authorization: String, @Body body: DispatchDto): Response<Unit>
    @GET("production/{parcelId}") suspend fun history(@Header("Authorization") authorization: String, @Path("parcelId") parcelId: String): Response<ProductionHistoryDto>
}
