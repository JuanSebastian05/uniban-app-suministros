package co.edu.udea.uniban.suministros.data

import androidx.room.withTransaction
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.SyncMetadataEntity
import co.edu.udea.uniban.suministros.data.remote.ProductionApi
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/** Cosecha y despacho son unidades indivisibles para WorkManager y el servidor. */
class ProductionSyncRepository(private val database: InventoryDatabase, private val api: ProductionApi) {
    private val dao = database.productionDao()
    private val gson = Gson()

    suspend fun sync(authorization: String): SyncSummary {
        dao.resetHarvests()
        dao.resetDispatches()
        var synced = 0
        var failed = 0
        var retry = false
        suspend fun send(id: String, harvest: Boolean, action: suspend () -> Response<Unit>): Boolean {
            suspend fun status(value: String, error: String? = null) = withContext(NonCancellable) {
                database.withTransaction {
                    if (harvest) { dao.harvestStatus(id, value, error); dao.activityStatus(id, value, error) }
                    else { dao.dispatchStatus(id, value, error); dao.consumptionStatus(id, value, error) }
                }
            }
            status("SYNCING")
            try {
                val response = action()
                if (response.isSuccessful) { status("SYNCED"); synced++ }
                else {
                    val message = runCatching {
                        gson.fromJson(response.errorBody()?.string(), com.google.gson.JsonObject::class.java)?.get("detail")?.asString
                    }.getOrNull() ?: "El servidor rechazó el registro (HTTP ${response.code()}). Revisa sus datos."
                    status("ERROR", message)
                    failed++
                    if (response.code() >= 500 || response.code() in listOf(401, 403, 408, 429)) retry = true
                }
            } catch (error: CancellationException) { status("PENDING"); throw error }
            catch (_: IOException) { status("ERROR", "Sin conexión. El registro se conserva y se volverá a enviar."); failed++; retry = true }
            catch (_: Exception) { status("ERROR", "No se pudo preparar el registro. Revisa sus datos."); failed++ }
            return !retry
        }
        for (item in dao.pendingHarvests()) if (!send(item.id, true) { api.harvest(authorization, item.toDto()) }) break
        if (!retry) for (item in dao.pendingDispatches()) {
            if (dao.periodHarvests(item.parcelId, item.period).any { it.syncStatus != "SYNCED" }) continue
            if (!send(item.id, false) { api.dispatch(authorization, item.toDto(gson)) }) break
        }
        if (synced > 0) database.syncMetadataDao().saveLastSuccessfulAt(SyncMetadataEntity(lastSuccessfulAtEpochMillis = System.currentTimeMillis()))
        return SyncSummary(synced, failed, retry)
    }
}
