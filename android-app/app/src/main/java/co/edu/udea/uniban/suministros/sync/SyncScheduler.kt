package co.edu.udea.uniban.suministros.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Programa la sincronización. WorkManager conserva las solicitudes aunque se cierre la app y
 * las ejecuta cuando hay conexión, sin que el productor intervenga (HU_06, RT_04, RT_18).
 */
object SyncScheduler {
    private const val ONE_TIME_WORK = "movement-sync"
    private const val PERIODIC_WORK = "movement-sync-periodic"

    private val requiresNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Pide una sincronización en cuanto haya red (al abrir la app y al guardar un movimiento). */
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(requiresNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Red de seguridad: reintenta periódicamente los movimientos en ERROR rechazados por el servidor. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(requiresNetwork)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
