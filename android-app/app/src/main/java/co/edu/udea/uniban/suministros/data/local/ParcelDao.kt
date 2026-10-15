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
