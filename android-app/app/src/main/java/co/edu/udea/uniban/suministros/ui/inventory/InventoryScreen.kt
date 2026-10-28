package co.edu.udea.uniban.suministros.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.R
import co.edu.udea.uniban.suministros.data.Quantity

@Composable
fun InventoryScreen(
    state: InventoryUiState,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    onNewEntry: () -> Unit,
    onLogout: () -> Unit,
    // Barra de pestañas (Parcelas / Inventario); nula si la app no la muestra.
    navigationBar: (@Composable () -> Unit)? = null,
    // HU_07: abre el historial de todos los insumos; nulo si la app no lo muestra.
    onHistory: (() -> Unit)? = null,
    // HU_10: abre el centro de estado de sincronización.
    onSyncStatus: (() -> Unit)? = null,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    if (confirmLogout) LogoutDialog(onConfirm = { confirmLogout = false; onLogout() }, onDismiss = { confirmLogout = false })
    val categories = state.items.map { it.category }.distinct().sortedBy { it.lowercase() }
    val filtered = state.items.filter {
        it.name.contains(search.trim(), ignoreCase = true) && (category == null || it.category == category)
    }
    InventoryScaffold(
        title = "Inventario",
        pendingCount = state.pendingCount,
        extraActions = {
            if (onHistory != null) TextButton(
                onClick = onHistory,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
            ) { Text("Historial") }
            IconButton(onClick = { confirmLogout = true }) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.logout_action))
            }
        },
        bottomBar = {
            Column {
                BottomAction(
                    "+ Registrar movimiento",
                    enabled = !state.loading && state.error == null && state.items.isNotEmpty(),
                    applyNavigationBarsPadding = navigationBar == null,
                    onClick = onNewEntry,
                )
                navigationBar?.invoke()
            }
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Categoría", style = MaterialTheme.typography.labelMedium)
                        Box {
                            OutlinedButton(
                                onClick = { categoryMenuOpen = true },
                                modifier = Modifier.fillMaxWidth().testTag("inventory-category-filter"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(category ?: "Todas las categorías", Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Elegir categoría")
                            }
                            DropdownMenu(
                                expanded = categoryMenuOpen,
                                onDismissRequest = { categoryMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Todas las categorías") },
                                    onClick = { category = null; categoryMenuOpen = false },
                                )
                                categories.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = { category = option; categoryMenuOpen = false },
                                    )
                                }
                            }
                        }
                    }
                }
                if (onSyncStatus != null) item {
                    OutlinedCard(
                        onClick = onSyncStatus,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Estado de sincronización", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (state.pendingCount == 0) "Consulta el estado de tus registros"
                                    else "${state.pendingCount} ${if (state.pendingCount == 1) "registro pendiente o por revisar" else "registros pendientes o por revisar"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
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


@Composable
internal fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logout_dialog_title)) },
        text = { Text(stringResource(R.string.logout_dialog_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.logout_dialog_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.logout_dialog_cancel)) } },
    )
}
