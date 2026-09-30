package co.edu.udea.uniban.suministros.ui.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.data.Quantity
import co.edu.udea.uniban.suministros.data.SavedEntry

@Composable
fun EntrySavedScreen(entry: SavedEntry, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    Scaffold(bottomBar = { BottomAction("Volver al inventario", onClick = onDone) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null,
                    modifier = Modifier.padding(20.dp).size(36.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Movimiento guardado", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(24.dp),
            ) {
                Text("Pendiente de sincronización",
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(20.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Insumo actualizado", style = MaterialTheme.typography.labelSmall)
                    Text(entry.supplyName, style = MaterialTheme.typography.titleSmall)
                    Text("Entrada · ${Quantity.format(entry.quantity)} ${entry.unit}",
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

