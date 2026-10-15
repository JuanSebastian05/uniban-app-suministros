@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package co.edu.udea.uniban.suministros.ui.auth

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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.udea.uniban.suministros.R
import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.ui.theme.UnibanGreenMedium

private val CardOverlap = 24.dp
private val FieldShape = RoundedCornerShape(12.dp)

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    onContinueDemo: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .imePadding().verticalScroll(rememberScrollState()),
    ) {
        LoginHeader()
        Column(
            Modifier.padding(horizontal = 20.dp).pullUp(CardOverlap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoginCard(state, onEmailChange, onPasswordChange, onTogglePassword, onSubmit, onContinueDemo)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.login_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun LoginHeader() {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding().padding(top = 48.dp, bottom = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoMark()
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 24.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.login_tagline),
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LoginCard(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    onContinueDemo: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.login_title),
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!state.firebaseConfigured) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = FieldShape,
                ) {
                    Text(
                        stringResource(R.string.login_error_not_configured),
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            LabeledField(
                label = stringResource(R.string.login_email_label),
                value = state.email,
                onValueChange = onEmailChange,
                placeholder = stringResource(R.string.login_email_placeholder),
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.testTag("login-email").autofill(AutofillType.EmailAddress, onEmailChange),
            )
            LabeledField(
                label = stringResource(R.string.login_password_label),
                value = state.password,
                onValueChange = onPasswordChange,
                placeholder = stringResource(R.string.login_password_placeholder),
                enabled = !state.isLoading,
                visualTransformation =
                    if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
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
                modifier = Modifier.testTag("login-password").autofill(AutofillType.Password, onPasswordChange),
            )
            state.error?.let { ErrorBanner(stringResource(it.messageRes())) }
            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                shape = FieldShape,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("login-submit"),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(if (state.isLoading) R.string.login_submitting else R.string.login_submit),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (state.demoAvailable) {
                TextButton(
                    onClick = onContinueDemo,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().testTag("login-demo"),
                ) { Text(stringResource(R.string.login_demo)) }
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            enabled = enabled, singleLine = true, shape = FieldShape,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            visualTransformation = visualTransformation, trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                disabledContainerColor = MaterialTheme.colorScheme.background,
                focusedBorderColor = UnibanGreenMedium,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = modifier.fillMaxWidth(),
        )
    }
}

private fun LoginError.messageRes(): Int = when (this) {
    LoginError.EmptyFields -> R.string.login_error_empty
    LoginError.InvalidEmail -> R.string.login_error_invalid_email
    is LoginError.Auth -> when (error) {
        AuthError.InvalidCredentials -> R.string.login_error_credentials
        AuthError.Network -> R.string.login_error_network
        AuthError.TooManyRequests -> R.string.login_error_too_many
        AuthError.AccountDisabled -> R.string.login_error_disabled
        AuthError.NotConfigured -> R.string.login_error_not_configured
        AuthError.Unknown -> R.string.login_error_unknown
    }
}

/** Sube el contenido [amount] sobre el elemento anterior (efecto «-mt-6» del mockup). */
private fun Modifier.pullUp(amount: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val offset = amount.roundToPx()
    layout(placeable.width, placeable.height - offset) { placeable.place(0, -offset) }
}
