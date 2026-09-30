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
}

