package co.edu.udea.uniban.suministros

import android.app.Application
import co.edu.udea.uniban.suministros.core.network.NetworkModule
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.MovementSyncRepository
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.remote.MovementApi
import co.edu.udea.uniban.suministros.sync.SyncScheduler

class UnibanApplication : Application() {
    private val database: InventoryDatabase by lazy { InventoryDatabase.create(this) }

    val inventoryRepository: InventoryRepository by lazy {
        // Cada movimiento guardado pide una sincronización; se ejecuta cuando haya red.
        InventoryRepository(database, onMovementSaved = { SyncScheduler.enqueue(this) })
    }

    val movementSyncRepository: MovementSyncRepository by lazy {
        MovementSyncRepository(database, NetworkModule.createRetrofit().create(MovementApi::class.java))
    }

    override fun onCreate() {
        super.onCreate()
        // Retoma lo que quedó pendiente (o interrumpido) en la sesión anterior.
        SyncScheduler.enqueue(this)
        SyncScheduler.schedulePeriodic(this)
    }
}
