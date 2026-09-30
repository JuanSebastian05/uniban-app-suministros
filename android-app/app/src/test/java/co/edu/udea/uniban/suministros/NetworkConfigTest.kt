package co.edu.udea.uniban.suministros

import co.edu.udea.uniban.suministros.core.network.NetworkModule
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prueba técnica de la configuración base (no corresponde a ninguna HU). */
class NetworkConfigTest {

    @Test
    fun apiBaseUrl_usaPrefijoVersionado() {
        assertTrue(NetworkModule.apiBaseUrl.endsWith("/api/v1/"))
    }
}
