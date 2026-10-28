package co.edu.udea.uniban.suministros.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.CycleStatus
import co.edu.udea.uniban.suministros.data.ParcelValidation
import co.edu.udea.uniban.suministros.data.Quantity
import co.edu.udea.uniban.suministros.data.local.ActivityType
import co.edu.udea.uniban.suministros.data.local.HomeRecord
import co.edu.udea.uniban.suministros.data.local.MovementType
import co.edu.udea.uniban.suministros.ui.auth.LogoIcon
import co.edu.udea.uniban.suministros.ui.inventory.InventoryUiState
import co.edu.udea.uniban.suministros.ui.inventory.LogoutDialog
import co.edu.udea.uniban.suministros.ui.inventory.SyncStatusUiState
import co.edu.udea.uniban.suministros.ui.parcels.ParcelsUiState
import co.edu.udea.uniban.suministros.ui.theme.UnibanPending
import co.edu.udea.uniban.suministros.ui.theme.UnibanPendingText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    name: String?,
    email: String?,
    parcels: ParcelsUiState,
    inventory: InventoryUiState,
    sync: SyncStatusUiState,
    recent: HomeRecordsState,
    online: Boolean?,
    onParcels: (String?) -> Unit,
    onNewParcel: () -> Unit,
    onParcel: (String) -> Unit,
    onNewActivity: () -> Unit,
    onNewMovement: () -> Unit,
    onActivities: () -> Unit,
    onHistory: () -> Unit,
    onRecord: (HomeRecord) -> Unit,
    onSync: () -> Unit,
    onRetryParcels: () -> Unit,
    onLogout: () -> Unit,
    navigationBar: @Composable () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    if (confirmLogout) LogoutDialog(
        onConfirm = { confirmLogout = false; onLogout() },
        onDismiss = { confirmLogout = false },
    )
    val firstName = name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() }
    val newCycles = parcels.parcels.filter { it.cycleId == null && it.activityCycleId == null && it.currentStatus == null }
    Scaffold(bottomBar = navigationBar) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("home-list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(LogoIcon, contentDescription = null, modifier = Modifier.size(38.dp))
                    Text("Unibán", Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    var accountOpen by remember { mutableStateOf(false) }
                    Box {
                        FilledTonalIconButton(
                            onClick = { accountOpen = true },
                            modifier = Modifier.size(48.dp).testTag("home-account").semantics { contentDescription = "Mi cuenta" },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            val initials = name?.trim()?.split(Regex("\\s+"))?.take(2)
                                ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }?.joinToString("").orEmpty()
                            if (initials.isEmpty()) Icon(Icons.Default.Person, null)
                            else Text(initials, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(accountOpen, onDismissRequest = { accountOpen = false }) {
                            if (!email.isNullOrBlank()) DropdownMenuItem(text = { Text(email) }, onClick = {}, enabled = false)
                            DropdownMenuItem(text = { Text("Cerrar sesión") }, onClick = {
                                accountOpen = false; confirmLogout = true
                            })
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (firstName == null) "Hola, bienvenido" else "Hola, $firstName",
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Así va tu actividad hoy", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (online == false) item {
                Surface(color = UnibanPending, contentColor = UnibanPendingText, shape = RoundedCornerShape(16.dp)) {
                    Text("Sin conexión · Mostrando datos guardados", Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading("Mis parcelas", "Ver parcelas") { onParcels(null) }
                    when {
                        parcels.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        parcels.error != null -> HomeLinkCard("No se pudieron cargar las parcelas", "Toca para reintentar", Icons.Default.Refresh, onRetryParcels)
                        parcels.parcels.isEmpty() -> OutlinedCard(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Tu finca empieza aquí", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Registra tu primera parcela para organizar sus ciclos y actividades.")
                                Button(onClick = onNewParcel, modifier = Modifier.testTag("home-first-parcel")) {
                                    Text("Registrar mi primera parcela")
                                }
                            }
                        }
                        else -> {
                            ParcelSummaryGrid(parcels, onParcels)
                            if (parcels.offline && online != false) Text(
                                "No se pudieron actualizar las parcelas. Se muestran los datos guardados.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading("Acciones rápidas")
                    val largeText = LocalDensity.current.fontScale > 1.2f
                    val activityEnabled = !parcels.loading && parcels.error == null && parcels.parcels.any { it.activityCycleId != null }
                    val movementEnabled = !inventory.loading && inventory.error == null && inventory.items.isNotEmpty()
                    if (largeText) {
                        QuickAction("Registrar actividad", LogoIcon, true, activityEnabled, Modifier.fillMaxWidth(), onNewActivity)
                        QuickAction("Registrar movimiento", MovementIcon, false, movementEnabled, Modifier.fillMaxWidth(), onNewMovement)
                    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickAction("Registrar actividad", LogoIcon, true, activityEnabled, Modifier.weight(1f), onNewActivity)
                        QuickAction("Registrar movimiento", MovementIcon, false, movementEnabled, Modifier.weight(1f), onNewMovement)
                    }
                    if (!parcels.loading && parcels.error == null && !activityEnabled) Text(
                        "Para registrar actividades necesitas una parcela con un ciclo iniciado.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (inventory.error != null) Text(inventory.error, color = MaterialTheme.colorScheme.error)
                }
            }
            if (sync.issues.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading("Atención necesaria")
                    HomeLinkCard(
                        "${sync.issues.size} ${if (sync.issues.size == 1) "error de envío" else "errores de envío"}",
                        "Revisa los registros para volver a enviarlos.", Icons.Default.Warning, onSync, error = true,
                    )
                }
            }
            if (!parcels.loading && parcels.error == null && newCycles.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading("Para empezar")
                    newCycles.take(2).forEach { parcel ->
                        HomeLinkCard("Iniciar primer ciclo", parcel.name, LogoIcon, { onParcel(parcel.id) })
                    }
                    if (newCycles.size > 2) TextButton(onClick = { onParcels("SIN_CICLO") }) { Text("Ver parcelas sin ciclo") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var historyOpen by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Registros recientes", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Box {
                            TextButton(onClick = { historyOpen = true }) { Text("Ver historial") }
                            DropdownMenu(historyOpen, onDismissRequest = { historyOpen = false }) {
                                DropdownMenuItem(text = { Text("Actividades") }, onClick = { historyOpen = false; onActivities() })
                                DropdownMenuItem(text = { Text("Movimientos") }, onClick = { historyOpen = false; onHistory() })
                            }
                        }
                    }
                    when {
                        recent.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        recent.error -> Text("No se pudieron cargar los registros. Puedes consultarlos desde Actividades o Inventario.")
                        recent.records.isEmpty() -> Text("Tus próximas actividades y movimientos aparecerán aquí.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (recent.records.isNotEmpty()) item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    recent.records.forEachIndexed { index, record ->
                        val activity = record.kind == "ACTIVITY"
                        Row(
                            Modifier.fillMaxWidth().clickable { onRecord(record) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(if (activity) LogoIcon else Icons.AutoMirrored.Filled.List, null,
                                Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (activity) ActivityType.fromCode(record.type)?.label ?: record.type else if (record.kind == "DISPATCH") "Despacho" else MovementType.label(record.type),
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(if (activity) record.name ?: "Parcela no disponible"
                                    else "${record.name.orEmpty()} · ${record.quantity?.let(Quantity::format).orEmpty()} ${record.unit.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(homeDate(record.date, today), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        }
                        if (index < recent.records.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            item {
                if (sync.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                    val title = when {
                        sync.syncingCount > 0 -> "Sincronización en curso"
                        sync.pendingCount > 0 -> "${sync.pendingCount} ${if (sync.pendingCount == 1) "registro pendiente" else "registros pendientes"}"
                        sync.issues.isNotEmpty() -> "Revisar sincronización"
                        sync.lastSuccessfulAtEpochMillis != null && online == true -> "Todo sincronizado"
                        else -> "Sin registros pendientes"
                    }
                    val detail = when {
                        sync.syncingCount > 0 -> "Enviando tus registros al sistema central."
                        sync.pendingCount > 0 && online == false -> "Guardados en este dispositivo. Se enviarán cuando haya conexión."
                        sync.pendingCount > 0 -> "Consulta el estado de envío de tus registros."
                        sync.lastSuccessfulAtEpochMillis != null -> "Último envío exitoso: ${homeTimestamp(sync.lastSuccessfulAtEpochMillis)}"
                        else -> "Aún no se ha realizado un envío de registros."
                    }
                    HomeLinkCard(title, detail,
                        if (sync.pendingCount > 0 || sync.syncingCount > 0 || sync.issues.isNotEmpty()) Icons.Default.Refresh else Icons.Default.CheckCircle,
                        onSync, pending = sync.pendingCount > 0)
                }
            }
        }
    }
}

@Composable
private fun ParcelSummaryGrid(state: ParcelsUiState, onSelect: (String?) -> Unit) {
    val counts = listOf(
        Triple("Total", state.parcels.size, null),
        Triple("Preparación", state.parcels.count { (it.currentStatus ?: it.cycleStatus) == CycleStatus.EN_PREPARACION.name }, CycleStatus.EN_PREPARACION.name),
        Triple("Producción", state.parcels.count { (it.currentStatus ?: it.cycleStatus) == CycleStatus.PRODUCCION.name }, CycleStatus.PRODUCCION.name),
        Triple("Descanso", state.parcels.count { it.currentStatus == CycleStatus.DESCANSO.name }, CycleStatus.DESCANSO.name),
    )
    BoxWithConstraints {
        val columns = if (maxWidth < 600.dp || LocalDensity.current.fontScale > 1.2f) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            counts.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, count, status) ->
                        OutlinedCard(
                            onClick = { onSelect(status) }, modifier = Modifier.weight(1f).testTag("home-count-${status ?: "total"}"),
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, primary: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 136.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeLinkCard(title: String, detail: String, icon: ImageVector, onClick: () -> Unit,
                         error: Boolean = false, pending: Boolean = false) {
    OutlinedCard(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = when { error -> MaterialTheme.colorScheme.errorContainer; pending -> UnibanPending; else -> MaterialTheme.colorScheme.surface },
            contentColor = when { error -> MaterialTheme.colorScheme.onErrorContainer; pending -> UnibanPendingText; else -> MaterialTheme.colorScheme.onSurface },
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

internal fun homeDate(date: String, today: LocalDate): String = when (date) {
    today.toString() -> "Hoy"
    today.minusDays(1).toString() -> "Ayer"
    else -> ParcelValidation.formatDate(date)
}

private fun homeTimestamp(value: Long): String = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
