package co.edu.udea.uniban.suministros.ui.parcels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.ParcelRecommendation
import co.edu.udea.uniban.suministros.data.ParcelValidation
import co.edu.udea.uniban.suministros.data.RecommendationSupply
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sección «Recomendaciones» del detalle de la parcela (HU_12): volumen estimado, fecha límite de corte, si cumple la
 * ventana y los insumos con su déficit. Solo muestra lo que calculó el Equipo 2; la app no calcula nada.
 * Estados: consultando, con datos (también sin conexión), sin recomendación aún, pidiendo y problema.
 */
@Composable
internal fun RecommendationSection(state: RecommendationUiState, onRequest: () -> Unit) {
    val recommendation = state.recommendation
    when {
        recommendation != null -> RecommendationCard(recommendation)
        state.loading -> Row(
            Modifier.testTag("recommendation-loading"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                "Consultando recomendaciones…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> Text(
            "Sin recomendación aún",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("recommendation-empty"),
        )
    }

    if (state.requesting) {
        Row(
            Modifier.testTag("recommendation-requesting"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                "Pidiendo la recomendación… puede tardar hasta un minuto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    state.problem?.let { problem ->
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("recommendation-problem"),
        ) {
            Text(
                problemMessage(problem),
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    val label = if (recommendation == null) "Solicitar recomendación" else "Actualizar recomendación"
    if (recommendation == null) {
        Button(
            onClick = onRequest, enabled = !state.requesting,
            modifier = Modifier.fillMaxWidth().testTag("recommendation-request"),
        ) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onRequest, enabled = !state.requesting,
            modifier = Modifier.fillMaxWidth().testTag("recommendation-request"),
        ) { Text(label) }
    }
}

internal fun problemMessage(problem: RecommendationProblem): String = when (problem) {
    RecommendationProblem.NO_AFORAMIENTO ->
        "Esta parcela todavía no tiene aforamiento. Cuando se reciba, podrás pedir la recomendación."
    RecommendationProblem.SERVICE_UNAVAILABLE ->
        "El servicio de recomendaciones no está disponible ahora. Intenta de nuevo en unos minutos."
    RecommendationProblem.OFFLINE -> "Sin conexión. Conéctate a internet para pedir la recomendación."
    RecommendationProblem.SESSION -> "Tu sesión no es válida. Cierra sesión e ingresa de nuevo."
    RecommendationProblem.OTHER -> "No se pudo obtener la recomendación. Intenta de nuevo."
}

@Composable
private fun RecommendationCard(recommendation: ParcelRecommendation) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag("recommendation-card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RecommendationRow("Volumen estimado", "${formatBunches(recommendation.volumeBunches)} racimos")
            RecommendationRow("Fecha límite de corte", formatDateTime(recommendation.cutDeadline))
            WindowIndicator(recommendation.meetsWindow)
            recommendation.reason?.let {
                Text(
                    "Observación: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("recommendation-reason"),
                )
            }
            HorizontalDivider()
            Text("Insumos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (recommendation.supplies.isEmpty()) {
                Text(
                    "El servicio no indicó insumos para este periodo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("recommendation-no-supplies"),
                )
            }
            recommendation.supplies.forEach { SupplyRow(it) }
            HorizontalDivider()
            Text(
                "Generada el ${formatGenerated(recommendation.generatedAt)} · periodo ${recommendation.period}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("recommendation-generated"),
            )
        }
    }
}

@Composable
private fun WindowIndicator(meets: Boolean) {
    Surface(
        color = if (meets) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (meets) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag(if (meets) "window-meets" else "window-fails"),
    ) {
        Text(
            if (meets) "Cumple la ventana de despacho" else "No cumple la ventana de despacho",
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SupplyRow(supply: RecommendationSupply) {
    val hasDeficit = supply.deficit > 0
    Column(Modifier.fillMaxWidth().testTag("supply-${supply.name}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text(supply.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(
                "Para el ${formatShortDate(supply.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Requerido ${formatAmount(supply.requiredQuantity)} · Disponible ${formatAmount(supply.availableQuantity)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasDeficit) {
            Text(
                "Déficit: ${formatAmount(supply.deficit)}",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("deficit-${supply.name}"),
            )
        } else {
            Text("Sin déficit", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("no-deficit-${supply.name}"))
        }
    }
}

@Composable
private fun RecommendationRow(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

// --- Formato (solo presentación) ---

private val SPANISH = Locale.forLanguageTag("es-CO")
private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/** Racimos: «1.850», con coma decimal solo si el valor no es entero. */
internal fun formatBunches(value: Double): String = formatAmount(value)

/** Cantidades con separador de miles y hasta dos decimales: «1.850», «12,5». */
internal fun formatAmount(value: Double): String =
    java.text.NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = 2 }.format(value)

/** «02/11/2026 14:30» a partir de un texto ISO sin zona; si no se puede leer, se muestra tal cual. */
internal fun formatDateTime(iso: String): String =
    runCatching { LocalDateTime.parse(iso).format(DATE_TIME) }.getOrDefault(iso)

/** Fecha prevista «01/11/2026» a partir de AAAA-MM-DD. */
internal fun formatShortDate(iso: String): String = ParcelValidation.formatDate(iso)

/** Instante de generación (con zona) en la hora del teléfono: «26/10/2026 14:05». */
internal fun formatGenerated(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(DATE_TIME) }
        .recoverCatching { LocalDateTime.parse(iso).format(DATE_TIME) }
        .getOrDefault(iso)
