package co.edu.udea.uniban.suministros.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.local.*
import co.edu.udea.uniban.suministros.data.remote.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.time.*
import java.util.UUID
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ProductionRepositoryTest {
    private lateinit var db: InventoryDatabase
    private lateinit var repo: ProductionRepository
    private val api = RecordingProductionApi()
    private val gson = Gson()
    private val date = LocalDate.now()
    private val at get() = OffsetDateTime.now(ZoneId.of("America/Bogota")).minusMinutes(5).withNano(0)
    private val planId = UUID.randomUUID().toString()
    private val parcelId = "p1"
    private lateinit var plan: RecommendationDto

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InventoryDatabase::class.java).build()
        InventoryRepository(db).initializeDemo()
        db.parcelDao().upsertParcels(listOf(ParcelEntity(parcelId, "La Esperanza", "2", "PRODUCCION", "c1")))
        db.parcelDao().upsertCycles(listOf(ProductionCycleEntity("c1", parcelId, date.minusDays(10).toString(), null, "PRODUCCION")))
        plan = RecommendationDto(planId, parcelId, "2026-W44", Instant.now().toString(), "test", 145.6,
            date.toString() + "T16:00:00", true,
            RecommendationPlanDto(null, parcelId, date.toString() + "T08:00:00", "V-3", 1, 146, true),
            listOf(RecommendationSupplyDto("cajas", date.toString(), 100.0, 500.0, 0.0),
                RecommendationSupplyDto("carton", date.toString(), 200.0, 500.0, 0.0),
                RecommendationSupplyDto("bolsas", date.toString(), 300.0, 500.0, 0.0)),
            aforamientoId = "af1", cycleId = "c1")
        db.productionDao().savePlan(requireNotNull(plan.productionPlan(gson)))
        db.inventoryDao().observeInventory().first().filter { it.category == "Empaque" }.forEach { db.inventoryDao().updateQuantity(it.id, 500f) }
        repo = ProductionRepository(db, api, "demo") { "token" }
    }
    @After fun tearDown() { db.close() }
    private fun id() = UUID.randomUUID().toString()

    @Test fun cosechaCreaUnaSolaActividadYReintentarNoDuplica() = runBlocking {
        val id = id()
        repo.harvest(id, planId, 100, date, "Real", false)
        repo.harvest(id, planId, 100, date, "Real", false)
        assertEquals(100, repo.observe(parcelId).first().periods.single().available)
        assertEquals(id, db.activityDao().observeActivities().first().single().id)
        assertTrue(db.activityDao().findSyncable().isEmpty())
        assertEquals(1, InventoryRepository(db).pendingCount.first())
    }
    @Test fun parcialNoReaplicaConfianzaYExcesoRequiereConfirmacion() = runBlocking {
        repo.harvest(id(), planId, 145, date, "", false)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.harvest(id(), planId, 1, date, "", false) } }
        repo.harvest(id(), planId, 1, date, "", true)
        assertEquals(146, repo.observe(parcelId).first().periods.single().harvested)
    }
    @Test fun actualizacionDeRecomendacionNoRenuevaElCupo() = runBlocking {
        repo.harvest(id(), planId, 100, date, "", false)
        val other = plan.copy(id = id(), volumeBunches = 1000.0)
        db.productionDao().savePlan(requireNotNull(other.productionPlan(gson)))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.harvest(id(), other.id, 100, date, "", false) } }
        assertEquals(planId, repo.observe(parcelId).first().periods.single().reference.id)
    }
    @Test fun dosDespachosDescuentanSoloElTotalYSeReintentanSinDuplicar() = runBlocking {
        repo.harvest(id(), planId, 146, date.minusDays(1), "", true)
        val firstId = id()
        val whenSent = at
        repo.dispatch(firstId, planId, 73, whenSent, "V-3", 1, "")
        repo.dispatch(firstId, planId, 73, whenSent, "V-3", 1, "")
        repo.dispatch(id(), planId, 73, at, "V-3", 1, "")
        val overview = repo.observe(parcelId).first()
        assertEquals(0, overview.periods.single().available)
        val stock = overview.inventory.associateBy { it.name }
        assertEquals(400f, stock["cajas"]!!.quantity)
        assertEquals(300f, stock["carton"]!!.quantity)
        assertEquals(200f, stock["bolsas"]!!.quantity)
        assertTrue(db.inventoryDao().findSyncable().isEmpty())
        assertEquals(3, InventoryRepository(db).pendingCount.first())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.dispatch(id(), planId, 1, at, "V-3", 1, "") } }
        Unit
    }
    @Test fun faltanteNoDejaDespachoNiConsumosParciales() = runBlocking {
        repo.harvest(id(), planId, 100, date.minusDays(1), "", false)
        val item = db.inventoryDao().observeInventory().first().first { it.name == "bolsas" }
        db.inventoryDao().updateQuantity(item.id, 0f)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.dispatch(id(), planId, 73, at, "V-3", 1, "") } }
        assertTrue(db.productionDao().pendingDispatches().isEmpty())
        assertTrue(db.inventoryDao().observeMovements().first().isEmpty())
        assertEquals(500f, db.inventoryDao().observeInventory().first().first { it.name == "cajas" }.quantity)
    }
    @Test fun descansoPermiteDespacharPeroNoCosechar() = runBlocking {
        repo.harvest(id(), planId, 100, date.minusDays(1), "", false)
        db.parcelDao().finishActiveCycle(parcelId, date.toString())
        db.parcelDao().updateLifecycleState(parcelId, "DESCANSO", "c1")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.harvest(id(), planId, 1, date, "", false) } }
        repo.dispatch(id(), planId, 73, at, "V-3", 1, "")
        assertEquals("c1", db.productionDao().pendingDispatches().single().cycleId)
    }
    @Test fun sincronizaCosechaAntesDeDespachoYMarcaEmpaquesJuntos() = runBlocking {
        repo.harvest(id(), planId, 100, date.minusDays(1), "", false)
        repo.dispatch(id(), planId, 73, at, "V-3", 1, "")
        val summary = ProductionSyncRepository(db, api).sync("Bearer token")
        assertEquals(2, summary.synced)
        assertEquals(listOf("harvest", "dispatch"), api.calls)
        assertTrue(db.inventoryDao().observeMovements().first().all { it.syncStatus == "SYNCED" })
        assertEquals(0, InventoryRepository(db).pendingCount.first())
    }
    @Test fun siCosechaFallaElDespachoPermanecePendiente() = runBlocking {
        repo.harvest(id(), planId, 100, date.minusDays(1), "", false)
        repo.dispatch(id(), planId, 73, at, "V-3", 1, "")
        api.failHarvest = true
        val summary = ProductionSyncRepository(db, api).sync("Bearer token")
        assertTrue(summary.retryLater)
        assertEquals(listOf("harvest"), api.calls)
        assertEquals("PENDING", db.productionDao().pendingDispatches().single().syncStatus)
    }
}

private class RecordingProductionApi : ProductionApi {
    val calls = mutableListOf<String>()
    var failHarvest = false
    override suspend fun harvest(authorization: String, body: HarvestDto): Response<Unit> {
        calls += "harvest"
        return if (failHarvest) Response.error(503, "".toResponseBody()) else Response.success(Unit)
    }
    override suspend fun dispatch(authorization: String, body: DispatchDto): Response<Unit> { calls += "dispatch"; return Response.success(Unit) }
    override suspend fun history(authorization: String, parcelId: String): Response<ProductionHistoryDto> = Response.success(ProductionHistoryDto(emptyList(), emptyList(), emptyList()))
}
