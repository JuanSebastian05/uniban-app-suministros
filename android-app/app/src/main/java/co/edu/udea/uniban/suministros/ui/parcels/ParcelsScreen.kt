package co.edu.udea.uniban.suministros.ui.parcels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.CycleStatus
import co.edu.udea.uniban.suministros.data.ParcelValidation
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import co.edu.udea.uniban.suministros.ui.inventory.BottomAction
import co.edu.udea.uniban.suministros.ui.inventory.InventoryScaffold
import co.edu.udea.uniban.suministros.ui.inventory.LoadingOrError
import co.edu.udea.uniban.suministros.ui.theme.UnibanGreen
import co.edu.udea.uniban.suministros.ui.theme.UnibanGreenLight
import co.edu.udea.uniban.suministros.ui.theme.UnibanMuted
import co.edu.udea.uniban.suministros.ui.theme.UnibanPending
import co.edu.udea.uniban.suministros.ui.theme.UnibanPendingText

/**
 * «Mis parcelas» (HU_14). Lista sencilla con nombre, área y ciclo activo; el detalle y el diseño
 * completo corresponden a HU_02.
 */
@Composable
fun ParcelsScreen(
    state: ParcelsUiState,
    pendingCount: Int,
    onRetry: () -> Unit,
    onNewParcel: () -> Unit,
    navigationBar: @Composable () -> Unit,
) {
    InventoryScaffold(
        title = "Mis parcelas",
        pendingCount = pendingCount,
        bottomBar = {
            Column {
                BottomAction(
                    "+ Registrar parcela",
                    enabled = !state.loading && state.error == null,
                    applyNavigationBarsPadding = false,
                    onClick = onNewParcel,
                )
                navigationBar()
            }
        },
    ) { padding ->
        if (state.loading || state.error != null) {
            LoadingOrError(state.error, onRetry, Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("parcel-list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "${state.parcels.size} ${if (state.parcels.size == 1) "parcela registrada" else "parcelas registradas"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.parcels.isEmpty()) {
                    item {
                        Text(
                            "Aún no tienes parcelas. Registra la primera con el botón de abajo.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    }
                }
                items(state.parcels, key = { it.id }) { parcel -> ParcelCard(parcel) }
            }
        }
    }
}

@Composable
private fun ParcelCard(parcel: ParcelSummary) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(parcel.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Área: ${ParcelValidation.formatArea(parcel.areaHa)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UnibanGreen,
                )
                parcel.cycleStartDate?.let {
                    Text("Inicio: $it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusChip(CycleStatus.fromCode(parcel.cycleStatus))
        }
    }
}

@Composable
private fun StatusChip(status: CycleStatus?) {
    val (container, content) = when (status) {
        CycleStatus.PRODUCCION -> UnibanGreenLight to UnibanGreen
        CycleStatus.EN_PREPARACION -> UnibanPending to UnibanPendingText
        CycleStatus.DESCANSO, null -> Color(0xFFEDEFED) to UnibanMuted
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(20.dp)) {
        Text(
            status?.label ?: "Sin ciclo activo",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
