package com.aistudio.bozokpro.model

/* ============================================================================
 * BOZOK PRO — Enum'lar
 * HTML referans: bozok_chartshell_v2 (bias, severity, side...)
 * ========================================================================== */

enum class Direction { LONG, SHORT, NEUTRAL }

enum class Bias(val htmlKey: String, val icon: String) {
    BULLISH("bullish", "🟢"),
    BEARISH("bearish", "🔴"),
    WARNING("warning", "⚠️"),
    NEUTRAL("neutral", "🌐");

    companion object {
        fun fromKey(key: String?): Bias = entries.firstOrNull { it.htmlKey == key } ?: NEUTRAL
    }
}

enum class Severity(val htmlKey: String) {
    LOW("low"), MEDIUM("medium"), HIGH("high"), CRITICAL("critical")
}

enum class Side(val htmlKey: String) {
    BUY("buy"), SELL("sell");

    companion object {
        fun fromKey(key: String?): Side = if (key?.equals("sell", true) == true) SELL else BUY
    }
}

enum class ConnStatus(val htmlKey: String) {
    IDLE("idle"),
    CONNECTING("connecting"),
    CONNECTED("connected"),   // "live"
    ERROR("error"),
    DISCONNECTED("disconnected"),
    STALE("stale")
}

enum class BookMode(val htmlKey: String) {
    BINANCE("binance"),
    GLOBAL("global");

    companion object {
        fun fromKey(k: String?): BookMode = if (k == "global") GLOBAL else BINANCE
    }
}

enum class SensitivityPreset(val htmlKey: String, val label: String) {
    CONSERVATIVE("CONSERVATIVE", "Muhafazakâr"),
    NORMAL("NORMAL", "Normal"),
    AGGRESSIVE("AGGRESSIVE", "Agresif"),
    CUSTOM("CUSTOM", "Özel");

    // HTML CFG değerleri (satır ~1050 civarı SENS_PRESETS)
    val wallMult: Double get() = when (this) { CONSERVATIVE -> 4.5; NORMAL -> 3.5; AGGRESSIVE -> 2.5; CUSTOM -> 3.5 }
    val spoofWindowMs: Long get() = when (this) { CONSERVATIVE -> 2000; NORMAL -> 3000; AGGRESSIVE -> 5000; CUSTOM -> 3000 }
    val imbalanceThresh: Double get() = when (this) { CONSERVATIVE -> 2.6; NORMAL -> 2.2; AGGRESSIVE -> 1.8; CUSTOM -> 2.2 }
    val minPatternConfidence: Int get() = when (this) { CONSERVATIVE -> 80; NORMAL -> 65; AGGRESSIVE -> 55; CUSTOM -> 65 }
    val minSignalConfidence: Int get() = when (this) { CONSERVATIVE -> 75; NORMAL -> 60; AGGRESSIVE -> 50; CUSTOM -> 60 }

    companion object {
        fun fromKey(k: String?): SensitivityPreset =
            entries.firstOrNull { it.htmlKey == k } ?: NORMAL
    }
}

enum class OverlayDensity(val htmlKey: String) {
    LOW("LOW"), NORMAL("NORMAL"), HIGH("HIGH");
    companion object { fun fromKey(k: String?): OverlayDensity = entries.firstOrNull { it.htmlKey == k } ?: NORMAL }
}

enum class ChartMode(val htmlKey: String) {
    MINIMAL("MINIMAL"), NORMAL("NORMAL");
    companion object { fun fromKey(k: String?): ChartMode = entries.firstOrNull { it.htmlKey == k } ?: NORMAL }
}

enum class Theme(val htmlKey: String, val label: String) {
    PROFESSIONAL("professional", "Professional"),
    NEON("neon", "Neon"),
    MINIMAL("minimal", "Minimal");
    companion object { fun fromKey(k: String?): Theme = entries.firstOrNull { it.htmlKey == k } ?: PROFESSIONAL }
}

enum class FlowCandleMode(val htmlKey: String) {
    TIME("time"), VOLUME("volume");
    companion object { fun fromKey(k: String?): FlowCandleMode = entries.firstOrNull { it.htmlKey == k } ?: TIME }
}

/**
 * HTML'deki 7 sekme (data-view). Bar sırası referansla birebir aynı.
 */
enum class Tab(val htmlView: String, val icon: String, val label: String) {
    BOOK("bookView", "📊", "BOOK"),
    FLOW("flowView", "🕯️", "FLOW"),
    DEPTH("depthView", "📚", "DEPTH"),
    SIGNALS("signalsView", "🎯", "SIGNALS"),
    LEVELS("levelsView", "📍", "LEVELS"),
    MARKETS("marketsView", "🌐", "MARKETS"),
    SETTINGS("settingsView", "⚙️", "SETTINGS");

    companion object {
        fun fromKey(k: String?): Tab = entries.firstOrNull { it.htmlView == k } ?: BOOK
    }
}

/**
 * Heatmap katmanları — HTML L481-485 data-layer değerleri
 */
enum class HeatmapLayer(val htmlKey: String, val label: String) {
    LIQUIDITY("liquidity", "Likidite"),
    VELOCITY("velocity", "Hız"),
    TRADES("trades", "İşlemler"),
    WALLS("walls", "Duvarlar"),
    LIQ_POOLS("liqpools", "Liq Havuzları");

    companion object {
        fun fromKey(k: String?): HeatmapLayer? = entries.firstOrNull { it.htmlKey == k }
        /** HTML defaultu: liquidity + walls + trades + liqpools açık */
        val defaults: Set<HeatmapLayer> = setOf(LIQUIDITY, WALLS, TRADES, LIQ_POOLS)
    }
}
