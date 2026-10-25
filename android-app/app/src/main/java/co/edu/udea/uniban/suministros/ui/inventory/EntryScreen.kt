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
import co.edu.udea.uniban.suministros.data.ParcelValidation
import co.edu.udea.uniban.suministros.data.Quantity
import co.edu.udea.uniban.suministros.data.local.AdjustDirection
import co.edu.udea.uniban.suministros.data.local.LossReason
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    inventoryState: InventoryUiState,
    viewModel: EntryViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRetryInventory: () -> Unit,
    // HU_05: parcelas de la cuenta para asociar un consumo (copia local; funciona sin conexión).
    parcels: List<ParcelSummary> = emptyList(),
) {
    val state = viewModel.state
    state.saved?.let {
        EntrySavedScreen(it, onDone)
        return
    }
    BackHandler(enabled = state.saving) {}
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var parcelExpanded by remember { mutableStateOf(false) }
    val selectedParcel = parcels.find { it.id == state.parcelId }
    val typeLabel = MovementType.label(state.type)
    var showCatalogInfo by rememberSaveable { mutableStateOf(false) }
    val selected = inventoryState.items.find { it.id == state.inventoryId }
    val ready = !state.restoring && !inventoryState.loading && inventoryState.error == null
    val editable = ready && !state.saving

    InventoryScaffold(
        title = "Registrar ${typeLabel.lowercase()}",
        pendingCount = inventoryState.pendingCount,
        onBack = onBack,
        backEnabled = !state.saving,
        bottomBar = {
            BottomAction(
                label = if (state.saving) "Guardando…" else "Guardar ${typeLabel.lowercase()}",
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
                        // De dónde sale o a dónde entra el insumo: el stock es de la bodega del
                        // productor (ubicación de inventario), no de la parcela.
                        Text(
                            "${when (state.type) {
                                MovementType.ENTRADA -> "Entra a"
                                MovementType.AJUSTE -> "Ajusta"
                                else -> "Sale de"
                            }}: ${it.location}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "Stock actual: ${Quantity.format(it.quantity)} ${it.unit}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { showCatalogInfo = true }, enabled = editable) {
                        Text("¿No encuentras el insumo?")
                    }
                }
                Column {
                    Text("Tipo de movimiento", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MovementType.REGISTRABLE.forEach { type ->
                            FilterChip(
                                selected = state.type == type,
                                onClick = { viewModel.changeType(type) },
                                label = { Text(MovementType.label(type)) },
                                enabled = editable,
                            )
                        }
                    }
                }
                if (state.type == MovementType.CONSUMO) {
                    Column {
                        ExposedDropdownMenuBox(
                            expanded = parcelExpanded,
                            onExpandedChange = { if (editable && parcels.isNotEmpty()) parcelExpanded = !parcelExpanded },
                        ) {
                            OutlinedTextField(
                                value = selectedParcel?.name ?: "", onValueChange = {}, readOnly = true,
                                enabled = editable && parcels.isNotEmpty(), label = { Text("Parcela donde se usó") },
                                placeholder = { Text("Seleccionar parcela") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(parcelExpanded) },
                                isError = state.parcelError != null,
                                supportingText = {
                                    Text(state.parcelError ?: when {
                                        parcels.isEmpty() -> "Aún no tienes parcelas. Regístralas en la pestaña Parcelas."
                                        selectedParcel == null -> "¿Dónde se usó el insumo?"
                                        selectedParcel.cycleId == null -> "Sin ciclo activo: el consumo se asocia solo a la parcela."
                                        else -> "Se asocia al ciclo activo (inicio ${ParcelValidation.formatDate(selectedParcel.cycleStartDate.orEmpty())})."
                                    })
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = editable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = parcelExpanded, onDismissRequest = { parcelExpanded = false }) {
                                parcels.forEach { parcel ->
                                    DropdownMenuItem(
                                        text = { Text(parcel.name) },
                                        onClick = { viewModel.selectParcel(parcel.id); parcelExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.type == MovementType.PERDIDA) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Motivo de la pérdida", style = MaterialTheme.typography.labelMedium)
                        LossReason.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { reason ->
                                    FilterChip(
                                        selected = state.lossReason == reason,
                                        onClick = { viewModel.selectLossReason(reason) },
                                        label = { Text(reason.label) },
                                        enabled = editable,
                                    )
                                }
                            }
                        }
                        state.reasonError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (state.type == MovementType.AJUSTE) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sentido del ajuste", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdjustDirection.entries.forEach { direction ->
                                FilterChip(
                                    selected = state.adjustDirection == direction,
                                    onClick = { viewModel.selectAdjustDirection(direction) },
                                    label = { Text("${direction.symbol} ${direction.label}") },
                                    enabled = editable,
                                )
                            }
                        }
                        Text(
                            "Un ajuste corrige la existencia sin borrar movimientos anteriores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.reasonError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
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
                    label = {
                        Text(if (state.type == MovementType.AJUSTE) "Motivo del ajuste (obligatorio)"
                        else if (state.type == MovementType.PERDIDA && state.lossReason == LossReason.OTRO)
                            "Observación (obligatoria)" else "Observación (opcional)")
                    },
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
                    if (state.type == MovementType.ENTRADA)
                        "La entrada se guardará en este dispositivo y quedará pendiente de sincronización."
                    else if (state.type == MovementType.AJUSTE)
                        "El ajuste se guardará en este dispositivo, corregirá la existencia y quedará pendiente de sincronización."
                    else "${if (state.type == MovementType.PERDIDA) "La pérdida" else "El consumo"} se guardará en " +
                        "este dispositivo, descontará la existencia y quedará pendiente de sincronización.",
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

