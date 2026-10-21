package co.edu.udea.uniban.suministros.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.local.InventoryItem
import co.edu.udea.uniban.suministros.data.local.MovementEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * [detailMovements] corresponde a [detailId]. El detalle compara ambos antes de mostrarlos,
 * para no pintar los movimientos del insumo anterior mientras llega la consulta nueva.
 */
data class InventoryUiState(
    val loading: Boolean = true,
    val items: List<InventoryItem> = emptyList(),
    val pendingCount: Int = 0,
    val detailId: String? = null,
    val detailMovements: List<MovementEntity> = emptyList(),
    val error: String? = null,
)

class InventoryViewModel(private val repository: InventoryRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(InventoryUiState())
    val state = mutableState.asStateFlow()
    private val selectedId = MutableStateFlow<String?>(null)
    private var loadJob: Job? = null

    init { load() }

    /** El detalle anuncia qué insumo abre para que Room consulte solo sus movimientos. */
    fun select(inventoryId: String) {
        selectedId.value = inventoryId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load() {
        loadJob?.cancel()
        mutableState.value = InventoryUiState()
        loadJob = viewModelScope.launch {
            try {
                repository.initializeDemo()
                val detail = selectedId.flatMapLatest { id ->
                    if (id == null) flowOf(null to emptyList<MovementEntity>())
                    else repository.recentMovements(id).map { id to it }
                }
                combine(repository.inventory, repository.pendingCount, detail) { items, pending, (id, movements) ->
                    InventoryUiState(
                        loading = false,
                        items = items,
                        pendingCount = pending,
                        detailId = id,
                        detailMovements = movements,
                    )
                }.collect { mutableState.value = it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = InventoryUiState(
                    loading = false, error = "No se pudo abrir el inventario. Intenta de nuevo.",
                )
            }
        }
    }
}
