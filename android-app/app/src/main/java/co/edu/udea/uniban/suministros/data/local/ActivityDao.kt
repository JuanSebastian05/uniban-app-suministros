package co.edu.udea.uniban.suministros.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Actividades productivas (HU_08): consulta para las pantallas y estados de sincronización. */
@Dao
interface ActivityDao {
    @Insert
    suspend fun insertActivity(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun findActivity(id: String): ActivityEntity?

    /** Actividades de una parcela, de la más reciente a la más antigua, con el número de consumos. */
    @Query("""
        SELECT a.id, a.type, a.date, a.observation, a.cycleId, a.syncStatus,
            (SELECT COUNT(*) FROM movements m WHERE m.activityId = a.id) AS consumptionCount
        FROM activities a WHERE a.parcelId = :parcelId ORDER BY a.date DESC, a.createdAt DESC
    """)
    fun observeParcelActivities(parcelId: String): Flow<List<ParcelActivity>>

    /** Todas las actividades de la cuenta, con el nombre de la parcela (nulo si ya no está en el dispositivo). */
    @Query("""
        SELECT a.id, a.parcelId, p.name AS parcelName, a.type, a.date, a.observation, a.syncStatus,
            (SELECT COUNT(*) FROM movements m WHERE m.activityId = a.id) AS consumptionCount
        FROM activities a LEFT JOIN parcels p ON a.parcelId = p.id
        ORDER BY a.date DESC, a.createdAt DESC
    """)
    fun observeActivities(): Flow<List<ActivityListItem>>

    /** Actividades cuyo último intento falló, para el centro de sincronización (HU_10). */
    @Query("""
        SELECT a.id, a.type, a.date, p.name AS parcelName,
            (SELECT COUNT(*) FROM movements m WHERE m.activityId = a.id) AS consumptionCount,
            a.syncError
        FROM activities a
        LEFT JOIN parcels p ON a.parcelId = p.id
        WHERE a.syncStatus = 'ERROR'
        ORDER BY a.date DESC, a.createdAt DESC
    """)
    fun observeActivitySyncErrors(): Flow<List<ActivitySyncError>>

    // --- Sincronización: las actividades viajan antes que sus consumos ---

    @Query("SELECT * FROM activities WHERE syncStatus IN ('PENDING', 'ERROR') ORDER BY createdAt, id")
    suspend fun findSyncable(): List<ActivityEntity>

    @Query("UPDATE activities SET syncStatus = 'SYNCING', syncError = NULL WHERE id = :id AND syncStatus IN ('PENDING', 'ERROR')")
    suspend fun markSyncing(id: String): Int

    @Query("UPDATE activities SET syncStatus = :status, syncError = NULL WHERE id = :id AND syncStatus = 'SYNCING'")
    suspend fun finishSyncing(id: String, status: String): Int

    @Query("UPDATE activities SET syncStatus = :status, syncError = :error WHERE id = :id AND syncStatus = 'SYNCING'")
    suspend fun finishSyncingWithError(id: String, status: String, error: String): Int

    @Query("UPDATE activities SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetInterruptedSyncing(): Int
}
