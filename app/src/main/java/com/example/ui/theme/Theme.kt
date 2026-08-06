package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

/* ============================================================================
 * BOZOK TEMA — professional/neon koyu, minimal açık
 * ========================================================================== */

@Composable
fun BozokProTheme(
    theme: String = "professional",
    colorblind: Boolean = false,
    content: @Composable () -> Unit
) {
    SideEffect {
        ThemeMode = theme
        ColorblindMode = colorblind
    }

    val isDark = theme != "minimal"
    val scheme = if (isDark) {
        darkColorScheme(
            primary = Accent, onPrimary = Bg, secondary = Violet, onSecondary = TextPrimary,
            tertiary = GoldPoc, background = Bg, onBackground = TextPrimary,
            surface = Panel, onSurface = TextPrimary, surfaceVariant = Panel2,
            onSurfaceVariant = TextDim, outline = Border, error = Bear
        )
    } else {
        lightColorScheme(
            primary = Accent, onPrimary = Color_White, secondary = Violet, onSecondary = TextPrimary,
            tertiary = GoldPoc, background = Bg, onBackground = TextPrimary,
            surface = Panel, onSurface = TextPrimary, surfaceVariant = Panel2,
            onSurfaceVariant = TextDim, outline = Border, error = Bear
        )
    }

    MaterialTheme(colorScheme = scheme, content = content)
}

private val Color_White = androidx.compose.ui.graphics.Color.White
