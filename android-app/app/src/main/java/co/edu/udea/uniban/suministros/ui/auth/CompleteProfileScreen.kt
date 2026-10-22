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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.udea.uniban.suministros.R
import co.edu.udea.uniban.suministros.data.auth.AuthError

/**
 * Completar el perfil de una cuenta que existe en Firebase pero no en el backend. Misma banda y tarjeta
 * que el login. No se puede salir hacia el inventario: solo guardar el perfil o usar otra cuenta.
 */
@Composable
fun CompleteProfileScreen(
    state: CompleteProfileUiState,
    onNameChange: (String) -> Unit,
    onDocumentChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSignOut: () -> Unit,
) {
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
                    CompleteProfileForm(state, onNameChange, onDocumentChange, onPhoneChange, onSubmit)
                    TextButton(
                        onClick = onSignOut,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().testTag("complete-profile-sign-out"),
                    ) { Text(stringResource(R.string.complete_profile_sign_out)) }
                }
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun CompleteProfileForm(
    state: CompleteProfileUiState,
    onNameChange: (String) -> Unit,
    onDocumentChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val enabled = !state.isLoading
    Text(
        stringResource(R.string.complete_profile_title),
        fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        stringResource(R.string.complete_profile_message, state.email.ifEmpty { "—" }),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("complete-profile-message"),
    )
    LabeledField(
        label = stringResource(R.string.register_name_label),
        value = state.name, onValueChange = onNameChange,
        placeholder = stringResource(R.string.register_name_placeholder), enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.testTag("complete-profile-name"),
    )
    LabeledField(
        label = stringResource(R.string.register_document_label),
        value = state.document, onValueChange = onDocumentChange,
        placeholder = stringResource(R.string.register_document_placeholder), enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.testTag("complete-profile-document"),
    )
    LabeledField(
        label = stringResource(R.string.register_phone_label),
        value = state.phone, onValueChange = onPhoneChange,
        placeholder = stringResource(R.string.register_phone_placeholder), enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.testTag("complete-profile-phone"),
    )
    state.error?.let { ErrorBanner(stringResource(it.messageRes())) }
    if (state.isLoading) {
        Text(
            stringResource(R.string.server_wakeup_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("server-wakeup-hint"),
        )
    }
    Button(
        onClick = onSubmit,
        enabled = enabled,
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("complete-profile-submit"),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            stringResource(
                if (state.isLoading) R.string.complete_profile_submitting else R.string.complete_profile_submit,
            ),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun CompleteProfileError.messageRes(): Int = when (this) {
    CompleteProfileError.EmptyFields -> R.string.register_error_empty
    is CompleteProfileError.Auth -> when (error) {
        AuthError.Network -> R.string.complete_profile_error_network
        AuthError.DocumentInUse -> R.string.register_error_document_in_use
        else -> R.string.register_error_server
    }
}
