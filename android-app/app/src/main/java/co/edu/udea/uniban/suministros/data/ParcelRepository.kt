package co.edu.udea.uniban.suministros.data

import androidx.room.withTransaction
import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.ParcelEntity
import co.edu.udea.uniban.suministros.data.local.ProductionCycleEntity
import co.edu.udea.uniban.suministros.data.local.StarterInventory
import co.edu.udea.uniban.suministros.data.remote.ApiErrorDto
import co.edu.udea.uniban.suministros.data.remote.CycleDto
import co.edu.udea.uniban.suministros.data.remote.CycleResponseDto
import co.edu.udea.uniban.suministros.data.remote.ParcelApi
import co.edu.udea.uniban.suministros.data.remote.ParcelDto
import co.edu.udea.uniban.suministros.data.remote.ParcelResponseDto
import com.google.gson.Gson
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
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
) {
    private val dao = database.parcelDao()
    val parcels = dao.observeParcels()

    private val isDemo get() = ownerUid == StarterInventory.DEMO_OWNER

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
                ParcelSaveResult.Saved
            }
            code == 409 && errorCode(response) == "DUPLICATE_NAME" -> ParcelSaveResult.DuplicateName
            code == 401 || code == 403 -> ParcelSaveResult.Rejected(SESSION_MESSAGE)
            code == 408 || code == 429 || code >= 500 -> ParcelSaveResult.Offline
            else -> ParcelSaveResult.Rejected("El servidor no aceptó la parcela. Revisa los datos e intenta de nuevo.")
        }
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
            ParcelEntity(it.id, it.name, it.areaHa.setScale(2, RoundingMode.HALF_UP).toPlainString())
        })
        dao.upsertCycles(parcels.mapNotNull { parcel ->
            parcel.activeCycle?.let { ProductionCycleEntity(it.id, parcel.id, it.startDate, it.endDate, it.status) }
        })
    }

    private fun ParcelDto.toResponse() = ParcelResponseDto(
        id = id,
        name = name,
        areaHa = areaHa,
        activeCycle = cycle?.let { CycleResponseDto(it.id, it.startDate, null, it.status) },
    )

    private fun errorCode(response: Response<*>): String? = runCatching {
        Gson().fromJson(response.errorBody()?.charStream(), ApiErrorDto::class.java)?.detail?.code
    }.getOrNull()

    private companion object {
        const val SESSION_MESSAGE = "Tu sesión no es válida. Cierra sesión e ingresa de nuevo."
    }
}
