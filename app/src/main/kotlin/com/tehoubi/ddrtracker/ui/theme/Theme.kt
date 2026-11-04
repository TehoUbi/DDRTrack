package com.tehoubi.ddrtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

/** Always dark: the app is built around the cabinet's night palette. */
@Composable
fun DDRTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = DdrTypography,
        content = content,
    )
}
