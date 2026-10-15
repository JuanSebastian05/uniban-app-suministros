package co.edu.udea.uniban.suministros.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.auth.AuthRepository
import co.edu.udea.uniban.suministros.data.auth.SessionState
import co.edu.udea.uniban.suministros.ui.auth.LoginScreen
import co.edu.udea.uniban.suministros.ui.auth.LoginViewModel
import co.edu.udea.uniban.suministros.ui.inventory.*

@Composable
fun AppNavHost(
    repository: InventoryRepository,
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
) {
    val session by authRepository.sessionState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    if (session is SessionState.Loading) {
        Box(
            modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("session-loading"),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    // El destino inicial se fija una sola vez, con la sesión ya resuelta (sin parpadeo del login).
    val startDestination = remember {
        if (session is SessionState.Authenticated) AppDestinations.MAIN else AppDestinations.LOGIN
    }
    SessionGuard(navController, session)

    val loginFactory = remember(authRepository) {
        viewModelFactory { initializer { LoginViewModel(authRepository, createSavedStateHandle()) } }
    }
    val inventoryFactory = remember(repository) {
        viewModelFactory { initializer { InventoryViewModel(repository) } }
    }
    val entryFactory = remember(repository) {
        viewModelFactory { initializer { EntryViewModel(repository, createSavedStateHandle()) } }
    }

    NavHost(navController, startDestination = startDestination, modifier = modifier) {
        composable(AppDestinations.LOGIN) {
            val loginViewModel: LoginViewModel = viewModel(factory = loginFactory)
            LoginScreen(
                state = loginViewModel.state,
                onEmailChange = loginViewModel::changeEmail,
                onPasswordChange = loginViewModel::changePassword,
                onTogglePassword = loginViewModel::togglePasswordVisible,
                onSubmit = loginViewModel::submit,
                onContinueDemo = loginViewModel::continueAsDemo,
            )
        }
        // El inventario se carga solo dentro de este grafo: sin sesión no se abre ningún dato,
        // y al cerrar sesión el grafo sale de la pila y su ViewModel se destruye.
        navigation(route = AppDestinations.MAIN, startDestination = AppDestinations.INVENTORY) {
            composable(AppDestinations.INVENTORY) { entry ->
                val inventoryViewModel = mainViewModel(navController, entry, inventoryFactory)
                val state by inventoryViewModel.state.collectAsStateWithLifecycle()
                InventoryScreen(
                    state = state,
                    onRetry = inventoryViewModel::load,
                    onSelect = { navController.navigate(AppDestinations.detail(it)) },
                    onNewEntry = { navController.navigate(AppDestinations.entry()) { launchSingleTop = true } },
                    onLogout = authRepository::signOut,
                )
            }
            composable(
                AppDestinations.DETAIL,
                arguments = listOf(navArgument("inventoryId") { type = NavType.StringType }),
            ) { entry ->
                val id = requireNotNull(entry.arguments?.getString("inventoryId"))
                val inventoryViewModel = mainViewModel(navController, entry, inventoryFactory)
                val state by inventoryViewModel.state.collectAsStateWithLifecycle()
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
            ) { entry ->
                val inventoryViewModel = mainViewModel(navController, entry, inventoryFactory)
                val state by inventoryViewModel.state.collectAsStateWithLifecycle()
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
}

/** ViewModel de inventario compartido por las pantallas del grafo [AppDestinations.MAIN]. */
@Composable
private fun mainViewModel(
    navController: NavController,
    entry: NavBackStackEntry,
    factory: androidx.lifecycle.ViewModelProvider.Factory,
): InventoryViewModel {
    val parent = remember(entry) { navController.getBackStackEntry(AppDestinations.MAIN) }
    return viewModel(parent, factory = factory)
}

/**
 * Guard global: sin sesión solo existe el login (cubre cierre de sesión y expiración con la app
 * abierta) y, al autenticarse, se reemplaza el login por el inventario. Se vacía la pila para
 * que Atrás no regrese a pantallas del otro estado.
 */
@Composable
private fun SessionGuard(navController: NavController, session: SessionState) {
    LaunchedEffect(session) {
        val current = navController.currentDestination ?: return@LaunchedEffect
        val inMain = current.hierarchy.any { it.route == AppDestinations.MAIN }
        val target = when {
            session is SessionState.Authenticated && !inMain -> AppDestinations.MAIN
            session is SessionState.Unauthenticated && current.route != AppDestinations.LOGIN -> AppDestinations.LOGIN
            else -> return@LaunchedEffect
        }
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}
