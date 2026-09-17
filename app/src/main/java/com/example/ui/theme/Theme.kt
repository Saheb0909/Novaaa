package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NovaCyan,
    onPrimary = Color.Black,
    primaryContainer = NovaVioletDark,
    onPrimaryContainer = Color.White,
    secondary = NovaViolet,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = NovaCyan,
    tertiary = NovaEmerald,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder,
    error = NovaCoral,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = NovaCyanDark,
    onPrimary = Color.White,
    primaryContainer = NovaViolet,
    onPrimaryContainer = Color.White,
    secondary = NovaVioletDark,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = Color.Black,
    tertiary = NovaEmerald,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder,
    error = NovaCoral,
    onError = Color.White
)

@Composable
fun NovaAssistantTheme(
    darkTheme: Boolean = true, // Default to sleek futuristic dark assistant aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// For compatibility with template references
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    NovaAssistantTheme(darkTheme = true, content = content)
}
