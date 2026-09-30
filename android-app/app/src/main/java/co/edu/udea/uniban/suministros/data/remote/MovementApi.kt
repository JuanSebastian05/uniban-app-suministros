package co.edu.udea.uniban.suministros.data.remote

import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Contrato con el backend FastAPI (`/api/v1/movements`, RT_03).
 *
 * 201 = guardado, 200 = ya existía idéntico (idempotente por UUID), 409/422 = rechazado.
 * Los nombres de campo coinciden con el JSON camelCase del backend.
 */
interface MovementApi {
    @POST("movements")
    suspend fun send(@Body movement: MovementDto): Response<Unit>
}

data class ProducerDto(val id: String, val name: String)

data class LocationDto(val id: String, val name: String, val producer: ProducerDto)

data class SupplyDto(val id: String, val name: String, val category: String, val unit: String)

data class InventoryDto(
    val id: String,
    val initialQuantity: BigDecimal,
    val location: LocationDto,
    val supply: SupplyDto,
)

data class MovementDto(
    val id: String,
    val type: String,
    val quantity: BigDecimal,
    val date: String,
    val observation: String,
    val createdAt: String,
    val updatedAt: String,
    val inventory: InventoryDto,
)
