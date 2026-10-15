package co.edu.udea.uniban.suministros

import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.data.auth.mapFirebaseError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28], application = android.app.Application::class)
class AuthErrorMapperTest {
    @Test
    fun wrongCredentialsAndUnknownUserShareTheSameError() {
        assertEquals(
            AuthError.InvalidCredentials,
            mapFirebaseError(FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "x")),
        )
        assertEquals(
            AuthError.InvalidCredentials,
            mapFirebaseError(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "x")),
        )
    }

    @Test
    fun disabledAccount() {
        assertEquals(
            AuthError.AccountDisabled,
            mapFirebaseError(FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "x")),
        )
    }

    @Test
    fun networkFailures() {
        assertEquals(AuthError.Network, mapFirebaseError(FirebaseNetworkException("x")))
        assertEquals(AuthError.Network, mapFirebaseError(IOException("x")))
    }

    @Test
    fun tooManyRequests() {
        assertEquals(AuthError.TooManyRequests, mapFirebaseError(FirebaseTooManyRequestsException("x")))
    }

    @Test
    fun anythingElseIsUnknown() {
        assertEquals(AuthError.Unknown, mapFirebaseError(IllegalStateException("x")))
    }
}
