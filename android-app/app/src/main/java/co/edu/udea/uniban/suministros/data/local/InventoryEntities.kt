package co.edu.udea.uniban.suministros.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "producers")
data class ProducerEntity(@PrimaryKey val id: String, val name: String)

@Entity(
    tableName = "locations",
    foreignKeys = [ForeignKey(ProducerEntity::class, ["id"], ["producerId"])],
    indices = [Index("producerId")],
)
data class LocationEntity(@PrimaryKey val id: String, val producerId: String, val name: String)

@Entity(tableName = "supplies")
data class SupplyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val unit: String,
)

@Entity(
    tableName = "inventory",
    foreignKeys = [
        ForeignKey(LocationEntity::class, ["id"], ["locationId"]),
        ForeignKey(SupplyEntity::class, ["id"], ["supplyId"]),
    ],
    indices = [Index(value = ["locationId", "supplyId"], unique = true), Index("supplyId")],
)
data class InventoryEntity(
    @PrimaryKey val id: String,
    val locationId: String,
    val supplyId: String,
    val initialQuantity: Float,
    val quantity: Float,
)

@Entity(
    tableName = "movements",
    foreignKeys = [ForeignKey(InventoryEntity::class, ["id"], ["inventoryId"])],
    indices = [Index("inventoryId"), Index("syncStatus")],
)
data class MovementEntity(
    @PrimaryKey val id: String,
    val inventoryId: String,
    val quantity: Float,
    val date: String,
    val observation: String,
    val createdAt: String,
    val updatedAt: String,
    val type: String = "ENTRADA",
    val syncStatus: String = "PENDING",
    // HU_05: parcela y ciclo activo del consumo; motivo de la pérdida. Nulos en las entradas.
    val parcelId: String? = null,
    val cycleId: String? = null,
    val lossReason: String? = null,
)

/** Contexto de un inventario que se envía junto con su primer movimiento (HU_06). */
data class InventorySyncContext(
    val inventoryId: String,
    val initialQuantity: Float,
    val supplyId: String,
    val supplyName: String,
    val supplyCategory: String,
    val supplyUnit: String,
    val locationId: String,
    val locationName: String,
    val producerId: String,
    val producerName: String,
)

/** Consumo registrado en una parcela, con el insumo que se usó (HU_05, detalle de parcela). */
data class ParcelConsumption(
    val id: String,
    val supplyName: String,
    val unit: String,
    val quantity: Float,
    val date: String,
    val cycleId: String?,
    val syncStatus: String,
)

/**
 * Movimiento del historial (HU_07) con el insumo, la parcela y si el ciclo del consumo sigue activo.
 * [parcelName] es nulo si el movimiento no tiene parcela o si la parcela ya no está en este dispositivo.
 */
data class HistoryMovement(
    val id: String,
    val inventoryId: String,
    val supplyName: String,
    val unit: String,
    val type: String,
    val quantity: Float,
    val date: String,
    val createdAt: String,
    val observation: String,
    val syncStatus: String,
    val parcelId: String?,
    val parcelName: String?,
    val cycleId: String?,
    val cycleActive: Boolean,
    val lossReason: String?,
)

/** Saldo inicial y existencia de un insumo, para el resumen del historial (RT_10, HU_07). */
data class InventoryBalance(
    val id: String,
    val name: String,
    val unit: String,
    val initialQuantity: Float,
    val quantity: Float,
)

data class InventoryItem(
    val id: String,
    val name: String,
    val category: String,
    val unit: String,
    val location: String,
    val quantity: Float,
)

