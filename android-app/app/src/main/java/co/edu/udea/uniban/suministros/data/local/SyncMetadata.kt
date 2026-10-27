package co.edu.udea.uniban.suministros.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Última sincronización exitosa de esta cuenta, guardada localmente para HU_10. */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastSuccessfulAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT lastSuccessfulAtEpochMillis FROM sync_metadata WHERE id = 1")
    fun observeLastSuccessfulAt(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLastSuccessfulAt(metadata: SyncMetadataEntity)
}

/** Datos de un movimiento rechazado por el servidor. */
data class MovementSyncError(
    val id: String,
    val type: String,
    val supplyName: String,
    val unit: String,
    val quantity: Float,
    val date: String,
    val parcelName: String?,
    val syncError: String?,
)

/** Datos de una actividad rechazada por el servidor. */
data class ActivitySyncError(
    val id: String,
    val type: String,
    val date: String,
    val parcelName: String?,
    val consumptionCount: Int,
    val syncError: String?,
)
