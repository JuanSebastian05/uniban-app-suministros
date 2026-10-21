package co.edu.udea.uniban.suministros.data

import co.edu.udea.uniban.suministros.data.local.InventoryDatabase
import co.edu.udea.uniban.suministros.data.local.InventorySyncContext
import co.edu.udea.uniban.suministros.data.local.MovementEntity
import co.edu.udea.uniban.suministros.data.local.SyncStatus
import co.edu.udea.uniban.suministros.data.remote.InventoryDto
import co.edu.udea.uniban.suministros.data.remote.LocationDto
import co.edu.udea.uniban.suministros.data.remote.MovementApi
import co.edu.udea.uniban.suministros.data.remote.MovementDto
import co.edu.udea.uniban.suministros.data.remote.ProducerDto
import co.edu.udea.uniban.suministros.data.remote.SupplyDto
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Resultado de una pasada de sincronización.
 * [retryLater] es verdadero cuando el fallo fue de red o del servidor y conviene reintentar.
 */
data class SyncSummary(val synced: Int, val failed: Int, val retryLater: Boolean)

/**
 * Envía al backend los movimientos locales pendientes (HU_06).
 *
 * Estados (RT_02): PENDING → SYNCING → SYNCED, o ERROR si el envío falla. Los ERROR se
 * conservan y se reintentan en la siguiente pasada. El backend es idempotente por UUID
 * (RT_07), así que reenviar un movimiento que ya llegó no lo duplica.
 *
 * [database] es la base de una sola cuenta e [idToken] devuelve el token de esa misma cuenta
 * (o `null` si ya no es la sesión abierta): los movimientos de un usuario solo viajan con su
 * propio token, y el backend los guarda en el inventario de ese usuario.
 */
class MovementSyncRepository(
    database: InventoryDatabase,
    private val api: MovementApi,
    private val idToken: suspend () -> String?,
) {
    private val dao = database.inventoryDao()

    private sealed interface Outcome {
        data object Accepted : Outcome
        data object Rejected : Outcome // el servidor lo rechazó (4xx): reintentar sin cambios no ayuda ya
        data object Unavailable : Outcome // sin red, tiempo agotado o error del servidor
    }

    suspend fun syncPending(): SyncSummary {
        val token = try {
            idToken()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return SyncSummary(0, 0, retryLater = true) // p. ej. sin red para renovar el token
        } ?: return SyncSummary(0, 0, retryLater = false) // la cuenta no tiene sesión: se envía al volver
        val authorization = "Bearer $token"
        // Solo corre una sincronización a la vez (trabajo único de WorkManager): cualquier
        // SYNCING es residuo de un cierre anterior y se retoma.
        dao.resetInterruptedSyncing()
        var synced = 0
        var failed = 0
        var retryLater = false
        for (movement in dao.findSyncable()) {
            if (dao.markSyncing(movement.id) != 1) continue
            val outcome = try {
                send(movement, authorization)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { dao.finishSyncing(movement.id, SyncStatus.PENDING) }
                throw cancelled
            } catch (_: IOException) {
                Outcome.Unavailable
            } catch (_: Exception) {
                Outcome.Rejected
            }
            when (outcome) {
                Outcome.Accepted -> {
                    withContext(NonCancellable) { dao.finishSyncing(movement.id, SyncStatus.SYNCED) }
                    synced++
                }
                Outcome.Rejected -> {
                    withContext(NonCancellable) { dao.finishSyncing(movement.id, SyncStatus.ERROR) }
                    failed++
                }
                Outcome.Unavailable -> {
                    withContext(NonCancellable) { dao.finishSyncing(movement.id, SyncStatus.ERROR) }
                    failed++
                    retryLater = true
                    break // sin conexión no tiene sentido intentar el resto; siguen PENDING
                }
            }
        }
        return SyncSummary(synced, failed, retryLater)
    }

    private suspend fun send(movement: MovementEntity, authorization: String): Outcome {
        val context = dao.findSyncContext(movement.inventoryId) ?: return Outcome.Rejected
        val response = api.send(authorization, toDto(movement, context))
        val code = response.code()
        return when {
            response.isSuccessful -> Outcome.Accepted
            // Token vencido o perfil aún sin crear: el movimiento no tiene la culpa; se reintenta.
            code == 401 || code == 403 -> Outcome.Unavailable
            code == 408 || code == 429 || code >= 500 -> Outcome.Unavailable
            else -> Outcome.Rejected
        }
    }

    private fun toDto(movement: MovementEntity, context: InventorySyncContext) = MovementDto(
        id = movement.id,
        type = movement.type,
        quantity = decimal(movement.quantity),
        date = movement.date,
        observation = movement.observation,
        createdAt = instant(movement.createdAt),
        updatedAt = instant(movement.updatedAt),
        inventory = InventoryDto(
            id = context.inventoryId,
            initialQuantity = decimal(context.initialQuantity),
            location = LocationDto(
                id = context.locationId,
                name = context.locationName,
                producer = ProducerDto(context.producerId, context.producerName),
            ),
            supply = SupplyDto(
                context.supplyId, context.supplyName, context.supplyCategory, context.supplyUnit,
            ),
        ),
        parcelId = movement.parcelId,
        cycleId = movement.cycleId,
        lossReason = movement.lossReason,
    )

    // Float → decimal de dos posiciones, igual que Quantity, para no enviar ruido binario.
    private fun decimal(value: Float): BigDecimal =
        BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP)

    // El backend guarda hasta microsegundos; se envía con precisión de milisegundos.
    private fun instant(text: String): String = Instant.parse(text).truncatedTo(ChronoUnit.MILLIS).toString()
}
