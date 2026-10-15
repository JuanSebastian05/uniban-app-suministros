package co.edu.udea.uniban.suministros.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("""
        SELECT i.id, s.name, s.category, s.unit, l.name AS location, i.quantity
        FROM inventory i JOIN supplies s ON i.supplyId = s.id
        JOIN locations l ON i.locationId = l.id ORDER BY s.name
    """)
    fun observeInventory(): Flow<List<InventoryItem>>

    @Query("""
        SELECT i.id, s.name, s.category, s.unit, l.name AS location, i.quantity
        FROM inventory i JOIN supplies s ON i.supplyId = s.id
        JOIN locations l ON i.locationId = l.id WHERE i.id = :id
    """)
    suspend fun findInventory(id: String): InventoryItem?

    @Query("SELECT * FROM movements ORDER BY date DESC, createdAt DESC")
    fun observeMovements(): Flow<List<MovementEntity>>

    @Query("SELECT * FROM movements WHERE id = :id")
    suspend fun findMovement(id: String): MovementEntity?

    // --- Consulta del inventario (HU_03) ---

    /** Movimientos más recientes de un solo insumo: el detalle no carga el historial completo. */
    @Query("""
        SELECT * FROM movements WHERE inventoryId = :inventoryId
        ORDER BY date DESC, createdAt DESC LIMIT :limit
    """)
    fun observeRecentMovements(inventoryId: String, limit: Int): Flow<List<MovementEntity>>

    /** Consumos registrados en una parcela, del más reciente al más antiguo (HU_05). */
    @Query("""
        SELECT m.id, s.name AS supplyName, s.unit, m.quantity, m.date, m.cycleId, m.syncStatus
        FROM movements m JOIN inventory i ON m.inventoryId = i.id JOIN supplies s ON i.supplyId = s.id
        WHERE m.parcelId = :parcelId AND m.type = 'CONSUMO'
        ORDER BY m.date DESC, m.createdAt DESC
    """)
    fun observeParcelConsumptions(parcelId: String): Flow<List<ParcelConsumption>>

    // --- Historial y trazabilidad (HU_07) ---

    /**
     * Todos los movimientos registrados en este dispositivo, del más reciente al más antiguo, con el
     * insumo y la parcela. `cycleActive` indica si el ciclo del consumo sigue siendo el activo.
     */
    @Query("""
        SELECT m.id, m.inventoryId, s.name AS supplyName, s.unit, m.type, m.quantity, m.date, m.createdAt,
            m.observation, m.syncStatus, m.parcelId, p.name AS parcelName, m.cycleId,
            (c.id IS NOT NULL AND c.endDate IS NULL) AS cycleActive, m.lossReason
        FROM movements m JOIN inventory i ON m.inventoryId = i.id JOIN supplies s ON i.supplyId = s.id
        LEFT JOIN parcels p ON m.parcelId = p.id
        LEFT JOIN production_cycles c ON m.cycleId = c.id
        ORDER BY m.date DESC, m.createdAt DESC
    """)
    fun observeHistory(): Flow<List<HistoryMovement>>

    @Query("""
        SELECT i.id, s.name, s.unit, i.initialQuantity, i.quantity
        FROM inventory i JOIN supplies s ON i.supplyId = s.id ORDER BY s.name
    """)
    fun observeBalances(): Flow<List<InventoryBalance>>

    @Query("SELECT COUNT(*) FROM movements WHERE syncStatus <> 'SYNCED'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM producers WHERE id = :id)")
    suspend fun hasProducer(id: String): Boolean

    @Insert
    suspend fun insertProducer(producer: ProducerEntity)

    @Insert
    suspend fun insertLocation(location: LocationEntity)

    @Insert
    suspend fun insertSupplies(supplies: List<SupplyEntity>)

    @Insert
    suspend fun insertInventory(inventory: List<InventoryEntity>)

    @Insert
    suspend fun insertMovement(movement: MovementEntity)

    @Query("UPDATE inventory SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: String, quantity: Float): Int

    // --- Sincronización (HU_06) ---

    @Query("SELECT * FROM movements WHERE syncStatus IN ('PENDING', 'ERROR') ORDER BY createdAt, id")
    suspend fun findSyncable(): List<MovementEntity>

    /** Toma el movimiento para enviarlo; devuelve 0 si otro proceso ya lo tomó o ya está SYNCED. */
    @Query("UPDATE movements SET syncStatus = 'SYNCING' WHERE id = :id AND syncStatus IN ('PENDING', 'ERROR')")
    suspend fun markSyncing(id: String): Int

    @Query("UPDATE movements SET syncStatus = :status WHERE id = :id AND syncStatus = 'SYNCING'")
    suspend fun finishSyncing(id: String, status: String): Int

    /** Un SYNCING que sobrevive a un cierre de la app se retoma como PENDING. */
    @Query("UPDATE movements SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetInterruptedSyncing(): Int

    @Query("""
        SELECT i.id AS inventoryId, i.initialQuantity AS initialQuantity,
            s.id AS supplyId, s.name AS supplyName, s.category AS supplyCategory, s.unit AS supplyUnit,
            l.id AS locationId, l.name AS locationName, p.id AS producerId, p.name AS producerName
        FROM inventory i JOIN supplies s ON i.supplyId = s.id
        JOIN locations l ON i.locationId = l.id JOIN producers p ON l.producerId = p.id
        WHERE i.id = :inventoryId
    """)
    suspend fun findSyncContext(inventoryId: String): InventorySyncContext?
}

