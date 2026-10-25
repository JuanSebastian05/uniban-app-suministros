package co.edu.udea.uniban.suministros.data.remote

import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Contrato con el backend FastAPI (`/api/v1/movements`, RT_03).
 *
 * Exige el token de Firebase: el backend guarda el movimiento en el inventario del usuario
 * dueño del token. 201 = guardado, 200 = ya existía idéntico (idempotente por UUID),
 * 401 = token inválido, 403 = cuenta sin perfil, 409/422 = rechazado.
 * Los nombres de campo coinciden con el JSON camelCase del backend.
 */
interface MovementApi {
    @POST("movements")
    suspend fun send(
        @Header("Authorization") authorization: String,
        @Body movement: MovementDto,
    ): Response<Unit>
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
    // HU_05. Gson omite los nulos, así que las entradas viajan igual que antes.
    val parcelId: String? = null,
    val cycleId: String? = null,
    val lossReason: String? = null,
    // Ajustes: AUMENTO o DISMINUCION.
    val adjustDirection: String? = null,
    // HU_08: actividad del consumo.
    val activityId: String? = null,
)
