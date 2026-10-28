package co.edu.udea.uniban.suministros.ui.activities

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.ParcelValidation
import co.edu.udea.uniban.suministros.data.Quantity
import co.edu.udea.uniban.suministros.data.local.ActivityType
import co.edu.udea.uniban.suministros.data.local.InventoryItem
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import co.edu.udea.uniban.suministros.ui.inventory.BottomAction
import co.edu.udea.uniban.suministros.ui.inventory.InventoryScaffold
import co.edu.udea.uniban.suministros.ui.inventory.InventoryUiState
import co.edu.udea.uniban.suministros.ui.inventory.LoadingOrError
import co.edu.udea.uniban.suministros.ui.inventory.displayDate
import co.edu.udea.uniban.suministros.ui.theme.UnibanPending
import co.edu.udea.uniban.suministros.ui.theme.UnibanPendingText
import java.time.LocalDate

/**
 * «Registrar actividad» (HU_08). La actividad exige una parcela con ciclo asociado; los insumos usados se
 * guardan como consumos en el mismo flujo. Al guardar con éxito llama a [onDone].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityFormScreen(
    inventoryState: InventoryUiState,
    parcels: List<ParcelSummary>,
    viewModel: ActivityFormViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRetryInventory: () -> Unit,
) {
    val state = viewModel.state
    LaunchedEffect(state.saved) { if (state.saved != null) onDone() }
    BackHandler(enabled = state.saving) {}
    val context = LocalContext.current
    val editable = !state.saving && state.saved == null
    val ready = !inventoryState.loading && inventoryState.error == null
    val selectedParcel = parcels.find { it.id == state.parcelId }
    val selectedStatus = selectedParcel?.currentStatus ?: selectedParcel?.cycleStatus
    val stageWarning = state.type?.stageWarning(selectedStatus)
    var parcelExpanded by remember { mutableStateOf(false) }

    InventoryScaffold(
        title = "Registrar actividad",
        pendingCount = inventoryState.pendingCount,
        onBack = onBack,
        backEnabled = !state.saving,
        bottomBar = {
            BottomAction(
                label = if (state.saving) "Guardando…" else "Guardar actividad",
                enabled = editable && ready,
                onClick = viewModel::save,
            )
        },
    ) { padding ->
        if (!ready) {
            LoadingOrError(inventoryState.error, onRetryInventory, Modifier.padding(padding))
            return@InventoryScaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = parcelExpanded,
                onExpandedChange = { if (editable && parcels.isNotEmpty()) parcelExpanded = !parcelExpanded },
            ) {
                OutlinedTextField(
                    value = selectedParcel?.name ?: "", onValueChange = {}, readOnly = true,
                    enabled = editable && parcels.isNotEmpty(), label = { Text("Parcela") },
                    placeholder = { Text("Seleccionar parcela") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(parcelExpanded) },
                    isError = state.parcelError != null || (selectedParcel != null && selectedParcel.activityCycleId == null),
                    supportingText = {
                        Text(state.parcelError ?: when {
                            parcels.isEmpty() -> "Aún no tienes parcelas. Regístralas en la pestaña Parcelas."
                            selectedParcel == null -> "¿Dónde se hizo la actividad?"
                            selectedParcel.activityCycleId == null ->
                                "Esta parcela aún no tiene ciclos para asociar actividades. Inicia uno primero."
                            selectedParcel.cycleId == null ->
                                "La parcela está en descanso; la actividad se asociará al último ciclo."
                            else -> "Se asocia al ciclo activo (inicio ${ParcelValidation.formatDate(selectedParcel.cycleStartDate.orEmpty())})."
                        })
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = editable)
                        .fillMaxWidth().testTag("activity-parcel"),
                )
                ExposedDropdownMenu(
                    expanded = parcelExpanded, onDismissRequest = { parcelExpanded = false },
                ) {
                    parcels.forEach { parcel ->
                        DropdownMenuItem(
                            text = { Text(parcel.name) },
                            onClick = { viewModel.selectParcel(parcel.id); parcelExpanded = false },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tipo de actividad", style = MaterialTheme.typography.labelMedium)
                ActivityType.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { type ->
                            FilterChip(
                                selected = state.type == type,
                                onClick = { viewModel.selectType(type) },
                                label = { Text(type.label) },
                                enabled = editable,
                                modifier = Modifier.testTag("activity-type-${type.name}"),
                            )
                        }
                    }
                }
                state.typeError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                stageWarning?.let {
                    Surface(
                        color = UnibanPending,
                        contentColor = UnibanPendingText,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("activity-stage-warning"),
                    ) {
                        Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

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
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                supportingText = { Text("${state.observation.length}/500") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("activity-observation"),
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Insumos usados (opcional)", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Cada insumo se registra como un consumo de esta parcela y se descuenta del inventario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.rows.forEach { row ->
                    SupplyRowEditor(
                        row = row, items = inventoryState.items, editable = editable,
                        onItem = { viewModel.selectRowItem(row.key, it) },
                        onQuantity = { viewModel.changeRowQuantity(row.key, it) },
                        onRemove = { viewModel.removeRow(row.key) },
                    )
                }
                TextButton(
                    onClick = viewModel::addRow,
                    enabled = editable && inventoryState.items.isNotEmpty(),
                    modifier = Modifier.testTag("add-supply"),
                ) { Text("+ Agregar insumo") }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "La actividad se guardará en este dispositivo y quedará pendiente de sincronización.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplyRowEditor(
    row: SupplyRow,
    items: List<InventoryItem>,
    editable: Boolean,
    onItem: (String) -> Unit,
    onQuantity: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = items.find { it.id == row.inventoryId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("supply-row-${row.key}")) {
        Row(verticalAlignment = Alignment.Top) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (editable) expanded = !expanded },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = selected?.name ?: "", onValueChange = {}, readOnly = true,
                    enabled = editable, label = { Text("Insumo") },
                    placeholder = { Text("Seleccionar insumo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    isError = row.itemError != null,
                    supportingText = {
                        Text(row.itemError ?: selected?.let { "Stock: ${Quantity.format(it.quantity)} ${it.unit}" } ?: "")
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = editable)
                        .fillMaxWidth().testTag("supply-item-${row.key}"),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = { onItem(item.id); expanded = false },
                        )
                    }
                }
            }
            IconButton(onClick = onRemove, enabled = editable, modifier = Modifier.testTag("remove-supply-${row.key}")) {
                Icon(Icons.Default.Close, contentDescription = "Quitar insumo")
            }
        }
        OutlinedTextField(
            value = row.quantity, onValueChange = onQuantity,
            label = { Text("Cantidad usada") }, placeholder = { Text("0,00") },
            suffix = { Text(selected?.unit ?: "") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, enabled = editable,
            isError = row.quantityError != null,
            supportingText = { Text(row.quantityError ?: "Mayor que cero. Máximo dos decimales.") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("supply-quantity-${row.key}"),
        )
    }
}
