package com.aistudio.bozokpro.model

/* ============================================================================
 * BOZOK PRO — Sinyal & Pattern modelleri
 * HTML: class PatternSignal + signalUX + trade plan
 * ========================================================================== */

/** Sinyal fiyat bandı — HTML `zone: {low, high}` */
data class SignalZone(val low: Double, val high: Double)

/**
 * HTML `class PatternSignal { id, type, title, bias, price, zone, confidence,
 * severity, timeframe, explanation, invalidation, metadata, createdAt, visual }`
 */
data class PatternSignal(
    val id: String,
    val type: String,               // "STRONG_BID_WALL", "WALL_PULL", "ABSORPTION", ...
    val title: String,
    val bias: Bias,
    val price: Double,
    val confidence: Int,             // 0..100
    val severity: Severity = Severity.MEDIUM,
    val timeframe: String = "1-5dk",
    val explanation: String = "",
    val invalidation: Double? = null,
    val zone: SignalZone? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    var verified: VerifiedResult? = null
) {
    fun isExpired(maxAgeMs: Long = 300_000L): Boolean =
        System.currentTimeMillis() - createdAt > maxAgeMs
    fun ageSeconds(): Long = (System.currentTimeMillis() - createdAt) / 1000

    val icon: String get() = bias.icon
    val legacyBias: String get() = when (bias) {
        Bias.BULLISH -> "bull"; Bias.BEARISH -> "bear"; else -> "warn"
    }
}

/** HTML sigVerify.results kaydı — bir sinyal fiyat hareketiyle onaylandı mı? */
data class VerifiedResult(val hit: Boolean, val pctMove: Double)

/** HTML `S.sigVerify.pending[]` — doğrulama bekleyen sinyal */
data class VerifyRecord(
    val id: String,
    val type: String,
    val bias: String,             // "bull" | "bear"
    val entryPrice: Double,
    val t: Long,
    var verified: Boolean = false
)

/** Rolling doğruluk göstergesi — SIGNALS sekmesi statAcc */
data class RollingAccuracy(
    val directional: Int?,        // % — son 20 sinyal doğru yönde
    val n: Int
)

/* ============================================================================
 * TRADE PLAN — LEVELS sekmesi
 * ========================================================================== */

data class PriceZone(val low: Double, val high: Double, val reasoning: String = "") {
    val mid: Double get() = (low + high) / 2.0
}

data class TrailingStopSpec(
    val active: Boolean,
    val distance: Double = 0.0,
    val trigger: Double? = null
)

/**
 * HTML `S.tradePlan` — MetaStrategyEngine'in ürettiği plan.
 */
data class TradePlan(
    val strategyId: String,
    val strategyName: String,
    val direction: Direction,
    val confidence: Int,
    val entry: PriceZone,
    val stopLoss: PriceZone? = null,
    val tp1: PriceZone? = null,
    val tp2: PriceZone? = null,
    val riskReward1: Double = 0.0,
    val riskReward2: Double = 0.0,
    val trailingStop: TrailingStopSpec = TrailingStopSpec(false),
    val reasoning: String = "",
    val kellyRiskPct: Double = 0.0,
    val leverage: Int = 1,
    val positionSizeUsd: Double = 0.0
) {
    val entryMid: Double get() = entry.mid
}

/* ============================================================================
 * NARRATIVE — LEVELS sekmesi meta-sentez
 * ========================================================================== */
data class Narrative(
    val icon: String = "🌐",
    val title: String = "NÖTR / BEKLE",
    val bias: String = "neu",
    val text: String = "Şu an meta-sentez için yeterli çapraz sinyal yok."
)

/* ============================================================================
 * FLOW — HTML class FlowCandleBuilder
 * ========================================================================== */

data class FootprintCell(var buy: Double = 0.0, var sell: Double = 0.0)

data class LiquidationCandleData(
    var longLiqNotional: Double = 0.0,
    var shortLiqNotional: Double = 0.0,
    var longCount: Int = 0,
    var shortCount: Int = 0
)

data class FlowEvent(val type: String, val icon: String, val severity: String)

data class CandleMeta(
    var samples: Int = 0,
    var avgBidLiquidity: Double = 0.0,
    var avgAskLiquidity: Double = 0.0,
    var tradeCount: Int = 0
)

data class FlowCandle(
    val bucketId: Long,
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val activity: Double,
    val buyActivity: Double,
    val sellActivity: Double,
    val poc: Double,
    val events: List<FlowEvent> = emptyList(),
    val liquidationData: LiquidationCandleData = LiquidationCandleData(),
    val metadata: CandleMeta = CandleMeta(),
    val footprint: Map<Double, FootprintCell> = emptyMap(),
    val isLive: Boolean = false,
    val direction: String = "neutral",   // "bull" | "bear" | "neutral"
    val strength: Double = 0.0
) {
    val totalLiquidation: Double get() = liquidationData.longLiqNotional + liquidationData.shortLiqNotional
    val isBull: Boolean get() = close >= open
}

/* ============================================================================
 * PERFORMANS — MetaStrategyEngine.perfTracker
 * ========================================================================== */
data class StrategyPerformance(
    val strategyId: String,
    val totalTrades: Int = 0,
    val winTrades: Int = 0,
    val netRReturn: Double = 0.0,
    val equityHistory: List<Double> = emptyList()
) {
    val winRatePct: Double get() = if (totalTrades > 0) winTrades * 100.0 / totalTrades else 0.0
    val profitFactor: Double
        get() {
            // Basit: R kazancı toplam / R kaybı toplam
            if (equityHistory.size < 2) return 0.0
            var gain = 0.0; var loss = 0.0
            for (i in 1 until equityHistory.size) {
                val d = equityHistory[i] - equityHistory[i - 1]
                if (d > 0) gain += d else loss += -d
            }
            return if (loss > 0) gain / loss else if (gain > 0) 99.0 else 0.0
        }
}

/* ============================================================================
 * MICRO OPTIMIZER — LEVELS sekmesi mikro plan kartı
 * ========================================================================== */
data class MicroResult(
    val balance: Double,
    val riskUsd: Double,
    val leverage: Int,
    val notionalUsd: Double,
    val marginUsd: Double,
    val feeUsd: Double,
    val fundingUsd: Double,
    val breakEvenPct: Double,
    val liqPrice: Double,
    val statusText: String = "",
    val statusIsWarn: Boolean = false
)
