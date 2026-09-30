package co.edu.udea.uniban.suministros

import android.app.Application
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase

class UnibanApplication : Application() {
    val inventoryRepository: InventoryRepository by lazy {
        InventoryRepository(InventoryDatabase.create(this))
    }
}

