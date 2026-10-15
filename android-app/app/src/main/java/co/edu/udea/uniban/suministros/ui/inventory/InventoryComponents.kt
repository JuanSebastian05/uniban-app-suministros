package co.edu.udea.uniban.suministros.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
internal fun displayDate(date: LocalDate): String = date.format(dateFormat)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventoryScaffold(
    title: String,
    pendingCount: Int,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack, enabled = backEnabled) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (pendingCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Text(
                                "$pendingCount ${if (pendingCount == 1) "pendiente" else "pendientes"}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
internal fun BottomAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 2.dp) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 52.dp),
        ) { Text(label) }
    }
}

@Composable
internal fun LoadingOrError(error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (error == null) CircularProgressIndicator() else {
            Text(error)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Intentar de nuevo") }
        }
    }
}

