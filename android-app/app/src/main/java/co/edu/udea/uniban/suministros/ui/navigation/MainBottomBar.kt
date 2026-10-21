package co.edu.udea.uniban.suministros.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag

/**
 * Pestañas principales (mockups: Inicio, Parcelas, Inventario, Actividades). Solo se muestran las
 * que ya tienen pantallas; Inicio y Actividades se agregan cuando existan (regla de hu04.md).
 */
enum class MainTab(val route: String, val label: String, val icon: ImageVector) {
    PARCELS(AppDestinations.PARCELS, "Parcelas", Icons.Filled.Place),
    INVENTORY(AppDestinations.INVENTORY, "Inventario", Icons.AutoMirrored.Filled.List),
}

@Composable
fun MainBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier.testTag("main-bottom-bar")) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { if (tab != selected) onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("tab-${tab.name.lowercase()}"),
            )
        }
    }
}
