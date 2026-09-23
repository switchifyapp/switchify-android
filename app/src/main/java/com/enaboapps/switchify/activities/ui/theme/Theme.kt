package com.enaboapps.switchify.activities.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Semantic colours Material 3 has no slot for. Used as accents (icons, edges)
 * on surface containers, never as full backgrounds, so they only need to
 * contrast with the surface.
 */
@Immutable
data class SwitchifySemanticColors(
    val success: Color,
    val warning: Color
)

private val LightSemanticColors = SwitchifySemanticColors(
    success = Color(0xFF2E7D32),
    warning = Color(0xFFB26A00)
)

private val DarkSemanticColors = SwitchifySemanticColors(
    success = Color(0xFF81C784),
    warning = Color(0xFFFFB74D)
)

val LocalSwitchifySemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** Accessors for theme values outside [MaterialTheme], mirroring how MaterialTheme exposes its own. */
object SwitchifyTheme {
    val semanticColors: SwitchifySemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSwitchifySemanticColors.current
}

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
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalSwitchifySemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}