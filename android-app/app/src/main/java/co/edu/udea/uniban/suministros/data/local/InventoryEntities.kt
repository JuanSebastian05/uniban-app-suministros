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
    // Ajustes: AUMENTO o DISMINUCION. Nulo en los demás tipos.
    val adjustDirection: String? = null,
    // HU_08: actividad a la que pertenece el consumo. Sin clave foránea: ver [ActivityEntity].
    val activityId: String? = null,
    /** Causa más reciente del error de sincronización, si el último intento falló. */
    val syncError: String? = null,
)

/**
 * Actividad productiva (HU_08), registrada sobre una parcela y su ciclo activo o el último ciclo cerrado durante el descanso. **Sin clave foránea hacia
 * `parcels`**: `ParcelRepository.refresh()` borra y reinserta las parcelas y una FK rompería ese refresco.
 * Nace en el dispositivo con UUID y `PENDING`; se sincroniza antes que sus consumos.
 */
@Entity(tableName = "activities", indices = [Index("parcelId"), Index("syncStatus")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val parcelId: String,
    val cycleId: String,
    val type: String,
    val date: String,
    val observation: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: String = "PENDING",
    /** Causa más reciente del error de sincronización, si el último intento falló. */
    val syncError: String? = null,
)

/** Actividad con la cantidad de consumos que se le asociaron, para el detalle de la parcela (HU_08). */
data class ParcelActivity(
    val id: String,
    val type: String,
    val date: String,
    val observation: String,
    val cycleId: String,
    val syncStatus: String,
    val consumptionCount: Int,
)

/** Actividad con el nombre de su parcela, para la lista de la pestaña Actividades (HU_08). */
data class ActivityListItem(
    val id: String,
    val parcelId: String,
    val parcelName: String?,
    val type: String,
    val date: String,
    val observation: String,
    val syncStatus: String,
    val consumptionCount: Int,
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
    val adjustDirection: String?,
    /** Tipo de la actividad del consumo (HU_08), o nulo si no pertenece a una. */
    val activityType: String?,
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

