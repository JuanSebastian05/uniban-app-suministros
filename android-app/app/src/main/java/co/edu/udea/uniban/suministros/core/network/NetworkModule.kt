package co.edu.udea.uniban.suministros.core.network

import co.edu.udea.uniban.suministros.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Infraestructura HTTP base (Retrofit + OkHttp).
 *
 * - La URL base viene de BuildConfig.API_BASE_URL (configurada en local.properties,
 *   clave `api.baseUrl`); no se hardcodean direcciones en el código.
 * - Todas las rutas quedan bajo el prefijo versionado `/api/v1/` (RT_03).
 * - La app solo se comunica con el backend FastAPI del Equipo 3 (RT_11).
 *
 * El servicio de inventario se creará en `data/remote` al implementar HU_06,
 * con `retrofit.create(XxxApi::class.java)`.
 * Aquí NO se definen endpoints de dominio.
 */
object NetworkModule {

    const val API_VERSION_PATH = "api/v1/"
    private const val TIMEOUT_SECONDS = 30L

    /**
     * Render duerme el servicio a los 15 min y despierta en ≈ 1 min: la primera petición se queda
     * esperando la respuesta. Con 30 s fallaba el registro y la creación de parcelas; 90 s da margen.
     */
    const val READ_TIMEOUT_SECONDS = 90L

    /**
     * Pedir una recomendación es síncrono y nuestro backend espera a su vez al Equipo 2 (también en Render): calentamiento
     * de hasta 60 s + 90 s + un reintento de 90 s ≈ 4 min en el peor caso. Solo para ese endpoint (`RecommendationApi`).
     */
    const val RECOMMENDATION_READ_TIMEOUT_SECONDS = 270L

    val apiBaseUrl: String
        get() = BuildConfig.API_BASE_URL + API_VERSION_PATH

    fun createOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        return builder.build()
    }

    /**
     * Despierta el servidor sin esperar su respuesta: un `GET /health` (sin datos del usuario) al abrir la
     * app para que, cuando el productor termine de llenar el registro, el servicio ya esté arriba.
     */
    fun warmUp(client: OkHttpClient, baseUrl: String = BuildConfig.API_BASE_URL) {
        val url = (baseUrl + HEALTH_PATH).toHttpUrlOrNull() ?: return
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) = response.close()
        })
    }

    /**
     * Igual que [warmUp] pero espera la respuesta (o el fallo) para que el POST de la recomendación no consuma su espera
     * despertando nuestro servidor. Bloqueante: llamarlo desde un hilo de E/S. Cualquier fallo se ignora.
     */
    fun warmUpAndWait(client: OkHttpClient, baseUrl: String = BuildConfig.API_BASE_URL) {
        val url = (baseUrl + HEALTH_PATH).toHttpUrlOrNull() ?: return
        try {
            client.newCall(Request.Builder().url(url).build()).execute().close()
        } catch (_: IOException) {
            // Mejor esfuerzo.
        }
    }

    private const val HEALTH_PATH = "health"

    fun createRetrofit(client: OkHttpClient = createOkHttpClient()): Retrofit =
        Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}
