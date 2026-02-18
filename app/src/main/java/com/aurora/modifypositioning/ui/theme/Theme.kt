package com.aurora.modifypositioning.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF4C6357),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4FBF5),
    onBackground = Color(0xFF161D19),
    surface = Color(0xFFF4FBF5),
    onSurface = Color(0xFF161D19),
)

@Composable
fun ModifyPositioningTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
