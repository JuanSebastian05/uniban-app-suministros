package co.edu.udea.uniban.suministros.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Las cuatro secciones principales de la cuenta. */
enum class MainTab(val route: String, val label: String, val icon: ImageVector) {
    HOME(AppDestinations.HOME, "Inicio", Icons.Filled.Home),
    PARCELS(AppDestinations.PARCELS, "Parcelas", Icons.Filled.Place),
    INVENTORY(AppDestinations.INVENTORY, "Inventario", Icons.AutoMirrored.Filled.List),
    ACTIVITIES(AppDestinations.ACTIVITIES, "Actividades", Icons.Filled.DateRange),
}

@Composable
fun MainBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier.testTag("main-bottom-bar"), containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { if (tab != selected) onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("tab-${tab.name.lowercase()}"),
            )
        }
    }
}
