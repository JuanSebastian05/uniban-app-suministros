package co.edu.udea.uniban.suministros.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelDao {
    @Query("""
        SELECT p.id, p.name, p.areaHa, c.id AS cycleId, c.startDate AS cycleStartDate, c.status AS cycleStatus
        FROM parcels p LEFT JOIN production_cycles c ON c.parcelId = p.id AND c.endDate IS NULL
        ORDER BY p.name COLLATE NOCASE
    """)
    fun observeParcels(): Flow<List<ParcelSummary>>

    @Query("SELECT EXISTS(SELECT 1 FROM parcels WHERE id = :id)")
    suspend fun hasParcel(id: String): Boolean

    /** Ciclo activo de la parcela (HU_05: se asocia al consumo), o nulo si no tiene. */
    @Query("SELECT id FROM production_cycles WHERE parcelId = :parcelId AND endDate IS NULL LIMIT 1")
    suspend fun activeCycleId(parcelId: String): String?

    /** Nombres registrados, para avisar de un nombre repetido antes de ir al servidor. */
    @Query("SELECT name FROM parcels")
    suspend fun names(): List<String>

    /** Aforamiento más reciente guardado de la parcela (HU_09); nulo si no hay ninguno. */
    @Query("SELECT * FROM parcel_aforamientos WHERE parcelId = :parcelId")
    fun observeAforamiento(parcelId: String): Flow<AforamientoEntity?>

    /** Última recomendación guardada de la parcela (HU_12); nula si no hay ninguna. */
    @Query("SELECT * FROM parcel_recommendations WHERE parcelId = :parcelId")
    fun observeRecommendation(parcelId: String): Flow<RecommendationEntity?>

    @Upsert
    suspend fun upsertRecommendation(recommendation: RecommendationEntity)

    @Upsert
    suspend fun upsertAforamientos(aforamientos: List<AforamientoEntity>)

    /** Solo al reemplazar la copia local por la del servidor (la tabla no tiene FK a `parcels`). */
    @Query("DELETE FROM parcel_aforamientos")
    suspend fun deleteAllAforamientos()

    @Upsert
    suspend fun upsertParcels(parcels: List<ParcelEntity>)

    @Upsert
    suspend fun upsertCycles(cycles: List<ProductionCycleEntity>)

    /** Los ciclos se borran en cascada. Solo se usa al reemplazar la copia local por la del servidor. */
    @Query("DELETE FROM parcels")
    suspend fun deleteAllParcels()
}
