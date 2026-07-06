package com.aurora.modifypositioning.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF146C63),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF4D665F),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFB06B16),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7FAF7),
    onBackground = Color(0xFF17211E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17211E),
    surfaceVariant = Color(0xFFE2EAE5),
    onSurfaceVariant = Color(0xFF52615B),
    outline = Color(0xFFB9C7C0),
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
