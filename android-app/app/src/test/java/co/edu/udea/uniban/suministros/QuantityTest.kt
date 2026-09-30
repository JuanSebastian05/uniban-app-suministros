package co.edu.udea.uniban.suministros

import co.edu.udea.uniban.suministros.data.Quantity
import org.junit.Assert.*
import org.junit.Test

class QuantityTest {
    @Test
    fun acceptsPositiveAmountsWithCommaOrPoint() {
        assertEquals(1.25f, Quantity.parse("1,25"), 0.0001f)
        assertEquals(1.5f, Quantity.parse("1.5"), 0.0001f)
        assertEquals(0.25f, Quantity.parse(".25"), 0.0001f)
        assertEquals(12f, Quantity.parse("12"), 0.0001f)
    }

    @Test
    fun rejectsInvalidOrOverlyPreciseAmounts() {
        listOf("", "0", "-1", "1.234", "NaN", "Infinity", "1e3", "1,2.3", "100000").forEach {
            assertThrows("Debe rechazar $it", IllegalArgumentException::class.java) { Quantity.parse(it) }
        }
    }

    @Test
    fun repeatedSmallEntriesDoNotAccumulateBinaryRounding() {
        var stock = 0f
        repeat(100) { stock = Quantity.add(stock, 0.1f) }
        assertEquals(10f, stock, 0.0001f)
        assertThrows(IllegalArgumentException::class.java) { Quantity.add(99999.99f, 0.01f) }
    }

    @Test
    fun formatsWithTwoDecimalPlaces() {
        assertEquals("1,50", Quantity.format(1.5f))
        assertEquals("120,00", Quantity.format(120f))
    }
}

