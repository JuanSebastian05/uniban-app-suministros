package co.edu.udea.uniban.suministros.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.remote.AforamientoDto
import co.edu.udea.uniban.suministros.data.remote.CycleResponseDto
import co.edu.udea.uniban.suministros.data.remote.CycleStageUpdateDto
import co.edu.udea.uniban.suministros.data.remote.CycleFinishDto
import co.edu.udea.uniban.suministros.data.remote.CycleStartDto
import co.edu.udea.uniban.suministros.data.remote.ParcelApi
import co.edu.udea.uniban.suministros.data.remote.ParcelDto
import co.edu.udea.uniban.suministros.data.remote.ParcelResponseDto
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/** Registro de parcelas (HU_14): se guarda localmente solo si el servidor la aceptó. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ParcelRepositoryTest {
    private lateinit var database: InventoryDatabase
    private val api = FakeParcelApi()
    private var token: String? = "token"

    private fun repository(owner: String = "uid-ana") = ParcelRepository(database, api, owner) { token }

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InventoryDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    private fun draft(name: String = "La Esperanza", area: String = "2,4", cycle: CycleDraft? = null) =
        ParcelDraft(name, area, cycle)

    @Test fun guardaLocalmenteLaParcelaAceptada() = runTest {
        val cycle = CycleDraft(UUID.randomUUID().toString(), LocalDate.of(2024, 1, 15), CycleStatus.PRODUCCION)
        val result = repository().create("p1", draft(name = "  La   Esperanza ", cycle = cycle))

        assertEquals(ParcelSaveResult.Saved, result)
        val sent = api.created.single()
        assertEquals("La Esperanza", sent.name)
        assertEquals(BigDecimal("2.40"), sent.areaHa)
        assertEquals("PRODUCCION", sent.cycle?.status)
        assertEquals("Bearer token", api.lastAuthorization)
        val local = repository().parcels.first().single()
        assertEquals("La Esperanza", local.name)
        assertEquals("2.40", local.areaHa)
        assertEquals("2024-01-15", local.cycleStartDate)
        assertEquals("PRODUCCION", local.cycleStatus)
    }

    @Test fun sinRedNoGuardaNada() = runTest {
        api.failWith = IOException("sin red")
        assertEquals(ParcelSaveResult.Offline, repository().create("p1", draft()))
        assertTrue(repository().parcels.first().isEmpty())
    }

    @Test fun errorDelServidorSeTrataComoSinConexion() = runTest {
        api.createCode = 503
        assertEquals(ParcelSaveResult.Offline, repository().create("p1", draft()))
        assertTrue(repository().parcels.first().isEmpty())
    }

    @Test fun nombreRepetidoEnElServidor() = runTest {
        api.createCode = 409
        api.errorJson = """{"detail":{"code":"DUPLICATE_NAME","message":"Ya tienes una parcela con ese nombre."}}"""
        assertEquals(ParcelSaveResult.DuplicateName, repository().create("p1", draft()))
    }

    @Test fun nombreRepetidoLocalNoLlamaAlServidor() = runTest {
        repository().create("p1", draft(name = "La Esperanza"))
        val result = repository().create("p2", draft(name = "la  ESPERANZA"))
        assertEquals(ParcelSaveResult.DuplicateName, result)
        assertEquals(1, api.created.size)
    }

    @Test fun sesionInvalida() = runTest {
        api.createCode = 401
        assertTrue(repository().create("p1", draft()) is ParcelSaveResult.Rejected)
    }

    @Test fun demoGuardaSoloEnElDispositivo() = runTest {
        token = null
        val result = repository(owner = "demo").create("p1", draft())
        assertEquals(ParcelSaveResult.Saved, result)
        assertTrue(api.created.isEmpty())
        assertEquals(1, repository(owner = "demo").parcels.first().size)
    }

    @Test fun refrescarReemplazaLaCopiaLocal() = runTest {
        repository().create("p1", draft(name = "Vieja"))
        api.listed = listOf(
            ParcelResponseDto("p2", "El Mirador", BigDecimal("1.50"), null),
            ParcelResponseDto("p3", "La Palma", BigDecimal("3.10"), CycleResponseDto("c3", "2024-03-01", null, "DESCANSO")),
        )
        assertTrue(repository().refresh())
        val local = repository().parcels.first()
        assertEquals(listOf("El Mirador", "La Palma"), local.map { it.name })
        assertNull(local[0].cycleStatus)
        assertEquals("DESCANSO", local[1].cycleStatus)
    }

    @Test fun refrescarSinRedConservaLaCopiaLocal() = runTest {
        repository().create("p1", draft())
        api.failWith = IOException("sin red")
        assertEquals(false, repository().refresh())
        assertEquals(1, repository().parcels.first().size)
    }
}

private class FakeParcelApi : ParcelApi {
    val created = mutableListOf<ParcelDto>()
    var lastAuthorization: String? = null
    var createCode = 201
    var errorJson = "{}"
    var failWith: IOException? = null
    var listed: List<ParcelResponseDto> = emptyList()

    override suspend fun changeCycleStage(authorization: String, parcelId: String, request: CycleStageUpdateDto): Response<ParcelResponseDto> =
        error("La gestión de ciclos no se usa en estas pruebas.")

    override suspend fun finishCycle(authorization: String, parcelId: String, request: CycleFinishDto): Response<ParcelResponseDto> =
        error("La gestión de ciclos no se usa en estas pruebas.")

    override suspend fun startCycle(authorization: String, parcelId: String, request: CycleStartDto): Response<ParcelResponseDto> =
        error("La gestión de ciclos no se usa en estas pruebas.")

    override suspend fun create(authorization: String, parcel: ParcelDto): Response<ParcelResponseDto> {
        failWith?.let { throw it }
        lastAuthorization = authorization
        if (createCode !in 200..299) {
            return Response.error(createCode, errorJson.toResponseBody("application/json".toMediaType()))
        }
        created += parcel
        return Response.success(
            createCode,
            ParcelResponseDto(
                parcel.id, parcel.name, parcel.areaHa,
                parcel.cycle?.let { CycleResponseDto(it.id, it.startDate, null, it.status) },
            ),
        )
    }

    override suspend fun list(authorization: String): Response<List<ParcelResponseDto>> {
        failWith?.let { throw it }
        return Response.success(listed)
    }

    override suspend fun aforamiento(authorization: String, parcelId: String): Response<AforamientoDto> =
        error("El aforamiento se prueba en ParcelAforamientoTest.")
}
