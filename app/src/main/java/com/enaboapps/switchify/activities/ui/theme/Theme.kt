package com.enaboapps.switchify.activities.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary Colors - Improved for accessibility
private val Primary = Color(0xFFD32F2F)  // Slightly darker red for better contrast
private val PrimaryContainer = Color(0xFFFFDAD6)  // Light container for dark text
private val OnPrimaryContainer = Color(0xFF410002)  // Dark text for light container

// Secondary Colors - Improved for accessibility
private val Secondary = Color(0xFF0B57D0)  // Darker blue for better contrast
private val SecondaryContainer = Color(0xFFD8E2FF)  // Light container for dark text
private val OnSecondaryContainer = Color(0xFF001A41)  // Dark text for light container

// Light Theme Colors
private val LightBackground = Color(0xFFFFFBFF)
private val LightSurface = Color(0xFFFFFBFF)
private val LightSurfaceVariant = Color(0xFFE7E0EC)
private val LightOnBackground = Color(0xFF1C1B1F)
private val LightOnSurface = Color(0xFF1C1B1F)
private val LightOnSurfaceVariant = Color(0xFF49454F)

// Dark Theme Colors
private val DarkBackground = Color(0xFF1C1B1F)
private val DarkSurface = Color(0xFF1C1B1F)
private val DarkSurfaceVariant = Color(0xFF49454F)
private val DarkOnBackground = Color(0xFFE6E1E5)
private val DarkOnSurface = Color(0xFFE6E1E5)
private val DarkOnSurfaceVariant = Color(0xFFCAC4D0)

// Error colors
private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorLight = Color(0xFFFFFFFF)
private val OnErrorDark = Color(0xFF690005)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4AB),  // Lighter in dark theme for contrast
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),

    secondary = Color(0xFFADC6FF),  // Lighter in dark theme for contrast
    onSecondary = Color(0xFF002E69),
    secondaryContainer = Color(0xFF0B57D0).copy(alpha = 0.7f),
    onSecondaryContainer = Color(0xFFD8E2FF),

    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,

    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,

    secondary = Secondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,

    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,

    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
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