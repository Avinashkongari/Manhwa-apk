package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val AxionDarkColorScheme = darkColorScheme(
    primary = AxionViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF281F59),
    onPrimaryContainer = AxionVioletSoft,
    secondary = AxionCyan,
    onSecondary = Color(0xFF041E2B),
    secondaryContainer = Color(0xFF12324A),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AxionCobalt,
    onTertiary = Color.White,
    background = AxionObsidian,
    onBackground = AxionTextPrimaryDark,
    surface = AxionCharcoal,
    onSurface = AxionTextPrimaryDark,
    surfaceVariant = AxionSlateSurface,
    onSurfaceVariant = AxionTextSecondaryDark,
    surfaceContainerLowest = AxionObsidian,
    surfaceContainerLow = AxionCharcoal,
    surfaceContainer = AxionSlateSurface,
    surfaceContainerHigh = AxionElevatedSurface,
    surfaceContainerHighest = Color(0xFF282D42),
    outline = AxionBorderDark,
    outlineVariant = Color(0xFF222638),
    error = AxionError,
    onError = Color.White
)

private val AxionLightColorScheme = lightColorScheme(
    primary = AxionVioletDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE7FF),
    onPrimaryContainer = Color(0xFF25146B),
    secondary = AxionCobaltDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = AxionLightBg,
    onBackground = AxionTextPrimaryLight,
    surface = AxionLightSurface,
    onSurface = AxionTextPrimaryLight,
    surfaceVariant = AxionLightSurfaceVar,
    onSurfaceVariant = AxionTextSecondaryLight,
    surfaceContainerLowest = AxionLightSurface,
    surfaceContainerLow = Color(0xFFF9FAFD),
    surfaceContainer = AxionLightSurfaceVar,
    surfaceContainerHigh = Color(0xFFE3E8F4),
    surfaceContainerHighest = Color(0xFFD8DFEF),
    outline = AxionLightBorder,
    outlineVariant = Color(0xFFE4E9F5),
    error = AxionError,
    onError = Color.White
)

val AxionShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object AxionGradients {
    val primaryAccent = Brush.horizontalGradient(
        colors = listOf(AxionViolet, AxionCobalt, AxionCyan)
    )
    val cardGlow = Brush.linearGradient(
        colors = listOf(
            AxionViolet.copy(alpha = 0.18f),
            AxionCyan.copy(alpha = 0.08f)
        )
    )
}

@Composable
fun AxionMangaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AxionDarkColorScheme else AxionLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AxionTypography,
        shapes = AxionShapes,
        content = content
    )
}
