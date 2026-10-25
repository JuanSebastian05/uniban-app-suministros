package co.edu.udea.uniban.suministros

import co.edu.udea.uniban.suministros.core.network.NetworkModule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prueba técnica de la configuración base (no corresponde a ninguna HU). */
class NetworkConfigTest {

    @Test
    fun apiBaseUrl_usaPrefijoVersionado() {
        assertTrue(NetworkModule.apiBaseUrl.endsWith("/api/v1/"))
    }

    @Test
    fun readTimeoutCoversTheRenderColdStart() {
        // Render despierta en ≈ 1 min: con 30 s fallaban el registro y la creación de parcelas (P-02).
        val client = NetworkModule.createOkHttpClient()
        assertTrue(client.readTimeoutMillis >= 90_000)
        assertEquals(NetworkModule.READ_TIMEOUT_SECONDS * 1_000, client.readTimeoutMillis.toLong())
    }

    @Test
    fun warmUpSendsAGetToHealthWithoutWaitingForTheAnswer() {
        val requests = mutableListOf<Pair<String, String>>()
        val arrived = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.method to request.url.toString()
            arrived.countDown()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody()).build()
        }.build()
        NetworkModule.warmUp(client, "https://servidor.ejemplo/")
        assertTrue(arrived.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("GET" to "https://servidor.ejemplo/health"), requests)
    }

    @Test
    fun warmUpIgnoresAnInvalidBaseUrlAndNetworkFailures() {
        val failed = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { _ ->
            failed.countDown()
            throw java.io.IOException("sin red")
        }.build()
        NetworkModule.warmUp(client, "esto no es una url")
        NetworkModule.warmUp(client, "https://servidor.ejemplo/")
        assertTrue(failed.await(5, TimeUnit.SECONDS))
    }
}
