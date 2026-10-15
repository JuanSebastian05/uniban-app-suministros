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

    @Upsert
    suspend fun upsertParcels(parcels: List<ParcelEntity>)

    @Upsert
    suspend fun upsertCycles(cycles: List<ProductionCycleEntity>)

    /** Los ciclos se borran en cascada. Solo se usa al reemplazar la copia local por la del servidor. */
    @Query("DELETE FROM parcels")
    suspend fun deleteAllParcels()
}
