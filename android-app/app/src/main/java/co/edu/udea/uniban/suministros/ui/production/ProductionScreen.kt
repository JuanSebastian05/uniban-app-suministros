package co.edu.udea.uniban.suministros.ui.production

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.*
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import co.edu.udea.uniban.suministros.ui.inventory.InventoryScaffold
import co.edu.udea.uniban.suministros.ui.inventory.syncLabel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionScreen(vm: ProductionViewModel, parcels: List<ParcelSummary>, onBack: () -> Unit,
                     onParcel: (String) -> Unit, onEntry: () -> Unit) {
    val data by vm.overview.collectAsState()
    val parcelId by vm.parcelId.collectAsState()
    val period = data.periods.firstOrNull { it.reference.id == vm.planId } ?: data.periods.firstOrNull()
    LaunchedEffect(period?.reference?.id) { period?.let { if (vm.planId != it.reference.id) vm.selectPlan(it) } }
    val form = vm.form
    val number = remember { DecimalFormat("#,##0.##") }
    BackHandler(enabled = vm.saving) {}
    InventoryScaffold(title = "Cosechas y despachos", pendingCount = 0, onBack = onBack, backEnabled = !vm.saving) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
            .testTag("production-screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            var parcelsOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(parcelsOpen, onExpandedChange = { if (!vm.saving) parcelsOpen = !parcelsOpen }) {
                OutlinedTextField(parcels.firstOrNull { it.id == parcelId }?.name.orEmpty(), {}, label = { Text("Parcela") },
                    readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(parcelsOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(parcelsOpen, onDismissRequest = { parcelsOpen = false }) {
                    parcels.forEach { parcel -> DropdownMenuItem(text = { Text(parcel.name) }, onClick = {
                        vm.selectParcel(parcel.id); parcelsOpen = false
                    }) }
                }
            }
            if (parcels.isEmpty()) Text("Primero registra una parcela y su ciclo productivo.")
            if (parcelId.isNotBlank()) {
                TextButton(onClick = vm::refresh, enabled = !vm.refreshing && !vm.saving) {
                    Text(if (vm.refreshing) "Actualizando avance…" else "Actualizar avance")
                }
                if (data.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (period == null) {
                    Text("Necesitas un aforamiento y una recomendación con su ciclo de origen para registrar cosechas.")
                    OutlinedButton(onClick = { onParcel(parcelId) }) { Text("Consultar aforamiento y recomendación") }
                }
            }
            vm.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("production-notice")) }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("production-error")) }
            if (period != null) {
                var plansOpen by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(plansOpen, onExpandedChange = { if (!vm.saving) plansOpen = !plansOpen }) {
                    OutlinedTextField(period.reference.period, {}, label = { Text("Periodo de origen") }, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(plansOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(plansOpen, onDismissRequest = { plansOpen = false }) {
                        data.periods.forEach { option -> DropdownMenuItem(text = { Text(option.reference.period) }, onClick = {
                            vm.selectPlan(option); plansOpen = false
                        }) }
                    }
                }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Plan y avance real", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Estimación semanal: ${number.format(period.plan.volumeBunches)} racimos")
                        Text("Carga entera del plan: ${period.plan.plan?.estimatedLoadBunches ?: "No disponible"} racimos")
                        HorizontalDivider()
                        Text("Cosechados: ${period.harvested} · Despachados: ${period.dispatched}")
                        Text("Disponibles: ${period.available} racimos", fontWeight = FontWeight.Bold)
                        Text("Pendiente de cosechar: ${number.format(period.remaining)} racimos")
                        Text("Pendiente del plan de despacho: ${period.remainingDispatch} racimos")
                        runCatching { ProductionRepository.packing(period.plan, period.dispatched, period.remainingDispatch, data.inventory) }
                            .getOrNull()?.forEach { Text("Empaque pendiente del plan: ${it.name} · ${it.quantity} unidades", style = MaterialTheme.typography.bodySmall) }
                        Text("El aforamiento es una estimación de plantas y no se descuenta.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(form.mode == "COSECHA", onClick = { vm.selectPlan(period, "COSECHA") }, label = { Text("Cosecha") }, enabled = !vm.saving)
                    FilterChip(form.mode == "DESPACHO", onClick = { vm.selectPlan(period, "DESPACHO") }, label = { Text("Despacho") }, enabled = !vm.saving)
                }
                Text(if (form.mode == "COSECHA") "Confirma la cosecha real" else "Confirma el envío real", style = MaterialTheme.typography.titleMedium)
                Text("Revisa los datos sugeridos y corrige lo que ocurrió realmente.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(form.quantity, { vm.change(form.copy(quantity = it)) }, label = { Text("Racimos enteros") },
                    enabled = !vm.saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("production-quantity"))
                OutlinedTextField(form.date, { vm.change(form.copy(date = it)) }, label = { Text("Fecha real (AAAA-MM-DD)") }, enabled = !vm.saving, modifier = Modifier.fillMaxWidth())
                if (form.mode == "COSECHA") {
                    Row {
                        Checkbox(form.exceptionConfirmed, onCheckedChange = { vm.change(form.copy(exceptionConfirmed = it)) }, enabled = !vm.saving, modifier = Modifier.testTag("production-exception"))
                        Text("Confirmo una excepción si el total cosechado supera la estimación semanal.", Modifier.padding(top = 10.dp))
                    }
                    Text("Solo se permite cosechar durante Producción.", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(form.time, { vm.change(form.copy(time = it)) }, label = { Text("Hora real (HH:mm), Colombia") }, enabled = !vm.saving, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(form.vehicle, { vm.change(form.copy(vehicle = it)) }, label = { Text("Vehículo") }, enabled = !vm.saving, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(form.order, { vm.change(form.copy(order = it)) }, label = { Text("Orden del envío") }, enabled = !vm.saving, modifier = Modifier.fillMaxWidth())
                    val preview = runCatching { ProductionRepository.packing(period.plan, period.dispatched, form.quantity.toIntOrNull()?.coerceAtLeast(0) ?: 0, data.inventory) }
                    Text("Empaques de este despacho", fontWeight = FontWeight.Bold)
                    preview.getOrNull()?.forEach { line ->
                        Text("${line.name}: ${line.quantity} unidades · disponible ${line.available}")
                        if (line.shortage.signum() > 0) Text("Faltan ${line.shortage} unidades de ${line.name}", color = MaterialTheme.colorScheme.error)
                    }
                    preview.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (preview.getOrNull()?.any { it.shortage.signum() > 0 } == true) OutlinedButton(onClick = onEntry) { Text("Registrar entrada de empaques") }
                    Text("El despacho y sus empaques se guardan juntos. Solo en Producción o Descanso.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(form.observation, { vm.change(form.copy(observation = it)) }, label = { Text("Observaciones (opcional)") }, enabled = !vm.saving, modifier = Modifier.fillMaxWidth())
                Button(onClick = vm::save, enabled = !vm.saving && !data.loading,
                    modifier = Modifier.fillMaxWidth().testTag("production-save")) {
                    Text(if (vm.saving) "Guardando…" else if (form.mode == "COSECHA") "Guardar cosecha" else "Guardar despacho")
                }
                Text("Registros de este periodo", style = MaterialTheme.typography.titleMedium)
                data.harvests.filter { it.period == period.reference.period }.forEach {
                    Text("Cosecha · ${it.quantity} racimos · ${it.date} · ${syncLabel(it.syncStatus)}")
                    it.syncError?.let { message -> Text(message, color = MaterialTheme.colorScheme.error) }
                }
                data.dispatches.filter { it.period == period.reference.period }.forEach {
                    Text("Despacho · ${it.quantity} racimos · ${it.dispatchedAt} · ${syncLabel(it.syncStatus)}")
                    it.syncError?.let { message -> Text(message, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
