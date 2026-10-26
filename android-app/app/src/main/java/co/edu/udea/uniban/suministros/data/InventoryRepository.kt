package co.edu.udea.uniban.suministros.data

import androidx.room.withTransaction
import co.edu.udea.uniban.suministros.data.local.ActivityEntity
import co.edu.udea.uniban.suministros.data.local.ActivityType
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

/** Insumo usado en una actividad (HU_08): el insumo del inventario y la cantidad escrita. */
data class ActivitySupply(val inventoryId: String, val quantityText: String)

data class SavedActivity(val id: String, val type: ActivityType, val cycleId: String, val consumptions: Int)

/** Lo que el formulario de actividades necesita del repositorio (HU_08); permite probar el ViewModel sin Room. */
interface ActivityRegistrar {
    suspend fun registerActivity(
        id: String,
        parcelId: String,
        type: ActivityType,
        date: LocalDate,
        observation: String,
        supplies: List<ActivitySupply> = emptyList(),
    ): SavedActivity
}

/** Un insumo de la actividad no alcanza (HU_08): se guarda nada. [inventoryId] dice cuál. */
class ActivityStockException(val inventoryId: String, val available: Float) :
    IllegalArgumentException("La cantidad supera la existencia disponible (${Quantity.format(available)}).")

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
) : ActivityRegistrar {
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

    /** Actividades productivas de la cuenta, con el nombre de su parcela (HU_08). */
    val activities = database.activityDao().observeActivities()

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
        /** HU_08: actividad a la que pertenece el consumo; el consumo toma su parcela y su ciclo. */
        activityId: String? = null,
        /** Falso cuando lo llama [registerActivity] dentro de su transacción: avisa una sola vez al final. */
        notify: Boolean = true,
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
            val activity = if (type == MovementType.CONSUMO) activityId?.let {
                requireNotNull(database.activityDao().findActivity(it)) { "La actividad ya no está disponible." }
            } else null
            if (activity != null) require(activity.parcelId == parcel) { "La actividad es de otra parcela." }
            val cycleId = parcel?.let {
                require(database.parcelDao().hasParcel(it)) { "La parcela ya no está disponible." }
                // Un consumo de una actividad usa el ciclo de la actividad aunque la parcela ya tenga otro.
                activity?.cycleId ?: database.parcelDao().activeCycleId(it)
            }
            val existing = dao.findMovement(id)
            if (existing != null) {
                require(existing.inventoryId == inventoryId && existing.type == type &&
                    existing.quantity == quantity && existing.date == date.toString() &&
                    existing.observation == cleanObservation && existing.parcelId == parcel &&
                    existing.lossReason == reason?.name && existing.adjustDirection == direction?.name &&
                    existing.activityId == activity?.id) {
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
                    adjustDirection = direction?.name, activityId = activity?.id,
                ))
                check(dao.updateQuantity(inventoryId, newQuantity) == 1)
            }
            SavedEntry(id, item.name, quantity, item.unit, type)
        }
        if (notify) onMovementSaved()
        return saved
    }

    /**
     * Registra una actividad productiva y, en la misma transacción, un CONSUMO por cada insumo usado
     * (HU_08). Todo o nada: si algún insumo no alcanza se lanza [ActivityStockException] y no se guarda
     * ni la actividad ni los demás consumos. Funciona sin conexión: todo queda PENDING.
     *
     * - Exige que la parcela tenga un **ciclo activo**; la actividad y sus consumos se asocian a él.
     * - Reintentar con el mismo [id] y los mismos datos no duplica nada: los consumos usan un UUID derivado de
     *   la actividad y del insumo.
     */
    override suspend fun registerActivity(
        id: String,
        parcelId: String,
        type: ActivityType,
        date: LocalDate,
        observation: String,
        supplies: List<ActivitySupply>,
    ): SavedActivity {
        UUID.fromString(id)
        val cleanObservation = observation.trim()
        require(parcelId.isNotBlank()) { "Selecciona la parcela." }
        require(observation.length <= 500) { "La observación admite hasta 500 caracteres." }
        require(supplies.map { it.inventoryId }.toSet().size == supplies.size) { "No repitas el mismo insumo." }
        val activityDao = database.activityDao()
        val saved = database.withTransaction {
            require(database.parcelDao().hasParcel(parcelId)) { "La parcela ya no está disponible." }
            val existing = activityDao.findActivity(id)
            val cycleId = if (existing != null) {
                require(existing.parcelId == parcelId && existing.type == type.name &&
                    existing.date == date.toString() && existing.observation == cleanObservation) {
                    "Esta actividad ya fue registrada con otros datos."
                }
                existing.cycleId
            } else {
                val cycle = database.parcelDao().activeCycleId(parcelId)
                requireNotNull(cycle) { "La parcela no tiene un ciclo activo. Inicia un ciclo para registrar actividades." }
                val now = Instant.now().toString()
                activityDao.insertActivity(ActivityEntity(
                    id = id, parcelId = parcelId, cycleId = cycle, type = type.name, date = date.toString(),
                    observation = cleanObservation, createdAt = now, updatedAt = now,
                ))
                cycle
            }
            supplies.forEach { supply ->
                try {
                    registerMovement(
                        id = UUID.nameUUIDFromBytes("$id|${supply.inventoryId}".toByteArray()).toString(),
                        type = MovementType.CONSUMO, inventoryId = supply.inventoryId,
                        quantityText = supply.quantityText, date = date, observation = "",
                        parcelId = parcelId, activityId = id, notify = false,
                    )
                } catch (error: InsufficientStockException) {
                    throw ActivityStockException(supply.inventoryId, error.available)
                }
            }
            SavedActivity(id, type, cycleId, supplies.size)
        }
        onMovementSaved()
        return saved
    }

    companion object {
        const val RECENT_MOVEMENTS = 20
    }
}

