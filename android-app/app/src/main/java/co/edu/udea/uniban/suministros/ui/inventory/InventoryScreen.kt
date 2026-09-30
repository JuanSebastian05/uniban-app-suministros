package co.edu.udea.uniban.suministros.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.Quantity

@Composable
fun InventoryScreen(
    state: InventoryUiState,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    onNewEntry: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = state.items.filter { it.name.contains(search.trim(), ignoreCase = true) }
    InventoryScaffold(
        title = "Inventario",
        pendingCount = state.pendingCount,
        bottomBar = {
            BottomAction("+ Registrar entrada", !state.loading && state.error == null && state.items.isNotEmpty(), onNewEntry)
        },
    ) { padding ->
        if (state.loading || state.error != null) {
            LoadingOrError(state.error, onRetry, Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("inventory-list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        placeholder = { Text("Buscar insumo…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text("Inventario principal · ${filtered.size} insumos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (filtered.isEmpty()) item { Text("No se encontraron insumos.") }
                items(filtered, key = { it.id }) { item ->
                    OutlinedCard(
                        onClick = { onSelect(item.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Text(item.category, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(item.location, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${Quantity.format(item.quantity)} ${item.unit}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleSmall)
                                Text("disponibles", style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

