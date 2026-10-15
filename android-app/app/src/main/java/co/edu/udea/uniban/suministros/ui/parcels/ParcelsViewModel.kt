package co.edu.udea.uniban.suministros.ui.parcels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.ParcelRepository
import co.edu.udea.uniban.suministros.data.local.ParcelSummary
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
)

/**
 * Lista de parcelas de la cuenta. Muestra siempre la copia local (funciona sin conexión) y, si hay
 * red, la actualiza con lo que tiene el servidor.
 */
class ParcelsViewModel(private val repository: ParcelRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ParcelsUiState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private val refreshFailed = MutableStateFlow(false)

    init { load() }

    /** Consumos de una parcela para su detalle; los entrega Room y se actualizan solos. */
    fun consumptions(parcelId: String) = repository.consumptions(parcelId)

    fun load() {
        loadJob?.cancel()
        mutableState.value = ParcelsUiState()
        refreshFailed.value = false
        loadJob = viewModelScope.launch {
            launch { refreshFailed.value = !repository.refresh() && !repository.localOnly }
            try {
                combine(repository.parcels, refreshFailed) { parcels, failed ->
                    ParcelsUiState(loading = false, parcels = parcels, offline = failed)
                }.collect { mutableState.value = it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = ParcelsUiState(loading = false, error = "No se pudieron abrir las parcelas. Intenta de nuevo.")
            }
        }
    }
}
