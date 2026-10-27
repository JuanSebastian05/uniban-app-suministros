package co.edu.udea.uniban.suministros.ui.parcels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.ParcelRecommendation
import co.edu.udea.uniban.suministros.data.RecommendationRefresh
import co.edu.udea.uniban.suministros.data.RecommendationResult
import co.edu.udea.uniban.suministros.data.RecommendationSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Por qué no se pudo obtener una recomendación nueva; la pantalla lo traduce a un mensaje sencillo. */
enum class RecommendationProblem { NO_AFORAMIENTO, SERVICE_UNAVAILABLE, OFFLINE, SESSION, OTHER }

/**
 * Estado de la sección «Recomendaciones» del detalle de la parcela (HU_12).
 *
 * - [recommendation] no nula: se muestra, también sin conexión (última copia guardada).
 * - Sin copia y [loading]: se está consultando la última guardada.
 * - Sin copia: «Sin recomendación aún» con el botón para pedirla.
 * - [requesting]: se pidió una nueva y se espera (puede tardar hasta un minuto o más).
 * - [problem]: la última petición falló; se muestra junto al botón para reintentar.
 */
data class RecommendationUiState(
    val loading: Boolean = true,
    val recommendation: ParcelRecommendation? = null,
    val requesting: Boolean = false,
    val problem: RecommendationProblem? = null,
)

/**
 * Lee la copia local de la última recomendación (Room) y la completa con la que haya guardada en el servidor; el botón
 * pide una nueva a nuestro backend, que consulta al Equipo 2. La app nunca habla con el Equipo 2 (RT_11) y no calcula nada.
 */
class RecommendationViewModel(
    private val source: RecommendationSource,
    private val parcelId: String,
) : ViewModel() {
    private val refreshed = MutableStateFlow(false)
    private val requesting = MutableStateFlow(false)
    private val problem = MutableStateFlow<RecommendationProblem?>(null)
    private val mutableState = MutableStateFlow(RecommendationUiState())
    val state = mutableState.asStateFlow()
    private var requestJob: Job? = null

    init {
        viewModelScope.launch {
            combine(source.observeRecommendation(parcelId), refreshed, requesting, problem) { local, done, busy, failure ->
                RecommendationUiState(
                    loading = !done && local == null,
                    recommendation = local,
                    requesting = busy,
                    problem = failure,
                )
            }.collect { mutableState.value = it }
        }
        viewModelScope.launch {
            try {
                source.refreshRecommendation(parcelId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Se queda con la copia local.
            }
            refreshed.value = true
        }
    }

    /** Pide una recomendación nueva. No hace nada si ya hay una petición en curso. */
    fun request() {
        if (requesting.value) return
        requesting.value = true
        problem.value = null
        requestJob = viewModelScope.launch {
            val result = try {
                source.requestRecommendation(parcelId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                RecommendationResult.Failed
            }
            problem.value = when (result) {
                RecommendationResult.Saved -> null
                RecommendationResult.NoAforamiento -> RecommendationProblem.NO_AFORAMIENTO
                RecommendationResult.ServiceUnavailable -> RecommendationProblem.SERVICE_UNAVAILABLE
                RecommendationResult.Offline -> RecommendationProblem.OFFLINE
                RecommendationResult.SessionInvalid -> RecommendationProblem.SESSION
                RecommendationResult.Failed -> RecommendationProblem.OTHER
            }
            requesting.value = false
        }
    }
}
