package co.edu.udea.uniban.suministros.ui.parcels

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.CycleStatus
import co.edu.udea.uniban.suministros.data.ParcelValidation
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import co.edu.udea.uniban.suministros.ui.inventory.InventoryScaffold
import co.edu.udea.uniban.suministros.ui.inventory.LoadingOrError
import co.edu.udea.uniban.suministros.ui.theme.UnibanPending
import co.edu.udea.uniban.suministros.ui.theme.UnibanPendingText
import java.time.LocalDate

/**
 * Detalle de una parcela (HU_02): área y ciclo productivo activo (estado, fecha de inicio y día del
 * ciclo), o un aviso explícito si no tiene ciclo activo. Lee la copia local: funciona sin conexión.
 *
 * Las secciones del mockup de aforamiento, actividades, insumos consumidos y recomendaciones
 * corresponden a HU_09, HU_08, HU_05 y HU_12; no se muestran hasta que existan.
 */
@Composable
fun ParcelDetailScreen(
    parcelId: String,
    state: ParcelsUiState,
    pendingCount: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val parcel = state.parcels.find { it.id == parcelId }
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
                    if (parcel.cycleStartDate == null) NoActiveCycle() else ActiveCycle(parcel, today)
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
}

/** Banda verde con los cuatro datos principales, como en el mockup. */
@Composable
private fun SummaryHeader(parcel: ParcelSummary) {
    val status = CycleStatus.fromCode(parcel.cycleStatus)
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderTile("Área", ParcelValidation.formatArea(parcel.areaHa), Modifier.weight(1f))
            HeaderTile("Estado", status?.label ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderTile("Ciclo", if (parcel.cycleStartDate == null) "Sin ciclo activo" else "Activo", Modifier.weight(1f))
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
                StatusChip(CycleStatus.fromCode(parcel.cycleStatus))
            }
            HorizontalDivider()
            DetailRow("Fecha de inicio", ParcelValidation.formatDate(start))
            DetailRow("Fecha de fin", "En curso")
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
private fun NoActiveCycle() {
    Surface(
        color = UnibanPending,
        contentColor = UnibanPendingText,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("no-active-cycle"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Esta parcela no tiene un ciclo productivo activo.", fontWeight = FontWeight.Bold)
            Text(
                "Los ciclos se inician al registrar la parcela. Iniciar un ciclo en una parcela existente " +
                    "aún no está disponible.",
                style = MaterialTheme.typography.bodySmall,
            )
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
