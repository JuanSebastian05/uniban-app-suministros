package co.edu.udea.uniban.suministros.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.StarterInventory
import co.edu.udea.uniban.suministros.data.remote.RecommendationApi
import co.edu.udea.uniban.suministros.data.remote.RecommendationDto
import co.edu.udea.uniban.suministros.data.remote.RecommendationPlanDto
import co.edu.udea.uniban.suministros.data.remote.RecommendationSupplyDto
import java.io.IOException
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

/** HU_12: copia local de la última recomendación (Room, sin FK a `parcels`) y traducción de las respuestas de nuestro backend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class RecommendationRepositoryTest {
    private lateinit var database: InventoryDatabase
    private val api = FakeRecommendationApi()
    private var token: String? = "token"
    private val events = mutableListOf<String>()

    private fun repository(owner: String = "uid-ana", warmUp: suspend () -> Unit = { events += "warm" }) =
        RecommendationRepository(database, api, owner, warmUp) { token }

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InventoryDatabase::class.java)
            .allowMainThreadQueries().build()
        api.onCall = { events += it }
    }

    @After fun tearDown() = database.close()

    private fun dto(
        parcelId: String = "p1", id: String = "r1", volume: Double = 1850.0, meets: Boolean = true,
        reason: String? = null, supplies: List<RecommendationSupplyDto>? = listOf(
            RecommendationSupplyDto("cajas", "2026-11-01", 120.0, 80.0, 40.0),
            RecommendationSupplyDto("bolsas", "2026-11-01", 50.0, 90.0, 0.0),
        ),
    ) = RecommendationDto(
        id, parcelId, "2026-W44", "2026-10-26T19:00:00.123456Z", "v0.3.1", volume, "2026-11-02T14:30:00", meets,
        RecommendationPlanDto(reason), supplies,
    )

    private fun error(code: Int, body: String = "{}") =
        Response.error<RecommendationDto>(code, body.toResponseBody("application/json".toMediaType()))

    private fun detail(code: String) = """{"detail":{"code":"$code","message":"x"}}"""

    // --- requestRecommendation: POST ---

    @Test fun pedirGuardaLaCopiaLocalConTodosLosDatos() = runTest {
        api.requestResult = Response.success(201, dto(reason = "Llega tarde"))

        assertEquals(RecommendationResult.Saved, repository().requestRecommendation("p1"))

        assertEquals("Bearer token", api.lastAuthorization)
        assertEquals("p1", api.lastParcelId)
        val saved = repository().observeRecommendation("p1").first()!!
        assertEquals("2026-W44", saved.period)
        assertEquals("v0.3.1", saved.sourceVersion)
        assertEquals(1850.0, saved.volumeBunches, 0.0)
        assertEquals("2026-11-02T14:30:00", saved.cutDeadline)
        assertEquals("2026-10-26T19:00:00.123456Z", saved.generatedAt)
        assertEquals(true, saved.meetsWindow)
        assertEquals("Llega tarde", saved.reason)
        assertEquals(listOf("cajas", "bolsas"), saved.supplies.map { it.name })
        assertEquals(RecommendationSupply("cajas", "2026-11-01", 120.0, 80.0, 40.0), saved.supplies[0])
        assertEquals(0.0, saved.supplies[1].deficit, 0.0)
    }

    @Test fun calientaAntesDelPost() = runTest {
        api.requestResult = Response.success(201, dto())
        repository().requestRecommendation("p1")
        assertEquals(listOf("warm", "post"), events)
    }

    @Test fun siFallaElCalentamientoIgualPideLaRecomendacion() = runTest {
        api.requestResult = Response.success(201, dto())
        val result = repository(warmUp = { throw IOException("sin red") }).requestRecommendation("p1")
        assertEquals(RecommendationResult.Saved, result)
    }

    @Test fun unaObservacionVaciaOAusenteQuedaNula() = runTest {
        api.requestResult = Response.success(201, dto(reason = "  "))
        repository().requestRecommendation("p1")
        assertNull(repository().observeRecommendation("p1").first()!!.reason)
    }

    @Test fun sinListaDeInsumosQuedaVacia() = runTest {
        api.requestResult = Response.success(201, dto(supplies = null))
        repository().requestRecommendation("p1")
        assertTrue(repository().observeRecommendation("p1").first()!!.supplies.isEmpty())
    }

    @Test fun unaRecomendacionNuevaReemplazaALaAnterior() = runTest {
        api.requestResult = Response.success(201, dto(id = "r1", volume = 1000.0))
        repository().requestRecommendation("p1")
        api.requestResult = Response.success(201, dto(id = "r2", volume = 2100.0, meets = false))
        repository().requestRecommendation("p1")

        val saved = repository().observeRecommendation("p1").first()!!
        assertEquals(2100.0, saved.volumeBunches, 0.0)
        assertEquals(false, saved.meetsWindow)
    }

    @Test fun cadaParcelaTieneSuPropiaCopia() = runTest {
        api.requestResult = Response.success(201, dto(parcelId = "p1", volume = 1.0))
        repository().requestRecommendation("p1")
        api.requestResult = Response.success(201, dto(parcelId = "p2", volume = 2.0))
        repository().requestRecommendation("p2")
        assertEquals(1.0, repository().observeRecommendation("p1").first()!!.volumeBunches, 0.0)
        assertEquals(2.0, repository().observeRecommendation("p2").first()!!.volumeBunches, 0.0)
    }

    @Test fun traduceLasRespuestasDeNuestroBackend() = runTest {
        val cases = listOf(
            error(409, detail("NO_AFORAMIENTO")) to RecommendationResult.NoAforamiento,
            error(503, detail("EQUIPO2_UNAVAILABLE")) to RecommendationResult.ServiceUnavailable,
            error(502, detail("EQUIPO2_BAD_RESPONSE")) to RecommendationResult.ServiceUnavailable,
            error(401) to RecommendationResult.SessionInvalid,
            error(403) to RecommendationResult.SessionInvalid,
            error(503, "Service Unavailable") to RecommendationResult.Offline, // nuestro propio Render dormido
            error(500) to RecommendationResult.Offline,
            error(504) to RecommendationResult.Offline,
            error(408) to RecommendationResult.Offline,
            error(429) to RecommendationResult.Offline,
            error(404, detail("X")) to RecommendationResult.Failed,
            error(409, detail("OTRO")) to RecommendationResult.Failed,
            error(422) to RecommendationResult.Failed,
        )
        for ((response, expected) in cases) {
            api.requestResult = response
            assertEquals("código ${response.code()}", expected, repository().requestRecommendation("p1"))
        }
        assertNull(repository().observeRecommendation("p1").first()) // ningún fallo guarda nada
    }

    @Test fun sinRedEsSinConexionYConservaLaCopia() = runTest {
        api.requestResult = Response.success(201, dto(volume = 1850.0))
        repository().requestRecommendation("p1")

        api.failWith = IOException("sin red")
        assertEquals(RecommendationResult.Offline, repository().requestRecommendation("p1"))

        assertEquals(1850.0, repository().observeRecommendation("p1").first()!!.volumeBunches, 0.0)
    }

    @Test fun sinTokenEsSesionNoValidaSinLlamarAlServidor() = runTest {
        token = null
        assertEquals(RecommendationResult.SessionInvalid, repository().requestRecommendation("p1"))
        assertEquals(emptyList<String>(), events)
    }

    @Test fun enModoDemostracionNoHayServidor() = runTest {
        assertEquals(RecommendationResult.Failed, repository(StarterInventory.DEMO_OWNER).requestRecommendation("p1"))
        assertEquals(emptyList<String>(), events)
    }

    // --- refreshRecommendation: GET ---

    @Test fun consultarLaUltimaGuardadaLaCopiaLocalmente() = runTest {
        api.latestResult = Response.success(dto(volume = 777.0))

        assertEquals(RecommendationRefresh.UPDATED, repository().refreshRecommendation("p1"))

        assertEquals(777.0, repository().observeRecommendation("p1").first()!!.volumeBunches, 0.0)
        assertEquals(listOf("get"), events) // no se calienta ni se pide al Equipo 2
    }

    @Test fun sinRecomendacionesEn404EsNone() = runTest {
        api.latestResult = error(404, detail("NO_RECOMMENDATION"))
        assertEquals(RecommendationRefresh.NONE, repository().refreshRecommendation("p1"))
        assertNull(repository().observeRecommendation("p1").first())
    }

    @Test fun losFallosDeLaConsultaConservanLaCopia() = runTest {
        api.latestResult = Response.success(dto(volume = 555.0))
        repository().refreshRecommendation("p1")

        for (failure in listOf(error(401), error(403), error(500), error(503))) {
            api.latestResult = failure
            assertEquals(RecommendationRefresh.FAILED, repository().refreshRecommendation("p1"))
        }
        api.failWith = IOException("sin red")
        assertEquals(RecommendationRefresh.FAILED, repository().refreshRecommendation("p1"))
        assertEquals(555.0, repository().observeRecommendation("p1").first()!!.volumeBunches, 0.0)
    }

    @Test fun laConsultaSinTokenOEnDemostracion() = runTest {
        token = null
        assertEquals(RecommendationRefresh.FAILED, repository().refreshRecommendation("p1"))
        token = "token"
        assertEquals(RecommendationRefresh.NONE, repository(StarterInventory.DEMO_OWNER).refreshRecommendation("p1"))
    }

    // --- JSON del servidor ---

    @Test fun elJsonDelBackendSeInterpretaYSeIgnoranLosCamposQueLaAppNoUsa() {
        val parsed = com.google.gson.Gson().fromJson(
            """{"id":"r1","parcelId":"p1","period":"2026-W44","generatedAt":"2026-10-26T19:00:00Z",
                "sourceVersion":"v1","volumeBunches":1850.0,"cutDeadline":"2026-11-02T14:30:00","meetsWindow":false,
                "plan":{"cutDate":"2026-11-02T08:00:00","order":1,"estimatedLoadBunches":420,"meetsWindow":false,
                        "vehicleId":null,"reason":"Fuera de ventana"},
                "supplies":[{"name":"cajas","date":"2026-11-01","requiredQuantity":120.0,"availableQuantity":80.0,"deficit":40.0}],
                "metrics":{"windowCompliance":0.5,"capacityUse":0.9}}""",
            RecommendationDto::class.java,
        )
        assertEquals("Fuera de ventana", parsed.plan?.reason)
        assertEquals(40.0, parsed.supplies!![0].deficit, 0.0)
        assertEquals(false, parsed.meetsWindow)
    }

    private class FakeRecommendationApi : RecommendationApi {
        var lastAuthorization: String? = null
        var lastParcelId: String? = null
        var failWith: IOException? = null
        var onCall: (String) -> Unit = {}
        var requestResult: Response<RecommendationDto> = Response.success(null)
        var latestResult: Response<RecommendationDto> = Response.success(null)

        override suspend fun request(authorization: String, parcelId: String): Response<RecommendationDto> {
            onCall("post")
            failWith?.let { throw it }
            lastAuthorization = authorization
            lastParcelId = parcelId
            return requestResult
        }

        override suspend fun latest(authorization: String, parcelId: String): Response<RecommendationDto> {
            onCall("get")
            failWith?.let { throw it }
            lastAuthorization = authorization
            lastParcelId = parcelId
            return latestResult
        }
    }
}
