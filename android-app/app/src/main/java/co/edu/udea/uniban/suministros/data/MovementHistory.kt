package co.edu.udea.uniban.suministros.data

import co.edu.udea.uniban.suministros.data.local.HistoryMovement
import co.edu.udea.uniban.suministros.data.local.InventoryBalance
import co.edu.udea.uniban.suministros.data.local.MovementType
import java.time.LocalDate

/** Filtros del historial (HU_07). Un campo nulo o vacío no filtra. Se pueden combinar. */
data class HistoryFilter(
    val inventoryId: String? = null,
    val types: Set<String> = emptySet(),
    val parcelId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    /** Verdadero si hay algún filtro aparte del insumo (el insumo lo fija la pantalla de origen). */
    val hasExtraFilters get() = types.isNotEmpty() || parcelId != null || from != null || to != null
}

/**
 * Resumen de un insumo según RT_10: inicial + entradas − consumos − pérdidas (± otros) = existencia.
 * [others] reúne los tipos que aún no se registran (SALIDA resta; AJUSTE se toma como suma con signo).
 */
data class HistorySummary(
    val unit: String,
    val initial: Float,
    val entries: Float,
    val consumptions: Float,
    val losses: Float,
    val others: Float,
    val expected: Float,
    val actual: Float,
) {
    /** Tolerancia de redondeo de Float; las cantidades tienen 2 decimales. */
    val matches get() = kotlin.math.abs(expected - actual) < 0.005f
}

object MovementHistory {
    fun filter(movements: List<HistoryMovement>, filter: HistoryFilter): List<HistoryMovement> =
        movements.filter { movement ->
            val date = LocalDate.parse(movement.date)
            (filter.inventoryId == null || movement.inventoryId == filter.inventoryId) &&
                (filter.types.isEmpty() || movement.type in filter.types) &&
                (filter.parcelId == null || movement.parcelId == filter.parcelId) &&
                (filter.from == null || !date.isBefore(filter.from)) &&
                (filter.to == null || !date.isAfter(filter.to))
        }

    /**
     * Resumen de un insumo calculado con **todos** sus movimientos (no con la lista filtrada), para
     * que la suma siempre se pueda comparar con la existencia.
     */
    fun summary(balance: InventoryBalance, movements: List<HistoryMovement>): HistorySummary {
        val own = movements.filter { it.inventoryId == balance.id }
        fun total(type: String) = own.filter { it.type == type }.sumOf { it.quantity.toDouble() }.toFloat()
        val entries = total(MovementType.ENTRADA)
        val consumptions = total(MovementType.CONSUMO)
        val losses = total(MovementType.PERDIDA)
        val others = total(MovementType.AJUSTE) - total(MovementType.SALIDA)
        return HistorySummary(
            unit = balance.unit,
            initial = balance.initialQuantity,
            entries = entries,
            consumptions = consumptions,
            losses = losses,
            others = others,
            expected = balance.initialQuantity + entries - consumptions - losses + others,
            actual = balance.quantity,
        )
    }
}
