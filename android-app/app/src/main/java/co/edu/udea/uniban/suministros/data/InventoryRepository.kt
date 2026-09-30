package co.edu.udea.uniban.suministros.data

import androidx.room.withTransaction
import co.edu.udea.uniban.suministros.data.local.DemoInventory
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.MovementEntity
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SavedEntry(val id: String, val supplyName: String, val quantity: Float, val unit: String)

class InventoryRepository(private val database: InventoryDatabase) {
    private val dao = database.inventoryDao()
    val inventory = dao.observeInventory()
    val movements = dao.observeMovements()

    suspend fun initializeDemo() = database.withTransaction {
        if (!dao.hasProducer(DemoInventory.producer.id)) {
            dao.insertProducer(DemoInventory.producer)
            dao.insertLocation(DemoInventory.location)
            dao.insertSupplies(DemoInventory.supplies)
            dao.insertInventory(DemoInventory.inventory)
        }
    }

    suspend fun findSavedEntry(id: String): SavedEntry? {
        val movement = dao.findMovement(id) ?: return null
        val item = requireNotNull(dao.findInventory(movement.inventoryId))
        return SavedEntry(id, item.name, movement.quantity, item.unit)
    }

    suspend fun registerEntry(
        id: String,
        inventoryId: String,
        quantityText: String,
        date: LocalDate,
        observation: String,
    ): SavedEntry {
        UUID.fromString(id)
        val quantity = Quantity.parse(quantityText)
        require(observation.length <= 500) { "La observación admite hasta 500 caracteres." }
        return database.withTransaction {
            val item = requireNotNull(dao.findInventory(inventoryId)) { "Selecciona un insumo del catálogo." }
            val existing = dao.findMovement(id)
            if (existing != null) {
                require(existing.inventoryId == inventoryId && existing.quantity == quantity &&
                    existing.date == date.toString() && existing.observation == observation.trim()) {
                    "Esta entrada ya fue registrada con otros datos."
                }
            } else {
                val newQuantity = Quantity.add(item.quantity, quantity)
                val now = Instant.now().toString()
                dao.insertMovement(MovementEntity(
                    id = id, inventoryId = inventoryId, quantity = quantity,
                    date = date.toString(), observation = observation.trim(),
                    createdAt = now, updatedAt = now,
                ))
                check(dao.updateQuantity(inventoryId, newQuantity) == 1)
            }
            SavedEntry(id, item.name, quantity, item.unit)
        }
    }
}

