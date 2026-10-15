package co.edu.udea.uniban.suministros

import android.app.Application
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.auth.AuthRepository
import co.edu.udea.uniban.suministros.data.auth.FirebaseAuthRepository
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase

class UnibanApplication : Application() {
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(this) }

    val inventoryRepository: InventoryRepository by lazy {
        InventoryRepository(InventoryDatabase.create(this))
    }
}

