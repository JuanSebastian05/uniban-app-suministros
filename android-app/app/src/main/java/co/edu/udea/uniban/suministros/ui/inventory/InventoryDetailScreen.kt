package co.edu.udea.uniban.suministros.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.Quantity
import java.time.LocalDate

@Composable
fun InventoryDetailScreen(
    inventoryId: String,
    state: InventoryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNewEntry: () -> Unit,
) {
    val item = state.items.find { it.id == inventoryId }
    val movements = state.movements.filter { it.inventoryId == inventoryId }
    InventoryScaffold(
        title = item?.name ?: "Detalle del insumo",
        pendingCount = state.pendingCount,
        onBack = onBack,
        bottomBar = { BottomAction("+ Registrar entrada", item != null, onNewEntry) },
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
                item { Text("MOVIMIENTOS RECIENTES", style = MaterialTheme.typography.labelLarge) }
                if (movements.isEmpty()) item {
                    Text("Aún no hay movimientos. La existencia corresponde al saldo inicial.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(movements, key = { it.id }) { movement ->
                    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("+ Entrada · ${Quantity.format(movement.quantity)} ${item.unit}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall)
                            Text(displayDate(LocalDate.parse(movement.date)), style = MaterialTheme.typography.bodySmall)
                            if (movement.observation.isNotBlank()) Text(movement.observation)
                            Text("Pendiente de sincronización", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
    }
}

