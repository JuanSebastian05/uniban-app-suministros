package co.edu.udea.uniban.suministros.data

import androidx.room.withTransaction
import co.edu.udea.uniban.suministros.data.local.AdjustDirection
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.LossReason
import co.edu.udea.uniban.suministros.data.local.MovementEntity
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.data.local.StarterInventory
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SavedEntry(
    val id: String,
    val supplyName: String,
    val quantity: Float,
    val unit: String,
    val type: String = MovementType.ENTRADA,
)

/**
 * Inventario local de una sola cuenta: [database] es la base propia de [ownerUid], así que
 * nada de lo que se registra aquí aparece en el inventario de otro usuario del dispositivo.
 *
 * [onMovementSaved] se invoca después de confirmar la transacción de un registro; la app lo
 * usa para pedir la sincronización. La UI no conoce la red (RT_08).
 */
class InventoryRepository(
    private val database: InventoryDatabase,
    ownerUid: String = StarterInventory.DEMO_OWNER,
    private val onMovementSaved: () -> Unit = {},
) {
    private val dao = database.inventoryDao()
    private val starter = StarterInventory(ownerUid)
    val inventory = dao.observeInventory()
    val movements = dao.observeMovements()
    val pendingCount = dao.observePendingCount()

    /** Movimientos recientes de un insumo, para el detalle del inventario (HU_03). */
    fun recentMovements(inventoryId: String, limit: Int = RECENT_MOVEMENTS) =
        dao.observeRecentMovements(inventoryId, limit)

    /** Historial completo de movimientos de este dispositivo (HU_07). */
    val history = dao.observeHistory()

    /** Saldo inicial y existencia de cada insumo, para el resumen del historial (HU_07). */
    val balances = dao.observeBalances()

    /** Crea el inventario inicial de la cuenta la primera vez que se abre. */
    suspend fun initializeDemo() = database.withTransaction {
        if (!dao.hasProducer(starter.producer.id)) {
            dao.insertProducer(starter.producer)
            dao.insertLocation(starter.location)
            dao.insertSupplies(starter.supplies)
            dao.insertInventory(starter.inventory)
        }
    }

    suspend fun findSavedEntry(id: String): SavedEntry? {
        val movement = dao.findMovement(id) ?: return null
        val item = requireNotNull(dao.findInventory(movement.inventoryId))
        return SavedEntry(id, item.name, movement.quantity, item.unit, movement.type)
    }

    /** Registra una entrada (HU_04). Equivale a [registerMovement] con tipo ENTRADA. */
    suspend fun registerEntry(
        id: String,
        inventoryId: String,
        quantityText: String,
        date: LocalDate,
        observation: String,
    ): SavedEntry = registerMovement(id, MovementType.ENTRADA, inventoryId, quantityText, date, observation)

    /**
     * Registra una entrada, un consumo o una pérdida (HU_04, HU_05) y actualiza la existencia en
     * la misma transacción (RT_10). Funciona sin conexión: el movimiento queda PENDING.
     *
     * - CONSUMO exige [parcelId]; si la parcela tiene ciclo activo, se asocia automáticamente.
     * - PERDIDA exige [lossReason]; el motivo OTRO exige observación.
     * - AJUSTE (corrección) exige [adjustDirection] y el motivo en la observación; suma o resta la cantidad.
     * - Un consumo, una pérdida o un ajuste de disminución nunca deja la existencia negativa ([InsufficientStockException]).
     * - Reintentar con el mismo [id] y los mismos datos no vuelve a sumar ni restar.
     */
    suspend fun registerMovement(
        id: String,
        type: String,
        inventoryId: String,
        quantityText: String,
        date: LocalDate,
        observation: String,
        parcelId: String? = null,
        lossReason: LossReason? = null,
        adjustDirection: AdjustDirection? = null,
    ): SavedEntry {
        UUID.fromString(id)
        require(type in MovementType.REGISTRABLE) { "Tipo de movimiento no disponible." }
        val quantity = Quantity.parse(quantityText)
        val cleanObservation = observation.trim()
        require(observation.length <= 500) { "La observación admite hasta 500 caracteres." }
        val parcel = parcelId?.takeIf { type == MovementType.CONSUMO }
        val reason = lossReason?.takeIf { type == MovementType.PERDIDA }
        val direction = adjustDirection?.takeIf { type == MovementType.AJUSTE }
        if (type == MovementType.AJUSTE) {
            require(direction != null) { "Indica si el ajuste aumenta o disminuye la existencia." }
            require(cleanObservation.isNotEmpty()) { "Describe el motivo del ajuste en la observación." }
        }
        if (type == MovementType.CONSUMO) require(parcel != null) { "Selecciona la parcela donde se usó el insumo." }
        if (type == MovementType.PERDIDA) {
            require(reason != null) { "Selecciona el motivo de la pérdida." }
            require(reason != LossReason.OTRO || cleanObservation.isNotEmpty()) {
                "Describe el motivo en la observación."
            }
        }
        val saved = database.withTransaction {
            val item = requireNotNull(dao.findInventory(inventoryId)) { "Selecciona un insumo del catálogo." }
            val cycleId = parcel?.let {
                require(database.parcelDao().hasParcel(it)) { "La parcela ya no está disponible." }
                database.parcelDao().activeCycleId(it)
            }
            val existing = dao.findMovement(id)
            if (existing != null) {
                require(existing.inventoryId == inventoryId && existing.type == type &&
                    existing.quantity == quantity && existing.date == date.toString() &&
                    existing.observation == cleanObservation && existing.parcelId == parcel &&
                    existing.lossReason == reason?.name && existing.adjustDirection == direction?.name) {
                    if (type == MovementType.ENTRADA) "Esta entrada ya fue registrada con otros datos."
                    else "Este movimiento ya fue registrado con otros datos."
                }
            } else {
                val newQuantity = if (MovementType.subtracts(type, direction?.name)) Quantity.subtract(item.quantity, quantity)
                else Quantity.add(item.quantity, quantity)
                val now = Instant.now().toString()
                dao.insertMovement(MovementEntity(
                    id = id, inventoryId = inventoryId, quantity = quantity,
                    date = date.toString(), observation = cleanObservation,
                    createdAt = now, updatedAt = now, type = type,
                    parcelId = parcel, cycleId = cycleId, lossReason = reason?.name,
                    adjustDirection = direction?.name,
                ))
                check(dao.updateQuantity(inventoryId, newQuantity) == 1)
            }
            SavedEntry(id, item.name, quantity, item.unit, type)
        }
        onMovementSaved()
        return saved
    }

    companion object {
        const val RECENT_MOVEMENTS = 20
    }
}

