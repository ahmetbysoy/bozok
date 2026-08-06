package com.example.engine.detect

import com.example.engine.pattern.AppState
import com.example.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/* ============================================================================
 * DUVAR / SPOOF / ICEBERG / MERDİVEN / SIKIŞMA / SKEW / OFI / STOP-HUNT
 * (HTML PatternEngineV2 alt modülleri — birebir)
 * ========================================================================== */

class StrongWallDetector(private val side: String) {

    fun calculateDynamicThreshold(levels: List<BookLevel>): Double {
        if (levels.isEmpty()) return Double.MAX_VALUE
        val sorted = levels.map { it.notional }.sorted()
        return sorted[sorted.size / 2] * AppState.config.wallMult
    }

    fun analyze(levels: List<BookLevel>, mid: Double): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        if (levels.isEmpty() || !mid.isFinite()) return out
        val th = calculateDynamicThreshold(levels)
        for (lvl in levels) {
            if (lvl.notional < th) continue
            val isBid = side == "bid"
            if (isBid && lvl.price >= mid) continue
            if (!isBid && lvl.price <= mid) continue
            val ratio = lvl.notional / max(1.0, th)
            out += PatternSignal(
                id = "WALL_$side${lvl.price}",
                type = if (isBid) "STRONG_BID_WALL" else "STRONG_ASK_WALL",
                title = if (isBid) "Güçlü Alım Duvarı" else "Güçlü Satış Duvarı",
                bias = if (isBid) Bias.BULL else Bias.BEAR,
                price = lvl.price,
                confidence = Fmt.clamp(60 + (ratio * 8).toInt(), 60, 92),
                severity = if (ratio >= 3) Severity.HIGH else Severity.MEDIUM,
                timeframe = "10-60dk",
                explanation = Fmt.qty(lvl.qty) + " @ " + Fmt.price(lvl.price) + " (" + Fmt.fmtN(lvl.notional) + ") — kurumsal seviye duvarı",
                invalidation = if (isBid) lvl.price * 0.997 else lvl.price * 1.003,
                metadata = mapOf("notional" to lvl.notional, "ratio" to ratio)
            )
        }
        return out
    }
}

class WallPullDetector {
    private val lastFire = mutableMapOf<String, Long>()

    fun analyze(walls: List<WallRecord>, trades: List<Trade>): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        val t = System.currentTimeMillis()
        val win = AppState.config.spoofWindowMs

        for (wall in walls) {
            val age = t - wall.firstSeen
            if (age > win) continue
            if (wall.lastSeen - wall.firstSeen < 50) continue

            val executed = trades.filter {
                abs(it.price - wall.price) / wall.price < 0.0005 && t - it.timestamp < win
            }.sumOf { it.notional }

            val absorbedFrac = executed / max(wall.maxNotional, 1e-9)
            val isBid = wall.side == "bid"

            if (absorbedFrac < 0.25) {
                val key = wall.side + wall.price
                if (t - (lastFire[key] ?: 0) < 120_000) continue
                lastFire[key] = t
                val conf = Fmt.clamp(70 + (win - age) / win.toDouble() * 15, 70.0, 88.0).toInt()
                out += PatternSignal(
                    id = "SPOOF_${wall.side}_${wall.price}_$t",
                    type = "WALL_PULL",
                    title = if (isBid) "Sahte Alım Duvarı Çekildi" else "Sahte Satış Duvarı Çekildi",
                    bias = if (isBid) Bias.BEAR else Bias.BULL,
                    price = wall.price,
                    confidence = conf,
                    severity = if (conf > 78) Severity.HIGH else Severity.MEDIUM,
                    timeframe = "1-5dk",
                    explanation = Fmt.fmtN(wall.maxNotional) + " duvar " + age + "ms'de %" + "%.0f".format(absorbedFrac * 100) + " emilimle çekildi — spoof trap",
                    invalidation = if (isBid) wall.price * 0.997 else wall.price * 1.003,
                    metadata = mapOf("side" to wall.side, "lifeMs" to age, "absorbedFrac" to absorbedFrac)
                )
            }
        }
        return out
    }

    fun reset() { lastFire.clear() }
}

class AbsorptionDetector {
    private val lastFire = mutableMapOf<String, Long>()

    fun analyze(walls: List<WallRecord>, trades: List<Trade>): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        val t = System.currentTimeMillis()
        for (wall in walls) {
            val executed = trades.filter {
                abs(it.price - wall.price) / wall.price < 0.0008 && t - it.timestamp < 5000
            }.sumOf { it.notional }
            if (executed < wall.maxNotional * 0.35) continue
            val isBid = wall.side == "bid"
            val key = wall.side + wall.price
            if (t - (lastFire[key] ?: 0) < 90_000) continue
            lastFire[key] = t
            val conf = Fmt.clamp(55 + (executed / max(wall.maxNotional, 1e-9) * 25).toInt(), 55, 85)
            out += PatternSignal(
                id = "ABS_${wall.side}_${wall.price}_$t",
                type = "ABSORPTION",
                title = if (isBid) "Duvar Erimesi (Alıcı Emilimi)" else "Duvar Erimesi (Satıcı Emilimi)",
                bias = if (isBid) Bias.BULL else Bias.BEAR,
                price = wall.price,
                confidence = conf,
                severity = if (conf > 75) Severity.HIGH else Severity.MEDIUM,
                timeframe = "5-20dk",
                explanation = Fmt.fmtN(executed) + " işlem duvarı %" + "%.0f".format((executed / max(wall.maxNotional, 1e-9)) * 100) + " eritti",
                metadata = mapOf("side" to wall.side, "executed" to executed)
            )
        }
        return out
    }

    fun reset() { lastFire.clear() }
}

class LiquidityVoidDetector {
    private val lastFire = mutableMapOf<String, Long>()

    fun analyze(book: Book, mid: Double, trades: List<Trade>): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        if (!mid.isFinite() || book.bids.size < 4 || book.asks.size < 4) return out
        val t = System.currentTimeMillis()

        val askGap = findVoid(book.asks, mid)
        val bidGap = findVoid(book.bids, mid)

        val recentTrades = trades.filter { t - it.timestamp < 3000 }
        val buyFlow = recentTrades.filter { it.side == Side.BUY }.sumOf { it.notional }
        val sellFlow = recentTrades.filter { it.side == Side.SELL }.sumOf { it.notional }

        fun voidSignal(bias: Bias, price: Double, zone: SignalZone, up: Boolean, vacuum: Boolean): PatternSignal? {
            val key = if (up) "up" else "down"
            if (t - (lastFire[key] ?: 0) < 120_000) return null
            lastFire[key] = t
            return PatternSignal(
                id = "VOID_${key}_$t",
                type = "LIQUIDITY_VOID",
                title = if (up) "Yukarı Likidite Boşluğu" else "Aşağı Likidite Boşluğu",
                bias = bias,
                price = price,
                confidence = if (vacuum) 82 else 68,
                severity = if (vacuum) Severity.HIGH else Severity.MEDIUM,
                timeframe = "30sn-5dk",
                explanation = if (vacuum) "Boşluk agresif akışla dolduruluyor (vacuum fill) — devam sinyali"
                else (if (up) "Ask" else "Bid") + " tarafında ince likidite boşluğu — fiyat süpürmeye müsait",
                zone = zone,
                metadata = mapOf("vacuumFill" to vacuum)
            )
        }

        if (askGap != null && (buyFlow > sellFlow * 1.5 || askGap.second)) {
            voidSignal(Bias.BULL, askGap.first.high, askGap.first, up = true, vacuum = askGap.second)?.let { out += it }
        }
        if (bidGap != null && (sellFlow > buyFlow * 1.5 || bidGap.second)) {
            voidSignal(Bias.BEAR, bidGap.first.low, bidGap.first, up = false, vacuum = bidGap.second)?.let { out += it }
        }
        return out
    }

    private fun findVoid(levels: List<BookLevel>, mid: Double): Pair<SignalZone, Boolean>? {
        if (levels.size < 4) return null
        val gaps = mutableListOf<Double>()
        for (i in 1 until levels.size) gaps.add(abs(levels[i].price - levels[i - 1].price))
        val avgGap = gaps.average()
        if (avgGap <= 0) return null
        for (i in 1 until levels.size) {
            val gap = abs(levels[i].price - levels[i - 1].price)
            if (gap > avgGap * 8 && gap > mid * 0.0005) {
                val zone = SignalZone(min(levels[i - 1].price, levels[i].price), max(levels[i - 1].price, levels[i].price))
                return zone to false
            }
        }
        return null
    }

    fun reset() { lastFire.clear() }
}

class LadderDetectorV2 {
    private val seen = mutableMapOf<String, Long>()

    fun analyze(walls: List<WallRecord>, mid: Double): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        if (walls.size < 3) return out
        val t = System.currentTimeMillis()

        for (side in listOf("bid", "ask")) {
            val sideWalls = walls.filter { it.side == side }.sortedBy { it.price }
            if (sideWalls.size < 3) continue
            val gaps = mutableListOf<Double>()
            for (i in 1 until sideWalls.size) gaps.add(abs(sideWalls[i].price - sideWalls[i - 1].price))
            val avgGap = gaps.average()
            if (avgGap <= 0) continue
            if (!gaps.all { abs(it - avgGap) / avgGap < 0.15 }) continue

            val key = "ladder_$side"
            if (t - (seen[key] ?: 0) < 180_000) continue
            seen[key] = t

            val conf = Fmt.clamp(55 + walls.size * 5, 55, 88)
            out += PatternSignal(
                id = "LADDER_${side}_$t",
                type = "LADDER_ORDERS",
                title = if (side == "bid") "Algoritmik Alım Merdiveni" else "Algoritmik Satış Merdiveni",
                bias = if (side == "bid") Bias.BULL else Bias.BEAR,
                price = mid,
                confidence = conf,
                severity = if (conf > 75) Severity.HIGH else Severity.MEDIUM,
                timeframe = "5-30dk",
                explanation = walls.size.toString() + " duvar " + "%.1f".format(avgGap) + " farkla eşit aralıklı — algoritmik emir dizilimi",
                metadata = mapOf("side" to side, "gap" to avgGap, "count" to walls.size)
            )
        }
        return out
    }

    fun reset() { seen.clear() }
}

class CompressionDetector {
    private var lastFire = 0L

    fun analyze(signals: List<PatternSignal>, mid: Double): List<PatternSignal> {
        val t = System.currentTimeMillis()
        if (t - lastFire < 60_000) return emptyList()
        val bid = signals.filter { it.type == "STRONG_BID_WALL" && it.price < mid }.maxByOrNull { it.price } ?: return emptyList()
        val ask = signals.filter { it.type == "STRONG_ASK_WALL" && it.price > mid }.minByOrNull { it.price } ?: return emptyList()
        val band = (ask.price - bid.price) / mid
        if (band >= 0.01) return emptyList()
        lastFire = t
        val conf = Fmt.clamp(60 + ((1 - band / 0.01) * 25).toInt(), 60, 85)
        return listOf(
            PatternSignal(
                id = "COMPRESS_$t", type = "COMPRESSION_ZONE", title = "Sıkışma Bölgesi", bias = Bias.WARN,
                price = mid, confidence = conf, severity = Severity.MEDIUM, timeframe = "30sn-5dk",
                explanation = "Fiyat " + Fmt.price(bid.price) + " ve " + Fmt.price(ask.price) + " duvarları arasında sıkıştı — volatilite patlaması riski",
                zone = SignalZone(bid.price, ask.price), metadata = mapOf("band" to band)
            )
        )
    }

    fun reset() { lastFire = 0 }
}

class IcebergDetector {
    private val wallHistory = mutableMapOf<String, WallRecord>()
    private val fired = mutableSetOf<String>()

    fun analyze(currentWalls: List<WallRecord>, trades: List<Trade>): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        val t = System.currentTimeMillis()
        val currentKeys = currentWalls.map { "${it.side}:${it.price}" }.toSet()

        for ((key, h) in wallHistory) {
            if (currentKeys.contains(key)) continue
            val (side, priceStr) = key.split(":")
            val price = priceStr.toDouble()
            val life = t - h.firstSeen
            val executed = trades.filter { abs(it.price - price) / max(price, 1e-9) < 0.0005 }.sumOf { it.notional }
            val absorbedFrac = Fmt.clamp(executed / max(h.maxNotional, 1e-9), 0.0, 3.0)
            val stillBig = h.maxNotional >= h.notional * 0.7
            val fireKey = "$key:${h.firstSeen / 30_000}"

            if (life > 8_000 && absorbedFrac < 0.4 && stillBig && !fired.contains(fireKey)) {
                fired.add(fireKey)
                val bull = side == "bid"
                val conf = Fmt.clamp(40 + (life / 1000).toInt() + (min(h.sizeRatio, 8.0) * 1.2).toInt(), 40, 90)
                out += PatternSignal(
                    id = "ICEBERG_$key", type = "ICEBERG", title = "Gizli Iceberg",
                    bias = if (bull) Bias.BULL else Bias.BEAR, price = price, confidence = conf,
                    severity = if (conf > 75) Severity.HIGH else Severity.MEDIUM, timeframe = "10-30dk",
                    explanation = Fmt.fmtN(h.maxNotional) + " " + side + " duvarı " + (life / 1000).toInt() + "sn minimal infazla durdu — gizli birikim/dağıtım",
                    invalidation = if (bull) price * 0.997 else price * 1.003,
                    metadata = mapOf("side" to side, "lifeMs" to life, "sizeRatio" to h.sizeRatio, "absorbedFrac" to absorbedFrac)
                )
            }
            wallHistory.remove(key)
        }

        val medN = currentWalls.map { it.notional }.medianOrNull() ?: 1.0
        for (wall in currentWalls) {
            val key = "${wall.side}:${wall.price}"
            val h = wallHistory[key]
            if (h != null) {
                h.lastSeen = t; h.notional = wall.notional; h.maxNotional = max(h.maxNotional, wall.notional)
            } else {
                wallHistory[key] = WallRecord(wall.side, wall.price, wall.notional, wall.qty, t, t, wall.notional, wall.notional / max(medN, 1e-9))
            }
        }
        val cut = t - 600_000
        wallHistory.entries.removeAll { it.value.lastSeen < cut }
        if (fired.size > 600) fired.clear()
        return out
    }

    fun reset() { wallHistory.clear(); fired.clear() }
}

class OrderbookSkewDetector {
    private val lastFire = mutableMapOf<String, Long>()
    private val hist = ArrayDeque<Pair<Long, Double>>()

    fun analyze(currentWalls: List<WallRecord>, mid: Double): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        val t = System.currentTimeMillis()
        val bidWalls = currentWalls.filter { it.side == "bid" && it.price < mid }
        val askWalls = currentWalls.filter { it.side == "ask" && it.price > mid }
        val bidN = bidWalls.sumOf { it.notional }
        val askN = askWalls.sumOf { it.notional }
        if (bidN <= 0 || askN <= 0) return out
        val skew = (bidN - askN) / (bidN + askN)

        hist.addLast(t to skew)
        while (hist.isNotEmpty() && hist.first().first < t - 10_000) hist.removeFirst()
        var delta = 0.0
        if (hist.size >= 3) delta = hist.last().second - hist.first().second
        val rapidShift = abs(delta) >= 0.30

        if (abs(skew) < 0.45 && !rapidShift) return out
        val bidHeavy = skew > 0
        val side = if (bidHeavy) "bid" else "ask"
        if (t - (lastFire[side] ?: 0) < 120_000) return out
        lastFire[side] = t

        val conf = Fmt.clamp(50 + (abs(skew) * 30).toInt() + if (rapidShift) 10 else 0, 50, 90)
        out += PatternSignal(
            id = "SKEW_${side}_$t", type = "BOOK_SKEW",
            title = if (bidHeavy) "Bid-Ağırlıklı Emir Defteri" else "Ask-Ağırlıklı Emir Defteri",
            bias = if (bidHeavy) Bias.BULL else Bias.BEAR, price = mid, confidence = conf,
            severity = if (conf > 75) Severity.HIGH else Severity.MEDIUM, timeframe = "5-20dk",
            explanation = "Book skew " + (skew * 100).toInt() + "% " + (if (bidHeavy) "bid" else "ask") +
                (if (rapidShift) " — " + "%.0f".format(delta * 100) + "%/10sn hızla kayıyor" else "") +
                " (" + bidWalls.size + "b/" + askWalls.size + "a walls)",
            metadata = mapOf("skew" to skew, "delta10s" to delta, "rapidShift" to rapidShift)
        )
        return out
    }

    fun reset() { lastFire.clear(); hist.clear() }
}

class OFISpikeDetector {
    var lastFire = mutableMapOf<String, Long>()

    fun detect(trades: List<Trade>, mid: Double, priceStable: Boolean): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        val t = System.currentTimeMillis()
        if (!mid.isFinite()) return out

        var bidAggr = 0.0; var askAggr = 0.0
        for (tr in trades.asReversed()) {
            if (t - tr.timestamp > 3000) break
            if (tr.side == Side.BUY) bidAggr += tr.notional else askAggr += tr.notional
        }
        val spike = max(bidAggr, askAggr)
        if (!priceStable || spike < 300_000) return out

        val bidSide = bidAggr > askAggr
        val side = if (bidSide) "bid" else "ask"
        if (t - (lastFire[side] ?: 0) < 120_000) return out
        lastFire[side] = t

        val conf = Fmt.clamp(58 + min((spike / 500_000 * 20).toInt(), 25), 58, 88)
        out += PatternSignal(
            id = "OFI_${side}_$t", type = "HIDDEN_ABSORPTION",
            title = if (bidSide) "Bid Absorpsiyonu (Gizli)" else "Ask Absorpsiyonu (Gizli)",
            bias = if (bidSide) Bias.BULL else Bias.BEAR, price = mid, confidence = conf,
            severity = if (conf > 75) Severity.HIGH else Severity.MEDIUM, timeframe = "1-5dk",
            explanation = Fmt.fmtN(spike) + " agresif " + (if (bidSide) "alım" else "satım") + " akışı fiyat sabitken emiliyor — gizli absorpsiyon",
            metadata = mapOf("aggressiveNotional" to spike, "bidAggr" to bidAggr, "askAggr" to askAggr, "priceStable" to priceStable)
        )
        return out
    }

    fun reset() { lastFire = mutableMapOf("bid" to 0L, "ask" to 0L) }
}

/**
 * StopHuntDetector — EQH/EQL sweep. FIX (HTML): gerçek 15dk kayar pencere
 * (HTML 60sn'lik heatmap penceresini "15dk" diye kullanıyordu).
 */
class StopHuntDetector(private val windowMs: Long = 15 * 60 * 1000L) {
    private data class Px(val p: Double, val t: Long)
    private val history = ArrayDeque<Px>()
    private val lastFire = mutableMapOf<String, Long>()

    fun update(price: Double, nowMs: Long = System.currentTimeMillis()) {
        while (history.isNotEmpty() && nowMs - history.first().t > windowMs) history.removeFirst()
        history.addLast(Px(price, nowMs))
        while (history.size > 3600) history.removeFirst()
    }

    fun analyze(currentPrice: Double): List<PatternSignal> {
        val out = mutableListOf<PatternSignal>()
        if (history.isEmpty()) return out
        val t = System.currentTimeMillis()
        while (history.isNotEmpty() && t - history.first().t > windowMs) history.removeFirst()
        if (history.isEmpty()) return out

        val maxPx = history.maxOf { it.p }
        val minPx = history.minOf { it.p }
        val tol = Fmt.tickSizeFor(currentPrice) * 3

        if (currentPrice >= maxPx - tol && t - (lastFire["high"] ?: 0) > 120_000) {
            lastFire["high"] = t
            out += PatternSignal(
                id = "SWEEP_HIGH_$t", type = "STOP_HUNT_SWEEP", title = "Stop-Hunt Sweep (Tepe Avı / EQH)",
                bias = Bias.BEAR, price = currentPrice, confidence = 84, severity = Severity.HIGH, timeframe = "1-5dk",
                explanation = "Fiyat son 15dk tepe bölgesini (" + Fmt.price(maxPx) + ") iğneledi — stop-loss avı (sweep & reject)",
                metadata = mapOf("sweptLevel" to maxPx, "side" to "high")
            )
        } else if (currentPrice <= minPx + tol && t - (lastFire["low"] ?: 0) > 120_000) {
            lastFire["low"] = t
            out += PatternSignal(
                id = "SWEEP_LOW_$t", type = "STOP_HUNT_SWEEP", title = "Stop-Hunt Sweep (Dip Avı / EQL)",
                bias = Bias.BULL, price = currentPrice, confidence = 84, severity = Severity.HIGH, timeframe = "1-5dk",
                explanation = "Fiyat son 15dk dip bölgesini (" + Fmt.price(minPx) + ") iğneledi — stop-loss avı (sweep & reject)",
                metadata = mapOf("sweptLevel" to minPx, "side" to "low")
            )
        }
        update(currentPrice, t)
        return out
    }

    fun reset() { history.clear(); lastFire.clear() }
}

/**
 * VPIN — FIX (HTML): kova hacmi sembole göre adaptif ($100k-$2M).
 */
class VpinCalculator(private val numBuckets: Int = 10, var bucketVolumeUsd: Double = 500_000.0) {
    private var buyVol = 0.0
    private var sellVol = 0.0
    private val completed = ArrayDeque<Double>()

    fun setBucketVolume(newBucket: Double) {
        if (newBucket == bucketVolumeUsd) return
        bucketVolumeUsd = newBucket
        buyVol = 0.0; sellVol = 0.0
        completed.clear()
    }

    fun update(tr: Trade) {
        val vol = tr.notional
        if (tr.side == Side.BUY) buyVol += vol else sellVol += vol
        if (buyVol + sellVol >= bucketVolumeUsd) {
            val imbalance = abs(buyVol - sellVol)
            completed.addLast(imbalance / (buyVol + sellVol) * 100.0)
            if (completed.size > numBuckets) completed.removeFirst()
            buyVol = 0.0; sellVol = 0.0
        }
    }

    fun getVPIN(): Double? = if (completed.isEmpty()) null else completed.average()

    companion object {
        fun adaptiveBucketFor(price: Double): Double = when {
            price >= 1000 -> 2_000_000.0
            price >= 10 -> 500_000.0
            else -> 100_000.0
        }
    }
}

/** CVD Diverjans — balina vs retail (SMART_MONEY_DISTRIBUTION). */
class CvdDivergenceDetector {
    private var lastFire = 0L

    fun detect(priceSeries: List<Double>, cvdSeries: List<Double>, largeCvdSeries: List<Double>, smallCvdSeries: List<Double>, lastPrice: Double): PatternSignal? {
        val t = System.currentTimeMillis()
        if (t - lastFire < 120_000) return null
        if (priceSeries.size < 4 || cvdSeries.size < 4) return null

        val priceUp = priceSeries.last() > priceSeries.first()
        val cvdUp = cvdSeries.last() > cvdSeries.first()

        if (largeCvdSeries.size >= 4 && smallCvdSeries.size >= 4 && priceUp) {
            val largeUp = largeCvdSeries.last() > largeCvdSeries.first()
            val smallUp = smallCvdSeries.last() > smallCvdSeries.first()
            if (!largeUp && smallUp) {
                lastFire = t
                return PatternSignal(
                    id = "SMD_$t", type = "SMART_MONEY_DISTRIBUTION", title = "Akıllı Para Dağıtıyor",
                    bias = Bias.BEAR, price = lastPrice,
                    confidence = Fmt.clamp(60 + (abs(largeCvdSeries.last() - largeCvdSeries.first()) / max(1.0, largeCvdSeries.first()) * 20).toInt(), 60, 88),
                    severity = Severity.HIGH, timeframe = "10-30dk",
                    explanation = "Fiyat yükselirken balina CVD düşüyor ama retail alıyor — akıllı para satıyor olabilir",
                    metadata = mapOf("largeCvdDir" to (if (largeUp) "up" else "down"), "smallCvdDir" to (if (smallUp) "up" else "down"))
                )
            }
        }

        if (priceUp == cvdUp) return null
        val priceChg = abs((priceSeries.last() - priceSeries.first()) / priceSeries.first()) * 100
        if (priceChg < 0.05) return null
        val conf = Fmt.clamp(55 + min((priceChg * 15).toInt(), 25), 55, 85)
        lastFire = t
        return PatternSignal(
            id = "CVD_DIV_$t", type = "CVD_DIVERGENCE",
            title = if (priceUp) "Fiyat ↑ · CVD ↓" else "Fiyat ↓ · CVD ↑",
            bias = Bias.WARN, price = lastPrice, confidence = conf,
            severity = if (conf > 75) Severity.HIGH else Severity.MEDIUM, timeframe = "5-15dk",
            explanation = "Fiyat " + (if (priceUp) "yükselirken" else "düşerken") + " CVD " + (if (priceUp) "düşüyor" else "yükseliyor") + " — momentum zayıflıyor",
            metadata = mapOf("priceUp" to priceUp, "cvdUp" to cvdUp, "priceChgPct" to priceChg)
        )
    }
}
