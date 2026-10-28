package co.edu.udea.uniban.suministros.data.local

import androidx.room.*

/** Archivo inmutable de recomendaciones usadas por operaciones, sin FK a parcelas refrescables. */
@Entity(tableName = "production_plans", indices = [Index(value = ["parcelId", "period"])])
data class ProductionPlanEntity(
    @PrimaryKey val id: String, val parcelId: String, val period: String, val cycleId: String,
    val aforamientoId: String, val generatedAt: String, val payloadJson: String,
)

@Entity(tableName = "harvests", indices = [Index(value = ["parcelId", "period"])])
data class HarvestEntity(
    @PrimaryKey val id: String, val parcelId: String, val cycleId: String, val recommendationId: String,
    val aforamientoId: String, val period: String, val quantity: Int, val date: String,
    val observation: String, val exceptionConfirmed: Boolean, val createdAt: String,
    val syncStatus: String = "PENDING", val syncError: String? = null,
)

@Entity(tableName = "dispatches", indices = [Index(value = ["parcelId", "period"])])
data class DispatchEntity(
    @PrimaryKey val id: String, val parcelId: String, val cycleId: String, val recommendationId: String,
    val period: String, val quantity: Int, val dispatchedAt: String, val vehicleId: String, val order: Int,
    val observation: String, val createdAt: String, val consumptionsJson: String,
    val syncStatus: String = "PENDING", val syncError: String? = null,
)

@Dao
interface ProductionDao {
    @Query("SELECT * FROM production_plans WHERE parcelId = :parcelId ORDER BY generatedAt DESC, id")
    fun plans(parcelId: String): kotlinx.coroutines.flow.Flow<List<ProductionPlanEntity>>
    @Query("SELECT * FROM harvests WHERE parcelId = :parcelId ORDER BY createdAt, id")
    fun harvests(parcelId: String): kotlinx.coroutines.flow.Flow<List<HarvestEntity>>
    @Query("SELECT * FROM dispatches WHERE parcelId = :parcelId ORDER BY createdAt, id")
    fun dispatches(parcelId: String): kotlinx.coroutines.flow.Flow<List<DispatchEntity>>
    @Query("SELECT * FROM production_plans WHERE id = :id") suspend fun plan(id: String): ProductionPlanEntity?
    @Query("SELECT * FROM harvests WHERE parcelId = :parcelId AND period = :period ORDER BY createdAt, id")
    suspend fun periodHarvests(parcelId: String, period: String): List<HarvestEntity>
    @Query("SELECT * FROM dispatches WHERE parcelId = :parcelId AND period = :period ORDER BY createdAt, id")
    suspend fun periodDispatches(parcelId: String, period: String): List<DispatchEntity>
    @Query("SELECT * FROM harvests WHERE id = :id") suspend fun harvest(id: String): HarvestEntity?
    @Query("SELECT * FROM dispatches WHERE id = :id") suspend fun dispatch(id: String): DispatchEntity?
    @Upsert suspend fun savePlan(plan: ProductionPlanEntity)
    @Insert suspend fun insertHarvest(harvest: HarvestEntity)
    @Insert suspend fun insertDispatch(dispatch: DispatchEntity)
    @Query("SELECT * FROM harvests WHERE syncStatus IN ('PENDING','ERROR') ORDER BY createdAt, id") suspend fun pendingHarvests(): List<HarvestEntity>
    @Query("SELECT * FROM dispatches WHERE syncStatus IN ('PENDING','ERROR') ORDER BY createdAt, id") suspend fun pendingDispatches(): List<DispatchEntity>
    @Query("UPDATE harvests SET syncStatus = :status, syncError = :error WHERE id = :id") suspend fun harvestStatus(id: String, status: String, error: String?)
    @Query("UPDATE dispatches SET syncStatus = :status, syncError = :error WHERE id = :id") suspend fun dispatchStatus(id: String, status: String, error: String?)
    @Query("UPDATE activities SET syncStatus = :status, syncError = :error WHERE id = :id") suspend fun activityStatus(id: String, status: String, error: String?)
    @Query("UPDATE movements SET syncStatus = :status, syncError = :error WHERE dispatchId = :id") suspend fun consumptionStatus(id: String, status: String, error: String?)
    @Query("UPDATE harvests SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'") suspend fun resetHarvests()
    @Query("UPDATE dispatches SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'") suspend fun resetDispatches()
    @Query("SELECT 'dispatch:' || id AS id, 'Despacho' AS title, substr(dispatchedAt,1,10) AS date, quantity || ' racimos' AS context, COALESCE(syncError,'Error de envío') AS reason FROM dispatches WHERE syncStatus = 'ERROR'")
    fun errors(): kotlinx.coroutines.flow.Flow<List<co.edu.udea.uniban.suministros.data.SyncIssue>>
    @Query("SELECT * FROM production_cycles WHERE parcelId = :parcelId AND startDate <= :date ORDER BY startDate DESC LIMIT 1")
    suspend fun cycleAt(parcelId: String, date: String): ProductionCycleEntity?
    @Query("SELECT toStatus FROM production_cycle_status_changes WHERE cycleId = :cycleId AND effectiveDate <= :date ORDER BY effectiveDate DESC, changedAt DESC LIMIT 1")
    suspend fun stageAt(cycleId: String, date: String): String?
}
