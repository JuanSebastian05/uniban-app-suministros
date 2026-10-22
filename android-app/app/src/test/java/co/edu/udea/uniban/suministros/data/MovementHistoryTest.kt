package co.edu.udea.uniban.suministros.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.LossReason
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.data.local.ParcelEntity
import co.edu.udea.uniban.suministros.data.local.ProductionCycleEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Historial y trazabilidad (HU_07): consulta en Room, filtros combinables y resumen RT_10. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class MovementHistoryTest {
    private lateinit var database: InventoryDatabase
    private lateinit var repository: InventoryRepository
    private lateinit var ureaId: String
    private lateinit var otherId: String

    @Before fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InventoryDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = InventoryRepository(database)
        repository.initializeDemo()
        val items = repository.inventory.first()
        ureaId = items.first { it.name == "Urea 46%" }.id // 80 kg
        otherId = items.first { it.id != ureaId }.id
        database.parcelDao().upsertParcels(listOf(ParcelEntity("p1", "La Esperanza", "2.40"), ParcelEntity("p2", "El Mirador", "1.50")))
        database.parcelDao().upsertCycles(listOf(
            ProductionCycleEntity("old", "p1", "2025-01-01", "2025-12-31", "CERRADO"),
            ProductionCycleEntity("c1", "p1", "2026-01-15", null, "PRODUCCION"),
        ))
        repository.registerMovement(id(), MovementType.ENTRADA, ureaId, "20", LocalDate.of(2026, 10, 1), "Compra")
        repository.registerMovement(id(), MovementType.CONSUMO, ureaId, "12,5", LocalDate.of(2026, 10, 5), "", parcelId = "p1")
        repository.registerMovement(id(), MovementType.CONSUMO, ureaId, "3", LocalDate.of(2026, 10, 8), "", parcelId = "p2")
        repository.registerMovement(id(), MovementType.PERDIDA, ureaId, "2", LocalDate.of(2026, 10, 10), "", lossReason = LossReason.CLIMA)
        repository.registerMovement(id(), MovementType.ENTRADA, otherId, "1", LocalDate.of(2026, 10, 3), "")
    }

    @After fun tearDown() = database.close()

    private fun id() = UUID.randomUUID().toString()

    @Test fun historialIncluyeTodosLosMovimientosDelMasRecienteAlMasAntiguo() = runTest {
        val history = repository.history.first()
        assertEquals(5, history.size)
        assertEquals(history.map { it.date }.sortedDescending(), history.map { it.date })
    }

    @Test fun consumoMuestraNombreDeParcelaYCicloActivo() = runTest {
        val consumo = repository.history.first().first { it.type == MovementType.CONSUMO && it.parcelId == "p1" }
        assertEquals("La Esperanza", consumo.parcelName)
        assertEquals("c1", consumo.cycleId)
        assertTrue(consumo.cycleActive)
        val sinCiclo = repository.history.first().first { it.parcelId == "p2" }
        assertNull(sinCiclo.cycleId)
        assertFalse(sinCiclo.cycleActive)
    }

    @Test fun cicloCerradoSeMarcaComoAnterior() = runTest {
        val movementId = id()
        repository.registerMovement(movementId, MovementType.CONSUMO, ureaId, "1", LocalDate.of(2026, 10, 11), "", parcelId = "p1")
        // El ciclo c1 se cierra después del consumo.
        database.parcelDao().upsertCycles(listOf(ProductionCycleEntity("c1", "p1", "2026-01-15", "2026-10-12", "CERRADO")))
        val movement = repository.history.first().first { it.id == movementId }
        assertEquals("c1", movement.cycleId)
        assertFalse(movement.cycleActive)
    }

    @Test fun filtrosSeCombinan() = runTest {
        val all = repository.history.first()
        assertEquals(4, MovementHistory.filter(all, HistoryFilter(inventoryId = ureaId)).size)
        assertEquals(2, MovementHistory.filter(all, HistoryFilter(types = setOf(MovementType.CONSUMO))).size)
        assertEquals(1, MovementHistory.filter(all, HistoryFilter(types = setOf(MovementType.CONSUMO), parcelId = "p2")).size)
        val rango = MovementHistory.filter(all, HistoryFilter(from = LocalDate.of(2026, 10, 3), to = LocalDate.of(2026, 10, 8)))
        assertEquals(setOf("2026-10-03", "2026-10-05", "2026-10-08"), rango.map { it.date }.toSet())
        val tipos = MovementHistory.filter(all, HistoryFilter(inventoryId = ureaId, types = setOf(MovementType.ENTRADA, MovementType.PERDIDA)))
        assertEquals(2, tipos.size)
    }

    @Test fun resumenCuadraConLaExistencia() = runTest {
        val balance = repository.balances.first().first { it.id == ureaId }
        val summary = MovementHistory.summary(balance, repository.history.first())
        assertEquals(80f, summary.initial)
        assertEquals(20f, summary.entries)
        assertEquals(15.5f, summary.consumptions)
        assertEquals(2f, summary.losses)
        assertEquals(82.5f, summary.expected)
        assertEquals(82.5f, summary.actual)
        assertTrue(summary.matches)
    }

    @Test fun resumenUsaTodoElHistorialAunqueLaListaEsteFiltrada() = runTest {
        val all = repository.history.first()
        val filtered = MovementHistory.filter(all, HistoryFilter(inventoryId = ureaId, types = setOf(MovementType.PERDIDA)))
        assertEquals(1, filtered.size)
        val balance = repository.balances.first().first { it.id == ureaId }
        assertTrue(MovementHistory.summary(balance, all).matches)
    }
}
