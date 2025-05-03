package com.tehoubi.ddrtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Night,
    secondary = NeonMagenta,
    onSecondary = Night,
    tertiary = NeonYellow,
    background = Night,
    onBackground = Day,
    surface = Night,
    onSurface = Day,
    surfaceContainerLowest = Night,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = NightSurfaceHigh,
    surfaceVariant = NightSurfaceHigh,
    outline = NightOutline,
)

private val LightColors = lightColorScheme(
    primary = DeepCyan,
    secondary = DeepMagenta,
    background = Day,
    surface = Day,
    surfaceContainerLow = DaySurface,
    surfaceContainer = DaySurface,
    surfaceContainerHigh = DaySurfaceHigh,
    surfaceContainerHighest = DaySurfaceHigh,
)

@Composable
fun DDRTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DdrTypography,
        content = content,
    )
}
