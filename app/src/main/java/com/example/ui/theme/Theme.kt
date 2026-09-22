package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VoxCyanPrimary,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = VoxCyanLight,
    secondary = VoxEmeraldActive,
    onSecondary = Color(0xFF003822),
    secondaryContainer = Color(0xFF005234),
    onSecondaryContainer = Color(0xFF67FDB3),
    tertiary = VoxAmberWarning,
    onTertiary = Color(0xFF452B00),
    background = VoxDarkCanvas,
    onBackground = VoxTextPrimary,
    surface = VoxDarkSurface,
    onSurface = VoxTextPrimary,
    surfaceVariant = VoxDarkCard,
    onSurfaceVariant = VoxTextSecondary,
    outline = VoxDarkCardBorder,
    error = VoxCrimsonDistress,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00687A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFABEDFF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF006C47),
    onSecondary = Color.White,
    tertiary = Color(0xFF885200),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = VoxCrimsonDistress,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to tactical dark theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
