package co.edu.udea.uniban.suministros.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.local.InventoryItem
import co.edu.udea.uniban.suministros.data.local.MovementEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class InventoryUiState(
    val loading: Boolean = true,
    val items: List<InventoryItem> = emptyList(),
    val movements: List<MovementEntity> = emptyList(),
    val error: String? = null,
) {
    val pendingCount: Int get() = movements.count { it.syncStatus == "PENDING" }
}

class InventoryViewModel(private val repository: InventoryRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(InventoryUiState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        mutableState.value = InventoryUiState()
        loadJob = viewModelScope.launch {
            try {
                repository.initializeDemo()
                combine(repository.inventory, repository.movements) { items, movements ->
                    InventoryUiState(loading = false, items = items, movements = movements)
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

