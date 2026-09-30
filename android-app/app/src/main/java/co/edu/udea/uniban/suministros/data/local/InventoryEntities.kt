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
)

data class InventoryItem(
    val id: String,
    val name: String,
    val category: String,
    val unit: String,
    val location: String,
    val quantity: Float,
)

