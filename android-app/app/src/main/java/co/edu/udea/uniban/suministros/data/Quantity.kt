package co.edu.udea.uniban.suministros.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

object Quantity {
    private val maximum = BigDecimal("99999.99")
    private val inputPattern = Regex("""(?:\d+(?:[.,]\d{1,2})?|[.,]\d{1,2})""")

    fun parse(text: String): Float {
        val normalized = text.trim().replace(',', '.')
        require(inputPattern.matches(normalized)) { "Ingresa una cantidad con máximo dos decimales." }
        val decimal = normalized.toBigDecimalOrNull()
        require(decimal != null && decimal > BigDecimal.ZERO) { "La cantidad debe ser mayor que cero." }
        require(decimal <= maximum) { "La cantidad máxima es 99.999,99." }
        return decimal.toFloat()
    }

    fun add(current: Float, incoming: Float): Float {
        require(current.isFinite() && incoming.isFinite() && current >= 0 && incoming > 0) {
            "La cantidad no es válida."
        }
        // El cálculo decimal intermedio evita acumular el error binario del Float.
        val total = current.toString().toBigDecimal().add(incoming.toString().toBigDecimal())
            .setScale(2, RoundingMode.HALF_UP)
        require(total <= maximum) { "La existencia resultante supera 99.999,99." }
        return total.toFloat()
    }

    fun format(value: Float): String = String.format(Locale.forLanguageTag("es-CO"), "%.2f", value)
}

