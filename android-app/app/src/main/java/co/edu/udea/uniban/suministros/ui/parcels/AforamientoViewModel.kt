package co.edu.udea.uniban.suministros.ui.parcels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.AforamientoRefresh
import co.edu.udea.uniban.suministros.data.AforamientoSource
import co.edu.udea.uniban.suministros.data.local.AforamientoEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Estado del aforamiento en el detalle de la parcela (HU_09).
 *
 * - [aforamiento] no nulo: se muestra, aunque la consulta haya fallado ([refreshFailed]: última copia guardada).
 * - Sin copia y [loading]: se está consultando por primera vez.
 * - Sin copia, consulta fallida: no se sabe si hay aforamiento → error con reintento.
 * - Sin copia, consulta exitosa: la parcela no tiene aforamientos → «Sin aforamiento recibido».
 */
data class AforamientoUiState(
    val loading: Boolean = true,
    val aforamiento: AforamientoEntity? = null,
    val refreshFailed: Boolean = false,
)

/**
 * Lee la copia local del aforamiento de una parcela (Room, funciona sin conexión) y, al abrir el detalle, pregunta
 * al backend por uno más reciente. Nunca habla con el Equipo 1: los datos llegan por nuestro backend (RT_11).
 */
class AforamientoViewModel(
    private val source: AforamientoSource,
    private val parcelId: String,
) : ViewModel() {
    private val refresh = MutableStateFlow<AforamientoRefresh?>(null)
    private val mutableState = MutableStateFlow(AforamientoUiState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            combine(source.observeAforamiento(parcelId), refresh) { local, result ->
                AforamientoUiState(
                    loading = result == null && local == null,
                    aforamiento = local,
                    refreshFailed = result == AforamientoRefresh.FAILED,
                )
            }.collect { mutableState.value = it }
        }
        reload()
    }

    /** Vuelve a consultar al servidor (al abrir el detalle y desde «Intentar de nuevo»). */
    fun reload() {
        refreshJob?.cancel()
        refresh.value = null
        refreshJob = viewModelScope.launch {
            refresh.value = try {
                source.refreshAforamiento(parcelId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                AforamientoRefresh.FAILED
            }
        }
    }
}
