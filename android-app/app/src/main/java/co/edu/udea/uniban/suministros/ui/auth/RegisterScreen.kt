@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package co.edu.udea.uniban.suministros.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.udea.uniban.suministros.R
import co.edu.udea.uniban.suministros.data.auth.AuthError

/** Registro de productores: misma banda y tarjeta que el login (HU_01). */
@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onNameChange: (String) -> Unit,
    onDocumentChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    // Mientras se crea la cuenta no se abandona la pantalla a medias.
    BackHandler(enabled = state.isLoading) {}
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .imePadding().verticalScroll(rememberScrollState()),
    ) {
        AuthHeader()
        Column(
            Modifier.padding(horizontal = 20.dp).pullUp(CardOverlap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    RegisterForm(
                        state, onNameChange, onDocumentChange, onPhoneChange, onEmailChange,
                        onPasswordChange, onConfirmPasswordChange, onTogglePassword, onSubmit,
                    )
                    TextButton(
                        onClick = onBack,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().testTag("register-back"),
                    ) { Text(stringResource(R.string.register_have_account)) }
                }
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun RegisterForm(
    state: RegisterUiState,
    onNameChange: (String) -> Unit,
    onDocumentChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
) {
    val enabled = !state.isLoading
    val next = ImeAction.Next
    Text(
        stringResource(R.string.register_title),
        fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (!state.firebaseConfigured) NotConfiguredBanner()
    LabeledField(
        label = stringResource(R.string.register_name_label),
        value = state.name, onValueChange = onNameChange,
        placeholder = stringResource(R.string.register_name_placeholder), enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = next),
        modifier = Modifier.testTag("register-name").autofill(AutofillType.PersonFullName, onNameChange),
    )
    LabeledField(
        label = stringResource(R.string.register_document_label),
        value = state.document, onValueChange = onDocumentChange,
        placeholder = stringResource(R.string.register_document_placeholder), enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = next),
        modifier = Modifier.testTag("register-document"),
    )
    LabeledField(
        label = stringResource(R.string.register_phone_label),
        value = state.phone, onValueChange = onPhoneChange,
        placeholder = stringResource(R.string.register_phone_placeholder), enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = next),
        modifier = Modifier.testTag("register-phone").autofill(AutofillType.PhoneNumber, onPhoneChange),
    )
    LabeledField(
        label = stringResource(R.string.login_email_label),
        value = state.email, onValueChange = onEmailChange,
        placeholder = stringResource(R.string.login_email_placeholder), enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = next),
        modifier = Modifier.testTag("register-email").autofill(AutofillType.EmailAddress, onEmailChange),
    )
    val transformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
    LabeledField(
        label = stringResource(R.string.login_password_label),
        value = state.password, onValueChange = onPasswordChange,
        placeholder = stringResource(R.string.register_password_placeholder), enabled = enabled,
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = next),
        trailingIcon = {
            IconButton(onClick = onTogglePassword) {
                Icon(
                    if (state.passwordVisible) VisibilityOffIcon else VisibilityIcon,
                    contentDescription = stringResource(
                        if (state.passwordVisible) R.string.login_hide_password else R.string.login_show_password,
                    ),
                )
            }
        },
        modifier = Modifier.testTag("register-password").autofill(AutofillType.NewPassword, onPasswordChange),
    )
    LabeledField(
        label = stringResource(R.string.register_confirm_label),
        value = state.confirmPassword, onValueChange = onConfirmPasswordChange,
        placeholder = stringResource(R.string.register_password_placeholder), enabled = enabled,
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.testTag("register-confirm"),
    )
    state.error?.let { ErrorBanner(stringResource(it.messageRes())) }
    Button(
        onClick = onSubmit,
        enabled = enabled,
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("register-submit"),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            stringResource(if (state.isLoading) R.string.register_submitting else R.string.register_submit),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun RegisterError.messageRes(): Int = when (this) {
    RegisterError.EmptyFields -> R.string.register_error_empty
    RegisterError.InvalidEmail -> R.string.login_error_invalid_email
    RegisterError.ShortPassword -> R.string.register_error_short_password
    RegisterError.PasswordMismatch -> R.string.register_error_mismatch
    is RegisterError.Auth -> when (error) {
        // Al crear una cuenta, Firebase solo da credenciales inválidas por un correo mal formado.
        AuthError.InvalidCredentials -> R.string.login_error_invalid_email
        AuthError.Network -> R.string.register_error_network
        AuthError.TooManyRequests -> R.string.login_error_too_many
        AuthError.AccountDisabled -> R.string.login_error_disabled
        AuthError.NotConfigured -> R.string.login_error_not_configured
        AuthError.EmailInUse -> R.string.register_error_email_in_use
        AuthError.WeakPassword -> R.string.register_error_weak_password
        AuthError.Server -> R.string.register_error_server
        AuthError.Unknown -> R.string.register_error_unknown
    }
}
