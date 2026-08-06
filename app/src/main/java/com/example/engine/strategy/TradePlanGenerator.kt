package com.example.engine.strategy

import com.example.engine.pattern.AppState
import com.example.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/* ============================================================================
 * TRADE PLAN GENERATOR — duvar bazlı Entry/SL/TP + volatilite adaptif buffer
 * + fee-adjusted R:R + trailing stop önerisi
 * ========================================================================== */
class TradePlanGenerator {

    fun generatePlan(signals: List<PatternSignal>, book: Book): TradePlan {
        val mid = book.mid ?: AppState.lastPrice ?: return neutralPlan(0.0)
        val bull = signals.filter { it.bias == Bias.BULL }
        val bear = signals.filter { it.bias == Bias.BEAR }
        val bullCount = bull.size; val bearCount = bear.size
        val bullScore = if (bullCount > 0) bull.map { it.confidence }.average() else 0.0
        val bearScore = if (bearCount > 0) bear.map { it.confidence }.average() else 0.0
        val net = bullScore - bearScore
        val threshold = 40 + min(20, (bullCount + bearCount) * 2)

        return when {
            net > threshold -> generateDirectionalPlan(Direction.LONG, signals, mid)
            net < -threshold -> generateDirectionalPlan(Direction.SHORT, signals, mid)
            else -> neutralPlan(mid)
        }
    }

    fun buffer(mid: Double): Double {
        var range = 0.0
        val recent = AppState.heatHistory.takeLast(12).flatMap {
            listOfNotNull(it.bids.firstOrNull()?.price, it.asks.firstOrNull()?.price)
        }
        if (recent.size > 2) range = recent.max() - recent.min()
        return max(Fmt.tickSizeFor(mid) * 20, max(range * 0.06, mid * 0.00025))
    }

    private fun generateDirectionalPlan(direction: Direction, signals: List<PatternSignal>, mid: Double): TradePlan {
        val isLong = direction == Direction.LONG
        val buf = buffer(mid)

        val support = signals.filter { it.type == "STRONG_BID_WALL" && it.price < mid }.maxByOrNull { it.price }
        val resistance = signals.filter { it.type == "STRONG_ASK_WALL" && it.price > mid }.minByOrNull { it.price }

        val entry = when {
            support != null && isLong -> PriceZone(support.price, support.price + buf * 0.35, "Destek duvarı üstü giriş @ " + Fmt.price(support.price))
            resistance != null && !isLong -> PriceZone(resistance.price - buf * 0.35, resistance.price, "Direnç duvarı altı giriş @ " + Fmt.price(resistance.price))
            else -> PriceZone(mid - (if (isLong) buf * 0.35 else buf * 0.15), mid + (if (isLong) buf * 0.15 else buf * 0.35), "Piyasa fiyatı civarı")
        }

        val stopPrice = when {
            support != null && isLong -> min(support.invalidation ?: (support.price - buf), support.price - buf)
            resistance != null && !isLong -> max(resistance.invalidation ?: (resistance.price + buf), resistance.price + buf)
            else -> if (isLong) mid - buf * 1.2 else mid + buf * 1.2
        }

        val tp1Price = resistance?.price ?: (if (isLong) mid + buf * 2 else mid - buf * 2)
        val tp2Price = if (isLong) mid + buf * 4 else mid - buf * 4

        val e = (entry.low + entry.high) / 2
        val risk = max(if (isLong) e - stopPrice else stopPrice - e, 1e-9)
        val rr1 = if (isLong) (tp1Price - e) / risk else (e - tp1Price) / risk
        val rr2 = if (isLong) (tp2Price - e) / risk else (e - tp2Price) / risk

        val feeCost = AppState.config.feeRate * 2 * e
        val rr1Adj = max(0.0, (rr1 * risk - feeCost) / risk)
        val rr2Adj = max(0.0, (rr2 * risk - feeCost) / risk)

        val exhaustion = signals.any {
            it.type in setOf("FLOW_EXH_UP", "FLOW_EXH_DOWN", "LIQUIDATION_EXHAUSTION") &&
                System.currentTimeMillis() - it.createdAt < 60_000
        }
        val trailing = TrailingStopSpec(active = resistance != null || exhaustion, distance = buf * 2, trigger = resistance?.price)

        val conf = max(40, min(95, (support?.confidence ?: 0) + (resistance?.confidence ?: 0) / 2))

        return TradePlan(
            direction = direction, confidence = conf, entry = entry,
            stopLoss = PriceZone(stopPrice, stopPrice, "Sinyal geçersizlik seviyesi"),
            tp1 = PriceZone(tp1Price, tp1Price, "İlk direnç/destek hedefi"),
            tp2 = PriceZone(tp2Price, tp2Price, "Uzatılmış hedef"),
            riskReward1 = rr1Adj, riskReward2 = rr2Adj, trailingStop = trailing,
            reasoning = buildReason(signals, isLong)
        )
    }

    private fun neutralPlan(mid: Double): TradePlan {
        val buf = buffer(mid)
        return TradePlan(
            direction = Direction.NEUTRAL, confidence = 0,
            entry = PriceZone(mid - buf * 0.15, mid + buf * 0.15, "Fiyat civarı"),
            reasoning = "Orderbook net yön belirtmiyor; daha güçlü sinyal bekleniyor"
        )
    }

    private fun buildReason(signals: List<PatternSignal>, isLong: Boolean): String {
        val parts = mutableListOf<String>()
        val targets = signals.filter { (if (isLong) it.type == "STRONG_ASK_WALL" else it.type == "STRONG_BID_WALL") && it.price > 0 }
        if (targets.isNotEmpty()) parts += "${targets.size} ${if (isLong) "direnç" else "destek"} hedefi"
        if (signals.any { it.type == "ABSORPTION" }) parts += "aktif absorpsiyon"
        if (signals.any { it.type == "LIQUIDITY_VOID" }) parts += "likidite boşluğu"
        if (signals.any { it.type == "WALL_PULL" }) parts += "duvar çekilmesi (spoof)"
        return parts.joinToString("; ").ifEmpty { if (isLong) "boğa baskısı" else "ayı baskısı" }
    }
}
