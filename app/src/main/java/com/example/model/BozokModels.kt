package com.example.model

import kotlin.math.roundToLong

/* ============================================================================
 * BOZOK PRO — VERİ MODELLERİ (HTML bozok_chartshell_v2 birebir envanter)
 * ========================================================================== */

enum class Direction { LONG, SHORT, NEUTRAL }

enum class Bias(val uiIcon: String) { BULL("🟢"), BEAR("🔴"), WARN("⚠️"), NEUTRAL("🌐") }

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

enum class ConnStatus { IDLE, CONNECTING, LIVE, BAD }

enum class Side { BUY, SELL }

enum class SensitivityPreset(val displayName: String) {
    CONSERVATIVE("CONSERVATIVE"), NORMAL("NORMAL"), AGGRESSIVE("AGGRESSIVE"), CUSTOM("CUSTOM");
    val wallMult: Double get() = when (this) { CONSERVATIVE -> 4.5; NORMAL -> 3.5; AGGRESSIVE -> 2.5; CUSTOM -> 3.5 }
    val spoofWindowMs: Long get() = when (this) { CONSERVATIVE -> 2000; NORMAL -> 3000; AGGRESSIVE -> 5000; CUSTOM -> 3000 }
    val imbalanceThresh: Double get() = when (this) { CONSERVATIVE -> 2.6; NORMAL -> 2.2; AGGRESSIVE -> 1.8; CUSTOM -> 2.2 }
    val minPatternConfidence: Int get() = when (this) { CONSERVATIVE -> 80; NORMAL -> 65; AGGRESSIVE -> 55; CUSTOM -> 65 }
    val minSignalConfidence: Int get() = when (this) { CONSERVATIVE -> 75; NORMAL -> 60; AGGRESSIVE -> 50; CUSTOM -> 60 }
}

/* ------------------------- PİYASA VERİSİ ------------------------- */

data class BookLevel(val price: Double, val qty: Double, val notional: Double = price * qty)

data class Book(
    val bids: List<BookLevel> = emptyList(),
    val asks: List<BookLevel> = emptyList(),
    val ts: Long = 0L,
    val label: String = "Binance"
) {
    val bestBid: Double? get() = bids.firstOrNull()?.price
    val bestAsk: Double? get() = asks.firstOrNull()?.price
    val mid: Double? get() { val b = bestBid; val a = bestAsk; return if (b != null && a != null) (b + a) / 2.0 else null }
    val spreadBps: Double? get() {
        val b = bestBid ?: return null; val a = bestAsk ?: return null
        return if (a > 0) (a - b) / a * 10_000.0 else null
    }
    val bidDepth: Double get() = bids.take(10).sumOf { it.notional }
    val askDepth: Double get() = asks.take(10).sumOf { it.notional }
    val obi: Double get() {
        val s = bidDepth + askDepth
        return if (s > 0) (bidDepth - askDepth) / s * 100.0 else 0.0
    }
}

data class Trade(val price: Double, val qty: Double, val side: Side, val timestamp: Long) {
    val notional: Double get() = price * qty
}

data class ExchangeState(
    val key: String, val label: String, val status: ConnStatus = ConnStatus.IDLE,
    val bestBid: Double? = null, val bestAsk: Double? = null,
    val ts: Long? = null, val latencyMs: Long? = null, val lastError: String? = null
)

data class ArbitrageSkew(val venueA: String, val venueB: String, val priceA: Double, val priceB: Double) {
    val deviationBps: Double get() = if (priceA > 0) (priceA - priceB) / priceA * 10_000.0 else 0.0
    val isOpportunity: Boolean get() = deviationBps >= 8.0
    val leadVenue: String get() = if (priceA >= priceB) venueA else venueB
}

/* ------------------------- AYARLAR ------------------------- */

data class AppConfig(
    var symbol: String = "btcusdt",
    var multiExchange: Boolean = true,
    var wallMult: Double = 3.5,
    var spoofWindowMs: Long = 3000,
    var imbalanceThresh: Double = 2.2,
    var algoWarEventsPerSec: Double = 6.0,
    var heatmapWindowSec: Int = 60,
    var sensitivity: SensitivityPreset = SensitivityPreset.NORMAL,
    var soundOn: Boolean = true,
    var voiceAnnounce: Boolean = true,
    var notifications: Boolean = false,
    var colorblind: Boolean = false,
    var theme: String = "professional",   // professional | neon | minimal
    var flowTimeframeMs: Long = 5000,
    var flowCandleMode: String = "time",  // time | volume
    var flowVolumeTarget: Double = 1_000_000.0,
    var microBalance: Double = 5.0,
    var microRiskPct: Double = 0.20,
    var microMaxLeverage: Int = 20,
    var feeRate: Double = 0.0005,
    var minPatternConfidence: Int = 65,
    var minSignalConfidence: Int = 60
)

/* ------------------------- SİNYALLER ------------------------- */

data class SignalZone(val low: Double, val high: Double)

data class PatternSignal(
    val id: String,
    val type: String,
    val title: String,
    val bias: Bias,
    val price: Double,
    val confidence: Int,
    val severity: Severity = Severity.MEDIUM,
    val timeframe: String = "1-5dk",
    val explanation: String = "",
    val invalidation: Double? = null,
    val zone: SignalZone? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    var verified: VerifiedResult? = null
) {
    fun isExpired(maxAgeMs: Long): Boolean = System.currentTimeMillis() - createdAt > maxAgeMs
}

data class VerifiedResult(val hit: Boolean, val pct: Double)

data class WallRecord(
    val side: String, val price: Double, var notional: Double, var qty: Double,
    val firstSeen: Long, var lastSeen: Long, var maxNotional: Double = notional, var sizeRatio: Double = 1.0
)

data class LiquidationPool(val leverage: Int, val side: String, val price: Double, val estNotionalUsd: Double) {
    val estFormatted: String get() =
        if (estNotionalUsd >= 1_000_000) String.format("%.1fM", estNotionalUsd / 1_000_000)
        else String.format("%.0fK", estNotionalUsd / 1_000)
}

data class LiquidationEvent(val symbol: String, val side: String, val price: Double, val qty: Double, val notionalUsd: Double, val timestamp: Long)

/* ------------------------- FLOW ------------------------- */

data class FootprintCell(var buy: Double = 0.0, var sell: Double = 0.0)

data class LiquidationCandleData(var longLiqNotional: Double = 0.0, var shortLiqNotional: Double = 0.0, var longCount: Int = 0, var shortCount: Int = 0)

data class FlowEvent(val type: String, val icon: String, val severity: String)

data class CandleMeta(var samples: Int = 0, var avgBidLiquidity: Double = 0.0, var avgAskLiquidity: Double = 0.0, var tradeCount: Int = 0)

data class FlowCandle(
    val bucketId: Long, val timestamp: Long, val open: Double, val high: Double, val low: Double, val close: Double,
    val activity: Double, val buyActivity: Double, val sellActivity: Double, val poc: Double,
    val events: List<FlowEvent> = emptyList(), val liquidationData: LiquidationCandleData = LiquidationCandleData(),
    val metadata: CandleMeta = CandleMeta(), val footprint: Map<Double, FootprintCell> = emptyMap(),
    val isLive: Boolean = false, val direction: String = "neutral", val strength: Double = 0.0
) {
    val totalLiquidation: Double get() = liquidationData.longLiqNotional + liquidationData.shortLiqNotional
}

/* ------------------------- TRADE PLANI ------------------------- */

data class PriceZone(val low: Double, val high: Double, val reasoning: String = "")

data class TrailingStopSpec(val active: Boolean, val distance: Double, val trigger: Double? = null)

data class TradePlan(
    val direction: Direction, val confidence: Int, val entry: PriceZone,
    val stopLoss: PriceZone? = null, val tp1: PriceZone? = null, val tp2: PriceZone? = null,
    val riskReward1: Double = 0.0, val riskReward2: Double = 0.0,
    val trailingStop: TrailingStopSpec = TrailingStopSpec(false, 0.0),
    val reasoning: String = "", val webhookPayload: Map<String, Any?> = emptyMap()
) {
    val entryMid: Double get() = (entry.low + entry.high) / 2
}

data class StrategyPerformance(val strategyId: String, val totalTrades: Int = 0, val winTrades: Int = 0, val netRReturn: Double = 0.0, val equityHistory: List<Double> = emptyList()) {
    val winRatePct: Double get() = if (totalTrades > 0) winTrades * 100.0 / totalTrades else 0.0
}

data class VerifyRecord(val id: String, val bias: String, val entryPrice: Double, val t: Long, var verified: Boolean = false)
data class VerifyResult(val hit: Boolean, val pct: Double, val bias: String)
data class RollingAccuracy(val dirAcc: Int?, val volAcc: Int?, val dirN: Int, val volN: Int)

data class SessionEvent(val t: Long, val type: String, val value: Double = 0.0, val sig: PatternSignal? = null)

data class WebhookPayload(
    val event: String = "BOZOK_META_EXECUTION", val strategyId: String, val strategyName: String,
    val direction: String, val symbol: String, val confidence: Int, val entry: Double,
    val stopLoss: Double, val takeProfit1: Double, val takeProfit2: Double? = null,
    val leverage: Int, val kellyRiskPct: Double, val positionSizeUsd: Double, val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = buildString {
        append("{\"event\":\"$event\",\"strategyId\":\"$strategyId\",\"strategyName\":\"$strategyName\",")
        append("\"direction\":\"$direction\",\"symbol\":\"$symbol\",\"confidence\":$confidence,")
        append("\"entry\":${f4(entry)},\"stopLoss\":${f4(stopLoss)},\"takeProfit1\":${f4(takeProfit1)},")
        append("\"takeProfit2\":${takeProfit2?.let { f4(it) } ?: "null"},\"leverage\":$leverage,")
        append("\"kellyRiskPct\":${f2(kellyRiskPct)},\"positionSizeUsd\":${f2(positionSizeUsd)},\"timestamp\":$timestamp}")
    }
    private fun f4(v: Double) = String.format("%.4f", v)
    private fun f2(v: Double) = String.format("%.2f", v)
}

data class BotStatus(val isConnected: Boolean = false, val botName: String = "BOZOK-EXEC-PYTHON-V1", val pingMs: Long = 0, val activeOrdersCount: Int = 0, val totalRealizedPnlUsd: Double = 0.0)

/* ------------------------- UTIL ------------------------- */

object Fmt {
    fun tickSizeFor(price: Double): Double = when {
        price >= 1000 -> 0.1
        price >= 100 -> 0.05
        price >= 10 -> 0.01
        price >= 1 -> 0.001
        else -> 0.00001
    }
    fun price(p: Double): String = String.format("%.${decimalsFor(tickSizeFor(p))}f", p)
    fun qty(q: Double): String = String.format("%.3f", q)
    fun decimalsFor(tick: Double): Int = when {
        tick >= 1 -> 0; tick >= 0.1 -> 1; tick >= 0.01 -> 2; tick >= 0.001 -> 3
        tick >= 0.0001 -> 4; tick >= 0.00001 -> 5; else -> 8
    }
    fun clamp(v: Double, lo: Double, hi: Double): Double = v.coerceIn(lo, hi)
    fun clamp(v: Int, lo: Int, hi: Int): Int = v.coerceIn(lo, hi)
    fun roundToTick(v: Double, tick: Double): Double = (v / tick).roundToLong() * tick
    fun fmtN(n: Double): String = if (n >= 1_000_000) String.format("%.1fM$", n / 1_000_000) else String.format("%.0fK$", n / 1_000)
}

fun List<Double>.medianOrNull(): Double? {
    if (isEmpty()) return null
    val s = sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
}
