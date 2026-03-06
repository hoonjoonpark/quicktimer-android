package com.quicktimer.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

data class ThemeConfig(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val outline: Color
)

object ThemeConfigLoader {
    fun load(context: Context): ThemeConfig {
        return runCatching {
            val raw = context.assets.open("theme.json").bufferedReader().use { it.readText() }
            parse(raw)
        }.getOrElse {
            fallback()
        }
    }

    private fun parse(raw: String): ThemeConfig {
        val semantic = JSONObject(raw).getJSONObject("semantic")
        return ThemeConfig(
            background = semantic.getString("background").toColor(),
            onBackground = semantic.getString("onBackground").toColor(),
            surface = semantic.getString("surface").toColor(),
            onSurface = semantic.getString("onSurface").toColor(),
            surfaceVariant = semantic.getString("surfaceVariant").toColor(),
            onSurfaceVariant = semantic.getString("onSurfaceVariant").toColor(),
            primary = semantic.getString("primary").toColor(),
            onPrimary = semantic.getString("onPrimary").toColor(),
            primaryContainer = semantic.getString("primaryContainer").toColor(),
            onPrimaryContainer = semantic.getString("onPrimaryContainer").toColor(),
            secondary = semantic.getString("secondary").toColor(),
            onSecondary = semantic.getString("onSecondary").toColor(),
            outline = semantic.getString("outline").toColor()
        )
    }

    private fun fallback(): ThemeConfig = ThemeConfig(
        background = Color(0xFF060606),
        onBackground = Color(0xFFE3E5F0),
        surface = Color(0xFF2B2B2D),
        onSurface = Color(0xFFE3E5F0),
        surfaceVariant = Color(0xFF403F49),
        onSurfaceVariant = Color(0xFF9A9A9B),
        primary = Color(0xFF605FE4),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF2F2F59),
        onPrimaryContainer = Color(0xFFE3E5F0),
        secondary = Color(0xFF5C5BA3),
        onSecondary = Color(0xFFFFFFFF),
        outline = Color(0xFF403F49)
    )

    private fun String.toColor(): Color {
        val clean = removePrefix("#")
        val value = if (clean.length == 6) {
            ("FF" + clean).toLong(16)
        } else {
            clean.toLong(16)
        }
        return Color(value)
    }
}
