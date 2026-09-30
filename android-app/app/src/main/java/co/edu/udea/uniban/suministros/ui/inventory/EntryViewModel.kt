package co.edu.udea.uniban.suministros.ui.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.Quantity
import co.edu.udea.uniban.suministros.data.SavedEntry
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class EntryUiState(
    val inventoryId: String = "",
    val quantity: String = "",
    val date: LocalDate = LocalDate.now(),
    val observation: String = "",
    val restoring: Boolean = true,
    val saving: Boolean = false,
    val itemError: String? = null,
    val quantityError: String? = null,
    val error: String? = null,
    val saved: SavedEntry? = null,
)

class EntryViewModel(
    private val repository: InventoryRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val requestId = savedState.get<String>("requestId")
        ?: UUID.randomUUID().toString().also { savedState["requestId"] = it }

    var state by mutableStateOf(EntryUiState(
        inventoryId = savedState["inventoryId"] ?: "",
        quantity = savedState["quantity"] ?: "",
        date = savedState.get<String>("date")?.let(LocalDate::parse) ?: LocalDate.now(),
        observation = savedState["observation"] ?: "",
    ))
        private set

    init { restore() }

    fun restore() {
        state = state.copy(restoring = true, error = null)
        viewModelScope.launch {
            try {
                state = state.copy(restoring = false, saved = repository.findSavedEntry(requestId))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state = state.copy(error = "No se pudo comprobar el guardado anterior. Intenta de nuevo.")
            }
        }
    }

    fun selectItem(id: String) {
        savedState["inventoryId"] = id
        state = state.copy(inventoryId = id, itemError = null, error = null)
    }

    fun changeQuantity(value: String) {
        savedState["quantity"] = value
        state = state.copy(quantity = value, quantityError = null, error = null)
    }

    fun changeDate(value: LocalDate) {
        savedState["date"] = value.toString()
        state = state.copy(date = value, error = null)
    }

    fun changeObservation(value: String) {
        savedState["observation"] = value.take(500)
        state = state.copy(observation = value.take(500), error = null)
    }

    fun save() {
        if (state.saving || state.restoring || state.saved != null) return
        val itemError = if (state.inventoryId.isBlank()) "Selecciona un insumo." else null
        val quantityError = runCatching { Quantity.parse(state.quantity) }.exceptionOrNull()?.message
        state = state.copy(itemError = itemError, quantityError = quantityError, error = null)
        if (itemError != null || quantityError != null) return

        val form = state
        state = state.copy(saving = true)
        viewModelScope.launch {
            try {
                val saved = repository.registerEntry(
                    requestId, form.inventoryId, form.quantity, form.date, form.observation,
                )
                state = state.copy(saving = false, saved = saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                state = state.copy(saving = false, error = error.message)
            } catch (_: Exception) {
                state = state.copy(saving = false, error = "No se pudo guardar. Tus datos siguen aquí; intenta de nuevo.")
            }
        }
    }
}

