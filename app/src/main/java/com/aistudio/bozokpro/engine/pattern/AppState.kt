package com.aistudio.bozokpro.engine.pattern

import com.aistudio.bozokpro.model.*
import java.util.ArrayDeque

/* ============================================================================
 * BOZOK PRO — Global State (HTML `const S = {...}` karşılığı)
 *
 * HTML tarafında S global paylaşılan mutable state; motorlar buradan okuyup
 * buraya yazıyor. Android'de tek instance thread-safe erişim için @Synchronized
 * kullanıyoruz. ViewModel dışa StateFlow olarak sunar.
 * ========================================================================== */
object AppState {

    // ------- Book / trades / price -------
    @Volatile var book: Book = Book()
    @Volatile var lastPrice: Double? = null
    @Volatile var prevPrice: Double? = null
    @Volatile var ticker: TickerInfo = TickerInfo()

    /** Son ~2 dakikalık trade tape'i */
    val trades: ArrayDeque<Trade> = ArrayDeque()

    // ------- CVD -------
    @Volatile var cvd: Double = 0.0
    val cvdHistory: ArrayDeque<Double> = ArrayDeque()
    @Volatile var largeCvd: Double = 0.0
    @Volatile var smallCvd: Double = 0.0
    val largeCvdHistory: ArrayDeque<Double> = ArrayDeque()
    val smallCvdHistory: ArrayDeque<Double> = ArrayDeque()

    // ------- Heatmap örnekleri -------
    val heatHistory: ArrayDeque<HeatSnapshot> = ArrayDeque()

    // ------- Duvarlar -------
    val wallTracker: MutableMap<String, WallRecord> = HashMap()
    val wallEvents: ArrayDeque<WallEvent> = ArrayDeque()

    // ------- Pattern / signal log -------
    val signals: ArrayDeque<PatternSignal> = ArrayDeque()          // TÜM üretilen sinyaller
    val activePatterns: ArrayDeque<PatternSignal> = ArrayDeque()   // Aktif (henüz expire olmamış)
    val patternHistory: ArrayDeque<PatternSignal> = ArrayDeque()   // Görsel hitbox / heatmap dot

    @Volatile var tradePlan: TradePlan? = null

    var signalCountBull: Int = 0
    var signalCountBear: Int = 0
    var signalCountWarn: Int = 0

    // ------- Doğrulama / manip radarı -------
    val verifyPending: ArrayDeque<VerifyRecord> = ArrayDeque()
    val verifyResults: ArrayDeque<VerifiedResult> = ArrayDeque()
    @Volatile var manipIndex: Int = 0
    @Volatile var conflictActive: Boolean = false

    // ------- Bağlantı -------
    @Volatile var connState: ConnStatus = ConnStatus.CONNECTING
    val exchanges: MutableMap<String, ExchangeState> = mutableMapOf(
        "binance" to ExchangeState("binance", "Binance Futures", "fstream.binance.com/public/stream", ConnStatus.CONNECTING),
        "bybit"   to ExchangeState("bybit",   "Bybit Linear",    "stream.bybit.com/v5/public/linear"),
        "okx"     to ExchangeState("okx",     "OKX Swap",        "ws.okx.com/v5/public"),
        "mexc"    to ExchangeState("mexc",    "MEXC Contract",   "contract.mexc.com/edge"),
    )

    // ------- Odak fiyat (BOOK'ta 15sn highlight) -------
    @Volatile var focusPrice: Double? = null
    @Volatile var focusUntil: Long = 0L

    // ------- Sinyal sırası (id counter) -------
    @Volatile var sigSeq: Long = 0

    /** HTML resetForSymbolChange karşılığı — sembol değişiminde tüm buffer'ları temizle. */
    @Synchronized
    fun resetForSymbolChange() {
        book = Book()
        lastPrice = null; prevPrice = null
        ticker = TickerInfo()
        trades.clear()
        cvd = 0.0; cvdHistory.clear()
        largeCvd = 0.0; smallCvd = 0.0
        largeCvdHistory.clear(); smallCvdHistory.clear()
        heatHistory.clear()
        wallTracker.clear(); wallEvents.clear()
        signals.clear(); activePatterns.clear(); patternHistory.clear()
        tradePlan = null
        signalCountBull = 0; signalCountBear = 0; signalCountWarn = 0
        verifyPending.clear(); verifyResults.clear()
        manipIndex = 0; conflictActive = false
        focusPrice = null; focusUntil = 0L
    }

    // ------- Yardımcılar -------

    /** HTML `recentTradesForV2(windowMs=60000)` — sondan başlayıp pencereye kadar geri gel. */
    @Synchronized
    fun recentTrades(windowMs: Long = 60_000L): List<Trade> {
        val cut = System.currentTimeMillis() - windowMs
        val out = ArrayList<Trade>()
        val it = trades.descendingIterator()
        while (it.hasNext()) {
            val t = it.next()
            if (t.timestamp < cut) break
            out.add(t)
        }
        out.reverse()
        return out
    }

    /** Trade ekle + eski kayıtları düşür + tape maks 5000. */
    @Synchronized
    fun pushTrade(t: Trade) {
        trades.addLast(t)
        while (trades.size > 5000) trades.removeFirst()
        val cut = System.currentTimeMillis() - 120_000
        while (trades.isNotEmpty() && trades.first().timestamp < cut) trades.removeFirst()
    }

    @Synchronized
    fun pushHeatSnapshot(s: HeatSnapshot, maxWindowMs: Long) {
        heatHistory.addLast(s)
        val cut = System.currentTimeMillis() - maxWindowMs
        while (heatHistory.isNotEmpty() && heatHistory.first().t < cut) heatHistory.removeFirst()
    }
}
