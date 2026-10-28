package co.edu.udea.uniban.suministros.ui.parcels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.CycleOperationResult
import co.edu.udea.uniban.suministros.data.CycleStatus
import co.edu.udea.uniban.suministros.data.ParcelRepository
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ParcelsUiState(
    val loading: Boolean = true,
    val parcels: List<ParcelSummary> = emptyList(),
    val error: String? = null,
    /** No se pudo actualizar con el servidor: se muestran los datos guardados en el dispositivo. */
    val offline: Boolean = false,
    val cycleSaving: Boolean = false,
    val cycleError: String? = null,
)

private data class CycleActionState(val saving: Boolean = false, val error: String? = null)

/**
 * Lista de parcelas de la cuenta. Muestra siempre la copia local (funciona sin conexión) y, si hay
 * red, la actualiza con lo que tiene el servidor.
 */
class ParcelsViewModel(private val repository: ParcelRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ParcelsUiState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private val refreshFailed = MutableStateFlow(false)
    private val cycleAction = MutableStateFlow(CycleActionState())
    private var pendingCycleStart: Pair<String, Pair<LocalDate, String>>? = null

    init { load() }

    /** Consumos de una parcela para su detalle; los entrega Room y se actualizan solos. */
    fun consumptions(parcelId: String) = repository.consumptions(parcelId)

    /** Actividades de una parcela para su detalle (HU_08). */
    fun activities(parcelId: String) = repository.activities(parcelId)

    fun changeCycleStage(parcelId: String, status: CycleStatus) = runCycleAction {
        repository.changeCycleStage(parcelId, status)
    }

    fun finishCycle(parcelId: String, endDate: LocalDate) = runCycleAction {
        repository.finishCycle(parcelId, endDate)
    }

    fun startCycle(parcelId: String, startDate: LocalDate) {
        val request = pendingCycleStart?.takeIf { it.first == parcelId && it.second.first == startDate }
            ?: (parcelId to (startDate to UUID.randomUUID().toString())).also { pendingCycleStart = it }
        runCycleAction {
            repository.startCycle(parcelId, request.second.second, startDate).also {
                if (it == CycleOperationResult.Saved) pendingCycleStart = null
            }
        }
    }

    private fun runCycleAction(action: suspend () -> CycleOperationResult) {
        if (cycleAction.value.saving) return
        cycleAction.value = CycleActionState(saving = true)
        viewModelScope.launch {
            val result = try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                CycleOperationResult.Failed("No se pudo actualizar el ciclo. Intenta de nuevo.")
            }
            cycleAction.value = when (result) {
                CycleOperationResult.Saved -> CycleActionState()
                is CycleOperationResult.Failed -> CycleActionState(error = result.message)
            }
        }
    }

    fun load() {
        loadJob?.cancel()
        mutableState.value = ParcelsUiState()
        refreshFailed.value = false
        cycleAction.value = CycleActionState()
        loadJob = viewModelScope.launch {
            launch { refreshFailed.value = !repository.refresh() && !repository.localOnly }
            try {
                combine(repository.parcels, refreshFailed, cycleAction) { parcels, failed, action ->
                    ParcelsUiState(
                        loading = false, parcels = parcels, offline = failed,
                        cycleSaving = action.saving, cycleError = action.error,
                    )
                }.collect { mutableState.value = it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = ParcelsUiState(loading = false, error = "No se pudieron abrir las parcelas. Intenta de nuevo.")
            }
        }
    }
}
