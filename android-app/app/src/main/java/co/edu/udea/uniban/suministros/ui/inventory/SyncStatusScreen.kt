package co.edu.udea.uniban.suministros.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.SyncIssue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SyncStatusScreen(
    state: SyncStatusUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    InventoryScaffold(
        title = "Sincronización",
        pendingCount = state.pendingCount,
        onBack = onBack,
    ) { padding ->
        if (state.loading) {
            LoadingOrError(null, onRetry, Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SyncSummaryCard(state)
                }
                item {
                    Button(
                        onClick = onRetry,
                        enabled = state.pendingCount > 0 && state.syncingCount == 0,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(if (state.syncingCount > 0) "Sincronización en curso" else "Reintentar ahora") }
                }
                item {
                    Text(
                        "El reintento se ejecuta en segundo plano y necesita conexión a internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Text("Registros con error (${state.issues.size})", style = MaterialTheme.typography.titleMedium)
                }
                if (state.issues.isEmpty()) {
                    item {
                        Text(
                            if (state.pendingCount == 0) {
                                "No hay registros con error."
                            } else {
                                "Todavía no hay errores registrados. Los pendientes se enviarán cuando haya conexión."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.issues, key = SyncIssue::id) { issue -> SyncIssueCard(issue) }
                }
            }
        }
    }
}

@Composable
private fun SyncSummaryCard(state: SyncStatusUiState) {
    val hasIssues = state.issues.isNotEmpty()
    val title = when {
        state.syncingCount > 0 -> "Enviando registros"
        hasIssues -> "Hay registros que requieren atención"
        state.pendingCount > 0 -> "Hay registros pendientes"
        else -> "No hay registros pendientes"
    }
    val detail = when {
        state.syncingCount > 0 -> "Se están sincronizando ${countLabel(state.syncingCount)}."
        state.pendingCount > 0 -> "${countLabel(state.pendingCount)} aún no están respaldados en el sistema central."
        else -> "No hay movimientos ni actividades pendientes de envío."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasIssues) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text("Último envío exitoso de movimientos y actividades", style = MaterialTheme.typography.labelMedium)
            Text(
                state.lastSuccessfulAtEpochMillis?.let(::formatTimestamp)
                    ?: "Aún no se ha completado una sincronización exitosa.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SyncIssueCard(issue: SyncIssue) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(issue.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text("Fecha: ${formatDate(issue.date)}", style = MaterialTheme.typography.bodySmall)
            Text(issue.context, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(issue.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String = runCatching {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    formatter.format(Instant.ofEpochMilli(epochMillis))
}.getOrDefault("Fecha no disponible")

private fun countLabel(count: Int): String = "$count ${if (count == 1) "registro" else "registros"}"

private fun formatDate(date: String): String = runCatching {
    val parsed = java.time.LocalDate.parse(date)
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()).format(parsed)
}.getOrDefault(date)
