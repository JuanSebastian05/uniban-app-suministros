package co.edu.udea.uniban.suministros.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProducerEntity::class, LocationEntity::class, SupplyEntity::class,
        InventoryEntity::class, MovementEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao

    companion object {
        fun create(context: Context): InventoryDatabase =
            Room.databaseBuilder(context.applicationContext, InventoryDatabase::class.java, "inventory.db")
                .build()
    }
}

