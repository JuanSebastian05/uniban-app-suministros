package co.edu.udea.uniban.suministros

import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.data.auth.AuthException
import co.edu.udea.uniban.suministros.data.auth.ProfileState
import co.edu.udea.uniban.suministros.data.auth.Registration
import co.edu.udea.uniban.suministros.data.auth.fetchProfileState
import co.edu.udea.uniban.suministros.data.auth.sendProfile
import co.edu.udea.uniban.suministros.data.remote.RegisterProfileDto
import co.edu.udea.uniban.suministros.data.remote.UserApi
import co.edu.udea.uniban.suministros.data.remote.UserProfileDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class ProfileRegistrationTest {
    private val registration = Registration("Ana Pérez", "1020304050", null, "ana@unibanagro.com")

    private class RecordingApi(val reply: () -> Response<Unit>) : UserApi {
        val calls = mutableListOf<Pair<String, RegisterProfileDto>>()
        val meCalls = mutableListOf<String>()
        override suspend fun register(authorization: String, profile: RegisterProfileDto): Response<Unit> {
            calls += authorization to profile
            return reply()
        }

        override suspend fun me(authorization: String): Response<UserProfileDto> {
            meCalls += authorization
            val response = reply()
            return if (response.isSuccessful) Response.success(UserProfileDto("Ana Pérez"))
            else Response.error(response.code(), checkNotNull(response.errorBody()))
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
    fun duplicateDocumentIsItsOwnError() = runTest {
        val body = """{"detail":{"code":"DUPLICATE_DOCUMENT","message":"Ya existe una cuenta con este documento."}}"""
        val api = RecordingApi { Response.error(409, body.toResponseBody()) }
        assertEquals(AuthError.DocumentInUse, sendProfile(api, "t", registration).error())
    }

    @Test
    fun otherConflictsAreServerErrors() = runTest {
        listOf("{}", """{"detail":{"code":"OTHER"}}""", "no es json").forEach { body ->
            val api = RecordingApi { Response.error(409, body.toResponseBody()) }
            assertEquals(AuthError.Server, sendProfile(api, "t", registration).error())
        }
    }

    @Test
    fun noConnectionIsNetworkError() = runTest {
        val api = RecordingApi { throw IOException("sin red") }
        assertEquals(AuthError.Network, sendProfile(api, "t", registration).error())
    }

    // --- P-02: cuenta sin perfil en el backend ---

    @Test
    fun existingProfileIsComplete() = runTest {
        val api = RecordingApi { Response.success(200, Unit) }
        var name: String? = null
        assertEquals(ProfileState.Complete, fetchProfileState(api, "token-1") { name = it })
        assertEquals("Ana Pérez", name)
        assertEquals(listOf("Bearer token-1"), api.meCalls)
    }

    @Test
    fun unregisteredAccountIsMissing() = runTest {
        val api = RecordingApi { Response.error(404, """{"detail":"Usuario no registrado."}""".toResponseBody()) }
        assertEquals(ProfileState.Missing, fetchProfileState(api, "t"))
    }

    @Test
    fun failuresThatDoNotProveAMissingProfileAreUnknown() = runTest {
        listOf(401, 403, 500, 503).forEach { code ->
            val api = RecordingApi { Response.error(code, "{}".toResponseBody()) }
            assertEquals("código $code", ProfileState.Unknown, fetchProfileState(api, "t"))
        }
    }

    @Test
    fun noConnectionOrAnyOtherErrorLeavesTheProfileUnknown() = runTest {
        assertEquals(ProfileState.Unknown, fetchProfileState(RecordingApi { throw IOException("sin red") }, "t"))
        assertEquals(ProfileState.Unknown, fetchProfileState(RecordingApi { throw IllegalStateException("x") }, "t"))
    }

    @Test
    fun cancellationIsNotSwallowed() = runTest {
        val api = RecordingApi { throw CancellationException("cancelado") }
        try {
            fetchProfileState(api, "t")
            fail("debía propagar la cancelación")
        } catch (_: CancellationException) {
        }
    }
}
