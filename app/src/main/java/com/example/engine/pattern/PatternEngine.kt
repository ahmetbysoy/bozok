package com.example.engine.pattern

import com.example.engine.detect.*
import com.example.engine.flow.*
import com.example.model.*
import kotlin.math.abs

/* ============================================================================
 * PATTERN ENGINE V2 — tüm dedektörleri yöneten orkestratör
 * ========================================================================== */
class PatternEngineV2 {
    private val bidWall = StrongWallDetector("bid")
    private val askWall = StrongWallDetector("ask")
    private val wallPull = WallPullDetector()
    private val absorption = AbsorptionDetector()
    private val voidDetector = LiquidityVoidDetector()
    private val ladder = LadderDetectorV2()
    private val compression = CompressionDetector()
    private val iceberg = IcebergDetector()
    private val skew = OrderbookSkewDetector()
    private val ofiSpike = OFISpikeDetector()
    private val stopHunt = StopHuntDetector()
    private val cvdDivergence = CvdDivergenceDetector()
    private val flowPattern = FlowCandlePatternDetector()
    private val liqPattern = LiquidationPatternDetector()

    val activeSignals = LinkedHashMap<String, PatternSignal>()

    fun analyze(book: Book, trades: List<Trade>, liquidations: List<LiquidationEvent>): List<PatternSignal> {
        val mid = book.mid ?: return sorted()
        if (book.bids.isEmpty() || book.asks.isEmpty()) return sorted()

        val currentWalls = identifyWalls(book)
        val newSignals = mutableListOf<PatternSignal>()

        newSignals += bidWall.analyze(book.bids, mid)
        newSignals += askWall.analyze(book.asks, mid)
        newSignals += wallPull.analyze(currentWalls, trades)
        newSignals += absorption.analyze(currentWalls, trades)
        newSignals += voidDetector.analyze(book, mid, trades)
        newSignals += ladder.analyze(currentWalls, mid)
        newSignals += compression.analyze(newSignals, mid)
        newSignals += iceberg.analyze(currentWalls, trades)
        newSignals += skew.analyze(currentWalls, mid)
        newSignals += ofiSpike.detect(trades, mid, priceStable(book))
        newSignals += stopHunt.analyze(mid)
        newSignals += liqPattern.analyze(liquidations, mid)

        for (sig in newSignals) {
            val key = stableKey(sig)
            val existing = activeSignals[key]
            if (existing == null || sig.confidence > existing.confidence || existing.isExpired(90_000)) {
                activeSignals[key] = sig
            }
        }

        val t = System.currentTimeMillis()
        activeSignals.entries.removeAll { it.value.isExpired(300_000) }

        for (sig in newSignals) {
            if (sig.severity == Severity.HIGH || sig.severity == Severity.CRITICAL) {
                AppState.patternHistory.add(sig)
            }
        }
        val cut = t - AppState.config.heatmapWindowSec * 1000L
        AppState.patternHistory.removeAll { it.createdAt < cut }

        return sorted()
    }

    /** CVD + flow deseni analizi (ayrı periyotla çağrılır). */
    fun analyzeSecondary(candles: List<FlowCandle>, liquidations: List<LiquidationEvent>): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        out += flowPattern.detect(candles)
        val st = AppState
        val cvdSig = cvdDivergence.detect(
            priceSeries = st.heatHistory.takeLast(10).mapNotNull { it.bids.firstOrNull()?.price ?: it.asks.firstOrNull()?.price },
            cvdSeries = st.cvdHistory.toList(),
            largeCvdSeries = st.largeCvdHistory.toList(),
            smallCvdSeries = st.smallCvdHistory.toList(),
            lastPrice = st.lastPrice ?: 0.0
        )
        if (cvdSig != null) out += cvdSig
        return out
    }

    fun identifyWalls(book: Book): List<WallRecord> {
        val out = mutableListOf<WallRecord>()
        val bt = bidWall.calculateDynamicThreshold(book.bids)
        val at = askWall.calculateDynamicThreshold(book.asks)
        val t = System.currentTimeMillis()
        for (lvl in book.bids) if (lvl.notional >= bt) out += WallRecord("bid", lvl.price, lvl.notional, lvl.qty, t, t)
        for (lvl in book.asks) if (lvl.notional >= at) out += WallRecord("ask", lvl.price, lvl.notional, lvl.qty, t, t)
        return out
    }

    fun priceStable(book: Book): Boolean {
        val hv = AppState.heatHistory.takeLast(6).mapNotNull { it.bids.firstOrNull()?.price ?: it.asks.firstOrNull()?.price }
        if (hv.size < 3) return true
        return abs(hv.last() - hv.first()) / hv.first() * 100 < 0.03
    }

    private fun sorted(): List<PatternSignal> = activeSignals.values.sortedByDescending { it.confidence }

    private fun stableKey(sig: PatternSignal): String {
        val step = Fmt.tickSizeFor(sig.price) * if (sig.type == "WALL_PULL") 25 else 8
        val p = if (sig.price.isFinite()) (sig.price / step).toLong() else 0
        return "${sig.type}:${sig.bias}:$p"
    }

    fun reset() {
        activeSignals.clear()
        wallPull.reset(); absorption.reset(); iceberg.reset(); skew.reset()
        ofiSpike.reset(); voidDetector.reset(); ladder.reset(); compression.reset()
        stopHunt.reset()
    }
}
