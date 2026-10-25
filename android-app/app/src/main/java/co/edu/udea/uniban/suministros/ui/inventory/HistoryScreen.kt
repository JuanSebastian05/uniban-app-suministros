package co.edu.udea.uniban.suministros.ui.inventory

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.HistorySummary
import co.edu.udea.uniban.suministros.data.Quantity
import co.edu.udea.uniban.suministros.data.local.HistoryMovement
import co.edu.udea.uniban.suministros.data.local.LossReason
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.data.local.SyncStatus
import java.time.LocalDate

/**
 * Historial y trazabilidad (HU_07). Lista cronológica (más reciente primero) con filtros por insumo,
 * tipo, parcela y rango de fechas. El resumen RT_10 aparece solo con un insumo seleccionado.
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectSupply: (String?) -> Unit,
    onToggleType: (String) -> Unit,
    onSelectParcel: (String?) -> Unit,
    onChangeFrom: (LocalDate?) -> Unit,
    onChangeTo: (LocalDate?) -> Unit,
    onClearFilters: () -> Unit,
) {
    val supply = state.selectedSupply
    InventoryScaffold(
        title = supply?.let { "Historial · ${it.name}" } ?: "Historial",
        pendingCount = state.pendingCount,
        onBack = onBack,
    ) { padding ->
        if (state.loading || state.error != null) {
            LoadingOrError(state.error, onRetry, Modifier.padding(padding))
            return@InventoryScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("history-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Choice(
                    label = "Insumo",
                    value = supply?.name ?: "Todos los insumos",
                    options = listOf<Pair<String?, String>>(null to "Todos los insumos") + state.supplies.map { it.id to it.name },
                    onSelect = onSelectSupply,
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MovementType.REGISTRABLE.forEach { type ->
                        FilterChip(
                            selected = type in state.filter.types,
                            onClick = { onToggleType(type) },
                            label = { Text(movementLabel(type)) },
                        )
                    }
                }
            }
            if (state.parcels.isNotEmpty()) item {
                Choice(
                    label = "Parcela",
                    value = state.parcels.find { it.id == state.filter.parcelId }?.name ?: "Todas las parcelas",
                    options = listOf<Pair<String?, String>>(null to "Todas las parcelas") + state.parcels.map { it.id to it.name },
                    onSelect = onSelectParcel,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Desde", state.filter.from, onChangeFrom, Modifier.weight(1f))
                    DateField("Hasta", state.filter.to, onChangeTo, Modifier.weight(1f))
                }
            }
            if (state.filter.hasExtraFilters) item {
                TextButton(onClick = onClearFilters) { Text("Quitar filtros") }
            }
            state.summary?.let { summary -> item { SummaryCard(summary) } }
            item {
                Text(
                    "${state.movements.size} ${if (state.movements.size == 1) "movimiento" else "movimientos"} · " +
                        "registrados en este dispositivo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.movements.isEmpty()) item {
                Text(
                    if (state.filter.hasExtraFilters) "No hay movimientos con estos filtros."
                    else "Aún no hay movimientos registrados.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.movements, key = { it.id }) { movement ->
                HistoryCard(movement, showSupply = supply == null)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: HistorySummary) {
    val unit = summary.unit
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("history-summary"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("RESUMEN DEL INSUMO", style = MaterialTheme.typography.labelLarge)
            SummaryRow("Saldo inicial", Quantity.format(summary.initial), unit)
            SummaryRow("+ Entradas", Quantity.format(summary.entries), unit)
            SummaryRow("− Consumos", Quantity.format(summary.consumptions), unit)
            SummaryRow("− Pérdidas", Quantity.format(summary.losses), unit)
            if (summary.others != 0f) SummaryRow("± Otros", Quantity.format(summary.others), unit)
            HorizontalDivider()
            SummaryRow("= Existencia calculada", Quantity.format(summary.expected), unit)
            SummaryRow("Existencia actual", Quantity.format(summary.actual), unit)
            Text(
                if (summary.matches) "✓ El historial cuadra con la existencia."
                else "⚠ El historial no cuadra con la existencia de este dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = if (summary.matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(
                "Calculado con todos los movimientos del insumo, sin aplicar los filtros.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, unit: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$value $unit", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HistoryCard(movement: HistoryMovement, showSupply: Boolean) {
    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showSupply) Text(movement.supplyName, style = MaterialTheme.typography.labelLarge)
            Text(
                movementHeadline(movement.type, movement.quantity, movement.unit, movement.adjustDirection),
                color = movementColor(movement.type),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(displayDate(LocalDate.parse(movement.date)), style = MaterialTheme.typography.bodySmall)
            if (movement.parcelId != null) {
                val cycle = when {
                    movement.cycleId == null -> " · Sin ciclo"
                    !movement.cycleActive -> " · Ciclo anterior"
                    else -> ""
                }
                Text("Parcela: ${movement.parcelName ?: "no disponible"}$cycle", style = MaterialTheme.typography.bodySmall)
            }
            LossReason.fromCode(movement.lossReason)?.let {
                Text("Motivo: ${it.label}", style = MaterialTheme.typography.bodySmall)
            }
            if (movement.observation.isNotBlank()) Text(movement.observation)
            Text(
                syncLabel(movement.syncStatus), style = MaterialTheme.typography.labelSmall,
                color = if (movement.syncStatus == SyncStatus.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun Choice(
    label: String,
    value: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(
                onClick = { open = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(value, Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Elegir $label")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelect(id) })
                }
            }
        }
    }
}

@Composable
private fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    val initial = date ?: LocalDate.now()
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day)) },
                        initial.year, initial.monthValue - 1, initial.dayOfMonth,
                    ).show()
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(date?.let(::displayDate) ?: "Cualquiera", maxLines = 1) }
            if (date != null) TextButton(onClick = { onChange(null) }) { Text("✕") }
        }
    }
}
