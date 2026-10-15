package co.edu.udea.uniban.suministros.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.ui.inventory.*

@Composable
fun AppNavHost(repository: InventoryRepository, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val inventoryFactory = remember(repository) {
        viewModelFactory { initializer { InventoryViewModel(repository) } }
    }
    val inventoryViewModel: InventoryViewModel = viewModel(factory = inventoryFactory)
    val state by inventoryViewModel.state.collectAsStateWithLifecycle()
    val entryFactory = remember(repository) {
        viewModelFactory { initializer { EntryViewModel(repository, createSavedStateHandle()) } }
    }

    NavHost(navController, startDestination = AppDestinations.INVENTORY, modifier = modifier) {
        composable(AppDestinations.INVENTORY) {
            InventoryScreen(
                state = state,
                onRetry = inventoryViewModel::load,
                onSelect = { navController.navigate(AppDestinations.detail(it)) },
                onNewEntry = { navController.navigate(AppDestinations.entry()) { launchSingleTop = true } },
            )
        }
        composable(
            AppDestinations.DETAIL,
            arguments = listOf(navArgument("inventoryId") { type = NavType.StringType }),
        ) { entry ->
            val id = requireNotNull(entry.arguments?.getString("inventoryId"))
            InventoryDetailScreen(
                inventoryId = id, state = state,
                onBack = { navController.popBackStack() },
                onRetry = inventoryViewModel::load,
                onNewEntry = { navController.navigate(AppDestinations.entry(id)) { launchSingleTop = true } },
            )
        }
        composable(
            AppDestinations.ENTRY,
            arguments = listOf(navArgument("inventoryId") { type = NavType.StringType; defaultValue = "" }),
        ) {
            val entryViewModel: EntryViewModel = viewModel(factory = entryFactory)
            EntryScreen(
                inventoryState = state, viewModel = entryViewModel,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack(AppDestinations.INVENTORY, inclusive = false) },
                onRetryInventory = inventoryViewModel::load,
            )
        }
    }
}
