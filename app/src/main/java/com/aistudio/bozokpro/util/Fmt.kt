package com.aistudio.bozokpro.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Sayı biçimlendirme yardımcıları.
 * HTML `fmt`, `fmtPrice`, `fmtQty`, `fmtN` karşılıkları.
 *
 * ÖNEMLİ: `Locale.US` her yerde zorunlu — TR locale'de virgül ondalık ayıraç
 * kullanır ve borsa API payload'larını bozar.
 */
object Fmt {

    fun tickSizeFor(price: Double): Double = when {
        price >= 1000 -> 0.1
        price >= 100 -> 0.05
        price >= 10 -> 0.01
        price >= 1 -> 0.001
        price >= 0.01 -> 0.00001
        else -> 0.0000001
    }

    fun decimalsFor(tick: Double): Int = when {
        tick >= 1 -> 0
        tick >= 0.1 -> 1
        tick >= 0.01 -> 2
        tick >= 0.001 -> 3
        tick >= 0.0001 -> 4
        tick >= 0.00001 -> 5
        tick >= 0.000001 -> 6
        else -> 8
    }

    fun price(p: Double, tick: Double = tickSizeFor(p)): String =
        String.format(Locale.US, "%.${decimalsFor(tick)}f", p)

    fun qty(q: Double): String = String.format(Locale.US, "%.3f", q)

    /** HTML fmtN: 1.2M / 340K / 950 */
    fun notional(n: Double): String {
        val a = abs(n)
        return when {
            a >= 1_000_000 -> String.format(Locale.US, "%.1fM$", n / 1_000_000)
            a >= 1_000 -> String.format(Locale.US, "%.0fK$", n / 1_000)
            else -> String.format(Locale.US, "%.0f$", n)
        }
    }

    fun pct(v: Double, digits: Int = 2): String =
        String.format(Locale.US, "%.${digits}f%%", v)

    fun signedPct(v: Double, digits: Int = 2): String =
        String.format(Locale.US, "%+.${digits}f%%", v)

    /** Fiyatı en yakın tick'e yuvarla. */
    fun roundToTick(v: Double, tick: Double): Double = (v / tick).roundToLong() * tick

    fun clamp(v: Double, lo: Double, hi: Double): Double = v.coerceIn(lo, hi)
    fun clamp(v: Int, lo: Int, hi: Int): Int = v.coerceIn(lo, hi)
}

fun List<Double>.medianOrNull(): Double? {
    if (isEmpty()) return null
    val s = sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
}

fun nowMs(): Long = System.currentTimeMillis()
