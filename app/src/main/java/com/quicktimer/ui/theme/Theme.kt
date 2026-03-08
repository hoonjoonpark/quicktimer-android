package com.quicktimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import com.quicktimer.data.AppThemeMode

@Composable
fun QuickTimerTheme(
    themeConfig: ThemeConfig,
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colors = if (useDark) {
        darkColorScheme(
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
    } else {
        lightColorScheme(
            primary = themeConfig.primary,
            onPrimary = themeConfig.onPrimary,
            primaryContainer = themeConfig.primary.copy(alpha = 0.12f),
            onPrimaryContainer = themeConfig.primary,
            secondary = themeConfig.secondary,
            onSecondary = themeConfig.onSecondary,
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF15161A),
            surface = Color(0xFFF8FAFD),
            onSurface = Color(0xFF15161A),
            surfaceVariant = Color(0xFFEFF2F7),
            onSurfaceVariant = Color(0xFF5F6676),
            outline = Color(0xFF9096A6)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
