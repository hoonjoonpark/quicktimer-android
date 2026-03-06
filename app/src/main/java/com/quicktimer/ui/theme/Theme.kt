package com.quicktimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun QuickTimerTheme(
    themeConfig: ThemeConfig,
    content: @Composable () -> Unit
) {
    val colors = darkColorScheme(
        primary = themeConfig.primary,
        onPrimary = themeConfig.onPrimary,
        primaryContainer = themeConfig.primaryContainer,
        onPrimaryContainer = themeConfig.onPrimaryContainer,
        secondary = themeConfig.secondary,
        onSecondary = themeConfig.onSecondary,
        background = themeConfig.background,
        onBackground = themeConfig.onBackground,
        surface = themeConfig.surface,
        onSurface = themeConfig.onSurface,
        surfaceVariant = themeConfig.surfaceVariant,
        onSurfaceVariant = themeConfig.onSurfaceVariant,
        outline = themeConfig.outline
    )

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
