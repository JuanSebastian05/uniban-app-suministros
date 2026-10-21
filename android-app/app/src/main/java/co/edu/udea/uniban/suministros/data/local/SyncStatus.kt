package co.edu.udea.uniban.suministros.data.local

/** Estados de sincronización de un movimiento (RT_02). */
object SyncStatus {
    const val PENDING = "PENDING"
    const val SYNCING = "SYNCING"
    const val SYNCED = "SYNCED"
    const val ERROR = "ERROR"
}
