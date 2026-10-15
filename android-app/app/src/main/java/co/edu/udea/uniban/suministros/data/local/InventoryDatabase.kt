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
        /**
         * Una base por cuenta: cambiar de usuario en el mismo teléfono no muestra ni sincroniza
         * el inventario del anterior.
         */
        fun create(context: Context, ownerUid: String): InventoryDatabase =
            Room.databaseBuilder(context.applicationContext, InventoryDatabase::class.java, fileName(ownerUid))
                .build()

        /** Los uid de Firebase son alfanuméricos; cualquier otro carácter se rechaza. */
        fun fileName(ownerUid: String): String {
            require(ownerUid.isNotEmpty() && ownerUid.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                "uid no válido para nombrar la base local."
            }
            return "inventory-$ownerUid.db"
        }
    }
}

