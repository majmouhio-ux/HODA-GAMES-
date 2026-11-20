package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TacticalDarkColorScheme = darkColorScheme(
    primary = TacticalAmber,
    onPrimary = TacticalObsidian,
    primaryContainer = TacticalAmberDim,
    onPrimaryContainer = CrispIvory,
    secondary = ArcticCyan,
    onSecondary = TacticalObsidian,
    secondaryContainer = GunmetalElevated,
    onSecondaryContainer = CrispIvory,
    tertiary = NightVisionGreen,
    onTertiary = TacticalObsidian,
    background = TacticalObsidian,
    onBackground = CrispIvory,
    surface = ArmoredSlate,
    onSurface = CrispIvory,
    surfaceVariant = GunmetalSurface,
    onSurfaceVariant = MutedSteel,
    outline = SubtleBorder,
    error = CrimsonThreat,
    onError = CrispIvory
)

@Composable
fun BattleStormTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TacticalDarkColorScheme,
        typography = Typography,
        content = content
    )
}
