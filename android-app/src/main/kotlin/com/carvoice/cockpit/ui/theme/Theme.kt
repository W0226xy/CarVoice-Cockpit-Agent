package com.carvoice.cockpit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CockpitColorScheme = darkColorScheme(
    primary = CockpitAccent,
    onPrimary = CockpitBackground,
    primaryContainer = CockpitAccentMuted,
    onPrimaryContainer = CockpitTextPrimary,
    secondary = CockpitAccent,
    background = CockpitBackground,
    onBackground = CockpitTextPrimary,
    surface = CockpitSurface,
    onSurface = CockpitTextPrimary,
    surfaceVariant = CockpitSurfaceElevated,
    onSurfaceVariant = CockpitTextSecondary,
    error = CockpitDanger,
)

@Composable
fun CarVoiceCockpitTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CockpitColorScheme,
        typography = Typography,
        content = content,
    )
}