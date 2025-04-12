package com.enaboapps.switchify.activities.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary Colors
private val Primary = Color(0xFFE53935)  // Crimson
private val PrimaryContainer = Color(0xFFB71C1C)

// Secondary Colors
private val Secondary = Color(0xFF1E88E5)  // Sapphire
private val SecondaryContainer = Color(0xFF0D47A1)

// Light Theme Colors
private val LightBackground = Color(0xFFFFFFFF)
private val LightSurface = Color(0xFFF8F8F8)
private val LightSurfaceVariant = Color(0xFFF0F0F0)
private val LightOnBackground = Color(0xFF1A1A1A)
private val LightOnSurface = Color(0xFF1A1A1A)
private val LightOnSurfaceVariant = Color(0xFF333333)

// Dark Theme Colors
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkSurfaceVariant = Color(0xFF2D2D2D)
private val DarkOnBackground = Color(0xFFFFFFFF)
private val DarkOnSurface = Color(0xFFFFFFFF)
private val DarkOnSurfaceVariant = Color(0xFFE0E0E0)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = DarkOnBackground,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = DarkOnBackground,
    secondary = Secondary,
    onSecondary = DarkOnBackground,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = DarkOnBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = Primary,
    onError = DarkOnBackground
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = LightOnBackground,
    primaryContainer = Primary,
    onPrimaryContainer = LightOnBackground,
    secondary = Secondary,
    onSecondary = LightOnBackground,
    secondaryContainer = Secondary,
    onSecondaryContainer = LightOnBackground,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = Primary,
    onError = LightOnBackground
)

@Composable
fun SwitchifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}