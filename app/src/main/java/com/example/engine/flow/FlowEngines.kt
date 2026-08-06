package com.example.engine.flow

import com.example.engine.pattern.AppState
import com.example.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/* ============================================================================
 * BASINÇ MUMU + LİKİDASYON MOTORLARI
 * FIX (HTML hatası): kurgusal pattern hacmi (+$10k/pattern) YOK — aktivite
 * yalnızca gerçek trade + likidasyon verisinden türetilir.
 * ========================================================================== */

data class PressureResult(
    val pressure: Double, val buy: Double, val sell: Double,
    val longLiq: Double, val shortLiq: Double, val longCount: Int, val shortCount: Int,
    val totalLiq: Double, val dominant: String?
)

class PressureCalculator {
    fun calculate(book: Book, trades: List<Trade>, patterns: List<PatternSignal>, liquidations: List<LiquidationEvent>, windowMs: Long): PressureResult {
        val t = System.currentTimeMillis()

        var buyFlow = 0.0; var sellFlow = 0.0
        for (tr in trades.asReversed()) {
            if (t - tr.timestamp > windowMs) break
            if (tr.side == Side.BUY) buyFlow += tr.notional else sellFlow += tr.notional
        }

        var longLiq = 0.0; var shortLiq = 0.0
        var longCount = 0; var shortCount = 0
        for (liq in liquidations) {
            if (t - liq.timestamp > 60_000) continue
            if (liq.side == "SELL") { longLiq += liq.notionalUsd; longCount++ }
            else { shortLiq += liq.notionalUsd; shortCount++ }
        }

        var patternBias = 0.0
        for (p in patterns) {
            if (t - p.createdAt > windowMs) continue
            val w = when (p.severity) { Severity.HIGH -> 12.0; Severity.MEDIUM -> 7.0; Severity.LOW -> 3.0; Severity.CRITICAL -> 18.0 }
            when (p.bias) { Bias.BULL -> patternBias += w; Bias.BEAR -> patternBias -= w; else -> {} }
        }

        val netFlow = buyFlow - sellFlow
        val liqBias = (longLiq - shortLiq) / max(1.0, longLiq + shortLiq) * 50.0

        return PressureResult(
            pressure = netFlow / 1000.0 + patternBias + liqBias,
            buy = buyFlow, sell = sellFlow, longLiq = longLiq, shortLiq = shortLiq,
            longCount = longCount, shortCount = shortCount, totalLiq = longLiq + shortLiq,
            dominant = when {
                longLiq + shortLiq <= 0 -> null
                longLiq >= shortLiq * 1.5 -> "long"
                shortLiq >= longLiq * 1.5 -> "short"
                else -> "balanced"
            }
        )
    }
}

class FlowCandleBuilder(var timeframeMs: Long = 5_000L) {
    private class MutableCandle(
        var bucketId: Long, var timestamp: Long,
        var open: Double, var high: Double, var low: Double, var close: Double,
        var activity: Double, var buyActivity: Double, var sellActivity: Double,
        var poc: Double, var maxAct: Double
    ) {
        val events = mutableListOf<FlowEvent>()
        val liq = LiquidationCandleData()
        val meta = CandleMeta()
        val footprint = mutableMapOf<Double, FootprintCell>()

        fun toFlowCandle(isLive: Boolean): FlowCandle {
            val dir = when {
                close > open && close > 10 -> "bullish"
                close < open && close < -10 -> "bearish"
                else -> "neutral"
            }
            val range = high - low
            val strength = if (range > 0) abs(close - open) / range * abs(close) / 100.0 else 0.0
            return FlowCandle(
                bucketId, timestamp, open, high, low, close, activity, buyActivity, sellActivity,
                poc, events.toList(), liq, meta.copy(), footprint.toMap(), isLive, dir, strength
            )
        }
    }

    private var current: MutableCandle? = null
    private val candles = ArrayDeque<FlowCandle>()
    val maxCandles = 120
    private var mode = AppState.config.flowCandleMode
    private var targetVolume = AppState.config.flowVolumeTarget
    private var accVolume = 0.0
    private val calc = PressureCalculator()

    fun rebuild(timeframeMs: Long = this.timeframeMs, mode: String = this.mode, target: Double = this.targetVolume) {
        this.timeframeMs = timeframeMs; this.mode = mode; this.targetVolume = target
        candles.clear(); current = null; accVolume = 0.0
    }

    fun update(book: Book, trades: List<Trade>, patterns: List<PatternSignal>, liquidations: List<LiquidationEvent>) {
        val mid = book.mid ?: return
        val t = System.currentTimeMillis()
        val res = calc.calculate(book, trades, patterns, liquidations, timeframeMs)
        val activityTotal = (res.buy + res.sell) / 1000.0

        var needNew = false
        if (current == null) needNew = true
        else if (mode == "volume") {
            if (accVolume + activityTotal >= targetVolume / 1000.0) needNew = true
            accVolume += activityTotal
        } else {
            val bucket = t / timeframeMs
            if (current!!.bucketId != bucket) needNew = true
        }

        if (needNew) {
            close()
            accVolume = if (mode == "volume") activityTotal else 0.0
            current = MutableCandle(t / timeframeMs, t, res.pressure, res.pressure, res.pressure, res.pressure, activityTotal, res.buy / 1000.0, res.sell / 1000.0, res.pressure, activityTotal)
        }

        val c = current ?: return
        c.high = max(c.high, res.pressure)
        c.low = min(c.low, res.pressure)
        c.close = res.pressure
        c.activity += activityTotal
        c.buyActivity += res.buy / 1000.0
        c.sellActivity += res.sell / 1000.0
        c.events += eventsFor(patterns)
        if (activityTotal >= c.maxAct) { c.maxAct = activityTotal; c.poc = res.pressure }

        val tick = Fmt.tickSizeFor(mid) * 4
        for (tr in trades.asReversed()) {
            if (t - tr.timestamp > timeframeMs) break
            val bucketPx = (tr.price / tick).toLong() * tick
            val cell = c.footprint.getOrPut(bucketPx) { FootprintCell() }
            if (tr.side == Side.BUY) cell.buy += tr.notional else cell.sell += tr.notional
        }

        c.liq.longLiqNotional += res.longLiq
        c.liq.shortLiqNotional += res.shortLiq
        c.liq.longCount += res.longCount
        c.liq.shortCount += res.shortCount

        c.meta.samples++
        c.meta.avgBidLiquidity += book.bids.sumOf { it.notional }
        c.meta.avgAskLiquidity += book.asks.sumOf { it.notional }
        c.meta.tradeCount += trades.size
    }

    fun getCandles(): List<FlowCandle> {
        val out = candles.toMutableList()
        current?.let { out += it.toFlowCandle(isLive = true) }
        return out
    }

    private fun close() {
        val c = current ?: return
        if (c.meta.samples > 0) {
            c.meta.avgBidLiquidity /= c.meta.samples
            c.meta.avgAskLiquidity /= c.meta.samples
        }
        candles.addLast(c.toFlowCandle(isLive = false))
        while (candles.size > maxCandles) candles.removeFirst()
    }

    private fun eventsFor(patterns: List<PatternSignal>): List<FlowEvent> {
        val out = mutableListOf<FlowEvent>()
        for (p in patterns.take(6)) {
            when (p.type) {
                "WALL_PULL" -> out += FlowEvent("pull", "⚠️", "high")
                "ABSORPTION" -> out += FlowEvent("absorb", "◎", "medium")
                "LIQUIDITY_VOID" -> out += FlowEvent("void", "↯", "high")
                "STRONG_BID_WALL" -> out += FlowEvent("bid", "🛡️", "medium")
                "STRONG_ASK_WALL" -> out += FlowEvent("ask", "🧱", "medium")
            }
        }
        return out
    }
}

/** Flow mumu desenleri: momentum / dönüş / tükenme / sıkışma. */
class FlowCandlePatternDetector {
    private val lastFire = mutableMapOf<String, Long>()

    fun detect(candles: List<FlowCandle>): List<PatternSignal> {
        if (candles.size < 3) return emptyList()
        val out = mutableListOf<PatternSignal>()
        val t = System.currentTimeMillis()
        val cooldowns = mapOf(
            "FLOW_BULL" to 30_000L, "FLOW_BEAR" to 30_000L,
            "FLOW_REV_UP" to 45_000L, "FLOW_REV_DOWN" to 45_000L,
            "FLOW_EXH_UP" to 30_000L, "FLOW_EXH_DOWN" to 30_000L, "FLOW_COMP" to 45_000L
        )
        fun emit(sig: PatternSignal, key: String) {
            if (t - (lastFire[key] ?: 0) < (cooldowns[key] ?: 30_000)) return
            lastFire[key] = t
            out += sig
        }

        val r = candles.takeLast(3)
        if (r.size == 3 && r.all { it.direction == "bullish" } && r[0].close < r[1].close && r[1].close < r[2].close) {
            emit(PatternSignal("FLOW_BULL_$t", "FLOW_BULL", "Alım Akışı Güçleniyor", Bias.BULL, r[2].close, Fmt.clamp(70 + (r[2].strength * 20).toInt(), 70, 90), Severity.MEDIUM, "1-5dk", "3 mum ardışık yükselen boğa basıncı"), "FLOW_BULL")
        }
        if (r.size == 3 && r.all { it.direction == "bearish" } && r[0].close > r[1].close && r[1].close > r[2].close) {
            emit(PatternSignal("FLOW_BEAR_$t", "FLOW_BEAR", "Satış Akışı Güçleniyor", Bias.BEAR, r[2].close, Fmt.clamp(70 + (r[2].strength * 20).toInt(), 70, 90), Severity.MEDIUM, "1-5dk", "3 mum ardışık düşen ayı basıncı"), "FLOW_BEAR")
        }

        val rev = candles.takeLast(5)
        if (rev.size == 5) {
            if (rev.take(3).all { it.close < -20 } && rev.takeLast(2).all { it.close > 20 }) {
                emit(PatternSignal("FLOW_REV_UP_$t", "FLOW_REV_UP", "Akış Yukarı Döndü", Bias.BULL, rev.last().close, 65, Severity.MEDIUM, "1-5dk", "Satış baskısı ardından alım akışı"), "FLOW_REV_UP")
            }
            if (rev.take(3).all { it.close > 20 } && rev.takeLast(2).all { it.close < -20 }) {
                emit(PatternSignal("FLOW_REV_DOWN_$t", "FLOW_REV_DOWN", "Akış Aşağı Döndü", Bias.BEAR, rev.last().close, 65, Severity.MEDIUM, "1-5dk", "Alım baskısı ardından satış akışı"), "FLOW_REV_DOWN")
            }
        }

        val ex = candles.takeLast(4)
        if (ex.size == 4) {
            val shrink = ex[0].strength > ex[1].strength && ex[1].strength > ex[2].strength && ex[2].strength > ex[3].strength
            if (shrink && ex.all { it.direction == "bullish" }) emit(PatternSignal("FLOW_EXH_UP_$t", "FLOW_EXH_UP", "Alım Momentum Zayıflıyor", Bias.WARN, ex.last().close, 60, Severity.MEDIUM, "5-15dk", "Ardışık mumlarda güç azalıyor"), "FLOW_EXH_UP")
            if (shrink && ex.all { it.direction == "bearish" }) emit(PatternSignal("FLOW_EXH_DOWN_$t", "FLOW_EXH_DOWN", "Satış Momentum Zayıflıyor", Bias.WARN, ex.last().close, 60, Severity.MEDIUM, "5-15dk", "Ardışık mumlarda güç azalıyor"), "FLOW_EXH_DOWN")
        }

        val comp = candles.takeLast(3)
        if (comp.size == 3 && comp.all { abs(it.close - it.open) < 15 && it.high - it.low < 30 }) {
            emit(PatternSignal("FLOW_COMP_$t", "FLOW_COMP", "Akış Sıkıştı", Bias.WARN, comp.last().close, 55, Severity.LOW, "5-15dk", "Düşük genlikli ardışık mumlar"), "FLOW_COMP")
        }
        return out
    }
}

class LiquidationPoolSimulator {
    private data class Tier(val lev: Int, val longMult: Double, val shortMult: Double, val baseNotional: Double)
    private val tiers = listOf(
        Tier(100, 0.991, 1.009, 450_000.0),
        Tier(50, 0.982, 1.018, 1_200_000.0),
        Tier(25, 0.964, 1.036, 2_400_000.0),
        Tier(10, 0.910, 1.090, 4_800_000.0)
    )

    fun getPools(mid: Double, cvd: Double, symbol: String): List<LiquidationPool> {
        if (!mid.isFinite()) return emptyList()
        val absCvdMult = min(2.5, max(0.6, 1 + abs(cvd) / 500.0))
        val scale = if (symbol.lowercase().contains("btc")) 1.0 else 0.3
        return tiers.flatMap { tier ->
            val longEst = tier.baseNotional * scale * absCvdMult * (if (cvd > 0) 1.35 else 0.85)
            val shortEst = tier.baseNotional * scale * absCvdMult * (if (cvd < 0) 1.35 else 0.85)
            listOf(
                LiquidationPool(tier.lev, "long", mid * tier.longMult, longEst),
                LiquidationPool(tier.lev, "short", mid * tier.shortMult, shortEst)
            )
        }
    }
}

class LiquidationPressureCalculator {
    fun calculate(pools: List<LiquidationPool>, mid: Double): Double {
        if (pools.isEmpty() || !mid.isFinite()) return 0.0
        var weighted = 0.0; var totalW = 0.0
        for (p in pools) {
            val distPct = abs(p.price - mid) / mid
            if (distPct <= 0) continue
            val w = p.estNotionalUsd / 1_000_000.0
            weighted += w * (1.0 / (distPct * 100.0))
            totalW += w
        }
        if (totalW <= 0) return 0.0
        return (weighted / totalW * 10.0).coerceIn(0.0, 100.0)
    }
}

class LiquidationPatternDetector {
    private val lastFire = mutableMapOf<String, Long>()

    fun analyze(events: List<LiquidationEvent>, mid: Double): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        val t = System.currentTimeMillis()
        val recent = events.filter { t - it.timestamp < 60_000 }
        if (recent.isEmpty()) return out

        val longLiq = recent.filter { it.side == "SELL" }.sumOf { it.notionalUsd }
        val shortLiq = recent.filter { it.side == "BUY" }.sumOf { it.notionalUsd }

        if (longLiq >= 2_000_000 && t - (lastFire["cascade_long"] ?: 0) > 120_000) {
            lastFire["cascade_long"] = t
            out += PatternSignal(
                id = "CASCADE_LONG_$t", type = "LIQUIDATION_CASCADE", title = "Long Tasfiye Şelalesi",
                bias = Bias.BEAR, price = mid, confidence = Fmt.clamp(60 + (longLiq / 1_000_000).toInt(), 60, 92),
                severity = Severity.HIGH, timeframe = "1-10dk",
                explanation = Fmt.fmtN(longLiq) + " long pozisyon 60sn'de tasfiye edildi — kademeli düşüş riski",
                metadata = mapOf("notional" to longLiq, "count" to recent.count { it.side == "SELL" })
            )
        }
        if (shortLiq >= 2_000_000 && t - (lastFire["cascade_short"] ?: 0) > 120_000) {
            lastFire["cascade_short"] = t
            out += PatternSignal(
                id = "CASCADE_SHORT_$t", type = "LIQUIDATION_CASCADE", title = "Short Tasfiye Şelalesi",
                bias = Bias.BULL, price = mid, confidence = Fmt.clamp(60 + (shortLiq / 1_000_000).toInt(), 60, 92),
                severity = Severity.HIGH, timeframe = "1-10dk",
                explanation = Fmt.fmtN(shortLiq) + " short pozisyon 60sn'de tasfiye edildi — kademeli yükseliş riski",
                metadata = mapOf("notional" to shortLiq, "count" to recent.count { it.side == "BUY" })
            )
        }

        val prev5min = events.filter { t - it.timestamp in 60_000..300_000 }
        val prevLiq = prev5min.sumOf { it.notionalUsd }
        if (prevLiq >= 2_000_000 && longLiq + shortLiq < prevLiq * 0.15 && t - (lastFire["exh"] ?: 0) > 120_000) {
            lastFire["exh"] = t
            out += PatternSignal(
                id = "LIQ_EXH_$t", type = "LIQUIDATION_EXHAUSTION", title = "Tasfiye Tükenmesi",
                bias = Bias.WARN, price = mid, confidence = 70, severity = Severity.MEDIUM, timeframe = "5-20dk",
                explanation = "Şelale sonrası tasfiye akışı durdu — ters dönüş adayı",
                metadata = mapOf("prevNotional" to prevLiq, "recentNotional" to (longLiq + shortLiq))
            )
        }
        return out
    }
}
