package co.edu.udea.uniban.suministros.core.network

import co.edu.udea.uniban.suministros.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

    val apiBaseUrl: String
        get() = BuildConfig.API_BASE_URL + API_VERSION_PATH

    fun createOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        // HU_01: agregar el token de Firebase en "Authorization" al implementar la API.
        return builder.build()
    }

    fun createRetrofit(client: OkHttpClient = createOkHttpClient()): Retrofit =
        Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}
