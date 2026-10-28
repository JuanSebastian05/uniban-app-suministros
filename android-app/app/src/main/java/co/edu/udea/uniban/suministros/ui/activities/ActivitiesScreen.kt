package co.edu.udea.uniban.suministros.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.ParcelValidation
import co.edu.udea.uniban.suministros.data.local.ActivityListItem
import co.edu.udea.uniban.suministros.data.local.ActivityType
import co.edu.udea.uniban.suministros.data.local.SyncStatus
import co.edu.udea.uniban.suministros.ui.inventory.BottomAction
import co.edu.udea.uniban.suministros.ui.inventory.InventoryScaffold
import co.edu.udea.uniban.suministros.ui.inventory.syncLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/** Actividades de la cuenta (HU_08), leídas de Room: la lista funciona sin conexión. */
class ActivitiesViewModel(repository: InventoryRepository) : ViewModel() {
    val activities = repository.activities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Pestaña «Actividades»: todas las actividades registradas y el botón para registrar una nueva. */
@Composable
fun ActivitiesScreen(
    activities: List<ActivityListItem>,
    pendingCount: Int,
    onNewActivity: () -> Unit,
    navigationBar: @Composable () -> Unit,
    onProduction: (() -> Unit)? = null,
) {
    InventoryScaffold(
        title = "Actividades",
        pendingCount = pendingCount,
        bottomBar = {
            Column {
                BottomAction("+ Registrar actividad", applyNavigationBarsPadding = false, onClick = onNewActivity)
                navigationBar()
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("activity-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onProduction != null) item {
                OutlinedButton(onClick = onProduction, modifier = Modifier.fillMaxWidth().testTag("activities-production")) {
                    Text("Cosechas y despachos")
                }
            }
            if (activities.isEmpty()) {
                item {
                    Text(
                        "Aún no hay actividades. Registra la primera con el botón de abajo.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp).testTag("no-activities"),
                    )
                }
            }
            items(activities, key = { it.id }) { ActivityCard(it) }
        }
    }
}

@Composable
private fun ActivityCard(activity: ActivityListItem) {
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
                "${activity.parcelName ?: "Parcela no disponible"} · ${ParcelValidation.formatDate(activity.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (activity.consumptionCount > 0) {
                Text(
                    "${activity.consumptionCount} ${if (activity.consumptionCount == 1) "insumo usado" else "insumos usados"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (activity.observation.isNotBlank()) Text(activity.observation, style = MaterialTheme.typography.bodyMedium)
            Text(
                syncLabel(activity.syncStatus), style = MaterialTheme.typography.labelSmall,
                color = if (activity.syncStatus == SyncStatus.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
