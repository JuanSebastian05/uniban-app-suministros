package co.edu.udea.uniban.suministros.ui.activities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.ActivitySupply
import co.edu.udea.uniban.suministros.data.ActivityStockException
import co.edu.udea.uniban.suministros.data.ActivityRegistrar
import co.edu.udea.uniban.suministros.data.Quantity
import co.edu.udea.uniban.suministros.data.SavedActivity
import co.edu.udea.uniban.suministros.data.local.ActivityType
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Una fila de «Insumos usados»: el insumo del inventario y la cantidad escrita (HU_08). */
data class SupplyRow(
    val key: Int,
    val inventoryId: String = "",
    val quantity: String = "",
    val itemError: String? = null,
    val quantityError: String? = null,
)

data class ActivityFormUiState(
    val parcelId: String = "",
    val type: ActivityType? = null,
    val date: LocalDate = LocalDate.now(),
    val observation: String = "",
    val rows: List<SupplyRow> = emptyList(),
    val parcelError: String? = null,
    val typeError: String? = null,
    val error: String? = null,
    val saving: Boolean = false,
    val saved: SavedActivity? = null,
)

/**
 * Formulario «Registrar actividad» (HU_08): parcela con ciclo activo, tipo, fecha, observación opcional y,
 * en el mismo flujo, los insumos usados. Se guarda todo junto en el dispositivo (funciona sin conexión).
 */
class ActivityFormViewModel(
    private val repository: ActivityRegistrar,
    private val savedState: SavedStateHandle,
    initialParcelId: String = "",
) : ViewModel() {
    // El mismo identificador en cada reintento: guardar dos veces no duplica la actividad ni sus consumos.
    private val requestId = savedState.get<String>("requestId")
        ?: UUID.randomUUID().toString().also { savedState["requestId"] = it }
    private var nextKey = 0

    var state by mutableStateOf(ActivityFormUiState(
        parcelId = savedState["parcelId"] ?: initialParcelId,
        type = ActivityType.fromCode(savedState["type"]),
        date = savedState.get<String>("date")?.let(LocalDate::parse) ?: LocalDate.now(),
        observation = savedState["observation"] ?: "",
    ))
        private set

    fun selectParcel(id: String) {
        savedState["parcelId"] = id
        state = state.copy(parcelId = id, parcelError = null, error = null)
    }

    fun selectType(value: ActivityType) {
        savedState["type"] = value.name
        state = state.copy(type = value, typeError = null, error = null)
    }

    fun changeDate(value: LocalDate) {
        savedState["date"] = value.toString()
        state = state.copy(date = value, error = null)
    }

    fun changeObservation(value: String) {
        savedState["observation"] = value.take(500)
        state = state.copy(observation = value.take(500), error = null)
    }

    fun addRow() {
        state = state.copy(rows = state.rows + SupplyRow(key = nextKey++), error = null)
    }

    fun removeRow(key: Int) {
        state = state.copy(rows = state.rows.filterNot { it.key == key }, error = null)
    }

    fun selectRowItem(key: Int, inventoryId: String) = updateRow(key) { copy(inventoryId = inventoryId, itemError = null) }

    fun changeRowQuantity(key: Int, value: String) = updateRow(key) { copy(quantity = value, quantityError = null) }

    private fun updateRow(key: Int, change: SupplyRow.() -> SupplyRow) {
        state = state.copy(rows = state.rows.map { if (it.key == key) it.change() else it }, error = null)
    }

    fun save() {
        if (state.saving || state.saved != null) return
        val chosen = state.rows.map { it.inventoryId }.filter { it.isNotBlank() }
        val rows = state.rows.map { row ->
            val itemError = when {
                row.inventoryId.isBlank() -> "Selecciona un insumo."
                chosen.count { it == row.inventoryId } > 1 -> "Este insumo ya está en la lista."
                else -> null
            }
            row.copy(
                itemError = itemError,
                quantityError = runCatching { Quantity.parse(row.quantity) }.exceptionOrNull()?.message,
            )
        }
        val parcelError = if (state.parcelId.isBlank()) "Selecciona la parcela." else null
        val typeError = if (state.type == null) "Selecciona el tipo de actividad." else null
        state = state.copy(rows = rows, parcelError = parcelError, typeError = typeError, error = null)
        val type = state.type
        if (parcelError != null || type == null || rows.any { it.itemError != null || it.quantityError != null }) return

        val form = state
        state = state.copy(saving = true)
        viewModelScope.launch {
            try {
                val saved = repository.registerActivity(
                    requestId, form.parcelId, type, form.date, form.observation,
                    rows.map { ActivitySupply(it.inventoryId, it.quantity) },
                )
                state = state.copy(saving = false, saved = saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ActivityStockException) {
                // No se guardó nada: se marca la fila cuyo insumo no alcanza.
                state = state.copy(
                    saving = false,
                    rows = state.rows.map {
                        if (it.inventoryId == error.inventoryId) it.copy(quantityError = error.message) else it
                    },
                )
            } catch (error: IllegalArgumentException) {
                state = state.copy(saving = false, error = error.message)
            } catch (_: Exception) {
                state = state.copy(saving = false, error = "No se pudo guardar. Tus datos siguen aquí; intenta de nuevo.")
            }
        }
    }
}
