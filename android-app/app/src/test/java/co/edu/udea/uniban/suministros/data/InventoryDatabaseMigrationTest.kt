package co.edu.udea.uniban.suministros.data

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las AutoMigration 5 → 6 (HU_09, `parcel_aforamientos`) y 6 → 7 (HU_12, `parcel_recommendations`) conservan los datos
 * existentes y dejan las tablas nuevas vacías y sin clave foránea hacia `parcels` (A-04). Usa los esquemas exportados en
 * `app/schemas`, que el build agrega como assets de las pruebas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class InventoryDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), InventoryDatabase::class.java)

    private val name = "migration-test"

    @Test fun deLa5ALa7ConservaLasParcelasYCreaLasTablasNuevas() {
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO parcels(id, name, areaHa) VALUES ('p1', 'La Esperanza', '2.40')")
            execSQL("INSERT INTO production_cycles(id, parcelId, startDate, endDate, status) VALUES ('c1', 'p1', '2026-01-15', NULL, 'PRODUCCION')")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 7, true)

        db.query("SELECT name, areaHa FROM parcels WHERE id = 'p1'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("La Esperanza", it.getString(0))
            assertEquals("2.40", it.getString(1))
        }
        db.query("SELECT COUNT(*) FROM production_cycles").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        listOf("parcel_aforamientos", "parcel_recommendations").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(table, 0, it.getInt(0)) }
        }
        db.close()
    }

    @Test fun lasTablasNuevasNoTienenClaveForaneaHaciaParcels() {
        helper.createDatabase(name, 6).close()
        val db = helper.runMigrationsAndValidate(name, 7, true)

        listOf("parcel_aforamientos", "parcel_recommendations").forEach { table ->
            db.query("PRAGMA foreign_key_list($table)").use { assertFalse("$table no debe tener FK", it.moveToFirst()) }
        }
        // Se puede guardar una recomendación y un aforamiento de una parcela que ni siquiera existe: no hay FK que lo impida.
        db.execSQL(
            "INSERT INTO parcel_recommendations(parcelId, recommendationId, period, generatedAt, sourceVersion, " +
                "volumeBunches, cutDeadline, meetsWindow, reason, suppliesJson) " +
                "VALUES ('sin-parcela', 'r1', '2026-W44', '2026-10-26T19:00:00Z', 'v1', 10.0, '2026-11-02T14:30:00', 1, NULL, '[]')",
        )
        db.query("SELECT COUNT(*) FROM parcel_recommendations").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.close()
    }

    @Test fun deLa6ALa7ConservaElAforamientoGuardado() {
        helper.createDatabase(name, 6).apply {
            execSQL(
                "INSERT INTO parcel_aforamientos(parcelId, aforamientoId, date, areaHa, estimatedPlants, confidence) " +
                    "VALUES ('p1', 'a1', '2026-10-29', '2.41', 1850, '0.870')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 7, true)

        db.query("SELECT estimatedPlants, confidence FROM parcel_aforamientos WHERE parcelId = 'p1'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(1850, it.getInt(0))
            assertEquals("0.870", it.getString(1))
        }
        db.close()
    }
}
