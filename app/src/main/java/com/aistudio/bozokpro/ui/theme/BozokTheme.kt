package com.aistudio.bozokpro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.aistudio.bozokpro.model.Theme as BozokTheme

@Composable
fun BozokProTheme(
    theme: BozokTheme = BozokTheme.PROFESSIONAL,
    colorblind: Boolean = false,
    content: @Composable () -> Unit
) {
    val c = bozokColors(theme, colorblind)
    val scheme = if (c.isDark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.bg,
            secondary = c.violet, onSecondary = c.text,
            tertiary = c.goldPoc,
            background = c.bg, onBackground = c.text,
            surface = c.panel, onSurface = c.text,
            surfaceVariant = c.panel2, onSurfaceVariant = c.textDim,
            outline = c.border, error = c.bear
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = c.violet, onSecondary = c.text,
            tertiary = c.goldPoc,
            background = c.bg, onBackground = c.text,
            surface = c.panel, onSurface = c.text,
            surfaceVariant = c.panel2, onSurfaceVariant = c.textDim,
            outline = c.border, error = c.bear
        )
    }
    ProvideBozokColors(c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
