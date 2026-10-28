package co.edu.udea.uniban.suministros

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class HomeRecordsTest {
    private lateinit var database: InventoryDatabase
    private lateinit var repository: InventoryRepository
    private lateinit var item: InventoryItem

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InventoryDatabase::class.java).build()
        repository = InventoryRepository(database)
        repository.initializeDemo()
        item = repository.inventory.first().first()
        database.parcelDao().upsertParcels(listOf(ParcelEntity("p1", "La Esperanza", "2")))
    }

    @After fun tearDown() { database.close() }

    private suspend fun movement(id: String, date: String, hour: String, activityId: String? = null) {
        database.inventoryDao().insertMovement(MovementEntity(
            id, item.id, 2f, date, "", "${date}T${hour}:00Z", "${date}T${hour}:00Z", activityId = activityId,
        ))
    }

    @Test fun combinaLimitaYOrdenaSinDuplicarLosConsumosDeUnaActividad() = runBlocking {
        movement("old", "2026-10-25", "08:00")
        movement("first", "2026-10-28", "08:00")
        movement("second", "2026-10-28", "09:00")
        database.activityDao().insertActivity(ActivityEntity(
            "activity", "p1", "c1", "FERTILIZACION", "2026-10-28", "", "2026-10-28T10:00:00Z", "2026-10-28T10:00:00Z",
        ))
        movement("consumption", "2026-10-28", "10:01", "activity")
        val recent = repository.homeRecords.first()
        assertEquals(listOf("activity", "second", "first"), recent.map { it.id })
        assertEquals("La Esperanza", recent.first().name)
        assertEquals("ACTIVITY", recent.first().kind)
        assertEquals(item.id, recent[1].inventoryId)
    }

    @Test fun conservaRegistrosAunqueLaParcelaYaNoEsteEnLaCopiaLocal() = runBlocking {
        database.activityDao().insertActivity(ActivityEntity(
            "orphan", "missing", "c1", "COSECHA", "2026-10-28", "", "2026-10-28T10:00:00Z", "2026-10-28T10:00:00Z",
        ))
        val record = repository.homeRecords.first().single()
        assertNull(record.name)
        assertEquals("missing", record.parcelId)
    }

    @Test fun inventarioSinRegistrosEntregaListaVacia() = runBlocking {
        assertTrue(repository.homeRecords.first().isEmpty())
    }
}
