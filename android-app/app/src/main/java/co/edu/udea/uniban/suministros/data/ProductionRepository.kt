package co.edu.udea.uniban.suministros.data

import androidx.room.withTransaction
import co.edu.udea.uniban.suministros.data.local.*
import co.edu.udea.uniban.suministros.data.remote.*
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

internal fun RecommendationDto.productionPlan(gson: Gson): ProductionPlanEntity? =
    if (cycleId.isNullOrBlank() || aforamientoId.isNullOrBlank()) null
    else ProductionPlanEntity(id, parcelId, period, cycleId, aforamientoId, generatedAt, gson.toJson(this))

internal fun packagingName(name: String): String = Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "")

data class ProductionPeriod(
    val reference: ProductionPlanEntity, val plan: RecommendationDto,
    val harvested: Int, val dispatched: Int,
) {
    val available get() = harvested - dispatched
    val remaining get() = (plan.volumeBunches - harvested).coerceAtLeast(0.0)
    val remainingDispatch get() = ((plan.plan?.estimatedLoadBunches ?: 0) - dispatched).coerceAtLeast(0)
}
data class ProductionOverview(
    val loading: Boolean = true, val periods: List<ProductionPeriod> = emptyList(),
    val harvests: List<HarvestEntity> = emptyList(), val dispatches: List<DispatchEntity> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
)
data class PackingLine(val name: String, val quantity: BigDecimal, val available: BigDecimal) {
    val shortage get() = (quantity - available).max(BigDecimal.ZERO)
}

interface ProductionSource {
    fun observe(parcelId: String): Flow<ProductionOverview>
    suspend fun refresh(parcelId: String): Boolean
    suspend fun harvest(id: String, planId: String, quantity: Int, date: LocalDate, observation: String, exceptionConfirmed: Boolean)
    suspend fun dispatch(id: String, planId: String, quantity: Int, at: OffsetDateTime, vehicle: String, order: Int, observation: String)
}

/** Balance de racimos independiente del inventario. Escritura local atómica e identidad estable. */
class ProductionRepository(
    private val database: InventoryDatabase, private val api: ProductionApi,
    private val ownerUid: String, private val onSaved: () -> Unit = {},
    private val idToken: suspend () -> String?,
) : ProductionSource {
    private val dao = database.productionDao()
    private val gson = Gson()

    override fun observe(parcelId: String) = combine(
        dao.plans(parcelId), dao.harvests(parcelId), dao.dispatches(parcelId), database.inventoryDao().observeInventory(),
    ) { plans, harvests, dispatches, inventory ->
        val periods = plans.groupBy { it.period }.values.mapNotNull { versions ->
            val firstHarvest = harvests.firstOrNull { it.period == versions.first().period }
            val reference = versions.firstOrNull { it.id == firstHarvest?.recommendationId } ?: versions.firstOrNull()
            reference?.let {
                ProductionPeriod(it, gson.fromJson(it.payloadJson, RecommendationDto::class.java),
                    harvests.filter { row -> row.period == it.period }.sumOf { row -> row.quantity },
                    dispatches.filter { row -> row.period == it.period }.sumOf { row -> row.quantity })
            }
        }.sortedByDescending { it.reference.period }
        ProductionOverview(false, periods, harvests, dispatches, inventory)
    }

    private suspend fun stage(parcelId: String, date: LocalDate): Pair<ProductionCycleEntity?, String?> {
        val cycle = dao.cycleAt(parcelId, date.toString()) ?: return null to null
        return cycle to if (cycle.endDate != null && cycle.endDate <= date.toString()) "DESCANSO"
            else dao.stageAt(cycle.id, date.toString()) ?: cycle.status
    }

    private suspend fun context(planId: String): Pair<ProductionPlanEntity, RecommendationDto> {
        val ref = requireNotNull(dao.plan(planId)) { "Actualiza la recomendación antes de registrar la operación." }
        val first = dao.periodHarvests(ref.parcelId, ref.period).firstOrNull()
        require(first == null || first.recommendationId == ref.id) { "Usa la recomendación de origen de este periodo." }
        return ref to gson.fromJson(ref.payloadJson, RecommendationDto::class.java)
    }

    override suspend fun harvest(id: String, planId: String, quantity: Int, date: LocalDate, observation: String, exceptionConfirmed: Boolean) {
        UUID.fromString(id)
        require(quantity > 0) { "Ingresa una cantidad entera positiva de racimos." }
        require(observation.length <= 500) { "La observación admite hasta 500 caracteres." }
        require(date <= LocalDate.now()) { "La cosecha real no puede tener fecha futura." }
        database.withTransaction {
            val (ref, plan) = context(planId)
            dao.harvest(id)?.let {
                require(it.recommendationId == planId && it.quantity == quantity && it.date == date.toString() &&
                    it.observation == observation.trim() && it.exceptionConfirmed == exceptionConfirmed) { "Este UUID ya identifica otra cosecha." }
                return@withTransaction
            }
            val (cycle, status) = stage(ref.parcelId, date)
            require(cycle?.id == ref.cycleId && status == "PRODUCCION") { "Solo se puede cosechar durante Producción." }
            val total = dao.periodHarvests(ref.parcelId, ref.period).sumOf { it.quantity.toLong() } + quantity
            require(total <= 1_000_000_000) { "La cantidad acumulada supera el límite permitido." }
            require(total.toBigDecimal() <= plan.volumeBunches.toBigDecimal() || exceptionConfirmed) {
                "La cosecha acumulada supera la estimación semanal. Confirma explícitamente la excepción."
            }
            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()
            database.activityDao().insertActivity(ActivityEntity(id, ref.parcelId, ref.cycleId, "COSECHA", date.toString(), observation.trim(), now, now))
            dao.insertHarvest(HarvestEntity(id, ref.parcelId, ref.cycleId, ref.id, ref.aforamientoId,
                ref.period, quantity, date.toString(), observation.trim(), exceptionConfirmed, now))
        }
        onSaved()
    }

    override suspend fun dispatch(id: String, planId: String, quantity: Int, at: OffsetDateTime, vehicle: String, order: Int, observation: String) {
        UUID.fromString(id)
        require(quantity > 0 && order > 0) { "La cantidad y el orden deben ser enteros positivos." }
        require(observation.length <= 500 && vehicle.length <= 120) { "Revisa la longitud de la observación o el vehículo." }
        require(!at.toInstant().isAfter(Instant.now())) { "El envío real no puede tener fecha y hora futuras." }
        database.withTransaction {
            val (ref, plan) = context(planId)
            dao.dispatch(id)?.let {
                require(it.recommendationId == planId && it.quantity == quantity && it.dispatchedAt == at.toString() &&
                    it.vehicleId == vehicle.trim() && it.order == order && it.observation == observation.trim()) { "Este UUID ya identifica otro despacho." }
                return@withTransaction
            }
            val date = at.atZoneSameInstant(ZoneId.of("America/Bogota")).toLocalDate()
            require(stage(ref.parcelId, date).second in listOf("PRODUCCION", "DESCANSO")) { "Solo se puede despachar en Producción o Descanso." }
            val harvests = dao.periodHarvests(ref.parcelId, ref.period)
            val shipped = dao.periodDispatches(ref.parcelId, ref.period).sumOf { it.quantity }
            val available = harvests.filter { it.date <= date.toString() }.sumOf { it.quantity } - shipped
            require(quantity <= available) { "Solo hay ${available.coerceAtLeast(0)} racimos disponibles para esa fecha." }
            val items = database.inventoryDao().observeInventory().first()
            val lines = packing(plan, shipped, quantity, items)
            val missing = lines.filter { it.shortage > BigDecimal.ZERO }
            require(missing.isEmpty()) { missing.joinToString("; ", prefix = "Registra una entrada de inventario. Faltan: ") { "${it.shortage} de ${it.name}" } }
            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()
            val movements = mutableListOf<MovementEntity>()
            val consumptions = mutableListOf<MovementDto>()
            lines.forEach { line ->
                var remaining = line.quantity
                items.filter { packagingName(it.category) == "empaque" && packagingName(it.name) == line.name && it.unit == "unidad" }
                    .sortedBy { it.id }.forEach { item ->
                        val used = remaining.min(item.quantity.toString().toBigDecimal())
                        if (used > BigDecimal.ZERO) {
                            val movementId = UUID.nameUUIDFromBytes("$id|${item.id}".toByteArray()).toString()
                            val movement = MovementEntity(movementId, item.id, used.toFloat(), date.toString(), "Empaque del despacho", now, now,
                                type = "CONSUMO", parcelId = ref.parcelId, cycleId = ref.cycleId, dispatchId = id)
                            val inventory = requireNotNull(database.inventoryDao().findSyncContext(item.id))
                            consumptions += MovementDto(movement.id, "CONSUMO", used, date.toString(), movement.observation, now, now,
                                InventoryDto(item.id, inventory.initialQuantity.toString().toBigDecimal().setScale(2, RoundingMode.HALF_UP),
                                    LocationDto(inventory.locationId, inventory.locationName, ProducerDto(inventory.producerId, inventory.producerName)),
                                    SupplyDto(inventory.supplyId, inventory.supplyName, inventory.supplyCategory, inventory.supplyUnit)),
                                parcelId = ref.parcelId, cycleId = ref.cycleId)
                            database.inventoryDao().updateQuantity(item.id, (item.quantity.toString().toBigDecimal() - used).toFloat())
                            movements += movement
                            remaining -= used
                        }
                    }
            }
            dao.insertDispatch(DispatchEntity(id, ref.parcelId, ref.cycleId, ref.id, ref.period, quantity,
                at.toString(), vehicle.trim(), order, observation.trim(), now, gson.toJson(consumptions)))
            movements.forEach { database.inventoryDao().insertMovement(it) }
        }
        onSaved()
    }

    override suspend fun refresh(parcelId: String): Boolean {
        if (ownerUid == StarterInventory.DEMO_OWNER) return true
        return try {
            val token = idToken() ?: return false
            val body = api.history("Bearer $token", parcelId).takeIf { it.isSuccessful }?.body() ?: return false
            require(body.harvests.all { it.parcelId == parcelId } && body.dispatches.all { it.parcelId == parcelId })
            database.withTransaction {
                body.recommendations.forEach { it.productionPlan(gson)?.let { ref -> dao.savePlan(ref) } }
                body.harvests.forEach { remote ->
                    val plan = requireNotNull(dao.plan(remote.recommendationId))
                    if (dao.harvest(remote.id) == null) {
                        if (database.activityDao().findActivity(remote.id) == null) database.activityDao().insertActivity(
                            ActivityEntity(remote.id, parcelId, remote.cycleId, "COSECHA", remote.date, remote.observation, remote.createdAt, remote.createdAt, "SYNCED"))
                        dao.insertHarvest(HarvestEntity(remote.id, parcelId, remote.cycleId, remote.recommendationId, remote.aforamientoId,
                            plan.period, remote.quantity, remote.date, remote.observation, remote.exceptionConfirmed, remote.createdAt, "SYNCED"))
                    } else require(dao.harvest(remote.id)!!.toDto().canonical() == remote.canonical()) { "Conflicto con una cosecha local." }
                    dao.harvestStatus(remote.id, "SYNCED", null)
                    dao.activityStatus(remote.id, "SYNCED", null)
                }
                body.dispatches.forEach { remote ->
                    val plan = requireNotNull(dao.plan(remote.recommendationId))
                    if (dao.dispatch(remote.id) == null) dao.insertDispatch(DispatchEntity(remote.id, parcelId, remote.cycleId, remote.recommendationId,
                        plan.period, remote.quantity, remote.dispatchedAt, remote.vehicleId, remote.order, remote.observation,
                        remote.createdAt, gson.toJson(remote.consumptions), "SYNCED"))
                    else require(dao.dispatch(remote.id)!!.toDto(gson).canonical() == remote.canonical()) { "Conflicto con un despacho local." }
                    dao.dispatchStatus(remote.id, "SYNCED", null)
                    dao.consumptionStatus(remote.id, "SYNCED", null)
                }
            }
            true
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { false }
    }

    companion object {
        fun packing(plan: RecommendationDto, shipped: Int, quantity: Int, items: List<InventoryItem>): List<PackingLine> {
            val load = requireNotNull(plan.plan?.estimatedLoadBunches) { "Actualiza el plan de despacho." }
            require(load > 0) { "El plan necesita una carga positiva para distribuir empaques." }
            val supplies = plan.supplies.orEmpty()
            require(supplies.map { packagingName(it.name) }.distinct().size == supplies.size) { "El plan repite empaques." }
            return supplies.map { supply ->
                val name = packagingName(supply.name)
                require(name in listOf("cajas", "carton", "bolsas")) { "El plan contiene un empaque desconocido." }
                fun target(count: Int) = supply.requiredQuantity.toBigDecimal()
                    .multiply(count.coerceAtMost(load).toBigDecimal()).divide(load.toBigDecimal(), 2, RoundingMode.HALF_UP)
                PackingLine(name, target(shipped + quantity) - target(shipped),
                    items.filter { packagingName(it.category) == "empaque" && packagingName(it.name) == name && it.unit == "unidad" }
                        .fold(BigDecimal.ZERO) { total, it -> total + it.quantity.toString().toBigDecimal() })
            }.filter { it.quantity > BigDecimal.ZERO }
        }
    }
}

internal fun HarvestEntity.toDto() = HarvestDto(id, parcelId, cycleId, recommendationId, aforamientoId, date, quantity, observation, exceptionConfirmed, createdAt)
internal fun DispatchEntity.toDto(gson: Gson) = DispatchDto(id, parcelId, cycleId, recommendationId, dispatchedAt, quantity, vehicleId, order,
    observation, createdAt, gson.fromJson(consumptionsJson, Array<MovementDto>::class.java).toList())

private fun timestamp(text: String) = Instant.parse(text).truncatedTo(ChronoUnit.MILLIS).toString()
private fun HarvestDto.canonical() = copy(createdAt = timestamp(createdAt))
private fun DispatchDto.canonical() = copy(dispatchedAt = OffsetDateTime.parse(dispatchedAt).toInstant().toString(),
    createdAt = timestamp(createdAt), consumptions = consumptions.sortedBy { it.id }.map {
        it.copy(quantity = it.quantity.stripTrailingZeros(), createdAt = timestamp(it.createdAt), updatedAt = timestamp(it.updatedAt),
            inventory = it.inventory.copy(initialQuantity = it.inventory.initialQuantity.stripTrailingZeros()))
    })
