package co.edu.udea.uniban.suministros.ui.parcels

import android.app.DatePickerDialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.CycleStatus
import co.edu.udea.uniban.suministros.data.ParcelValidation
import co.edu.udea.uniban.suministros.data.Quantity
import co.edu.udea.uniban.suministros.data.local.ActivityType
import co.edu.udea.uniban.suministros.data.local.ParcelActivity
import co.edu.udea.uniban.suministros.data.local.ParcelConsumption
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import co.edu.udea.uniban.suministros.data.local.SyncStatus
import co.edu.udea.uniban.suministros.ui.inventory.InventoryScaffold
import co.edu.udea.uniban.suministros.ui.inventory.LoadingOrError
import co.edu.udea.uniban.suministros.ui.inventory.syncLabel
import co.edu.udea.uniban.suministros.ui.theme.UnibanPending
import co.edu.udea.uniban.suministros.ui.theme.UnibanPendingText
import java.time.LocalDate

/**
 * Detalle de una parcela (HU_02): área y ciclo productivo activo (estado, fecha de inicio y día del
 * ciclo), o un aviso explícito si no tiene ciclo activo. Lee la copia local: funciona sin conexión.
 *
 * «Actividades» lista las actividades de la parcela (HU_08) y «Insumos consumidos» sus consumos (HU_05).
 * «Aforamiento» muestra el más reciente recibido del Equipo 1 (HU_09). «Recomendaciones» muestra la última del
 * Equipo 2 (HU_12) y solo aparece si se entrega [recommendation].
 */
@Composable
fun ParcelDetailScreen(
    parcelId: String,
    state: ParcelsUiState,
    pendingCount: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    today: LocalDate = LocalDate.now(),
    consumptions: List<ParcelConsumption> = emptyList(),
    // HU_08: actividades de la parcela y acceso al formulario. Sin [onNewActivity] no se muestra el botón.
    activities: List<ParcelActivity> = emptyList(),
    onNewActivity: (() -> Unit)? = null,
    // HU_09: aforamiento más reciente de la parcela (copia local) y reintento de la consulta.
    aforamiento: AforamientoUiState = AforamientoUiState(loading = false),
    onRetryAforamiento: () -> Unit = {},
    // HU_12: última recomendación del Equipo 2 y botón para pedir una nueva. Nulo = no se muestra la sección.
    recommendation: RecommendationUiState? = null,
    onRequestRecommendation: () -> Unit = {},
    cycleSaving: Boolean = false,
    cycleError: String? = null,
    onChangeCycleStage: ((CycleStatus) -> Unit)? = null,
    onFinishCycle: ((LocalDate) -> Unit)? = null,
    onStartCycle: ((LocalDate) -> Unit)? = null,
) {
    val parcel = state.parcels.find { it.id == parcelId }
    var showStageDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    InventoryScaffold(
        title = parcel?.name ?: "Detalle de la parcela",
        pendingCount = pendingCount,
        onBack = onBack,
    ) { padding ->
        when {
            state.loading || state.error != null -> LoadingOrError(state.error, onRetry, Modifier.padding(padding))
            parcel == null -> Text(
                "La parcela ya no está disponible.",
                Modifier.padding(padding).padding(24.dp),
            )
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).testTag("parcel-detail"),
            ) {
                SummaryHeader(parcel)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "CICLO PRODUCTIVO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (parcel.cycleStartDate == null) {
                        NoActiveCycle(resting = parcel.currentStatus == CycleStatus.DESCANSO.name)
                        if (onStartCycle != null) {
                            OutlinedButton(
                                enabled = !cycleSaving,
                                onClick = {
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            onStartCycle(LocalDate.of(year, month + 1, day))
                                        },
                                        today.year, today.monthValue - 1, today.dayOfMonth,
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth().testTag("start-cycle"),
                            ) { Text(if (parcel.currentStatus == CycleStatus.DESCANSO.name) "Iniciar nuevo ciclo" else "Iniciar ciclo") }
                        }
                    } else {
                        ActiveCycle(parcel, today)
                        if (onChangeCycleStage != null && parcel.currentStatus != CycleStatus.DESCANSO.name) {
                            OutlinedButton(
                                enabled = !cycleSaving,
                                onClick = { showStageDialog = true },
                                modifier = Modifier.fillMaxWidth().testTag("change-cycle-stage"),
                            ) { Text("Cambiar etapa") }
                        }
                        if (onFinishCycle != null) {
                            OutlinedButton(
                                enabled = !cycleSaving,
                                onClick = { showFinishDialog = true },
                                modifier = Modifier.fillMaxWidth().testTag("finish-cycle"),
                            ) { Text("Finalizar ciclo") }
                        }
                    }
                    if (cycleSaving) {
                        Text("Actualizando ciclo…", style = MaterialTheme.typography.bodySmall)
                    }
                    cycleError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "AFORAMIENTO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    AforamientoSection(aforamiento, onRetryAforamiento)
                    if (recommendation != null) {
                        Text(
                            "RECOMENDACIONES",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        RecommendationSection(recommendation, onRequestRecommendation)
                    }
                    Text(
                        "ACTIVIDADES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (activities.isEmpty()) {
                        Text(
                            "Aún no hay actividades registradas en esta parcela.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("no-activities"),
                        )
                    }
                    activities.forEach { ActivityCard(it, activeCycleId = parcel.cycleId) }
                    if (onNewActivity != null) {
                        if (parcel.activityCycleId == null) {
                            Text(
                                "Inicia un ciclo productivo para registrar actividades en esta parcela.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            OutlinedButton(
                                onClick = onNewActivity,
                                modifier = Modifier.fillMaxWidth().testTag("new-activity"),
                            ) { Text("+ Registrar actividad") }
                        }
                    }
                    Text(
                        "INSUMOS CONSUMIDOS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (consumptions.isEmpty()) {
                        Text(
                            "Aún no hay consumos registrados en esta parcela.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("no-consumptions"),
                        )
                    }
                    consumptions.forEach { ConsumptionCard(it, activeCycleId = parcel.cycleId) }
                    if (state.offline) {
                        Text(
                            "Sin conexión: datos guardados en este dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showStageDialog && parcel != null && onChangeCycleStage != null) {
        AlertDialog(
            onDismissRequest = { if (!cycleSaving) showStageDialog = false },
            title = { Text("Cambiar etapa") },
            text = { Text("Selecciona la etapa actual del cultivo.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CycleStatus.entries.filter { it != CycleStatus.DESCANSO }.forEach { stage ->
                        TextButton(
                            enabled = !cycleSaving && parcel.currentStatus != stage.name,
                            onClick = { onChangeCycleStage(stage); showStageDialog = false },
                        ) { Text(stage.label) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showStageDialog = false }) { Text("Cancelar") } },
        )
    }
    if (showFinishDialog && onFinishCycle != null) {
        AlertDialog(
            onDismissRequest = { if (!cycleSaving) showFinishDialog = false },
            title = { Text("Finalizar ciclo") },
            text = { Text("La parcela quedará en descanso. Podrás iniciar otro ciclo cuando corresponda.") },
            confirmButton = {
                TextButton(
                    enabled = !cycleSaving,
                    onClick = { onFinishCycle(today); showFinishDialog = false },
                ) { Text("Finalizar") }
            },
            dismissButton = { TextButton(onClick = { showFinishDialog = false }) { Text("Cancelar") } },
        )
    }
}

/** Banda verde con los cuatro datos principales, como en el mockup. */
@Composable
private fun SummaryHeader(parcel: ParcelSummary) {
    val status = CycleStatus.fromCode(parcel.currentStatus ?: parcel.cycleStatus)
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderTile("Área", ParcelValidation.formatArea(parcel.areaHa), Modifier.weight(1f))
            HeaderTile("Estado", status?.label ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderTile(
                "Ciclo",
                when {
                    parcel.cycleStartDate != null -> "Activo"
                    parcel.currentStatus == CycleStatus.DESCANSO.name -> "En descanso"
                    else -> "Sin ciclo activo"
                },
                Modifier.weight(1f),
            )
            HeaderTile("Inicio", parcel.cycleStartDate?.let(ParcelValidation::formatDate) ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeaderTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActiveCycle(parcel: ParcelSummary, today: LocalDate) {
    val start = requireNotNull(parcel.cycleStartDate)
    val day = ParcelValidation.cycleDay(start, today)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag("active-cycle"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Etapa actual", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                StatusChip(CycleStatus.fromCode(parcel.currentStatus ?: parcel.cycleStatus))
            }
            HorizontalDivider()
            DetailRow("Estado del ciclo", "En curso")
            DetailRow("Fecha de inicio", ParcelValidation.formatDate(start))
            DetailRow("Fecha de fin", "Sin finalizar")
            DetailRow(
                "Duración",
                when (day) {
                    null -> "Aún no comienza"
                    1L -> "Día 1 del ciclo"
                    else -> "Día $day del ciclo"
                },
            )
        }
    }
}

@Composable
private fun NoActiveCycle(resting: Boolean) {
    Surface(
        color = UnibanPending,
        contentColor = UnibanPendingText,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("no-active-cycle"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (resting) "La parcela está en descanso." else "Esta parcela no tiene un ciclo productivo activo.",
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (resting) "Puedes iniciar un nuevo ciclo cuando comience la siguiente etapa de producción."
                else "Puedes iniciar un ciclo productivo para comenzar a registrar sus actividades.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ActivityCard(activity: ParcelActivity, activeCycleId: String?) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag("activity-${activity.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                ActivityType.fromCode(activity.type)?.label ?: activity.type,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            )
            Text(
                ParcelValidation.formatDate(activity.date) + when (activity.consumptionCount) {
                    0 -> ""
                    1 -> " · 1 insumo usado"
                    else -> " · ${activity.consumptionCount} insumos usados"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (activity.observation.isNotBlank()) Text(activity.observation, style = MaterialTheme.typography.bodyMedium)
            if (activeCycleId != null && activity.cycleId != activeCycleId) {
                Text("Ciclo anterior", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                syncLabel(activity.syncStatus), style = MaterialTheme.typography.labelSmall,
                color = if (activity.syncStatus == SyncStatus.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun ConsumptionCard(consumption: ParcelConsumption, activeCycleId: String?) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag("consumption-${consumption.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(consumption.supplyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Consumo · ${Quantity.format(consumption.quantity)} ${consumption.unit} · " +
                    ParcelValidation.formatDate(consumption.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Un consumo de un ciclo que ya no es el activo (o sin ciclo) se distingue del ciclo actual.
            if (activeCycleId != null && consumption.cycleId != activeCycleId) {
                Text(
                    if (consumption.cycleId == null) "Sin ciclo" else "Ciclo anterior",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
