package co.edu.udea.uniban.suministros

import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.data.auth.AuthException
import co.edu.udea.uniban.suministros.data.auth.Registration
import co.edu.udea.uniban.suministros.data.auth.sendProfile
import co.edu.udea.uniban.suministros.data.remote.RegisterProfileDto
import co.edu.udea.uniban.suministros.data.remote.UserApi
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ProfileRegistrationTest {
    private val registration = Registration("Ana Pérez", "1020304050", null, "ana@unibanagro.com")

    private class RecordingApi(val reply: () -> Response<Unit>) : UserApi {
        val calls = mutableListOf<Pair<String, RegisterProfileDto>>()
        override suspend fun register(authorization: String, profile: RegisterProfileDto): Response<Unit> {
            calls += authorization to profile
            return reply()
        }
    }

    private fun Result<Unit>.error() = (exceptionOrNull() as AuthException).error

    @Test
    fun sendsTokenAsBearerAndProfileWithoutEmail() = runTest {
        val api = RecordingApi { Response.success(201, Unit) }
        assertTrue(sendProfile(api, "token-1", registration).isSuccess)
        assertEquals(listOf("Bearer token-1" to RegisterProfileDto("Ana Pérez", "1020304050", null)), api.calls)
    }

    @Test
    fun alreadyRegisteredCountsAsSuccess() = runTest {
        assertTrue(sendProfile(RecordingApi { Response.success(200, Unit) }, "t", registration).isSuccess)
    }

    @Test
    fun rejectedRequestIsServerError() = runTest {
        listOf(401, 422, 500, 503).forEach { code ->
            val api = RecordingApi { Response.error(code, "{}".toResponseBody()) }
            assertEquals(AuthError.Server, sendProfile(api, "t", registration).error())
        }
    }

    @Test
    fun noConnectionIsNetworkError() = runTest {
        val api = RecordingApi { throw IOException("sin red") }
        assertEquals(AuthError.Network, sendProfile(api, "t", registration).error())
    }
}
