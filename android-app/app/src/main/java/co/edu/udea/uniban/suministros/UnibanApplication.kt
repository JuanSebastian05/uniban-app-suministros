package co.edu.udea.uniban.suministros

import android.app.Application
import co.edu.udea.uniban.suministros.core.network.NetworkModule
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.MovementSyncRepository
import co.edu.udea.uniban.suministros.data.ParcelRepository
import co.edu.udea.uniban.suministros.data.RecommendationRepository
import co.edu.udea.uniban.suministros.data.auth.AuthRepository
import co.edu.udea.uniban.suministros.data.auth.FirebaseAuthRepository
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.remote.ActivityApi
import co.edu.udea.uniban.suministros.data.remote.MovementApi
import co.edu.udea.uniban.suministros.data.remote.ParcelApi
import co.edu.udea.uniban.suministros.data.remote.RecommendationApi
import co.edu.udea.uniban.suministros.data.remote.UserApi
import co.edu.udea.uniban.suministros.sync.SyncScheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UnibanApplication : Application() {
    /** Una base local por cuenta (uid de Firebase); se abre la primera vez que se usa. */
    private val databases = ConcurrentHashMap<String, InventoryDatabase>()

    private val httpClient by lazy { NetworkModule.createOkHttpClient() }

    private val retrofit by lazy { NetworkModule.createRetrofit(httpClient) }

    private val movementApi by lazy { retrofit.create(MovementApi::class.java) }

    private val parcelApi by lazy { retrofit.create(ParcelApi::class.java) }

    /** Cliente con espera larga solo para las recomendaciones (síncronas, HU_12). El resto usa [httpClient]. */
    private val recommendationHttpClient by lazy {
        httpClient.newBuilder().readTimeout(NetworkModule.RECOMMENDATION_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    }

    private val recommendationApi by lazy {
        retrofit.newBuilder().client(recommendationHttpClient).build().create(RecommendationApi::class.java)
    }

    private val activityApi by lazy { retrofit.create(ActivityApi::class.java) }
    private val productionApi by lazy { retrofit.create(co.edu.udea.uniban.suministros.data.remote.ProductionApi::class.java) }

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(this, retrofit.create(UserApi::class.java))
    }

    private fun database(uid: String): InventoryDatabase =
        databases.computeIfAbsent(uid) { InventoryDatabase.create(this, it) }

    /** Inventario de la cuenta [uid]; nunca ve los datos de otra cuenta del dispositivo. */
    fun inventoryRepository(uid: String): InventoryRepository {
        // Al abrir el inventario se envía lo que esta cuenta haya dejado pendiente.
        SyncScheduler.enqueue(this)
        // Cada movimiento guardado pide una sincronización; se ejecuta cuando haya red.
        return InventoryRepository(database(uid), uid, onMovementSaved = { SyncScheduler.enqueue(this) })
    }

    /** Parcelas de la cuenta [uid] (HU_14): se registran en el backend con el token de esa cuenta. */
    fun parcelRepository(uid: String): ParcelRepository =
        ParcelRepository(database(uid), parcelApi, uid) { authRepository.idToken(uid) }

    /** Recomendaciones de la cuenta [uid] (HU_12): las pide nuestro backend al Equipo 2; la app no lo llama (RT_11). */
    fun recommendationRepository(uid: String): RecommendationRepository =
        RecommendationRepository(
            database(uid), recommendationApi, uid,
            warmUp = { withContext(Dispatchers.IO) { NetworkModule.warmUpAndWait(recommendationHttpClient) } },
        ) { authRepository.idToken(uid) }

    /** Sincroniza los movimientos de [uid] con el token de esa misma cuenta. */
    fun movementSyncRepository(uid: String): MovementSyncRepository =
        MovementSyncRepository(database(uid), movementApi, activityApi, productionApi) { authRepository.idToken(uid) }

    fun productionRepository(uid: String): co.edu.udea.uniban.suministros.data.ProductionRepository =
        co.edu.udea.uniban.suministros.data.ProductionRepository(database(uid), productionApi, uid,
            onSaved = { SyncScheduler.enqueue(this) }) { authRepository.idToken(uid) }

    override fun onCreate() {
        super.onCreate()
        // Despierta el servidor (Render lo duerme a los 15 min) mientras el productor abre la app.
        NetworkModule.warmUp(httpClient)
        // Retoma lo que quedó pendiente (o interrumpido) en la sesión anterior.
        SyncScheduler.enqueue(this)
        SyncScheduler.schedulePeriodic(this)
    }
}
