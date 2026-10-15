package co.edu.udea.uniban.suministros.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import java.io.IOException

private const val ERROR_USER_DISABLED = "ERROR_USER_DISABLED"

/** Traduce excepciones de Firebase a [AuthError]. Función pura, sin red. */
internal fun mapFirebaseError(error: Throwable): AuthError = when (error) {
    is FirebaseNetworkException, is IOException -> AuthError.Network
    is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
    is FirebaseAuthUserCollisionException -> AuthError.EmailInUse
    // Subclase de FirebaseAuthInvalidCredentialsException: debe ir antes.
    is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
    is FirebaseAuthInvalidUserException ->
        if (error.errorCode == ERROR_USER_DISABLED) AuthError.AccountDisabled
        else AuthError.InvalidCredentials
    is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidCredentials
    is FirebaseAuthException ->
        if (error.errorCode == ERROR_USER_DISABLED) AuthError.AccountDisabled else AuthError.Unknown
    else -> AuthError.Unknown
}
