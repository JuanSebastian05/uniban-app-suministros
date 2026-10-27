package co.edu.udea.uniban.suministros.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.StarterInventory
import co.edu.udea.uniban.suministros.data.remote.AforamientoDto
import co.edu.udea.uniban.suministros.data.remote.ParcelApi
import co.edu.udea.uniban.suministros.data.remote.ParcelDto
import co.edu.udea.uniban.suministros.data.remote.ParcelResponseDto
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/** HU_09: copia local del aforamiento más reciente (Room), sin FK a `parcels`, que funciona sin conexión. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ParcelAforamientoTest {
    private lateinit var database: InventoryDatabase
    private val api = FakeAforamientoApi()
    private var token: String? = "token"

    private fun repository(owner: String = "uid-ana") = ParcelRepository(database, api, owner) { token }

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InventoryDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    private fun aforamiento(parcelId: String = "p1", date: String = "2026-10-29", plants: Int = 1850,
                            area: String = "2.41", confidence: String? = "0.870") =
        AforamientoDto("a-$date", parcelId, date, BigDecimal(area), plants, confidence?.let(::BigDecimal))

    private fun parcel(id: String = "p1", aforamiento: AforamientoDto? = null) =
        ParcelResponseDto(id, "Parcela $id", BigDecimal("2.40"), null, aforamiento)

    // --- refreshAforamiento: GET /parcels/{id}/aforamiento ---

    @Test fun consultarGuardaLaCopiaLocal() = runTest {
        api.aforamientoResult = Response.success(aforamiento())

        val result = repository().refreshAforamiento("p1")

        assertEquals(AforamientoRefresh.UPDATED, result)
        assertEquals("Bearer token", api.lastAuthorization)
        assertEquals("p1", api.lastParcelId)
        val saved = repository().observeAforamiento("p1").first()!!
        assertEquals("2026-10-29", saved.date)
        assertEquals("2.41", saved.areaHa)
        assertEquals(1850, saved.estimatedPlants)
        assertEquals("0.870", saved.confidence)
    }

    @Test fun laConfianzaEsOpcional() = runTest {
        api.aforamientoResult = Response.success(aforamiento(confidence = null))
        repository().refreshAforamiento("p1")
        assertNull(repository().observeAforamiento("p1").first()!!.confidence)
    }

    @Test fun unAforamientoMasRecienteReemplazaALaCopiaAnterior() = runTest {
        api.aforamientoResult = Response.success(aforamiento(date = "2026-10-01", plants = 900))
        repository().refreshAforamiento("p1")
        api.aforamientoResult = Response.success(aforamiento(date = "2026-10-29", plants = 1850))
        repository().refreshAforamiento("p1")
        val saved = repository().observeAforamiento("p1").first()!!
        assertEquals("2026-10-29", saved.date)
        assertEquals(1850, saved.estimatedPlants)
    }

    @Test fun sinAforamientosEn404ConservaLoLocalYAvisaQueNoHay() = runTest {
        api.aforamientoResult = notFound()
        assertEquals(AforamientoRefresh.NONE, repository().refreshAforamiento("p1"))
        assertNull(repository().observeAforamiento("p1").first())
    }

    @Test fun sinRedConservaLaUltimaCopia() = runTest {
        api.aforamientoResult = Response.success(aforamiento(plants = 1850))
        repository().refreshAforamiento("p1")

        api.failWith = IOException("sin red")
        assertEquals(AforamientoRefresh.FAILED, repository().refreshAforamiento("p1"))

        assertEquals(1850, repository().observeAforamiento("p1").first()!!.estimatedPlants)
    }

    @Test fun erroresDelServidorOLaSesionSonFallosYNoBorranNada() = runTest {
        api.aforamientoResult = Response.success(aforamiento())
        repository().refreshAforamiento("p1")
        for (code in listOf(401, 403, 500, 503)) {
            api.aforamientoResult = Response.error(code, "{}".toResponseBody("application/json".toMediaType()))
            assertEquals("código $code", AforamientoRefresh.FAILED, repository().refreshAforamiento("p1"))
        }
        assertEquals(1850, repository().observeAforamiento("p1").first()!!.estimatedPlants)
    }

    @Test fun sinTokenEsUnFalloSinLlamarAlServidor() = runTest {
        token = null
        assertEquals(AforamientoRefresh.FAILED, repository().refreshAforamiento("p1"))
        assertNull(api.lastParcelId)
    }

    @Test fun enModoDemostracionNoHayServidorNiAforamientos() = runTest {
        assertEquals(AforamientoRefresh.NONE, repository(StarterInventory.DEMO_OWNER).refreshAforamiento("p1"))
        assertNull(api.lastParcelId)
    }

    @Test fun cadaParcelaTieneSuPropiaCopia() = runTest {
        api.aforamientoResult = Response.success(aforamiento(parcelId = "p1", plants = 100))
        repository().refreshAforamiento("p1")
        api.aforamientoResult = Response.success(aforamiento(parcelId = "p2", plants = 200))
        repository().refreshAforamiento("p2")
        assertEquals(100, repository().observeAforamiento("p1").first()!!.estimatedPlants)
        assertEquals(200, repository().observeAforamiento("p2").first()!!.estimatedPlants)
    }

    // --- refresh(): GET /parcels trae el aforamiento embebido ---

    @Test fun elRefrescoDeParcelasGuardaElAforamientoEmbebido() = runTest {
        api.listed = listOf(parcel("p1", aforamiento("p1", plants = 1850)), parcel("p2", aforamiento = null))

        assertEquals(true, repository().refresh())

        assertEquals(1850, repository().observeAforamiento("p1").first()!!.estimatedPlants)
        assertNull(repository().observeAforamiento("p2").first())
    }

    @Test fun elRefrescoReemplazaLosAforamientosYNoDejaHuerfanos() = runTest {
        api.listed = listOf(parcel("p1", aforamiento("p1", plants = 1850)), parcel("p2", aforamiento("p2", plants = 70)))
        repository().refresh()
        // La parcela p2 desaparece del servidor y p1 trae un aforamiento nuevo.
        api.listed = listOf(parcel("p1", aforamiento("p1", date = "2026-11-05", plants = 2000)))
        repository().refresh()

        assertEquals(2000, repository().observeAforamiento("p1").first()!!.estimatedPlants)
        assertNull(repository().observeAforamiento("p2").first())
    }

    @Test fun siFallaLaRedElRefrescoConservaLaCopia() = runTest {
        api.listed = listOf(parcel("p1", aforamiento("p1", plants = 1850)))
        repository().refresh()
        api.failWith = IOException("sin red")
        assertEquals(false, repository().refresh())
        assertEquals(1850, repository().observeAforamiento("p1").first()!!.estimatedPlants)
    }

    @Test fun unServidorSinElCampoNoRompeLaLectura() = runTest {
        // Gson deja nulo un campo ausente: así llega una respuesta de un servidor anterior a HU_09.
        val old = com.google.gson.Gson().fromJson(
            """{"id":"p1","name":"La Esperanza","areaHa":"2.40","activeCycle":null}""", ParcelResponseDto::class.java,
        )
        assertNull(old.aforamiento)
        api.listed = listOf(old)
        assertEquals(true, repository().refresh())
        assertEquals(1, repository().parcels.first().size)
    }

    @Test fun elJsonDelServidorSeInterpretaConAreaYConfianzaComoTexto() {
        val parsed = com.google.gson.Gson().fromJson(
            """{"id":"p1","name":"La Esperanza","areaHa":"2.40","activeCycle":null,
               "aforamiento":{"id":"a1","parcelId":"p1","date":"2026-10-29","areaHa":"2.41",
                              "estimatedPlants":1850,"confidence":"0.870"}}""",
            ParcelResponseDto::class.java,
        )
        assertEquals(BigDecimal("2.41"), parsed.aforamiento!!.areaHa)
        assertEquals(BigDecimal("0.870"), parsed.aforamiento!!.confidence)
        assertEquals(1850, parsed.aforamiento!!.estimatedPlants)
    }

    private fun notFound(): Response<AforamientoDto> =
        Response.error(404, """{"detail":"La parcela no tiene aforamientos."}""".toResponseBody("application/json".toMediaType()))
}

private class FakeAforamientoApi : ParcelApi {
    var lastAuthorization: String? = null
    var lastParcelId: String? = null
    var failWith: IOException? = null
    var listed: List<ParcelResponseDto> = emptyList()
    var aforamientoResult: Response<AforamientoDto> = Response.success(null)

    override suspend fun create(authorization: String, parcel: ParcelDto): Response<ParcelResponseDto> =
        error("no se usa")

    override suspend fun list(authorization: String): Response<List<ParcelResponseDto>> {
        failWith?.let { throw it }
        return Response.success(listed)
    }

    override suspend fun aforamiento(authorization: String, parcelId: String): Response<AforamientoDto> {
        failWith?.let { throw it }
        lastAuthorization = authorization
        lastParcelId = parcelId
        return aforamientoResult
    }
}
