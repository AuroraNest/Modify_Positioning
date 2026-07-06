package com.aurora.modifypositioning.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5856D6),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFF9F0A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFC6C6C8),
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun ModifyPositioningTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
