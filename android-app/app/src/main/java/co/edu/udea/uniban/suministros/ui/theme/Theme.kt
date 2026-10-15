package co.edu.udea.uniban.suministros.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = lightColorScheme(
    primary = UnibanGreen,
    onPrimary = Color.White,
    primaryContainer = UnibanGreenLight,
    onPrimaryContainer = UnibanGreen,
    secondary = UnibanGreen,
    background = UnibanBackground,
    onBackground = UnibanText,
    surface = Color.White,
    onSurface = UnibanText,
    onSurfaceVariant = UnibanMuted,
    outline = UnibanBorder,
    outlineVariant = UnibanBorder,
    tertiaryContainer = UnibanPending,
    onTertiaryContainer = UnibanPendingText,
)

@Composable
fun UnibanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = UnibanTypography, content = content)
}
