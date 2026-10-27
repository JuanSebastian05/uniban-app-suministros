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
import androidx.compose.runtime.rememberUpdatedState
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
import co.edu.udea.uniban.suministros.data.ParcelRepository
import co.edu.udea.uniban.suministros.data.RecommendationSource
import co.edu.udea.uniban.suministros.data.auth.AuthRepository
import co.edu.udea.uniban.suministros.data.auth.ProfileState
import co.edu.udea.uniban.suministros.data.auth.SessionState
import co.edu.udea.uniban.suministros.ui.activities.ActivitiesScreen
import co.edu.udea.uniban.suministros.ui.activities.ActivitiesViewModel
import co.edu.udea.uniban.suministros.ui.activities.ActivityFormScreen
import co.edu.udea.uniban.suministros.ui.activities.ActivityFormViewModel
import co.edu.udea.uniban.suministros.ui.auth.CompleteProfileScreen
import co.edu.udea.uniban.suministros.ui.auth.CompleteProfileViewModel
import co.edu.udea.uniban.suministros.ui.auth.LoginScreen
import co.edu.udea.uniban.suministros.ui.auth.LoginViewModel
import co.edu.udea.uniban.suministros.ui.auth.RegisterScreen
import co.edu.udea.uniban.suministros.ui.auth.RegisterViewModel
import co.edu.udea.uniban.suministros.ui.inventory.*
import co.edu.udea.uniban.suministros.ui.parcels.AforamientoViewModel
import co.edu.udea.uniban.suministros.ui.parcels.ParcelDetailScreen
import co.edu.udea.uniban.suministros.ui.parcels.ParcelFormScreen
import co.edu.udea.uniban.suministros.ui.parcels.ParcelFormViewModel
import co.edu.udea.uniban.suministros.ui.parcels.ParcelsScreen
import co.edu.udea.uniban.suministros.ui.parcels.ParcelsViewModel
import co.edu.udea.uniban.suministros.ui.parcels.RecommendationViewModel

/**
 * [repositoryFor] entrega el inventario de una cuenta (su propia base local): cada usuario que
 * entra ve y modifica solo sus insumos y movimientos.
 *
 * [parcelRepositoryFor] entrega las parcelas de una cuenta (HU_14). Si es nulo, no se muestran la
 * pestaña de parcelas ni la barra de pestañas.
 */
@Composable
fun AppNavHost(
    repositoryFor: (uid: String) -> InventoryRepository,
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
    parcelRepositoryFor: ((uid: String) -> ParcelRepository)? = null,
    // HU_12: recomendaciones de la cuenta. Si es nulo, el detalle de la parcela no muestra esa sección.
    recommendationRepositoryFor: ((uid: String) -> RecommendationSource)? = null,
) {
    val session by authRepository.sessionState.collectAsStateWithLifecycle()
    val profile by authRepository.profileState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val uid = (session as? SessionState.Authenticated)?.uid
    // No bloquea: la app abre con los datos locales y, cuando el backend responde que la cuenta no tiene
    // perfil (cuenta huérfana), el guard la lleva a completarlo. Sin red el perfil queda «desconocido».
    LaunchedEffect(uid) { if (uid != null) authRepository.refreshProfile() }
    val repository = remember(uid) { uid?.let(repositoryFor) }
    // Los ViewModel del inventario se crean al entrar al grafo MAIN, que solo existe con sesión;
    // al cerrar sesión el grafo sale de la pila y la siguiente cuenta recibe ViewModel nuevos.
    val currentRepository by rememberUpdatedState(repository)
    val parcelRepository = remember(uid, parcelRepositoryFor) { uid?.let { parcelRepositoryFor?.invoke(it) } }
    val currentParcelRepository by rememberUpdatedState(parcelRepository)
    val recommendationRepository = remember(uid, recommendationRepositoryFor) {
        uid?.let { recommendationRepositoryFor?.invoke(it) }
    }
    val currentRecommendationRepository by rememberUpdatedState(recommendationRepository)

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
    SessionGuard(navController, session, profile)

    val loginFactory = remember(authRepository) {
        viewModelFactory { initializer { LoginViewModel(authRepository, createSavedStateHandle()) } }
    }
    val registerFactory = remember(authRepository) {
        viewModelFactory { initializer { RegisterViewModel(authRepository, createSavedStateHandle()) } }
    }
    val completeProfileFactory = remember(authRepository) {
        viewModelFactory { initializer { CompleteProfileViewModel(authRepository, createSavedStateHandle()) } }
    }
    val inventoryFactory = remember {
        viewModelFactory { initializer { InventoryViewModel(checkNotNull(currentRepository)) } }
    }
    val syncStatusFactory = remember {
        viewModelFactory { initializer { SyncStatusViewModel(checkNotNull(currentRepository)) } }
    }
    val entryFactory = remember {
        viewModelFactory { initializer { EntryViewModel(checkNotNull(currentRepository), createSavedStateHandle()) } }
    }
    val historyFactory = remember {
        viewModelFactory { initializer { HistoryViewModel(checkNotNull(currentRepository), createSavedStateHandle()) } }
    }
    val parcelsFactory = remember {
        viewModelFactory { initializer { ParcelsViewModel(checkNotNull(currentParcelRepository)) } }
    }
    val parcelFormFactory = remember {
        viewModelFactory { initializer { ParcelFormViewModel(checkNotNull(currentParcelRepository), createSavedStateHandle()) } }
    }
    val activitiesFactory = remember {
        viewModelFactory { initializer { ActivitiesViewModel(checkNotNull(currentRepository)) } }
    }
    val activityFormFactory = remember {
        viewModelFactory { initializer { ActivityFormViewModel(checkNotNull(currentRepository), createSavedStateHandle()) } }
    }
    val tabsEnabled = parcelRepositoryFor != null
    val openTab: (MainTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(AppDestinations.INVENTORY) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
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
                onCreateAccount = { navController.navigate(AppDestinations.REGISTER) { launchSingleTop = true } },
            )
        }
        composable(AppDestinations.REGISTER) {
            val registerViewModel: RegisterViewModel = viewModel(factory = registerFactory)
            RegisterScreen(
                state = registerViewModel.state,
                onNameChange = registerViewModel::changeName,
                onDocumentChange = registerViewModel::changeDocument,
                onPhoneChange = registerViewModel::changePhone,
                onEmailChange = registerViewModel::changeEmail,
                onPasswordChange = registerViewModel::changePassword,
                onConfirmPasswordChange = registerViewModel::changeConfirmPassword,
                onTogglePassword = registerViewModel::togglePasswordVisible,
                onSubmit = registerViewModel::submit,
                onBack = { navController.popBackStack() },
            )
        }
        // Cuenta sin perfil en el backend: el guard trae aquí y devuelve al inventario al completarlo.
        composable(AppDestinations.COMPLETE_PROFILE) {
            val completeProfileViewModel: CompleteProfileViewModel = viewModel(factory = completeProfileFactory)
            CompleteProfileScreen(
                state = completeProfileViewModel.state,
                onNameChange = completeProfileViewModel::changeName,
                onDocumentChange = completeProfileViewModel::changeDocument,
                onPhoneChange = completeProfileViewModel::changePhone,
                onSubmit = completeProfileViewModel::submit,
                onSignOut = completeProfileViewModel::signOut,
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
                    navigationBar = if (tabsEnabled) {
                        { MainBottomBar(MainTab.INVENTORY, openTab) }
                    } else null,
                    onHistory = { navController.navigate(AppDestinations.history()) { launchSingleTop = true } },
                    onSyncStatus = { navController.navigate(AppDestinations.SYNC_STATUS) { launchSingleTop = true } },
                )
            }
            composable(AppDestinations.SYNC_STATUS) {
                val syncStatusViewModel: SyncStatusViewModel = viewModel(factory = syncStatusFactory)
                val state by syncStatusViewModel.state.collectAsStateWithLifecycle()
                SyncStatusScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onRetry = syncStatusViewModel::retry,
                )
            }
            composable(
                AppDestinations.DETAIL,
                arguments = listOf(navArgument("inventoryId") { type = NavType.StringType }),
            ) { entry ->
                val id = requireNotNull(entry.arguments?.getString("inventoryId"))
                val inventoryViewModel = mainViewModel(navController, entry, inventoryFactory)
                val state by inventoryViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(id) { inventoryViewModel.select(id) }
                InventoryDetailScreen(
                    inventoryId = id, state = state,
                    onBack = { navController.popBackStack() },
                    onRetry = inventoryViewModel::load,
                    onNewEntry = { navController.navigate(AppDestinations.entry(id)) { launchSingleTop = true } },
                    onHistory = { navController.navigate(AppDestinations.history(id)) { launchSingleTop = true } },
                )
            }
            composable(
                AppDestinations.HISTORY,
                arguments = listOf(navArgument("inventoryId") { type = NavType.StringType; defaultValue = "" }),
            ) {
                // ViewModel propio de la pantalla: los filtros se reinician cada vez que se abre.
                val historyViewModel: HistoryViewModel = viewModel(factory = historyFactory)
                val state by historyViewModel.state.collectAsStateWithLifecycle()
                HistoryScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onRetry = historyViewModel::load,
                    onSelectSupply = historyViewModel::selectSupply,
                    onToggleType = historyViewModel::toggleType,
                    onSelectParcel = historyViewModel::selectParcel,
                    onChangeFrom = historyViewModel::changeFrom,
                    onChangeTo = historyViewModel::changeTo,
                    onClearFilters = historyViewModel::clearFilters,
                )
            }
            composable(
                AppDestinations.ENTRY,
                arguments = listOf(navArgument("inventoryId") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                val inventoryViewModel = mainViewModel(navController, entry, inventoryFactory)
                val state by inventoryViewModel.state.collectAsStateWithLifecycle()
                val entryViewModel: EntryViewModel = viewModel(factory = entryFactory)
                // HU_05: las parcelas (copia local) para asociar un consumo; vacías si no hay pestaña de parcelas.
                val parcels = if (tabsEnabled) {
                    val parcelsViewModel = mainScopedViewModel<ParcelsViewModel>(navController, entry, parcelsFactory)
                    parcelsViewModel.state.collectAsStateWithLifecycle().value.parcels
                } else emptyList()
                EntryScreen(
                    inventoryState = state, viewModel = entryViewModel,
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack(AppDestinations.INVENTORY, inclusive = false) },
                    onRetryInventory = inventoryViewModel::load,
                    parcels = parcels,
                )
            }
            if (tabsEnabled) {
                composable(AppDestinations.PARCELS) { entry ->
                    val inventoryViewModel = mainViewModel(navController, entry, inventoryFactory)
                    val inventoryState by inventoryViewModel.state.collectAsStateWithLifecycle()
                    val parcelsViewModel = mainScopedViewModel<ParcelsViewModel>(navController, entry, parcelsFactory)
                    val state by parcelsViewModel.state.collectAsStateWithLifecycle()
                    ParcelsScreen(
                        state = state,
                        pendingCount = inventoryState.pendingCount,
                        onRetry = parcelsViewModel::load,
                        onNewParcel = { navController.navigate(AppDestinations.PARCEL_NEW) { launchSingleTop = true } },
                        onSelect = { navController.navigate(AppDestinations.parcelDetail(it)) { launchSingleTop = true } },
                        navigationBar = { MainBottomBar(MainTab.PARCELS, openTab) },
                    )
                }
                composable(
                    AppDestinations.PARCEL_DETAIL,
                    arguments = listOf(navArgument("parcelId") { type = NavType.StringType }),
                ) { entry ->
                    val id = requireNotNull(entry.arguments?.getString("parcelId"))
                    val inventoryViewModel = mainViewModel(navController, entry, inventoryFactory)
                    val inventoryState by inventoryViewModel.state.collectAsStateWithLifecycle()
                    // Mismo ViewModel que la lista: el detalle no vuelve a consultar ni al servidor ni a Room.
                    val parcelsViewModel = mainScopedViewModel<ParcelsViewModel>(navController, entry, parcelsFactory)
                    val state by parcelsViewModel.state.collectAsStateWithLifecycle()
                    val consumptions by remember(id) { parcelsViewModel.consumptions(id) }
                        .collectAsStateWithLifecycle(emptyList())
                    val activities by remember(id) { parcelsViewModel.activities(id) }
                        .collectAsStateWithLifecycle(emptyList())
                    // ViewModel propio del detalle: cada vez que se abre vuelve a consultar el aforamiento (HU_09).
                    val aforamientoViewModel: AforamientoViewModel = viewModel(
                        key = "aforamiento-$id",
                        factory = remember(id) {
                            viewModelFactory {
                                initializer { AforamientoViewModel(checkNotNull(currentParcelRepository), id) }
                            }
                        },
                    )
                    val aforamiento by aforamientoViewModel.state.collectAsStateWithLifecycle()
                    // HU_12: ViewModel propio del detalle; al abrirlo trae la última recomendación guardada en el servidor.
                    val recommendationViewModel: RecommendationViewModel? = if (recommendationRepositoryFor != null) {
                        viewModel(
                            key = "recommendation-$id",
                            factory = remember(id) {
                                viewModelFactory {
                                    initializer { RecommendationViewModel(checkNotNull(currentRecommendationRepository), id) }
                                }
                            },
                        )
                    } else null
                    val recommendation = recommendationViewModel?.state?.collectAsStateWithLifecycle()?.value
                    ParcelDetailScreen(
                        parcelId = id,
                        state = state,
                        pendingCount = inventoryState.pendingCount,
                        onBack = { navController.popBackStack() },
                        onRetry = parcelsViewModel::load,
                        consumptions = consumptions,
                        activities = activities,
                        aforamiento = aforamiento,
                        onRetryAforamiento = aforamientoViewModel::reload,
                        recommendation = recommendation,
                        onRequestRecommendation = { recommendationViewModel?.request() },
                        onNewActivity = {
                            navController.navigate(AppDestinations.activityNew(id)) { launchSingleTop = true }
                        },
                    )
                }
                composable(AppDestinations.ACTIVITIES) { entry ->
                    val inventoryViewModel = mainViewModel(navController, entry, inventoryFactory)
                    val inventoryState by inventoryViewModel.state.collectAsStateWithLifecycle()
                    val activitiesViewModel = mainScopedViewModel<ActivitiesViewModel>(navController, entry, activitiesFactory)
                    val activities by activitiesViewModel.activities.collectAsStateWithLifecycle()
                    ActivitiesScreen(
                        activities = activities,
                        pendingCount = inventoryState.pendingCount,
                        onNewActivity = { navController.navigate(AppDestinations.activityNew()) { launchSingleTop = true } },
                        navigationBar = { MainBottomBar(MainTab.ACTIVITIES, openTab) },
                    )
                }
                composable(
                    AppDestinations.ACTIVITY_NEW,
                    arguments = listOf(navArgument("parcelId") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    val inventoryViewModel = mainViewModel(navController, entry, inventoryFactory)
                    val inventoryState by inventoryViewModel.state.collectAsStateWithLifecycle()
                    val parcelsViewModel = mainScopedViewModel<ParcelsViewModel>(navController, entry, parcelsFactory)
                    val parcels by parcelsViewModel.state.collectAsStateWithLifecycle()
                    val formViewModel: ActivityFormViewModel = viewModel(factory = activityFormFactory)
                    ActivityFormScreen(
                        inventoryState = inventoryState,
                        parcels = parcels.parcels,
                        viewModel = formViewModel,
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() },
                        onRetryInventory = inventoryViewModel::load,
                    )
                }
                composable(AppDestinations.PARCEL_NEW) { entry ->
                    val inventoryViewModel = mainViewModel(navController, entry, inventoryFactory)
                    val inventoryState by inventoryViewModel.state.collectAsStateWithLifecycle()
                    val formViewModel: ParcelFormViewModel = viewModel(factory = parcelFormFactory)
                    ParcelFormScreen(
                        state = formViewModel.state,
                        viewModel = formViewModel,
                        pendingCount = inventoryState.pendingCount,
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack(AppDestinations.PARCELS, inclusive = false) },
                    )
                }
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

/** ViewModel de tipo [T] asociado al grafo [AppDestinations.MAIN] (se destruye al cerrar sesión). */
@Composable
private inline fun <reified T : androidx.lifecycle.ViewModel> mainScopedViewModel(
    navController: NavController,
    entry: NavBackStackEntry,
    factory: androidx.lifecycle.ViewModelProvider.Factory,
): T {
    val parent = remember(entry) { navController.getBackStackEntry(AppDestinations.MAIN) }
    return viewModel(parent, factory = factory)
}

/**
 * Guard global: sin sesión solo existen el login y el registro (cubre cierre de sesión y expiración con la app
 * abierta) y, al autenticarse, se reemplaza el login por el inventario. Si el backend confirma que la cuenta no
 * tiene perfil ([ProfileState.Missing]) solo existe la pantalla de completarlo; con el perfil completo vuelve
 * al inventario. Se vacía la pila para que Atrás no regrese a pantallas del otro estado.
 */
@Composable
private fun SessionGuard(navController: NavController, session: SessionState, profile: ProfileState) {
    LaunchedEffect(session, profile) {
        val current = navController.currentDestination ?: return@LaunchedEffect
        val inMain = current.hierarchy.any { it.route == AppDestinations.MAIN }
        val authenticated = session is SessionState.Authenticated
        val needsProfile = authenticated && profile == ProfileState.Missing
        val target = when {
            needsProfile && current.route != AppDestinations.COMPLETE_PROFILE -> AppDestinations.COMPLETE_PROFILE
            authenticated && !needsProfile && !inMain -> AppDestinations.MAIN
            session is SessionState.Unauthenticated && current.route !in PUBLIC_ROUTES -> AppDestinations.LOGIN
            else -> return@LaunchedEffect
        }
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}

private val PUBLIC_ROUTES = setOf(AppDestinations.LOGIN, AppDestinations.REGISTER)
