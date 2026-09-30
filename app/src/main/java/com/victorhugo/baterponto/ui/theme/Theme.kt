package com.victorhugo.baterponto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF126B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F2DF),
    onPrimaryContainer = Color(0xFF123B31),
    secondary = Color(0xFF5A675E),
    secondaryContainer = Color(0xFFE5EBE3),
    background = Color(0xFFF6F7F2),
    surface = Color(0xFFFCFDF8),
    surfaceVariant = Color(0xFFE8ECE4),
    onSurface = Color(0xFF1E3029),
    onSurfaceVariant = Color(0xFF526158),
    outlineVariant = Color(0xFFD8DFD5),
    tertiary = Color(0xFF86531A),
    tertiaryContainer = Color(0xFFFFE8C9),
    onTertiaryContainer = Color(0xFF533007)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF92D6B4),
    onPrimary = Color(0xFF073B2B),
    primaryContainer = Color(0xFF245440),
    onPrimaryContainer = Color(0xFFD2F2DF),
    secondary = Color(0xFFBDCCC0),
    background = Color(0xFF101C17),
    surface = Color(0xFF17251E),
    surfaceVariant = Color(0xFF2B3930),
    onSurface = Color(0xFFE0EBE1),
    onSurfaceVariant = Color(0xFFBAC9BD),
    outlineVariant = Color(0xFF3C4C40),
    tertiary = Color(0xFFF1BD78),
    tertiaryContainer = Color(0xFF563C1C),
    onTertiaryContainer = Color(0xFFFFE8C9)
)

@Composable
fun BaterPontoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
