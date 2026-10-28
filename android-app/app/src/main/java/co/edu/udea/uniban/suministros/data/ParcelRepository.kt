package co.edu.udea.uniban.suministros.data

import androidx.room.withTransaction
import co.edu.udea.uniban.suministros.data.local.AforamientoEntity
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.ParcelEntity
import co.edu.udea.uniban.suministros.data.local.ProductionCycleEntity
import co.edu.udea.uniban.suministros.data.local.ProductionCycleStatusChangeEntity
import co.edu.udea.uniban.suministros.data.local.StarterInventory
import co.edu.udea.uniban.suministros.data.remote.AforamientoDto
import co.edu.udea.uniban.suministros.data.remote.ApiErrorDto
import co.edu.udea.uniban.suministros.data.remote.CycleDto
import co.edu.udea.uniban.suministros.data.remote.CycleFinishDto
import co.edu.udea.uniban.suministros.data.remote.CycleResponseDto
import co.edu.udea.uniban.suministros.data.remote.CycleStageUpdateDto
import co.edu.udea.uniban.suministros.data.remote.CycleStartDto
import co.edu.udea.uniban.suministros.data.remote.ParcelApi
import co.edu.udea.uniban.suministros.data.remote.ParcelDto
import co.edu.udea.uniban.suministros.data.remote.ParcelResponseDto
import com.google.gson.Gson
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import retrofit2.Response

/** Estados del ciclo productivo (mockups). [name] es el valor que viaja al backend. */
enum class CycleStatus(val label: String) {
    EN_PREPARACION("En preparación"),
    PRODUCCION("Producción"),
    DESCANSO("Descanso"),
    ;

    companion object {
        fun fromCode(code: String?): CycleStatus? = entries.firstOrNull { it.name == code }
    }
}

data class CycleDraft(val id: String, val startDate: LocalDate, val status: CycleStatus)

/** Datos del formulario. [areaText] se valida con [ParcelValidation]. */
data class ParcelDraft(val name: String, val areaText: String, val cycle: CycleDraft?)

sealed interface ParcelSaveResult {
    data object Saved : ParcelSaveResult
    data object DuplicateName : ParcelSaveResult
    /** Sin red o el servidor no respondió: el formulario se conserva para reintentar. */
    data object Offline : ParcelSaveResult
    data class Rejected(val message: String) : ParcelSaveResult
}

sealed interface CycleOperationResult {
    data object Saved : CycleOperationResult
    data class Failed(val message: String) : CycleOperationResult
}

/** Reglas del formulario de parcela, compartidas por el ViewModel y el repositorio. */
object ParcelValidation {
    const val MAX_NAME_LENGTH = 120
    private val AREA_FORMAT = Regex("""^\d{1,5}([.,]\d{1,2})?$""")

    /** Quita espacios al inicio, al final y repetidos, igual que el backend. */
    fun normalizeName(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    /** Clave para comparar nombres sin distinguir mayúsculas. */
    fun nameKey(name: String): String = normalizeName(name).lowercase(Locale.ROOT)

    fun nameError(name: String): String? {
        val clean = normalizeName(name)
        return when {
            clean.isEmpty() -> "Escribe el nombre de la parcela."
            clean.length > MAX_NAME_LENGTH -> "Máximo $MAX_NAME_LENGTH caracteres."
            else -> null
        }
    }

    fun areaError(text: String): String? {
        val clean = text.trim()
        return when {
            clean.isEmpty() -> "Escribe el área en hectáreas."
            !AREA_FORMAT.matches(clean) -> "Usa un número con máximo dos decimales (hasta 99999,99)."
            parseArea(clean).signum() <= 0 -> "El área debe ser mayor que cero."
            else -> null
        }
    }

    /** Acepta coma o punto decimal. Llamar solo si [areaError] es nulo. */
    fun parseArea(text: String): BigDecimal =
        BigDecimal(text.trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY)

    /** «2,4 ha»: sin ceros sobrantes y con coma decimal, como el resto de la app. */
    fun formatArea(areaHa: String): String {
        val value = runCatching { BigDecimal(areaHa) }.getOrNull() ?: return "$areaHa ha"
        val plain = if (value.signum() == 0) "0" else value.stripTrailingZeros().toPlainString()
        return "${plain.replace('.', ',')} ha"
    }

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** «15/01/2024» a partir de la fecha ISO guardada; si no se puede leer, la devuelve tal cual. */
    fun formatDate(isoDate: String): String =
        runCatching { LocalDate.parse(isoDate).format(DATE_FORMAT) }.getOrDefault(isoDate)

    /**
     * Día del ciclo en [today] (el día de inicio es el día 1). Nulo si el ciclo aún no empieza o la
     * fecha no se puede leer (HU_02).
     */
    fun cycleDay(startIsoDate: String, today: LocalDate = LocalDate.now()): Long? {
        val start = runCatching { LocalDate.parse(startIsoDate) }.getOrNull() ?: return null
        val days = ChronoUnit.DAYS.between(start, today)
        return if (days < 0) null else days + 1
    }
}

/** Resultado de consultar al servidor el aforamiento de una parcela (HU_09). */
enum class AforamientoRefresh {
    /** El servidor entregó el aforamiento más reciente y quedó guardado en el dispositivo. */
    UPDATED,

    /** El servidor respondió que la parcela todavía no tiene aforamientos. */
    NONE,

    /** No se pudo consultar (sin red, servidor dormido o sesión no válida): se conserva la última copia local. */
    FAILED,
}

/** Lo que necesita la pantalla del aforamiento; permite probar su ViewModel sin Room ni red (RT_08). */
interface AforamientoSource {
    /** Última copia local del aforamiento de la parcela (nula si no hay): funciona sin conexión. */
    fun observeAforamiento(parcelId: String): Flow<AforamientoEntity?>

    /** Consulta el servidor y actualiza la copia local. */
    suspend fun refreshAforamiento(parcelId: String): AforamientoRefresh
}

/**
 * Parcelas de una sola cuenta (HU_14). Se registran **en el servidor** (se necesita conexión) y
 * se guarda una copia en la base local de la cuenta para verlas sin conexión.
 *
 * [idToken] devuelve el token de Firebase de esta misma cuenta (o `null` si ya no es la sesión
 * abierta). En la sesión de demostración (sin Firebase) las parcelas se guardan solo en el
 * dispositivo. La UI no conoce la red (RT_08).
 */
class ParcelRepository(
    private val database: InventoryDatabase,
    private val api: ParcelApi,
    private val ownerUid: String,
    private val idToken: suspend () -> String?,
) : AforamientoSource {
    private val dao = database.parcelDao()
    val parcels = dao.observeParcels()

    override fun observeAforamiento(parcelId: String) = dao.observeAforamiento(parcelId)

    /**
     * Pide `GET /parcels/{id}/aforamiento` y guarda la copia. El backend responde 404 mientras la parcela no tiene
     * aforamientos; cualquier otro fallo deja la última copia local intacta.
     */
    override suspend fun refreshAforamiento(parcelId: String): AforamientoRefresh {
        if (isDemo) return AforamientoRefresh.NONE // sin servidor no hay aforamientos
        return try {
            val token = idToken() ?: return AforamientoRefresh.FAILED
            val response = api.aforamiento("Bearer $token", parcelId)
            val body = response.body()
            when {
                response.isSuccessful && body != null -> {
                    dao.upsertAforamientos(listOf(body.toEntity()))
                    AforamientoRefresh.UPDATED
                }
                response.code() == 404 -> AforamientoRefresh.NONE
                else -> AforamientoRefresh.FAILED
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AforamientoRefresh.FAILED
        }
    }

    /** Consumos registrados en la parcela [parcelId] (HU_05). Se leen del dispositivo: funcionan sin conexión. */
    fun consumptions(parcelId: String) = database.inventoryDao().observeParcelConsumptions(parcelId)

    /** Actividades de la parcela para su detalle (HU_08); las entrega Room y se actualizan solas. */
    fun activities(parcelId: String) = database.activityDao().observeParcelActivities(parcelId)

    private val isDemo get() = ownerUid == StarterInventory.DEMO_OWNER

    /** Verdadero en la sesión de demostración: no hay servidor con el cual actualizar. */
    val localOnly: Boolean get() = isDemo

    /**
     * Registra la parcela [id]. Reintentar con el mismo [id] y los mismos datos no la duplica:
     * el backend es idempotente por UUID.
     */
    suspend fun create(id: String, draft: ParcelDraft): ParcelSaveResult {
        require(ParcelValidation.nameError(draft.name) == null && ParcelValidation.areaError(draft.areaText) == null) {
            "Datos de parcela inválidos."
        }
        val name = ParcelValidation.normalizeName(draft.name)
        if (dao.names().any { ParcelValidation.nameKey(it) == ParcelValidation.nameKey(name) }) {
            return ParcelSaveResult.DuplicateName
        }
        val request = ParcelDto(
            id = id,
            name = name,
            areaHa = ParcelValidation.parseArea(draft.areaText),
            cycle = draft.cycle?.let { CycleDto(it.id, it.startDate.toString(), it.status.name) },
        )
        if (isDemo) {
            saveLocal(listOf(request.toResponse()))
            draft.cycle?.let { recordCycleChange(it.id, null, it.status.name, it.startDate) }
            return ParcelSaveResult.Saved
        }
        val token = try {
            idToken()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return ParcelSaveResult.Offline // sin red para renovar el token
        } ?: return ParcelSaveResult.Rejected(SESSION_MESSAGE)

        val response = try {
            api.create("Bearer $token", request)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return ParcelSaveResult.Offline
        }
        val code = response.code()
        return when {
            response.isSuccessful -> {
                saveLocal(listOf(response.body() ?: request.toResponse()))
                draft.cycle?.let { recordCycleChange(it.id, null, it.status.name, it.startDate) }
                ParcelSaveResult.Saved
            }
            code == 409 && errorCode(response) == "DUPLICATE_NAME" -> ParcelSaveResult.DuplicateName
            code == 401 || code == 403 -> ParcelSaveResult.Rejected(SESSION_MESSAGE)
            code == 408 || code == 429 || code >= 500 -> ParcelSaveResult.Offline
            else -> ParcelSaveResult.Rejected("El servidor no aceptó la parcela. Revisa los datos e intenta de nuevo.")
        }
    }

    suspend fun changeCycleStage(parcelId: String, status: CycleStatus): CycleOperationResult {
        require(status == CycleStatus.EN_PREPARACION || status == CycleStatus.PRODUCCION)
        if (isDemo) {
            val cycle = dao.activeCycle(parcelId)
                ?: return CycleOperationResult.Failed("La parcela no tiene un ciclo activo.")
            database.withTransaction {
                dao.updateActiveCycleStatus(parcelId, status.name)
                dao.updateLifecycleState(parcelId, status.name, cycle.id)
                if (cycle.status != status.name) recordCycleChange(cycle.id, cycle.status, status.name, LocalDate.now())
            }
            return CycleOperationResult.Saved
        }
        val token = idTokenOrError() ?: return CycleOperationResult.Failed(SESSION_MESSAGE)
        return try {
            val previous = dao.activeCycle(parcelId)
            val effectiveDate = LocalDate.now()
            val response = api.changeCycleStage(
                "Bearer $token", parcelId, CycleStageUpdateDto(status.name, effectiveDate.toString()),
            )
            saveCycleResponse(response).also { result ->
                if (result == CycleOperationResult.Saved && previous != null && previous.status != status.name) {
                    recordCycleChange(previous.id, previous.status, status.name, effectiveDate)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            CycleOperationResult.Failed("No se pudo conectar. Revisa tu conexión e intenta de nuevo.")
        } catch (_: Exception) {
            CycleOperationResult.Failed("No se pudo actualizar la etapa. Intenta de nuevo.")
        }
    }

    suspend fun finishCycle(parcelId: String, endDate: LocalDate): CycleOperationResult {
        if (isDemo) {
            val cycle = dao.activeCycle(parcelId)
                ?: return CycleOperationResult.Failed("La parcela no tiene un ciclo activo.")
            if (endDate < LocalDate.parse(cycle.startDate)) {
                return CycleOperationResult.Failed("La fecha de fin no puede ser anterior al inicio del ciclo.")
            }
            database.withTransaction {
                dao.finishActiveCycle(parcelId, endDate.toString())
                dao.updateLifecycleState(parcelId, CycleStatus.DESCANSO.name, cycle.id)
                recordCycleChange(cycle.id, cycle.status, CycleStatus.DESCANSO.name, endDate)
            }
            return CycleOperationResult.Saved
        }
        val token = idTokenOrError() ?: return CycleOperationResult.Failed(SESSION_MESSAGE)
        return try {
            val previous = dao.activeCycle(parcelId)
            saveCycleResponse(api.finishCycle("Bearer $token", parcelId, CycleFinishDto(endDate.toString()))).also { result ->
                if (result == CycleOperationResult.Saved && previous != null) {
                    recordCycleChange(previous.id, previous.status, CycleStatus.DESCANSO.name, endDate)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            CycleOperationResult.Failed("No se pudo conectar. Revisa tu conexión e intenta de nuevo.")
        } catch (_: Exception) {
            CycleOperationResult.Failed("No se pudo finalizar el ciclo. Intenta de nuevo.")
        }
    }

    suspend fun startCycle(parcelId: String, id: String, startDate: LocalDate): CycleOperationResult {
        if (isDemo) {
            if (dao.activeCycle(parcelId) != null) {
                return CycleOperationResult.Failed("La parcela ya tiene un ciclo activo.")
            }
            val cycle = ProductionCycleEntity(id, parcelId, startDate.toString(), null, CycleStatus.EN_PREPARACION.name)
            database.withTransaction {
                dao.upsertCycle(cycle)
                dao.updateLifecycleState(parcelId, CycleStatus.EN_PREPARACION.name, id)
                recordCycleChange(id, null, CycleStatus.EN_PREPARACION.name, startDate)
            }
            return CycleOperationResult.Saved
        }
        if (dao.activeCycle(parcelId)?.id == id) return CycleOperationResult.Saved
        val token = idTokenOrError() ?: return CycleOperationResult.Failed(SESSION_MESSAGE)
        return try {
            saveCycleResponse(api.startCycle("Bearer $token", parcelId, CycleStartDto(id, startDate.toString()))).also { result ->
                if (result == CycleOperationResult.Saved) {
                    recordCycleChange(id, null, CycleStatus.EN_PREPARACION.name, startDate)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            CycleOperationResult.Failed("No se pudo conectar. Revisa tu conexión e intenta de nuevo.")
        } catch (_: Exception) {
            CycleOperationResult.Failed("No se pudo iniciar el ciclo. Intenta de nuevo.")
        }
    }

    private suspend fun idTokenOrError(): String? = try {
        idToken()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun saveCycleResponse(response: Response<ParcelResponseDto>): CycleOperationResult {
        val parcel = response.body()
        if (response.isSuccessful && parcel != null) {
            saveLocal(listOf(parcel))
            return CycleOperationResult.Saved
        }
        val message = runCatching {
            Gson().fromJson(response.errorBody()?.charStream(), ApiErrorDto::class.java)?.detail?.message
        }.getOrNull()
        return CycleOperationResult.Failed(message ?: "El servidor no pudo actualizar el ciclo (${response.code()}).")
    }

    private suspend fun recordCycleChange(
        cycleId: String, fromStatus: String?, toStatus: String, effectiveDate: LocalDate,
    ) {
        dao.upsertCycleStatusChange(
            ProductionCycleStatusChangeEntity(
                id = UUID.randomUUID().toString(), cycleId = cycleId, fromStatus = fromStatus,
                toStatus = toStatus, effectiveDate = effectiveDate.toString(), changedAt = Instant.now().toString(),
            ),
        )
    }

    /**
     * Reemplaza la copia local por las parcelas del servidor. Sin red (o en demostración) no hace
     * nada y se sigue mostrando la copia local. Devuelve `true` si se actualizó.
     */
    suspend fun refresh(): Boolean {
        if (isDemo) return false
        return try {
            val token = idToken() ?: return false
            val response = api.list("Bearer $token")
            val remote = response.body()
            if (!response.isSuccessful || remote == null) return false
            database.withTransaction {
                dao.deleteAllParcels()
                dao.deleteAllAforamientos() // sin FK a parcels: no se borran solos (A-04)
                saveLocalInTransaction(remote)
            }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun saveLocal(parcels: List<ParcelResponseDto>) =
        database.withTransaction { saveLocalInTransaction(parcels) }

    private suspend fun saveLocalInTransaction(parcels: List<ParcelResponseDto>) {
        dao.upsertParcels(parcels.map {
            ParcelEntity(
                it.id, it.name, it.areaHa.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                it.currentStatus ?: it.activeCycle?.status, it.activityCycleId ?: it.activeCycle?.id,
            )
        })
        dao.upsertCycles(parcels.flatMap { parcel ->
            listOfNotNull(parcel.activeCycle, parcel.lastCycle).map {
                ProductionCycleEntity(it.id, parcel.id, it.startDate, it.endDate, it.status)
            }
        })
        dao.upsertAforamientos(parcels.mapNotNull { parcel -> parcel.aforamiento?.toEntity(parcel.id) })
    }

    // El área y la confianza se guardan con los decimales del contrato (2 y 3) para mostrarlas sin ruido.
    private fun AforamientoDto.toEntity(forParcel: String = parcelId) = AforamientoEntity(
        parcelId = forParcel,
        aforamientoId = id,
        date = date,
        areaHa = areaHa.setScale(2, RoundingMode.HALF_UP).toPlainString(),
        estimatedPlants = estimatedPlants,
        confidence = confidence?.setScale(3, RoundingMode.HALF_UP)?.toPlainString(),
    )

    private fun ParcelDto.toResponse() = ParcelResponseDto(
        id = id,
        name = name,
        areaHa = areaHa,
        activeCycle = cycle?.let { CycleResponseDto(it.id, it.startDate, null, it.status) },
        currentStatus = cycle?.status,
        activityCycleId = cycle?.id,
    )

    private fun errorCode(response: Response<*>): String? = runCatching {
        Gson().fromJson(response.errorBody()?.charStream(), ApiErrorDto::class.java)?.detail?.code
    }.getOrNull()

    private companion object {
        const val SESSION_MESSAGE = "Tu sesión no es válida. Cierra sesión e ingresa de nuevo."
    }
}
