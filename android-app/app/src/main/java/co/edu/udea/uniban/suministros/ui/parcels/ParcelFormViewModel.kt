package co.edu.udea.uniban.suministros.ui.parcels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.CycleDraft
import co.edu.udea.uniban.suministros.data.CycleStatus
import co.edu.udea.uniban.suministros.data.ParcelDraft
import co.edu.udea.uniban.suministros.data.ParcelRepository
import co.edu.udea.uniban.suministros.data.ParcelSaveResult
import co.edu.udea.uniban.suministros.data.ParcelValidation
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ParcelFormUiState(
    val name: String = "",
    val area: String = "",
    val startCycle: Boolean = false,
    val startDate: LocalDate = LocalDate.now(),
    val status: CycleStatus = CycleStatus.PRODUCCION,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val nameError: String? = null,
    val areaError: String? = null,
    val error: String? = null,
)

/**
 * Formulario de registro de parcela (HU_14). Los campos sobreviven a la rotación y a la
 * recreación del proceso. Los UUID de la parcela y del ciclo se conservan entre reintentos para
 * que el backend no la duplique; si el usuario cambia los datos después de un intento fallido,
 * se generan UUID nuevos (el anterior pudo haber llegado con otros datos).
 */
class ParcelFormViewModel(
    private val repository: ParcelRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    var state by mutableStateOf(
        ParcelFormUiState(
            name = savedState[KEY_NAME] ?: "",
            area = savedState[KEY_AREA] ?: "",
            startCycle = savedState[KEY_START_CYCLE] ?: false,
            startDate = savedState.get<String>(KEY_DATE)?.let(LocalDate::parse) ?: LocalDate.now(),
            status = savedState.get<String>(KEY_STATUS)?.let(CycleStatus::fromCode) ?: CycleStatus.PRODUCCION,
        )
    )
        private set

    private val parcelId get() = savedState.get<String>(KEY_PARCEL_ID) ?: newIds().first
    private val cycleId get() = savedState.get<String>(KEY_CYCLE_ID) ?: newIds().second
    private var attempted = false

    private fun newIds(): Pair<String, String> {
        val ids = UUID.randomUUID().toString() to UUID.randomUUID().toString()
        savedState[KEY_PARCEL_ID] = ids.first
        savedState[KEY_CYCLE_ID] = ids.second
        return ids
    }

    private fun edited(update: ParcelFormUiState.() -> ParcelFormUiState) {
        if (state.saving || state.saved) return
        if (attempted) { newIds(); attempted = false }
        state = state.update().copy(error = null)
    }

    fun changeName(value: String) {
        savedState[KEY_NAME] = value
        edited { copy(name = value, nameError = null) }
    }

    fun changeArea(value: String) {
        savedState[KEY_AREA] = value
        edited { copy(area = value, areaError = null) }
    }

    fun changeStartCycle(value: Boolean) {
        savedState[KEY_START_CYCLE] = value
        edited { copy(startCycle = value) }
    }

    fun changeStartDate(value: LocalDate) {
        savedState[KEY_DATE] = value.toString()
        edited { copy(startDate = value) }
    }

    fun changeStatus(value: CycleStatus) {
        savedState[KEY_STATUS] = value.name
        edited { copy(status = value) }
    }

    fun save() {
        if (state.saving || state.saved) return
        val nameError = ParcelValidation.nameError(state.name)
        val areaError = ParcelValidation.areaError(state.area)
        state = state.copy(nameError = nameError, areaError = areaError, error = null)
        if (nameError != null || areaError != null) return

        val form = state
        val draft = ParcelDraft(
            name = form.name,
            areaText = form.area,
            cycle = if (form.startCycle) CycleDraft(cycleId, form.startDate, form.status) else null,
        )
        val id = parcelId
        state = state.copy(saving = true)
        viewModelScope.launch {
            val result = try {
                repository.create(id, draft)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ParcelSaveResult.Rejected("No se pudo guardar la parcela. Tus datos siguen aquí; intenta de nuevo.")
            }
            attempted = result != ParcelSaveResult.Saved
            state = when (result) {
                ParcelSaveResult.Saved -> state.copy(saving = false, saved = true)
                ParcelSaveResult.DuplicateName ->
                    state.copy(saving = false, nameError = "Ya tienes una parcela con ese nombre.")
                ParcelSaveResult.Offline -> state.copy(
                    saving = false,
                    error = "Se necesita conexión a internet para registrar la parcela. Tus datos siguen aquí.",
                )
                is ParcelSaveResult.Rejected -> state.copy(saving = false, error = result.message)
            }
        }
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_AREA = "area"
        const val KEY_START_CYCLE = "startCycle"
        const val KEY_DATE = "startDate"
        const val KEY_STATUS = "status"
        const val KEY_PARCEL_ID = "parcelId"
        const val KEY_CYCLE_ID = "cycleId"
    }
}
