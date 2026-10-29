package co.edu.udea.uniban.suministros.ui.production

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.util.UUID
import kotlin.math.floor

data class ProductionForm(
    val mode: String = "COSECHA", val quantity: String = "", val date: String = LocalDate.now().toString(),
    val time: String = "", val vehicle: String = "", val order: String = "1", val observation: String = "",
    val exceptionConfirmed: Boolean = false,
)

data class ProductionSaved(val mode: String, val quantity: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionViewModel(private val source: ProductionSource, private val saved: SavedStateHandle) : ViewModel() {
    val parcelId = MutableStateFlow(saved.get<String>("parcelId").orEmpty())
    val overview = parcelId.flatMapLatest { if (it.isBlank()) flowOf(ProductionOverview(loading = false)) else source.observe(it) }
        .catch { error = "No se pudo abrir el avance guardado."; emit(ProductionOverview(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductionOverview())
    var planId by mutableStateOf(saved.get<String>("planId").orEmpty()); private set
    var form by mutableStateOf(ProductionForm(
        mode = saved["mode"] ?: "COSECHA", quantity = saved["quantity"] ?: "", date = saved["date"] ?: LocalDate.now().toString(),
        time = saved["time"] ?: "", vehicle = saved["vehicle"] ?: "", order = saved["order"] ?: "1",
        observation = saved["observation"] ?: "", exceptionConfirmed = saved["exceptionConfirmed"] ?: false,
    )); private set
    var saving by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    var refreshing by mutableStateOf(false); private set
    var savedConfirmation by mutableStateOf(saved.get<Int>("savedQuantity")?.let {
        ProductionSaved(saved["savedMode"] ?: "COSECHA", it)
    }); private set

    fun dismissSaved() {
        savedConfirmation = null
        saved.remove<Int>("savedQuantity"); saved.remove<String>("savedMode")
    }
    private var requestId = saved.get<String>("requestId") ?: UUID.randomUUID().toString().also { saved["requestId"] = it }

    private fun persist() {
        saved["planId"] = planId; saved["mode"] = form.mode; saved["quantity"] = form.quantity
        saved["date"] = form.date; saved["time"] = form.time; saved["vehicle"] = form.vehicle
        saved["order"] = form.order; saved["observation"] = form.observation; saved["exceptionConfirmed"] = form.exceptionConfirmed
        saved["requestId"] = requestId
    }

    init { if (parcelId.value.isNotBlank()) refresh() }

    fun selectParcel(id: String) {
        if (id == parcelId.value) return
        parcelId.value = id; saved["parcelId"] = id; planId = ""; form = ProductionForm(); error = null; notice = null
        persist()
        refresh()
    }
    fun selectPlan(period: ProductionPeriod, mode: String = form.mode) {
        planId = period.reference.id
        val cut = runCatching { LocalDateTime.parse(period.plan.plan?.cutDate) }.getOrNull()
        form = ProductionForm(mode = mode,
            quantity = if (mode == "COSECHA") floor(period.remaining).toInt().takeIf { it > 0 }?.toString().orEmpty()
                else minOf(period.available, period.plan.plan?.estimatedLoadBunches ?: period.available).coerceAtLeast(0).toString(),
            date = if (mode == "DESPACHO") cut?.toLocalDate()?.toString() ?: LocalDate.now().toString() else LocalDate.now().toString(),
            time = cut?.toLocalTime()?.toString() ?: LocalTime.now().withSecond(0).withNano(0).toString(),
            vehicle = period.plan.plan?.vehicleId.orEmpty(), order = (period.plan.plan?.order ?: 1).toString())
        requestId = UUID.randomUUID().toString(); error = null; notice = null
        persist()
    }
    fun change(value: ProductionForm) { if (!saving) { form = value; error = null; notice = null; persist() } }
    fun refresh() {
        if (refreshing || parcelId.value.isBlank()) return
        viewModelScope.launch {
            refreshing = true; notice = null
            try { if (!source.refresh(parcelId.value)) notice = "No se pudo actualizar el avance. Se muestran los registros guardados." }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { notice = "No se pudo actualizar el avance. Se muestran los registros guardados." }
            finally { refreshing = false }
        }
    }
    fun save() {
        if (saving) return
        viewModelScope.launch {
            saving = true; error = null; notice = null
            try {
                val quantity = requireNotNull(form.quantity.toIntOrNull()?.takeIf { it > 0 }) { "Ingresa racimos enteros mayores que cero." }
                val date = runCatching { LocalDate.parse(form.date) }.getOrElse { throw IllegalArgumentException("Escribe una fecha válida: AAAA-MM-DD.") }
                if (form.mode == "COSECHA") source.harvest(requestId, planId, quantity, date, form.observation, form.exceptionConfirmed)
                else {
                    val time = runCatching { LocalTime.parse(form.time) }.getOrElse { throw IllegalArgumentException("Escribe una hora válida: HH:mm.") }
                    val at = LocalDateTime.of(date, time).atZone(ZoneId.of("America/Bogota")).toOffsetDateTime()
                    source.dispatch(requestId, planId, quantity, at, form.vehicle,
                        requireNotNull(form.order.toIntOrNull()) { "El orden debe ser un entero positivo." }, form.observation)
                }
                notice = "Registro guardado en este dispositivo. Pendiente de sincronización."
                savedConfirmation = ProductionSaved(form.mode, quantity)
                saved["savedMode"] = form.mode; saved["savedQuantity"] = quantity
                form = form.copy(quantity = "", observation = "", exceptionConfirmed = false)
                requestId = UUID.randomUUID().toString()
                persist()
            } catch (error: CancellationException) { throw error }
            catch (failure: Exception) { error = failure.message ?: "No se pudo guardar el registro." }
            finally { saving = false }
        }
    }
}
