package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/* ============================================================================
 * BOZOK TASARIM TOKEN'LARI — 3 tema (professional/neon/minimal) + colorblind
 * (HTML :root + body.colorblind birebir)
 * ========================================================================== */

var ThemeMode by mutableStateOf("professional")
var ColorblindMode by mutableStateOf(false)

// ---- Professional (varsayılan) ----
private val ProfBg = Color(0xFF05070C)
private val ProfPanel = Color(0xFF0C101A)
private val ProfPanel2 = Color(0xFF101623)
private val ProfBorder = Color(0xFF1B2231)
private val ProfAccent = Color(0xFF2FD0E0)
private val ProfBull = Color(0xFF1FD67A)
private val ProfBear = Color(0xFFFF4D6D)
private val ProfSignal = Color(0xFFFFB020)
private val ProfViolet = Color(0xFF9B7BFF)
private val ProfText = Color(0xFFE7EDF6)
private val ProfDim = Color(0xFF8894A8)
private val ProfFaint = Color(0xFF4D5568)

// ---- Neon ----
private val NeonBg = Color(0xFF000012)
private val NeonPanel = Color(0xFF080018)
private val NeonPanel2 = Color(0xFF0C0022)
private val NeonBull = Color(0xFF39FF14)
private val NeonBear = Color(0xFFFF073A)
private val NeonAccent = Color(0xFF00FFFF)
private val NeonBorder = Color(0xFF1B2231)

// ---- Minimal ----
private val MinBg = Color(0xFFF4F6F9)
private val MinPanel = Color(0xFFFFFFFF)
private val MinPanel2 = Color(0xFFEEF1F5)
private val MinBorder = Color(0xFFDDE2EA)
private val MinText = Color(0xFF0F1420)
private val MinDim = Color(0xFF5A6478)
private val MinFaint = Color(0xFF9AA3B5)
private val MinBull = Color(0xFF0F9D58)
private val MinBear = Color(0xFFD93025)
private val MinAccent = Color(0xFF0E7490)
private val MinSignal = Color(0xFFB8860B)

// ---- Colorblind (Okabe-Ito) ----
private val CbBull = Color(0xFF3A86FF)
private val CbBear = Color(0xFFFB5607)
private val CbAccent = Color(0xFFFFBE0B)
private val CbSignal = Color(0xFFFF006E)
private val CbViolet = Color(0xFF8338EC)

// ---- Durumlu token'lar ----
val Bg: Color get() = when (ThemeMode) { "neon" -> NeonBg; "minimal" -> MinBg; else -> ProfBg }
val Panel: Color get() = when (ThemeMode) { "neon" -> NeonPanel; "minimal" -> MinPanel; else -> ProfPanel }
val Panel2: Color get() = when (ThemeMode) { "neon" -> NeonPanel2; "minimal" -> MinPanel2; else -> ProfPanel2 }
val Border: Color get() = when (ThemeMode) { "minimal" -> MinBorder; else -> ProfBorder }
val BorderSoft: Color get() = Color(0xFF161C29)

val Accent: Color get() = when {
    ColorblindMode -> CbAccent
    ThemeMode == "neon" -> NeonAccent
    ThemeMode == "minimal" -> MinAccent
    else -> ProfAccent
}
val Bull: Color get() = when {
    ColorblindMode -> CbBull
    ThemeMode == "neon" -> NeonBull
    ThemeMode == "minimal" -> MinBull
    else -> ProfBull
}
val Bear: Color get() = when {
    ColorblindMode -> CbBear
    ThemeMode == "neon" -> NeonBear
    ThemeMode == "minimal" -> MinBear
    else -> ProfBear
}
val Signal: Color get() = when {
    ColorblindMode -> CbSignal
    ThemeMode == "minimal" -> MinSignal
    else -> ProfSignal
}
val Violet: Color get() = if (ColorblindMode) CbViolet else ProfViolet

val TextPrimary: Color get() = when (ThemeMode) { "minimal" -> MinText; else -> ProfText }
val TextDim: Color get() = when (ThemeMode) { "minimal" -> MinDim; else -> ProfDim }
val TextFaint: Color get() = when (ThemeMode) { "minimal" -> MinFaint; else -> ProfFaint }

val GoldPoc: Color get() = if (ColorblindMode) CbAccent else Color(0xFFFFD166)
val PoolShort: Color get() = Color(0xFFD854FF)
val PoolLong: Color get() = Violet

val FlowBull: Color get() = if (ColorblindMode) CbBull else Color(0xFF21F6A2)
val FlowBear: Color get() = if (ColorblindMode) CbBear else Color(0xFFFF3868)
val FlowNeutral: Color get() = Color(0xFF64748B)
