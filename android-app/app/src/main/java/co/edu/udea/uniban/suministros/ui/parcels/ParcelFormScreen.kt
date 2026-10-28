package co.edu.udea.uniban.suministros.ui.parcels

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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.R
import co.edu.udea.uniban.suministros.data.CycleStatus
import co.edu.udea.uniban.suministros.ui.inventory.BottomAction
import co.edu.udea.uniban.suministros.ui.inventory.InventoryScaffold
import co.edu.udea.uniban.suministros.ui.inventory.displayDate
import java.time.LocalDate

/** Formulario «Registrar parcela» (HU_14). Al guardar con éxito llama a [onDone]. */
@Composable
fun ParcelFormScreen(
    state: ParcelFormUiState,
    viewModel: ParcelFormViewModel,
    pendingCount: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    BackHandler(enabled = state.saving) {}
    val context = LocalContext.current
    val editable = !state.saving && !state.saved

    InventoryScaffold(
        title = "Registrar parcela",
        pendingCount = pendingCount,
        onBack = onBack,
        backEnabled = !state.saving,
        bottomBar = {
            BottomAction(
                label = if (state.saving) "Guardando…" else "Guardar parcela",
                enabled = editable,
                onClick = viewModel::save,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::changeName,
                label = { Text("Nombre de la parcela") },
                placeholder = { Text("La Esperanza") },
                singleLine = true, enabled = editable,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = state.nameError != null,
                supportingText = { Text(state.nameError ?: "Único entre tus parcelas.") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("parcel-name"),
            )
            OutlinedTextField(
                value = state.area,
                onValueChange = viewModel::changeArea,
                label = { Text("Área") },
                placeholder = { Text("0,00") },
                suffix = { Text("ha") },
                singleLine = true, enabled = editable,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.areaError != null,
                supportingText = { Text(state.areaError ?: "En hectáreas. Mayor que cero, máximo dos decimales.") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("parcel-area"),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Iniciar ciclo productivo", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Opcional. Puedes registrar la parcela sin ciclo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.startCycle,
                    onCheckedChange = viewModel::changeStartCycle,
                    enabled = editable,
                    modifier = Modifier.testTag("parcel-start-cycle"),
                )
            }

            if (state.startCycle) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fecha de inicio", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day -> viewModel.changeStartDate(LocalDate.of(year, month + 1, day)) },
                                state.startDate.year, state.startDate.monthValue - 1, state.startDate.dayOfMonth,
                            ).show()
                        },
                        enabled = editable, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(displayDate(state.startDate), Modifier.weight(1f))
                        Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Estado del ciclo", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CycleStatus.entries.filter { it != CycleStatus.DESCANSO }.forEach { status ->
                            FilterChip(
                                selected = state.status == status,
                                onClick = { viewModel.changeStatus(status) },
                                label = { Text(status.label) },
                                enabled = editable,
                            )
                        }
                    }
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.saving) {
                Text(
                    stringResource(R.string.server_wakeup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("server-wakeup-hint"),
                )
            }
            Text(
                "La parcela se registra en el servidor: se necesita conexión a internet. " +
                    "Después podrás verla también sin conexión.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

