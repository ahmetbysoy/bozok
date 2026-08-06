package com.example.engine.strategy

import com.example.engine.pattern.AppState
import com.example.model.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/* ============================================================================
 * PERFORMANS TAKİBİ + META STRATEGY (4 Avcı) + MİKRO OPTİMİZÖR
 * ========================================================================== */

class StrategyPerformanceTracker {
    private val perfs = mutableMapOf<String, StrategyPerformance>()
    private val netByType = mutableMapOf<String, Double>()
    private val winByType = mutableMapOf<String, Int>()
    private val totalByType = mutableMapOf<String, Int>()

    fun addTrade(strategyId: String, hit: Boolean, netR: Double) {
        totalByType[strategyId] = (totalByType[strategyId] ?: 0) + 1
        if (hit) winByType[strategyId] = (winByType[strategyId] ?: 0) + 1
        netByType[strategyId] = (netByType[strategyId] ?: 0.0) + netR
        val hist = perfs[strategyId]?.equityHistory?.toMutableList() ?: mutableListOf(1000.0)
        hist.add(hist.last() + netR * 10)
        perfs[strategyId] = StrategyPerformance(
            strategyId = strategyId,
            totalTrades = totalByType[strategyId] ?: 0,
            winTrades = winByType[strategyId] ?: 0,
            netRReturn = netByType[strategyId] ?: 0.0,
            equityHistory = hist.takeLast(200)
        )
    }

    fun getPerformance(strategyId: String): StrategyPerformance = perfs[strategyId] ?: StrategyPerformance(strategyId)
    fun getAllPerformances(): List<StrategyPerformance> = perfs.values.toList()

    fun getStrategyBonus(strategyId: String): Int {
        val p = getPerformance(strategyId)
        return when {
            p.winRatePct >= 70.0 -> 10
            p.winRatePct >= 50.0 -> 5
            else -> 0
        }
    }
}

class MetaStrategyEngine(private val perfTracker: StrategyPerformanceTracker) {

    data class StrategyEval(
        val strategyId: String, val strategyName: String, val confidence: Int, val direction: Direction,
        val entry: PriceZone, val stopLoss: PriceZone?, val tp1: PriceZone?, val tp2: PriceZone?,
        val riskReward1: Double, val riskReward2: Double, val trailingStop: TrailingStopSpec,
        val reasoning: String, val webhookPayload: Map<String, Any?>
    )

    data class Tuning(val isMajor: Boolean, val buf: Double, val minDivBps: Double, val bonusDivBps: Double, val kelleStopMult: Double)

    fun getSymbolTuning(symbol: String, mid: Double): Tuning {
        val sym = symbol.lowercase()
        val isMajor = sym.contains("btc") || sym.contains("eth")
        val baseTick = Fmt.tickSizeFor(mid)
        return Tuning(
            isMajor = isMajor,
            buf = baseTick * (if (isMajor) 20 else 30),
            minDivBps = if (isMajor) 6.0 else 12.0,
            bonusDivBps = if (isMajor) 10.0 else 18.0,
            kelleStopMult = if (isMajor) 0.998 else 0.9965
        )
    }

    private val lastFire = mutableMapOf<String, Long>()

    fun evaluate(patterns: List<PatternSignal>, book: Book, trades: List<Trade>, liquidations: List<LiquidationEvent>, symbol: String, pools: List<LiquidationPool>): TradePlan? {
        val mid = book.mid ?: AppState.lastPrice ?: return null
        val now = System.currentTimeMillis()

        var best: StrategyEval? = null
        var maxConf = -1

        val evals = listOf(
            evalKaplanKapan(patterns, mid, symbol),
            evalKelleAvcisi(patterns, mid, symbol),
            evalBalinaTuzagi(patterns, mid, symbol),
            evalIsikArbitraj(mid, symbol)
        )

        for (res in evals) {
            if (res == null) continue
            var conf = Fmt.clamp(res.confidence + perfTracker.getStrategyBonus(res.strategyId), 0, 99)
            val tpPrice = res.tp1?.let { (it.low + it.high) / 2 }
            if (tpPrice != null && pools.any { abs(it.price - tpPrice) / mid < 0.003 }) {
                conf = Fmt.clamp(conf + 5, 0, 99)
            }
            if (conf >= 75 && conf > maxConf) {
                maxConf = conf
                best = res.copy(confidence = conf)
            }
        }

        val winner = best ?: return null
        if (now - (lastFire[winner.strategyId] ?: 0) <= 60_000) return null
        lastFire[winner.strategyId] = now

        return TradePlan(
            direction = winner.direction, confidence = winner.confidence, entry = winner.entry,
            stopLoss = winner.stopLoss, tp1 = winner.tp1, tp2 = winner.tp2,
            riskReward1 = winner.riskReward1, riskReward2 = winner.riskReward2,
            trailingStop = winner.trailingStop, reasoning = winner.reasoning,
            webhookPayload = winner.webhookPayload
        )
    }

    // ---------------- 1) KAPLAN KAPAN ----------------
    private fun evalKaplanKapan(patterns: List<PatternSignal>, mid: Double, symbol: String): StrategyEval? {
        val now = System.currentTimeMillis()
        val pull = patterns.find { (it.type == "WALL_PULL" || it.type == "SPOOF") && now - it.createdAt < 60_000 }
        val voidUp = patterns.find { it.type == "LIQUIDITY_VOID" && it.bias == Bias.BULL }
        val flowBull = patterns.find { it.type == "FLOW_BULL" || it.type == "FLOW_REV_UP" || it.type == "HIDDEN_ABSORPTION" }
        if (pull == null && !(voidUp != null && flowBull != null)) return null

        var score = 50
        if (pull != null && voidUp != null) score += 25
        if (flowBull != null) score += 15
        if (patterns.any { it.type == "SMART_MONEY_DISTRIBUTION" }) score += 10
        score = Fmt.clamp(score, 0, 96)

        val tune = getSymbolTuning(symbol, mid)
        val buf = tune.buf
        val e = pull?.price ?: mid
        val entry = PriceZone(e - buf * 0.2, e + buf * 0.2, "Ask duvarının çekildiği/boşluk başlangıcı fiyattan giriş")
        val stopPrice = e - buf * 1.2
        val tpPrice = voidUp?.zone?.high ?: (e + buf * 3.0)
        val risk = max(e - stopPrice, 1e-9)
        val rr1 = (tpPrice - e) / risk

        return StrategyEval(
            "KAPLAN_KAPAN", "KAPLAN KAPAN (Spoof Trap & Void Sweep)", score, Direction.LONG,
            entry, PriceZone(stopPrice, stopPrice, "Çekilen duvarın 3 tick altı"),
            PriceZone(tpPrice, tpPrice, "Likidite boşluğu tavanı (Magnet Price)"),
            PriceZone(tpPrice + buf * 1.5, tpPrice + buf * 1.5, "Uzatılmış momentum hedefi"),
            rr1, rr1 * 1.4, TrailingStopSpec(true, buf * 1.5, tpPrice),
            "Satıcı duvarı sahteydi (Spoof) çekildi; üstteki likidite boşluğuna doğru ani takibi yakalıyoruz.",
            buildPayload("KAPLAN_KAPAN", "KAPLAN KAPAN (Spoof Trap & Void Sweep)", "LONG", score, e, stopPrice, tpPrice)
        )
    }

    // ---------------- 2) KELLE AVCISI ----------------
    private fun evalKelleAvcisi(patterns: List<PatternSignal>, mid: Double, symbol: String): StrategyEval? {
        val now = System.currentTimeMillis()
        val cascade = patterns.find { it.type == "LIQUIDATION_CASCADE" && it.bias == Bias.BEAR && now - it.createdAt < 90_000 }
        val exh = patterns.find { it.type == "LIQUIDATION_EXHAUSTION" && now - it.createdAt < 90_000 }
        val abs = patterns.find {
            (it.type == "HIDDEN_ABSORPTION" || it.type == "ABSORPTION" || it.type == "ICEBERG") &&
                it.bias == Bias.BULL && now - it.createdAt < 90_000
        }
        if (cascade == null && abs == null) return null

        var score = 55
        if (cascade != null && abs != null) score += 25
        if (exh != null) score += 15
        if (patterns.any { it.type == "STRONG_BID_WALL" }) score += 10
        score = Fmt.clamp(score, 0, 95)

        val tune = getSymbolTuning(symbol, mid)
        val buf = tune.buf
        val e = abs?.price ?: mid
        val entry = PriceZone(e - buf * 0.15, e + buf * 0.15, "Gizli alıcı emilim seviyesi (Iceberg/Absorption)")
        val stopPrice = e * tune.kelleStopMult
        val tp1Price = e + buf * 2.5
        val tp2Price = e + buf * 4.5
        val risk = max(e - stopPrice, 1e-9)
        val rr1 = (tp1Price - e) / risk

        return StrategyEval(
            "KELLE_AVCISI", "KELLE AVCISI (Liquidation Cascade Reversal)", score, Direction.LONG,
            entry, PriceZone(stopPrice, stopPrice, "Emilim seviyesinin altı (Dar Stop)"),
            PriceZone(tp1Price, tp1Price, "Şelale düşüşünün kırılım direnci"),
            PriceZone(tp2Price, tp2Price, "İlk güçlü satıcı duvarı"),
            rr1, rr1 * 1.6, TrailingStopSpec(true, buf * 1.2, tp1Price),
            "Long tasfiyeleri yoruldu, dipte akıllı para gizli emirle yutuyor (V-Dönüş scalp).",
            buildPayload("KELLE_AVCISI", "KELLE AVCISI (Liquidation Cascade Reversal)", "LONG", score, e, stopPrice, tp1Price)
        )
    }

    // ---------------- 3) BALİNA TUZAĞI ----------------
    private fun evalBalinaTuzagi(patterns: List<PatternSignal>, mid: Double, symbol: String): StrategyEval? {
        val now = System.currentTimeMillis()
        val smd = patterns.find { it.type == "SMART_MONEY_DISTRIBUTION" && now - it.createdAt < 90_000 }
        val askIce = patterns.find { (it.type == "ICEBERG" || it.type == "STRONG_ASK_WALL") && it.price > mid && now - it.createdAt < 90_000 }
        val skew = patterns.find { it.type == "BOOK_SKEW" && it.bias == Bias.BEAR }
        val flowDown = patterns.find { it.type == "FLOW_REV_DOWN" || it.type == "FLOW_BEAR" }
        if (smd == null && !(askIce != null && skew != null)) return null

        var score = 55
        if (smd != null && askIce != null) score += 25
        if (skew != null) score += 15
        if (flowDown != null) score += 10
        score = Fmt.clamp(score, 0, 94)

        val tune = getSymbolTuning(symbol, mid)
        val buf = tune.buf
        val e = mid
        val entry = PriceZone(e - buf * 0.15, e + buf * 0.15, "Piyasa fiyatı (Flow aşağı dönüş)")
        val stopPrice = askIce?.price?.let { it + buf * 0.5 } ?: (e + buf * 1.5)
        val tp1Price = e - buf * 2.5
        val tp2Price = e - buf * 4.5
        val risk = max(stopPrice - e, 1e-9)
        val rr1 = (e - tp1Price) / risk

        return StrategyEval(
            "BALINA_TUZAGI", "BALİNA TUZAĞI (Smart Money Distribution Scalp)", score, Direction.SHORT,
            entry, PriceZone(stopPrice, stopPrice, "Ask Iceberg üstü / duvar direnci"),
            PriceZone(tp1Price, tp1Price, "Dağıtım sonrası ilk destek"),
            PriceZone(tp2Price, tp2Price, "Uzatılmış düşüş hedefi"),
            rr1, rr1 * 1.6, TrailingStopSpec(true, buf * 1.2, tp1Price),
            "Fiyat yükselirken balina CVD düşüyor (SMD) — retail alıyor, kurumsal dağıtıyor. Tepe Short scalp.",
            buildPayload("BALINA_TUZAGI", "BALİNA TUZAĞI (Smart Money Distribution Scalp)", "SHORT", score, e, stopPrice, tp1Price)
        )
    }

    // ---------------- 4) IŞIK HIZI ARBİTRAJI ----------------
    private fun evalIsikArbitraj(mid: Double, symbol: String): StrategyEval? {
        val ex = AppState.exchanges
        val b = ex["binance"]; val by = ex["bybit"]; val o = ex["okx"]
        val skews = mutableListOf<ArbitrageSkew>()
        if (b?.bestBid != null && b.bestAsk != null) {
            val bMid = (b.bestBid + b.bestAsk) / 2
            if (by?.bestBid != null && by.bestAsk != null) skews += ArbitrageSkew("Binance", "Bybit", bMid, (by.bestBid + by.bestAsk) / 2)
            if (o?.bestBid != null && o.bestAsk != null) skews += ArbitrageSkew("Binance", "OKX", bMid, (o.bestBid + o.bestAsk) / 2)
        }
        val best = skews.maxByOrNull { it.deviationBps } ?: return null
        val tune = getSymbolTuning(symbol, mid)
        if (best.deviationBps < tune.minDivBps) return null

        var score = Fmt.clamp(40 + min(30, best.deviationBps.toInt()), 0, 94)
        val buf = tune.buf
        val long = best.leadVenue == "Binance"
        val e = mid
        val direction = if (long) Direction.LONG else Direction.SHORT
        val stopPrice = if (long) e - buf * 1.0 else e + buf * 1.0
        val tpPrice = if (long) e + buf * 2.0 else e - buf * 2.0
        val risk = abs(e - stopPrice)
        val rr1 = abs(tpPrice - e) / max(risk, 1e-9)

        return StrategyEval(
            "ISIK_ARBITRAJ", "IŞIK HIZI ARBİTRAJI (Latency Front-Running)", score, direction,
            PriceZone(e - buf * 0.1, e + buf * 0.1, "Öncü borsa yönünde milisaniyelik giriş"),
            PriceZone(stopPrice, stopPrice, "Sapma kapanırsa iptal"),
            PriceZone(tpPrice, tpPrice, "Sapma normalleşme hedefi"),
            null, rr1, rr1 * 1.0, TrailingStopSpec(false, 0.0),
            "Binance-" + best.venueB + " sapması " + "%.1f".format(best.deviationBps) + " bps — öncü borsa " + best.leadVenue + ", front-running.",
            buildPayload("ISIK_ARBITRAJ", "IŞIK HIZI ARBİTRAJI (Latency Front-Running)", direction.name, score, e, stopPrice, tpPrice)
        )
    }

    private fun buildPayload(id: String, name: String, dir: String, conf: Int, entry: Double, sl: Double, tp1: Double): Map<String, Any?> =
        mapOf("strategyId" to id, "strategyName" to name, "direction" to dir, "confidence" to conf,
            "entry" to entry, "stopLoss" to sl, "takeProfit" to tp1)
}

/** Micro hesap — balance-aware kaldıraç + Kelly benzeri risk. */
class MicroAccountOptimizer {
    fun kellyRiskPct(confidence: Int): Double {
        val base = AppState.config.microRiskPct
        if (confidence <= 0) return base
        return Fmt.clamp(0.005 + (confidence / 100.0) * 0.045, 0.005, 0.05)
    }

    data class MicroResult(
        val riskAmountUsd: Double, val priceRiskPct: Double, val recommendedLeverage: Int,
        val positionNotionalUsd: Double, val requiredMarginUsd: Double, val isTradable: Boolean,
        val minStopPct: Double?, val breakEvenPrice: Double, val estLiquidationPrice: Double, val feeCostUsd: Double
    )

    fun calculate(entry: Double, stopLoss: Double, direction: Direction, confidence: Int): MicroResult? {
        if (!entry.isFinite() || !stopLoss.isFinite() || entry <= 0 || stopLoss <= 0) return null
        val balance = AppState.config.microBalance
        val maxLev = AppState.config.microMaxLeverage
        val feeRate = AppState.config.feeRate

        val riskAmount = balance * kellyRiskPct(confidence)
        val priceRiskPct = abs(entry - stopLoss) / entry
        if (priceRiskPct <= 0.0001) return null

        val idealLeverage = ceil(riskAmount / (priceRiskPct * balance)).toInt()
        val recommended = min(maxLev, max(3, idealLeverage))
        val positionNotional = riskAmount / priceRiskPct
        val requiredMargin = positionNotional / recommended
        val isTradable = requiredMargin <= balance

        var minStopPct: Double? = null
        if (!isTradable) minStopPct = riskAmount / (maxLev * balance)

        val isLong = direction == Direction.LONG
        val breakEven = if (isLong) entry + entry * feeRate else entry - entry * feeRate
        val estLiq = if (isLong) entry * (1 - (1.0 / recommended) + feeRate)
        else entry * (1 + (1.0 / recommended) - feeRate)
        val feeCost = positionNotional * feeRate

        return MicroResult(riskAmount, priceRiskPct, recommended, positionNotional, requiredMargin, isTradable, minStopPct, breakEven, estLiq, feeCost)
    }
}
