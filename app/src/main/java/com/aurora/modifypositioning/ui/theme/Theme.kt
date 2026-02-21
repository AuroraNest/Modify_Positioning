package com.aurora.modifypositioning.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF145374),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF1F7A8C),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF2E8A99),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF0F5FB),
    onBackground = Color(0xFF132433),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF132433),
    surfaceVariant = Color(0xFFE4EDF6),
    onSurfaceVariant = Color(0xFF3A4A59),
    outline = Color(0xFFB5C4D4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun ModifyPositioningTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
