package co.edu.udea.uniban.suministros.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Versión 11 (HU_15–16): planes de origen, cosechas, despachos y consumos enlazados al despacho.
 * Versión 10 (HU_14): conserva el estado de parcela, el ciclo asociado a actividades durante el descanso y el historial de cambios.
 * Versión 9 (HU_12): conserva el plan completo y las métricas de la recomendación para consulta sin conexión.
 * Versión 8 (HU_10): conserva la última sincronización exitosa y la causa del error más reciente por registro.
 * Versión 7 (HU_12): tabla `parcel_recommendations` (sin FK a `parcels`) con la última recomendación de cada parcela.
 * Versión 6 (HU_09): tabla `parcel_aforamientos` (sin FK a `parcels`) con el aforamiento más reciente de cada parcela.
 * Versión 2 (HU_14): agrega las tablas `parcels` y `production_cycles`.
 * Versión 5 (HU_08): tabla `activities` (sin FK a `parcels`) y columna nula `movements.activityId`.
 * Versión 4 (ajustes): agrega a `movements` la columna nula `adjustDirection`.
 * Versión 3 (HU_05): agrega a `movements` las columnas nulas `parcelId`, `cycleId` y `lossReason`.
 *
 * Room genera cada migración a partir de los esquemas exportados en `app/schemas/...` (desde `1.json`
 * hasta la versión anterior); no se borran ni modifican datos. Al compilar, Room escribe el esquema
 * de la versión nueva (`11.json`) en la misma carpeta: debe versionarse junto con el cambio.
 */
@Database(
    entities = [ProducerEntity::class, LocationEntity::class, SupplyEntity::class,
        InventoryEntity::class, MovementEntity::class, ParcelEntity::class, ProductionCycleEntity::class,
        ProductionCycleStatusChangeEntity::class, ActivityEntity::class, AforamientoEntity::class,
        RecommendationEntity::class, SyncMetadataEntity::class, ProductionPlanEntity::class, HarvestEntity::class, DispatchEntity::class],
    version = 11,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
    ],
)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao

    abstract fun parcelDao(): ParcelDao
    abstract fun activityDao(): ActivityDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun productionDao(): ProductionDao

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
