package co.edu.udea.uniban.suministros.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.HistoryFilter
import co.edu.udea.uniban.suministros.data.HistorySummary
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.MovementHistory
import co.edu.udea.uniban.suministros.data.local.HistoryMovement
import co.edu.udea.uniban.suministros.data.local.InventoryBalance
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Parcela que aparece en algún movimiento, para el filtro (HU_07). */
data class HistoryParcel(val id: String, val name: String)

data class HistoryUiState(
    val loading: Boolean = true,
    val filter: HistoryFilter = HistoryFilter(),
    val supplies: List<InventoryBalance> = emptyList(),
    val parcels: List<HistoryParcel> = emptyList(),
    val movements: List<HistoryMovement> = emptyList(),
    /** Solo existe cuando el historial está filtrado por un insumo: las unidades no se suman entre insumos. */
    val summary: HistorySummary? = null,
    val pendingCount: Int = 0,
    val error: String? = null,
) {
    val selectedSupply get() = supplies.find { it.id == filter.inventoryId }
}

/**
 * Historial y trazabilidad (HU_07). Lee solo de Room: funciona sin conexión. El argumento de
 * navegación `inventoryId` (vacío = todos) fija el insumo inicial.
 */
class HistoryViewModel(
    private val repository: InventoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val filter = MutableStateFlow(
        HistoryFilter(inventoryId = savedStateHandle.get<String>("inventoryId")?.takeIf { it.isNotBlank() }),
    )
    private val mutableState = MutableStateFlow(HistoryUiState(filter = filter.value))
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                combine(repository.history, repository.balances, repository.pendingCount, filter) { all, balances, pending, f ->
                    val supply = balances.find { it.id == f.inventoryId }
                    HistoryUiState(
                        loading = false,
                        filter = f,
                        supplies = balances,
                        parcels = all.mapNotNull { m -> m.parcelId?.let { HistoryParcel(it, m.parcelName ?: "Parcela no disponible") } }
                            .distinctBy { it.id }.sortedBy { it.name.lowercase() },
                        movements = MovementHistory.filter(all, f),
                        summary = supply?.let { MovementHistory.summary(it, all) },
                        pendingCount = pending,
                    )
                }.collect { mutableState.value = it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = "No se pudo abrir el historial. Intenta de nuevo.") }
            }
        }
    }

    fun selectSupply(inventoryId: String?) = filter.update { it.copy(inventoryId = inventoryId) }

    fun toggleType(type: String) = filter.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }

    fun selectParcel(parcelId: String?) = filter.update { it.copy(parcelId = parcelId) }

    /** Si el rango queda invertido, la otra fecha se mueve para que «desde» nunca supere a «hasta». */
    fun changeFrom(date: LocalDate?) = filter.update {
        it.copy(from = date, to = if (date != null && it.to != null && it.to.isBefore(date)) date else it.to)
    }

    fun changeTo(date: LocalDate?) = filter.update {
        it.copy(to = date, from = if (date != null && it.from != null && it.from.isAfter(date)) date else it.from)
    }

    /** Quita tipo, parcela y fechas; conserva el insumo. */
    fun clearFilters() = filter.update { HistoryFilter(inventoryId = it.inventoryId) }
}
