package co.edu.udea.uniban.suministros.data

import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.RecommendationEntity
import co.edu.udea.uniban.suministros.data.local.StarterInventory
import co.edu.udea.uniban.suministros.data.remote.ApiErrorDto
import co.edu.udea.uniban.suministros.data.remote.RecommendationApi
import co.edu.udea.uniban.suministros.data.remote.RecommendationDto
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.Response

/** Un insumo de empaque de la recomendación. El Equipo 2 no entrega «exceso»: solo requerido, disponible y déficit. */
data class RecommendationSupply(
    val name: String,
    /** Fecha prevista (AAAA-MM-DD) en la que se necesita. */
    val date: String,
    val requiredQuantity: Double,
    val availableQuantity: Double,
    val deficit: Double,
)

/** Última recomendación guardada de una parcela (HU_12). Las fechas llegan como texto ISO y se muestran tal cual. */
data class ParcelRecommendation(
    val parcelId: String,
    val period: String,
    /** Instante en que el backend la guardó (ISO con zona). */
    val generatedAt: String,
    val sourceVersion: String,
    val volumeBunches: Double,
    /** Fecha y hora límite de corte, sin zona horaria. */
    val cutDeadline: String,
    val meetsWindow: Boolean,
    /** Observación del Equipo 2 (por qué no cumple la ventana), si la hay. */
    val reason: String?,
    val supplies: List<RecommendationSupply>,
)

/** Resultado de pedir una recomendación nueva. */
sealed interface RecommendationResult {
    data object Saved : RecommendationResult

    /** La parcela aún no tiene aforamiento del Equipo 1: sin él no se puede recomendar. */
    data object NoAforamiento : RecommendationResult

    /** El servicio de recomendaciones (Equipo 2) no respondió bien; el backend sí. */
    data object ServiceUnavailable : RecommendationResult

    /** Sin red o nuestro servidor no respondió. */
    data object Offline : RecommendationResult

    /** El token no sirve o la cuenta no tiene perfil. */
    data object SessionInvalid : RecommendationResult

    data object Failed : RecommendationResult
}

/** Resultado de consultar la última recomendación guardada en el servidor. */
enum class RecommendationRefresh { UPDATED, NONE, FAILED }

/** Lo que necesita la pantalla; permite probar el ViewModel sin Room ni red (RT_08). */
interface RecommendationSource {
    /** Última copia local (nula si no hay): funciona sin conexión. */
    fun observeRecommendation(parcelId: String): Flow<ParcelRecommendation?>

    /** Trae del servidor la última recomendación guardada (no llama al Equipo 2). */
    suspend fun refreshRecommendation(parcelId: String): RecommendationRefresh

    /** Pide una recomendación nueva: puede tardar hasta unos minutos. */
    suspend fun requestRecommendation(parcelId: String): RecommendationResult
}

/**
 * Recomendaciones de una sola cuenta (HU_12). Se piden **a nuestro backend**, que consulta al Equipo 2 (RT_11); la última
 * se guarda en la base local de la cuenta para verla sin conexión.
 *
 * [idToken] devuelve el token de esta misma cuenta. [warmUp] despierta nuestro servidor antes del POST (Render lo duerme
 * a los 15 min); es de mejor esfuerzo. En la sesión de demostración no hay servidor.
 */
class RecommendationRepository(
    database: InventoryDatabase,
    private val api: RecommendationApi,
    private val ownerUid: String,
    private val warmUp: suspend () -> Unit = {},
    private val idToken: suspend () -> String?,
) : RecommendationSource {
    private val dao = database.parcelDao()
    private val gson = Gson()

    private val isDemo get() = ownerUid == StarterInventory.DEMO_OWNER

    override fun observeRecommendation(parcelId: String): Flow<ParcelRecommendation?> =
        dao.observeRecommendation(parcelId).map { it?.toDomain() }

    override suspend fun refreshRecommendation(parcelId: String): RecommendationRefresh {
        if (isDemo) return RecommendationRefresh.NONE
        return try {
            val token = idToken() ?: return RecommendationRefresh.FAILED
            val response = api.latest("Bearer $token", parcelId)
            val body = response.body()
            when {
                response.isSuccessful && body != null -> {
                    dao.upsertRecommendation(body.toEntity())
                    RecommendationRefresh.UPDATED
                }
                response.code() == 404 -> RecommendationRefresh.NONE
                else -> RecommendationRefresh.FAILED
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            RecommendationRefresh.FAILED
        }
    }

    override suspend fun requestRecommendation(parcelId: String): RecommendationResult {
        if (isDemo) return RecommendationResult.Failed // sin servidor no hay recomendaciones
        return try {
            val token = idToken() ?: return RecommendationResult.SessionInvalid
            try {
                warmUp()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Mejor esfuerzo: si no despertó, el POST lo intenta igual.
            }
            val response = api.request("Bearer $token", parcelId)
            val body = response.body()
            val code = response.code()
            when {
                response.isSuccessful && body != null -> {
                    dao.upsertRecommendation(body.toEntity())
                    RecommendationResult.Saved
                }
                code == 409 && errorCode(response) == NO_AFORAMIENTO -> RecommendationResult.NoAforamiento
                code == 401 || code == 403 -> RecommendationResult.SessionInvalid
                code == 502 || (code == 503 && errorCode(response) == EQUIPO2_UNAVAILABLE) ->
                    RecommendationResult.ServiceUnavailable
                code == 408 || code == 429 || code >= 500 -> RecommendationResult.Offline
                else -> RecommendationResult.Failed
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            RecommendationResult.Offline
        } catch (_: Exception) {
            RecommendationResult.Failed
        }
    }

    private fun errorCode(response: Response<*>): String? = runCatching {
        gson.fromJson(response.errorBody()?.charStream(), ApiErrorDto::class.java)?.detail?.code
    }.getOrNull()

    private fun RecommendationDto.toEntity() = RecommendationEntity(
        parcelId = parcelId,
        recommendationId = id,
        period = period,
        generatedAt = generatedAt,
        sourceVersion = sourceVersion,
        volumeBunches = volumeBunches,
        cutDeadline = cutDeadline,
        meetsWindow = meetsWindow,
        reason = plan?.reason?.takeIf { it.isNotBlank() },
        suppliesJson = gson.toJson(
            supplies.orEmpty().map { RecommendationSupply(it.name, it.date, it.requiredQuantity, it.availableQuantity, it.deficit) },
        ),
    )

    private fun RecommendationEntity.toDomain() = ParcelRecommendation(
        parcelId = parcelId,
        period = period,
        generatedAt = generatedAt,
        sourceVersion = sourceVersion,
        volumeBunches = volumeBunches,
        cutDeadline = cutDeadline,
        meetsWindow = meetsWindow,
        reason = reason,
        supplies = runCatching { gson.fromJson(suppliesJson, Array<RecommendationSupply>::class.java).toList() }
            .getOrDefault(emptyList()),
    )

    private companion object {
        const val NO_AFORAMIENTO = "NO_AFORAMIENTO"
        const val EQUIPO2_UNAVAILABLE = "EQUIPO2_UNAVAILABLE"
    }
}
