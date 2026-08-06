package com.example.engine.pattern

import com.example.model.*

/* ============================================================================
 * GLOBAL STATE (HTML'deki `S` nesnesi) + LegacySignal
 * ========================================================================== */
object AppState {
    val config = AppConfig()

    var book: Book = Book()
    var lastPrice: Double? = null
    var prevPrice: Double? = null
    var tickerChangePct: Double? = null
    val trades = ArrayDeque<Trade>()
    var cvd = 0.0
    val cvdHistory = ArrayDeque<Double>()
    var largeCvd = 0.0
    var smallCvd = 0.0
    val largeCvdHistory = ArrayDeque<Double>()
    val smallCvdHistory = ArrayDeque<Double>()

    data class HeatSample(val t: Long, val bids: List<BookLevel>, val asks: List<BookLevel>, val maxQty: Double)
    val heatHistory = ArrayDeque<HeatSample>()

    val wallTracker = LinkedHashMap<String, WallRecord>()
    val wallEvents = ArrayDeque<Long>()
    val signals = ArrayDeque<LegacySignal>()
    val activePatterns = mutableListOf<PatternSignal>()
    var tradePlan: TradePlan? = null
    val patternHistory = mutableListOf<PatternSignal>()
    val sigVerify = VerifyStore()
    var manipIndex = 0
    var conflictActive = false

    val exchanges = linkedMapOf(
        "binance" to ExchangeState("binance", "Binance Futures", ConnStatus.CONNECTING),
        "bybit" to ExchangeState("bybit", "Bybit Linear", ConnStatus.IDLE),
        "okx" to ExchangeState("okx", "OKX Swap", ConnStatus.IDLE),
        "mexc" to ExchangeState("mexc", "MEXC Contract", ConnStatus.IDLE)
    )

    val liquidations = ArrayDeque<LiquidationEvent>()
    var medianQty = 0.0
    var medianQtyAt = 0L
    var lastVPIN: Double? = null

    /** Kitap verisi bayat mı (stale overlay için). */
    fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean =
        book.ts > 0 && nowMs - book.ts > 4000

    fun resetForSymbolChange() {
        book = Book()
        lastPrice = null; prevPrice = null
        trades.clear(); cvd = 0.0
        cvdHistory.clear(); largeCvd = 0.0; smallCvd = 0.0
        largeCvdHistory.clear(); smallCvdHistory.clear()
        heatHistory.clear(); wallTracker.clear(); wallEvents.clear()
        signals.clear(); activePatterns.clear(); tradePlan = null
        patternHistory.clear(); liquidations.clear()
    }
}

class VerifyStore {
    val pending = ArrayDeque<VerifyRecord>()
    val results = ArrayDeque<VerifyResult>()
}

data class LegacySignal(
    val id: String, val type: String, val bias: String, val icon: String, val title: String,
    val desc: String, val price: Double, val confidence: Int, val severity: String,
    val timeframe: String, val t: Long = System.currentTimeMillis(),
    var verified: VerifiedResult? = null, val metadata: Map<String, Any?> = emptyMap()
)
