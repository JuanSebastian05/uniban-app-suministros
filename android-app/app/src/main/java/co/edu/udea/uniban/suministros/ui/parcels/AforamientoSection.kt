package co.edu.udea.uniban.suministros.ui.parcels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.ParcelValidation
import co.edu.udea.uniban.suministros.data.local.AforamientoEntity
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Sección «Aforamiento» del detalle de la parcela (HU_09): el aforamiento más reciente recibido del Equipo 1.
 * Estados: consultando, con datos (también sin conexión, con la última copia), «Sin aforamiento recibido» y error.
 */
@Composable
internal fun AforamientoSection(state: AforamientoUiState, onRetry: () -> Unit) {
    val aforamiento = state.aforamiento
    when {
        aforamiento != null -> {
            AforamientoCard(aforamiento)
            if (state.refreshFailed) {
                Text(
                    "Sin conexión: se muestra la última copia guardada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("aforamiento-stale"),
                )
            }
        }
        state.loading -> Row(
            Modifier.testTag("aforamiento-loading"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                "Consultando aforamiento…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.refreshFailed -> Column(
            Modifier.testTag("aforamiento-error"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "No se pudo consultar el aforamiento. Revisa tu conexión e intenta de nuevo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("aforamiento-retry")) {
                Text("Intentar de nuevo")
            }
        }
        else -> Text(
            "Sin aforamiento recibido",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("aforamiento-empty"),
        )
    }
}

@Composable
private fun AforamientoCard(aforamiento: AforamientoEntity) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag("aforamiento-card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AforamientoRow("Fecha del aforamiento", ParcelValidation.formatDate(aforamiento.date))
            HorizontalDivider()
            AforamientoRow("Área aforada", ParcelValidation.formatArea(aforamiento.areaHa))
            AforamientoRow("Plantas estimadas", formatPlants(aforamiento.estimatedPlants))
            aforamiento.confidence?.let { AforamientoRow("Confianza", formatConfidence(it)) }
        }
    }
}

@Composable
private fun AforamientoRow(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private val SPANISH = Locale.forLanguageTag("es-CO")

/** «1.850»: separador de miles de la app en español. */
internal fun formatPlants(plants: Int): String = NumberFormat.getIntegerInstance(SPANISH).format(plants)

/** Fracción 0–1 como porcentaje: «0.870» → «87 %», «0.875» → «87,5 %». Texto ilegible se muestra tal cual. */
internal fun formatConfidence(fraction: String): String {
    val percent = runCatching { BigDecimal(fraction).movePointRight(2).stripTrailingZeros() }.getOrNull()
        ?: return fraction
    val plain = if (percent.signum() == 0) "0" else percent.toPlainString()
    return "${plain.replace('.', ',')} %"
}
