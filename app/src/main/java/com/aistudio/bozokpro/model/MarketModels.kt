package com.aistudio.bozokpro.model

/* ============================================================================
 * BOZOK PRO — Piyasa veri modelleri
 * HTML: [[price, qty], ...] tuple'ları burada BookLevel'a taşındı.
 * ========================================================================== */

/** Emir defteri seviyesi. HTML `[price, qty]` tuple'ının Kotlin karşılığı. */
data class BookLevel(
    val price: Double,
    val qty: Double,
    val exchangeCount: Int = 1
) {
    val notional: Double get() = price * qty
}

/**
 * HTML `S.book = { bids, asks, ts }`
 * bids fiyata göre azalan sıralı; asks fiyata göre artan.
 */
data class Book(
    val bids: List<BookLevel> = emptyList(),
    val asks: List<BookLevel> = emptyList(),
    val ts: Long = 0L,
    val label: String = "Binance"
) {
    val bestBid: Double? get() = bids.firstOrNull()?.price
    val bestAsk: Double? get() = asks.firstOrNull()?.price
    val mid: Double? get() {
        val b = bestBid; val a = bestAsk
        return if (b != null && a != null) (b + a) / 2.0 else null
    }
    val spread: Double? get() {
        val b = bestBid; val a = bestAsk
        return if (b != null && a != null) a - b else null
    }
    val spreadBps: Double? get() {
        val b = bestBid ?: return null; val a = bestAsk ?: return null
        return if (a > 0) (a - b) / a * 10_000.0 else null
    }
    /** İlk 10 seviye toplam notional. */
    val bidDepth: Double get() = bids.take(10).sumOf { it.notional }
    val askDepth: Double get() = asks.take(10).sumOf { it.notional }
    /** Order Book Imbalance (yüzde). HTML `obi` gauge. */
    val obi: Double get() {
        val s = bidDepth + askDepth
        return if (s > 0) (bidDepth - askDepth) / s * 100.0 else 0.0
    }
    fun isEmpty(): Boolean = bids.isEmpty() && asks.isEmpty()
}

/** Trade tape kaydı. HTML `S.trades = [{p, q, side, t}]` */
data class Trade(
    val price: Double,
    val qty: Double,
    val side: Side,
    val timestamp: Long
) {
    val notional: Double get() = price * qty
}

/** Isı haritası örnek karesi. HTML `S.heatHistory = [{t, bids, asks}]` */
data class HeatSnapshot(
    val t: Long,
    val bids: List<BookLevel>,
    val asks: List<BookLevel>
)

/** Duvar takip kaydı — StrongWallDetector. HTML `S.wallTracker` entry'si. */
data class WallRecord(
    val key: String,               // "bid:65000.5"
    val side: String,              // "bid" | "ask"
    val price: Double,
    var qty: Double,
    var notional: Double,
    val firstSeen: Long,
    var lastSeen: Long,
    var maxNotional: Double = notional,
    var sizeRatio: Double = 1.0
)

/** WallEvent — HTML S.wallEvents (algo-war için ekleme/silme timestamp'leri) */
data class WallEvent(val t: Long, val action: String, val side: String, val price: Double, val notional: Double)

/** Borsa canlı durumu — HTML `S.exchanges.<venue>` */
data class ExchangeState(
    val key: String,               // "binance" | "bybit" | "okx" | "mexc"
    val label: String,             // "Binance Futures", ...
    val tag: String,               // WS host string
    val status: ConnStatus = ConnStatus.IDLE,
    val bid: Double? = null,
    val ask: Double? = null,
    val ts: Long? = null,
    val latencyMs: Long? = null,
    val lastError: String? = null
) {
    val mid: Double? get() {
        val b = bid; val a = ask
        return if (b != null && a != null) (b + a) / 2.0 else null
    }
}

/** Borsalar arası fiyat sapması — arbitraj göstergesi. */
data class ArbitrageSkew(
    val venueA: String, val venueB: String,
    val priceA: Double, val priceB: Double
) {
    val deviationBps: Double get() = if (priceA > 0) (priceA - priceB) / priceA * 10_000.0 else 0.0
    val absBps: Double get() = kotlin.math.abs(deviationBps)
    val leadVenue: String get() = if (priceA >= priceB) venueA else venueB
    fun isOpportunity(minBps: Double = 8.0): Boolean = absBps >= minBps
}

/** Ticker verisi (24s değişim). HTML `S.ticker`. */
data class TickerInfo(val changePct: Double? = null, val volume: Double? = null)

/** Sembol precision — tickSize, stepSize. */
data class SymbolPrecision(
    val symbol: String,
    val tickSize: Double,
    val stepSize: Double,
    val priceDecimals: Int,
    val qtyDecimals: Int
) {
    companion object {
        val DEFAULT = SymbolPrecision("BTCUSDT", 0.1, 0.001, 1, 3)
    }
}

/** Likidasyon eventi. HTML `BinanceLiquidationSource`. */
data class LiquidationEvent(
    val symbol: String,
    val side: String,              // "BUY" (long liq) | "SELL" (short liq)
    val price: Double,
    val qty: Double,
    val notionalUsd: Double,
    val timestamp: Long
)

/** Likidasyon havuzu (tahmini kaldıraç seviyeleri). */
data class LiquidationPool(
    val leverage: Int,             // 10, 25, 50, 100
    val side: String,              // "long" | "short"
    val price: Double,
    val estNotionalUsd: Double
)
