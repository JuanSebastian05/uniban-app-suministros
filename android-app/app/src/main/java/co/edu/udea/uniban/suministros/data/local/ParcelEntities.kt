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

/**
 * Copia local del aforamiento más reciente de una parcela (HU_09), enviado por el Equipo 1 al backend.
 * Una fila por parcela. **Sin clave foránea hacia `parcels`** (A-04): `ParcelRepository.refresh()` borra y reinserta
 * las parcelas, y una FK haría fallar ese refresco. [areaHa] y [confidence] se guardan como texto decimal
 * ("2.41", "0.870") para no perder precisión; [confidence] es nula si el Equipo 1 no la envió.
 */
@Entity(tableName = "parcel_aforamientos")
data class AforamientoEntity(
    @PrimaryKey val parcelId: String,
    val aforamientoId: String,
    val date: String,
    val areaHa: String,
    val estimatedPlants: Int,
    val confidence: String?,
)

/**
 * Copia local de la última recomendación del Equipo 2 para una parcela (HU_12), servida por nuestro backend. Una fila por
 * parcela y **sin clave foránea hacia `parcels`** (A-04). [suppliesJson] guarda la lista de insumos; [planJson] y
 * [metricsJson] conservan el resto de la respuesta para consulta sin conexión. Las fechas se guardan como texto ISO.
 */
@Entity(tableName = "parcel_recommendations")
data class RecommendationEntity(
    @PrimaryKey val parcelId: String,
    val recommendationId: String,
    val period: String,
    val generatedAt: String,
    val sourceVersion: String,
    val volumeBunches: Double,
    val cutDeadline: String,
    val meetsWindow: Boolean,
    val reason: String?,
    val suppliesJson: String,
    /** Plan completo del Equipo 2 para conservar campos opcionales y funcionar sin conexión. */
    val planJson: String? = null,
    /** Indicadores completos del Equipo 2; nulos en recomendaciones guardadas antes de HU_12 ampliada. */
    val metricsJson: String? = null,
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
