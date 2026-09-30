package co.edu.udea.uniban.suministros.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.edu.udea.uniban.suministros.UnibanApplication

/**
 * Trabajo de WorkManager que sincroniza los movimientos pendientes (RT_04).
 * Solo pide reintento (con espera exponencial) cuando el fallo fue de red o del servidor.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as UnibanApplication).movementSyncRepository
        return if (repository.syncPending().retryLater) Result.retry() else Result.success()
    }
}
