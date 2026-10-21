package co.edu.udea.uniban.suministros.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Copia local de una parcela registrada en el backend (HU_14). El área se guarda como texto
 * decimal ("2.40") para no perder precisión: siempre en hectáreas.
 */
@Entity(tableName = "parcels")
data class ParcelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val areaHa: String,
)

/** Ciclo productivo de una parcela; está activo mientras [endDate] sea nulo. */
@Entity(
    tableName = "production_cycles",
    foreignKeys = [ForeignKey(ParcelEntity::class, ["id"], ["parcelId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("parcelId")],
)
data class ProductionCycleEntity(
    @PrimaryKey val id: String,
    val parcelId: String,
    val startDate: String,
    val endDate: String?,
    val status: String,
)

/** Parcela con su ciclo activo (si lo tiene), para la lista de parcelas. */
data class ParcelSummary(
    val id: String,
    val name: String,
    val areaHa: String,
    val cycleId: String?,
    val cycleStartDate: String?,
    val cycleStatus: String?,
)
