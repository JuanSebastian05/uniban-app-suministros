package co.edu.udea.uniban.suministros.ui.inventory

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.Quantity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    inventoryState: InventoryUiState,
    viewModel: EntryViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRetryInventory: () -> Unit,
) {
    val state = viewModel.state
    state.saved?.let {
        EntrySavedScreen(it, onDone)
        return
    }
    BackHandler(enabled = state.saving) {}
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showCatalogInfo by rememberSaveable { mutableStateOf(false) }
    val selected = inventoryState.items.find { it.id == state.inventoryId }
    val ready = !state.restoring && !inventoryState.loading && inventoryState.error == null
    val editable = ready && !state.saving

    InventoryScaffold(
        title = "Registrar entrada",
        pendingCount = inventoryState.pendingCount,
        onBack = onBack,
        backEnabled = !state.saving,
        bottomBar = {
            BottomAction(
                label = if (state.saving) "Guardando…" else "Guardar entrada",
                enabled = editable && inventoryState.items.isNotEmpty(),
                onClick = viewModel::save,
            )
        },
    ) { padding ->
        if (!ready) {
            LoadingOrError(
                error = state.error ?: inventoryState.error,
                onRetry = {
                    if (state.restoring) viewModel.restore()
                    if (inventoryState.error != null) onRetryInventory()
                },
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).imePadding()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { if (editable) expanded = !expanded },
                    ) {
                        OutlinedTextField(
                            value = selected?.name ?: "", onValueChange = {}, readOnly = true,
                            enabled = editable, label = { Text("Insumo") },
                            placeholder = { Text("Seleccionar insumo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            isError = state.itemError != null,
                            supportingText = state.itemError?.let { message -> { Text(message) } },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = editable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            inventoryState.items.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.name) },
                                    onClick = { viewModel.selectItem(item.id); expanded = false },
                                )
                            }
                        }
                    }
                    selected?.let {
                        Text(
                            "Stock actual: ${Quantity.format(it.quantity)} ${it.unit}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    TextButton(onClick = { showCatalogInfo = true }, enabled = editable) {
                        Text("¿No encuentras el insumo?")
                    }
                }
                Column {
                    Text("Tipo de movimiento", style = MaterialTheme.typography.labelMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Entrada", Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
                }
                OutlinedTextField(
                    value = state.quantity,
                    onValueChange = viewModel::changeQuantity,
                    label = { Text("Cantidad") },
                    placeholder = { Text("0,00") },
                    suffix = { Text(selected?.unit ?: "") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, enabled = editable,
                    isError = state.quantityError != null,
                    supportingText = { Text(state.quantityError ?: "Mayor que cero. Máximo dos decimales.") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fecha", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day -> viewModel.changeDate(LocalDate.of(year, month + 1, day)) },
                                state.date.year, state.date.monthValue - 1, state.date.dayOfMonth,
                            ).show()
                        },
                        enabled = editable, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(displayDate(state.date), Modifier.weight(1f))
                        Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha")
                    }
                }
                OutlinedTextField(
                    value = state.observation, onValueChange = viewModel::changeObservation,
                    label = { Text("Observación (opcional)") },
                    placeholder = { Text("Observaciones adicionales…") },
                    enabled = editable, minLines = 2, maxLines = 5,
                    supportingText = { Text("${state.observation.length}/500") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "La entrada se guardará en este dispositivo y quedará pendiente de sincronización.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (showCatalogInfo) {
        AlertDialog(
            onDismissRequest = { showCatalogInfo = false },
            title = { Text("Catálogo disponible") },
            text = { Text("En esta demostración puedes registrar entradas de los insumos del catálogo. " +
                "Vuelve al selector para elegir uno existente. La creación de insumos aún no está disponible.") },
            confirmButton = {
                TextButton(onClick = { showCatalogInfo = false; expanded = true }) { Text("Elegir un insumo") }
            },
        )
    }
}

