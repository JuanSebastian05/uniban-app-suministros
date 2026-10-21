package co.edu.udea.uniban.suministros.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.edu.udea.uniban.suministros.UnibanApplication
import co.edu.udea.uniban.suministros.data.auth.SessionState

/**
 * Trabajo de WorkManager que sincroniza los movimientos pendientes (RT_04).
 * Solo envía los de la cuenta con sesión abierta: los de otra cuenta esperan a que vuelva a entrar.
 * Solo pide reintento (con espera exponencial) cuando el fallo fue de red o del servidor.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as UnibanApplication
        val session = app.authRepository.sessionState.value as? SessionState.Authenticated
            ?: return Result.success()
        val repository = app.movementSyncRepository(session.uid)
        return if (repository.syncPending().retryLater) Result.retry() else Result.success()
    }
}
