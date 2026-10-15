package co.edu.udea.uniban.suministros.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Versión 2 (HU_14): agrega las tablas `parcels` y `production_cycles`. La migración 1 → 2 la
 * genera Room a partir de `app/schemas/.../1.json` (solo crea tablas nuevas; no toca datos).
 * Al compilar, Room escribe `2.json` en la misma carpeta: debe versionarse junto con este cambio.
 */
@Database(
    entities = [ProducerEntity::class, LocationEntity::class, SupplyEntity::class,
        InventoryEntity::class, MovementEntity::class, ParcelEntity::class, ProductionCycleEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao

    abstract fun parcelDao(): ParcelDao

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
