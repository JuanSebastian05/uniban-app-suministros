package co.edu.udea.uniban.suministros.data

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Reglas del formulario de parcela (HU_14). JVM puro, sin Android. */
class ParcelValidationTest {
    @Test fun nombreObligatorioYLimpio() {
        assertNotNull(ParcelValidation.nameError(""))
        assertNotNull(ParcelValidation.nameError("   "))
        assertNull(ParcelValidation.nameError(" La Esperanza "))
        assertEquals("La Palma", ParcelValidation.normalizeName("  La   Palma  "))
    }

    @Test fun nombreMaximo120Caracteres() {
        assertNull(ParcelValidation.nameError("x".repeat(120)))
        assertNotNull(ParcelValidation.nameError("x".repeat(121)))
    }

    @Test fun nombreSeComparaSinMayusculasNiEspacios() {
        assertEquals(ParcelValidation.nameKey("La Esperanza"), ParcelValidation.nameKey("  LA   esperanza "))
    }

    @Test fun areaValida() {
        listOf("2.4", "2,4", "0,5", "1", "99999,99", " 3.25 ").forEach { assertNull(it, ParcelValidation.areaError(it)) }
        assertEquals(BigDecimal("2.40"), ParcelValidation.parseArea("2,4"))
    }

    @Test fun areaInvalida() {
        listOf("", "0", "0,00", "-1", "2,345", "100000", "abc", "1.2.3").forEach {
            assertNotNull(it, ParcelValidation.areaError(it))
        }
    }

    @Test fun formatoDeArea() {
        assertEquals("2,4 ha", ParcelValidation.formatArea("2.40"))
        assertEquals("3 ha", ParcelValidation.formatArea("3.00"))
        assertEquals("1,25 ha", ParcelValidation.formatArea("1.25"))
    }

    @Test fun diaDelCiclo() {
        val today = LocalDate.of(2024, 1, 24)
        assertEquals(1L, ParcelValidation.cycleDay("2024-01-24", today))
        assertEquals(10L, ParcelValidation.cycleDay("2024-01-15", today))
        assertNull(ParcelValidation.cycleDay("2024-02-01", today)) // aún no comienza
        assertNull(ParcelValidation.cycleDay("no-es-fecha", today))
    }

    @Test fun formatoDeFecha() {
        assertEquals("15/01/2024", ParcelValidation.formatDate("2024-01-15"))
        assertEquals("raro", ParcelValidation.formatDate("raro"))
    }
}
