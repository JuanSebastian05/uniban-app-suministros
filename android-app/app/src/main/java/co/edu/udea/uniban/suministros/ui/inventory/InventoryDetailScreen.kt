package co.edu.udea.uniban.suministros.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.Quantity
import co.edu.udea.uniban.suministros.data.local.LossReason
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.data.local.SyncStatus
import java.time.LocalDate

@Composable
fun InventoryDetailScreen(
    inventoryId: String,
    state: InventoryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNewEntry: () -> Unit,
    // HU_07: abre el historial completo del insumo; nulo si la app no lo muestra.
    onHistory: (() -> Unit)? = null,
) {
    val item = state.items.find { it.id == inventoryId }
    val movements = if (state.detailId == inventoryId) state.detailMovements else emptyList()
    InventoryScaffold(
        title = item?.name ?: "Detalle del insumo",
        pendingCount = state.pendingCount,
        onBack = onBack,
        bottomBar = { BottomAction("+ Registrar movimiento", item != null, onNewEntry) },
    ) { padding ->
        if (state.loading || state.error != null) {
            LoadingOrError(state.error, onRetry, Modifier.padding(padding))
        } else if (item == null) {
            Text("El insumo ya no está disponible.", Modifier.padding(padding).padding(24.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${item.category} · ${item.unit}", style = MaterialTheme.typography.labelLarge)
                            Text(item.location)
                            Text("Stock actual", style = MaterialTheme.typography.labelMedium)
                            Text("${Quantity.format(item.quantity)} ${item.unit}", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("MOVIMIENTOS RECIENTES", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (onHistory != null) TextButton(onClick = onHistory) { Text("Ver historial") }
                    }
                }
                if (movements.isEmpty()) item {
                    Text("Aún no hay movimientos. La existencia corresponde al saldo inicial.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(movements, key = { it.id }) { movement ->
                    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(movementHeadline(movement.type, movement.quantity, item.unit),
                                color = movementColor(movement.type),
                                style = MaterialTheme.typography.titleSmall)
                            Text(displayDate(LocalDate.parse(movement.date)), style = MaterialTheme.typography.bodySmall)
                            LossReason.fromCode(movement.lossReason)?.let {
                                Text("Motivo: ${it.label}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (movement.observation.isNotBlank()) Text(movement.observation)
                            Text(syncLabel(movement.syncStatus), style = MaterialTheme.typography.labelSmall,
                                color = if (movement.syncStatus == SyncStatus.ERROR) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
    }
}

/** Símbolos y colores por tipo según los mockups del equipo; el tipo se lee del movimiento. */
internal fun movementHeadline(type: String, quantity: Float, unit: String) =
    "${movementSymbol(type)} ${movementLabel(type)} · ${Quantity.format(quantity)} $unit"

/** Un ajuste puede sumar o restar, por eso «±» y no el signo de RT_10. */
internal fun movementSymbol(type: String) = when (type) {
    MovementType.ENTRADA -> "+"
    MovementType.AJUSTE -> "±"
    MovementType.SALIDA, MovementType.CONSUMO, MovementType.PERDIDA -> "−"
    else -> "·"
}

@Composable
internal fun movementColor(type: String) = when (type) {
    MovementType.PERDIDA -> MaterialTheme.colorScheme.error
    MovementType.SALIDA, MovementType.CONSUMO -> MaterialTheme.colorScheme.onTertiaryContainer
    MovementType.AJUSTE -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.primary
}

internal fun movementLabel(type: String) = when (type) {
    MovementType.ENTRADA -> "Entrada"
    MovementType.SALIDA -> "Salida"
    MovementType.CONSUMO -> "Consumo"
    MovementType.PERDIDA -> "Pérdida"
    MovementType.AJUSTE -> "Ajuste"
    else -> type
}

internal fun syncLabel(status: String) = when (status) {
    SyncStatus.SYNCED -> "Sincronizado"
    SyncStatus.SYNCING -> "Sincronizando…"
    SyncStatus.ERROR -> "Error al sincronizar · se reintentará"
    else -> "Pendiente de sincronización"
}
