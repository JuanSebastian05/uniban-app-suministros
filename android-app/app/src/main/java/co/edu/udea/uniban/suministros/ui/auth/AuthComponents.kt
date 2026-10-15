package co.edu.udea.uniban.suministros.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.edu.udea.uniban.suministros.ui.theme.UnibanErrorBorder

/** Contenedor del logotipo: cuadrado redondeado, blanco al 10 % con borde blanco al 20 %. */
@Composable
internal fun LogoMark(modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
        modifier = modifier.size(80.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(imageVector = LogoIcon, contentDescription = null, modifier = Modifier.size(44.dp))
        }
    }
}

/** Banner de error (el rojo se usa solo para errores); se anuncia a los lectores de pantalla. */
@Composable
internal fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.error,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, UnibanErrorBorder),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Pide al servicio de autocompletado del sistema (gestor de contraseñas) rellenar el campo. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.autofill(type: AutofillType, onFill: (String) -> Unit): Modifier = composed {
    val autofill = LocalAutofill.current
    val tree = LocalAutofillTree.current
    val node = remember(type) { AutofillNode(onFill = onFill, autofillTypes = listOf(type)) }
    tree += node
    onGloballyPositioned { node.boundingBox = it.boundsInWindow() }
        .onFocusChanged { focus ->
            autofill?.run {
                if (focus.isFocused) requestAutofillForNode(node) else cancelAutofillForNode(node)
            }
        }
}
