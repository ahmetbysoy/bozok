package com.aistudio.bozokpro.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aistudio.bozokpro.model.Theme as BozokTheme

/* ============================================================================
 * BOZOK PRO — Renk paleti (HTML :root design tokens birebir)
 * HTML L20-30 (Professional), L31-36 (Colorblind), + neon/minimal HTML L347...
 * ========================================================================== */

data class BozokColors(
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val border: Color,
    val borderSoft: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val bull: Color,
    val bullDim: Color,
    val bear: Color,
    val bearDim: Color,
    val accent: Color,
    val signal: Color,   // amber, sadece pattern-alert
    val violet: Color,
    val flowBull: Color,
    val flowBullWick: Color,
    val flowBullBorder: Color,
    val flowBear: Color,
    val flowBearWick: Color,
    val flowBearBorder: Color,
    val flowNeu: Color,
    val flowNeuWick: Color,
    val flowNeuBorder: Color,
    val tp1: Color,
    val tp2: Color,
    val inv: Color,     // invalidation gold
    val goldPoc: Color, // flow candle POC
    val poolLong: Color,
    val poolShort: Color,
    val isDark: Boolean
)

private val Professional = BozokColors(
    bg = Color(0xFF05070C), panel = Color(0xFF0C101A), panel2 = Color(0xFF101623),
    border = Color(0xFF1B2231), borderSoft = Color(0xFF161C29),
    text = Color(0xFFE7EDF6), textDim = Color(0xFF8894A8), textFaint = Color(0xFF4D5568),
    bull = Color(0xFF1FD67A), bullDim = Color(0xFF15864E),
    bear = Color(0xFFFF4D6D), bearDim = Color(0xFFA5304A),
    accent = Color(0xFF2FD0E0), signal = Color(0xFFFFB020), violet = Color(0xFF9B7BFF),
    flowBull = Color(0xFF21F6A2), flowBullWick = Color(0xFF16C784), flowBullBorder = Color(0xFF0FDF79),
    flowBear = Color(0xFFFF3868), flowBearWick = Color(0xFFE72350), flowBearBorder = Color(0xFFFF547A),
    flowNeu = Color(0xFF64748B), flowNeuWick = Color(0xFF475569), flowNeuBorder = Color(0xFF94A3B8),
    tp1 = Color(0xFF36D6FF), tp2 = Color(0xFFA78BFA), inv = Color(0xFFFFD166),
    goldPoc = Color(0xFFFFD166), poolLong = Color(0xFF9B7BFF), poolShort = Color(0xFFD854FF),
    isDark = true
)

private val Neon = Professional.copy(
    bg = Color(0xFF000012), panel = Color(0xFF080018), panel2 = Color(0xFF0C0022),
    bull = Color(0xFF39FF14), bear = Color(0xFFFF073A), accent = Color(0xFF00FFFF)
)

private val Minimal = BozokColors(
    bg = Color(0xFFF4F6F9), panel = Color(0xFFFFFFFF), panel2 = Color(0xFFEEF1F5),
    border = Color(0xFFDDE2EA), borderSoft = Color(0xFFE7ECF3),
    text = Color(0xFF0F1420), textDim = Color(0xFF5A6478), textFaint = Color(0xFF9AA3B5),
    bull = Color(0xFF0F9D58), bullDim = Color(0xFF0A6B3D),
    bear = Color(0xFFD93025), bearDim = Color(0xFF992017),
    accent = Color(0xFF0E7490), signal = Color(0xFFB8860B), violet = Color(0xFF6D28D9),
    flowBull = Color(0xFF10B981), flowBullWick = Color(0xFF047857), flowBullBorder = Color(0xFF34D399),
    flowBear = Color(0xFFDC2626), flowBearWick = Color(0xFF991B1B), flowBearBorder = Color(0xFFF87171),
    flowNeu = Color(0xFF64748B), flowNeuWick = Color(0xFF475569), flowNeuBorder = Color(0xFF94A3B8),
    tp1 = Color(0xFF0891B2), tp2 = Color(0xFF7C3AED), inv = Color(0xFFCA8A04),
    goldPoc = Color(0xFFCA8A04), poolLong = Color(0xFF7C3AED), poolShort = Color(0xFFDB2777),
    isDark = false
)

// Colorblind override (Okabe-Ito) — HTML body.colorblind
private fun BozokColors.colorblind(): BozokColors = copy(
    bull = Color(0xFF3A86FF), bullDim = Color(0xFF265DAA),
    bear = Color(0xFFFB5607), bearDim = Color(0xFFB83A05),
    accent = Color(0xFFFFBE0B), signal = Color(0xFFFF006E), violet = Color(0xFF8338EC),
    flowBull = Color(0xFF3A86FF), flowBullWick = Color(0xFF265DAA), flowBullBorder = Color(0xFF4D94FF),
    flowBear = Color(0xFFFB5607), flowBearWick = Color(0xFFB83A05), flowBearBorder = Color(0xFFFF7842),
    tp1 = Color(0xFFFFBE0B), tp2 = Color(0xFF8338EC), inv = Color(0xFFFF006E),
    goldPoc = Color(0xFFFFBE0B)
)

fun bozokColors(theme: BozokTheme, colorblind: Boolean): BozokColors {
    val base = when (theme) {
        BozokTheme.PROFESSIONAL -> Professional
        BozokTheme.NEON -> Neon
        BozokTheme.MINIMAL -> Minimal
    }
    return if (colorblind) base.colorblind() else base
}

val LocalBozokColors = compositionLocalOf { Professional }

@Composable
@ReadOnlyComposable
fun bz(): BozokColors = LocalBozokColors.current

@Composable
fun ProvideBozokColors(colors: BozokColors, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBozokColors provides colors, content = content)
}
