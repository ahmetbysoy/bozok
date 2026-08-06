package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.db.BozokDatabase
import com.example.db.SessionEntity
import com.example.engine.detect.*
import com.example.engine.flow.*
import com.example.engine.pattern.*
import com.example.engine.strategy.*
import com.example.model.*
import com.example.util.BozokNotifications
import com.example.util.BozokSpeech
import com.example.widget.BozokWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/* ============================================================================
 * BOZOK VIEWMODEL — veri akışının tek orkestratörü
 *
 *  Akış:
 *  [Binance WS + Bybit/OKX/MEXC + Liquidation]
 *      │ onBook/onTrade/onTicker/onLiquidation
 *      ▼
 *  AppState (book/trades/cvd/walls/heatHistory)
 *      │ 250ms quant loop
 *      ▼
 *  PatternEngineV2 ─► MetaStrategyEngine ─► TradePlan
 *      │ 500ms FlowCandleBuilder (FLOW mumları)
 *      │ 15sn SignalVerifier (doğruluk + manip radarı)
 *      │ Sinyal → TTS anons + kritikse bildirim/Morse
 * ========================================================================== */
class BozokViewModel(application: Application) : AndroidViewModel(application) {

    // ---- Veri ----
    private val binance = BinanceClient(viewModelScope)
    private val bybit = BybitClient(viewModelScope)
    private val okx = OkxClient(viewModelScope)
    private val mexc = MexcClient(viewModelScope)
    private val liquidationSource = LiquidationSource(viewModelScope)
    private val exchangeInfo = ExchangeInfoRepository()
    private val botClient = ExecutionBotClient()

    // ---- Motorlar ----
    private val patternEngine = PatternEngineV2()
    private val tradePlanGen = TradePlanGenerator()
    private val perfTracker = StrategyPerformanceTracker()
    private val metaEngine = MetaStrategyEngine(perfTracker)
    private val microOptimizer = MicroAccountOptimizer()
    private val verifier = SignalVerifier(perfTracker)
    private val poolSim = LiquidationPoolSimulator()
    private val liqPressureCalc = LiquidationPressureCalculator()
    private val flowBuilder = FlowCandleBuilder()
    private val vpinCalc = VpinCalculator()

    // ---- Servisler ----
    private var speech: BozokSpeech? = null
    private var lastVoiceAt = 0L
    private var lastNotifAt = 0L

    // ---- DB ----
    private val db = BozokDatabase.getInstance(application)
    private val sessionDao = db.sessionDao()

    // ---- UI State ----
    private val _symbol = MutableStateFlow("BTCUSDT")
    val symbol: StateFlow<String> = _symbol.asStateFlow()

    private val _activeTab = MutableStateFlow("BOOK")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    private val _book = MutableStateFlow(Book())
    val book: StateFlow<Book> = _book.asStateFlow()

    private val _trades = MutableStateFlow<List<Trade>>(emptyList())
    val trades: StateFlow<List<Trade>> = _trades.asStateFlow()

    private val _signals = MutableStateFlow<List<PatternSignal>>(emptyList())
    val signals: StateFlow<List<PatternSignal>> = _signals.asStateFlow()

    private val _feedSignals = MutableStateFlow<List<LegacySignal>>(emptyList())
    val feedSignals: StateFlow<List<LegacySignal>> = _feedSignals.asStateFlow()

    private val _tradePlan = MutableStateFlow<TradePlan?>(null)
    val tradePlan: StateFlow<TradePlan?> = _tradePlan.asStateFlow()

    private val _microResult = MutableStateFlow<MicroAccountOptimizer.MicroResult?>(null)
    val microResult: StateFlow<MicroAccountOptimizer.MicroResult?> = _microResult.asStateFlow()

    private val _flowCandles = MutableStateFlow<List<FlowCandle>>(emptyList())
    val flowCandles: StateFlow<List<FlowCandle>> = _flowCandles.asStateFlow()

    private val _narrative = MutableStateFlow(NarrativeEngine.Narrative("🌐", "NÖTR / BEKLE", "neu", "Veri bekleniyor..."))
    val narrative: StateFlow<NarrativeEngine.Narrative> = _narrative.asStateFlow()

    private val _vpin = MutableStateFlow<Double?>(null)
    val vpin: StateFlow<Double?> = _vpin.asStateFlow()

    private val _cvd = MutableStateFlow(0.0)
    val cvd: StateFlow<Double> = _cvd.asStateFlow()

    private val _liqPools = MutableStateFlow<List<LiquidationPool>>(emptyList())
    val liqPools: StateFlow<List<LiquidationPool>> = _liqPools.asStateFlow()

    private val _liqPressure = MutableStateFlow(0.0)
    val liqPressure: StateFlow<Double> = _liqPressure.asStateFlow()

    private val _liquidations = MutableStateFlow<List<LiquidationEvent>>(emptyList())
    val liquidations: StateFlow<List<LiquidationEvent>> = _liquidations.asStateFlow()

    private val _exchanges = MutableStateFlow<Map<String, ExchangeState>>(AppState.exchanges)
    val exchanges: StateFlow<Map<String, ExchangeState>> = _exchanges.asStateFlow()

    private val _arbitrage = MutableStateFlow<List<ArbitrageSkew>>(emptyList())
    val arbitrage: StateFlow<List<ArbitrageSkew>> = _arbitrage.asStateFlow()

    private val _accuracy = MutableStateFlow<RollingAccuracy?>(null)
    val accuracy: StateFlow<RollingAccuracy?> = _accuracy.asStateFlow()

    private val _manipIndex = MutableStateFlow(0)
    val manipIndex: StateFlow<Int> = _manipIndex.asStateFlow()

    private val _precision = MutableStateFlow(SymbolPrecision("BTCUSDT", 0.1, 0.001, 1, 3))
    val precision: StateFlow<SymbolPrecision> = _precision.asStateFlow()

    private val _config = MutableStateFlow(AppState.config)
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _replaySessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val replaySessions: StateFlow<List<SessionEntity>> = _replaySessions.asStateFlow()

    private val _replayPlaying = MutableStateFlow(false)
    val replayPlaying: StateFlow<Boolean> = _replayPlaying.asStateFlow()

    private val _replayPosition = MutableStateFlow(0f) // 0..1
    val replayPosition: StateFlow<Float> = _replayPosition.asStateFlow()

    // Drag-to-trade override'ları
    private val _dragStop = MutableStateFlow<Double?>(null)
    val dragStop: StateFlow<Double?> = _dragStop.asStateFlow()
    private val _dragTp1 = MutableStateFlow<Double?>(null)
    val dragTp1: StateFlow<Double?> = _dragTp1.asStateFlow()
    private val _dragTp2 = MutableStateFlow<Double?>(null)
    val dragTp2: StateFlow<Double?> = _dragTp2.asStateFlow()

    // Focus (ladder/sinyal → heatmap)
    private val _focusPrice = MutableStateFlow<Double?>(null)
    val focusPrice: StateFlow<Double?> = _focusPrice.asStateFlow()

    private var quantJob: Job? = null
    private var flowJob: Job? = null
    private var verifyJob: Job? = null
    private var recordBuf = mutableListOf<SessionEvent>()
    private var lastWidgetPushMs = 0L
    private var lastRecordMs = 0L

    init {
        speech = BozokSpeech(application)
        connectAll(_symbol.value)
        startQuantLoop()
        startFlowLoop()
        startVerifyLoop()
        viewModelScope.launch { refreshReplaySessions() }
    }

    // ======================= BAĞLANTI =======================

    private fun connectAll(symbol: String) {
        binance.connect(symbol)
        liquidationSource.connect(symbol)
        if (AppState.config.multiExchange) {
            bybit.connect(symbol); okx.connect(symbol); mexc.connect(symbol)
        }

        binance.onBook = { b ->
            AppState.book = b
            _book.value = b
            AppState.lastPrice = b.mid
        }
        binance.onTicker = { price, chg ->
            AppState.lastPrice = price
            AppState.tickerChangePct = chg
        }
        binance.onTrade = { handleTrade(it) }

        liquidationSource.onLiquidation = { ev ->
            AppState.liquidations.addLast(ev)
            while (AppState.liquidations.size > 500) AppState.liquidations.removeFirst()
            _liquidations.value = AppState.liquidations.toList()
        }

        listOf(bybit, okx, mexc).forEach { ex ->
            ex.onBest = { _, bid, ask, _, _ ->
                val st = AppState.exchanges[ex.key]
                if (st != null) {
                    AppState.exchanges[ex.key] = st.copy(bestBid = bid, bestAsk = ask, ts = System.currentTimeMillis())
                    _exchanges.value = AppState.exchanges.toMap()
                }
            }
        }

        viewModelScope.launch {
            // Borsa durumları (ExchangeState)
            listOf(binance.state, bybit.state, okx.state, mexc.state).forEach { flow ->
                flow.collect { st ->
                    AppState.exchanges[st.key] = st
                    _exchanges.value = AppState.exchanges.toMap()
                }
            }
            // Likidasyon kaynağı durumu (ConnStatus)
            liquidationSource.status.collect { status ->
                val st = AppState.exchanges["binance"] ?: return@collect
                AppState.exchanges["binance"] = st.copy(status = status)
                _exchanges.value = AppState.exchanges.toMap()
            }
        }
    }

    fun selectSymbol(newSymbol: String) {
        val upper = newSymbol.uppercase().trim()
        if (upper.isEmpty()) return
        _symbol.value = upper
        _focusPrice.value = null
        AppState.resetForSymbolChange()
        patternEngine.reset()
        flowBuilder.rebuild()
        connectAll(upper)
        viewModelScope.launch { _precision.value = exchangeInfo.getSymbolPrecision(upper) }
    }

    fun selectTab(tab: String) { _activeTab.value = tab }

    fun setFocusPrice(price: Double?) { _focusPrice.value = price }

    // ======================= TRADE AKIŞI =======================

    private fun handleTrade(tr: Trade) {
        val st = AppState
        st.trades.addLast(tr)
        while (st.trades.size > 5000) st.trades.removeFirst()
        val cut = System.currentTimeMillis() - 120_000
        while (st.trades.isNotEmpty() && st.trades.first().timestamp < cut) st.trades.removeFirst()

        st.cvd += if (tr.side == Side.BUY) tr.qty else -tr.qty
        st.cvdHistory.addLast(st.cvd)
        while (st.cvdHistory.size > 120) st.cvdHistory.removeFirst()

        if (System.currentTimeMillis() - st.medianQtyAt > 500 || st.medianQty == 0.0) {
            st.medianQty = st.trades.takeLast(100).map { it.qty }.medianOrNull() ?: 0.0
            st.medianQtyAt = System.currentTimeMillis()
        }
        val isWhale = st.medianQty > 0 && tr.qty >= st.medianQty * 3
        if (isWhale) st.largeCvd += if (tr.side == Side.BUY) tr.qty else -tr.qty
        else st.smallCvd += if (tr.side == Side.BUY) tr.qty else -tr.qty
        st.largeCvdHistory.addLast(st.largeCvd)
        st.smallCvdHistory.addLast(st.smallCvd)
        while (st.largeCvdHistory.size > 120) st.largeCvdHistory.removeFirst()
        while (st.smallCvdHistory.size > 120) st.smallCvdHistory.removeFirst()

        _cvd.value = st.cvd
        _trades.value = st.trades.toList()

        if (_isRecording.value) recordBuf.add(SessionEvent(tr.timestamp, "trade", tr.price))
    }

    // ======================= DÖNGÜLER =======================

    private fun startQuantLoop() {
        quantJob?.cancel()
        quantJob = viewModelScope.launch(Dispatchers.Default) {
            var lastSampleT = 0L; var lastPatternT = 0L; var lastSecondaryT = 0L
            var lastPlanT = 0L; var lastArbT = 0L
            while (true) {
                delay(120)
                if (_replayPlaying.value) continue
                val t = System.currentTimeMillis()
                val bookNow = _book.value
                val mid = bookNow.mid ?: continue

                if (t - lastSampleT >= 300) {
                    lastSampleT = t
                    val maxQty = maxOf(1.0, (bookNow.bids + bookNow.asks).maxOfOrNull { it.qty } ?: 1.0)
                    AppState.heatHistory.addLast(AppState.HeatSample(t, bookNow.bids, bookNow.asks, maxQty))
                    while (AppState.heatHistory.size > (AppState.config.heatmapWindowSec * 1000 / 300)) AppState.heatHistory.removeFirst()
                    updateWallTracker(bookNow)
                }

                if (t - lastPatternT >= 250) {
                    lastPatternT = t
                    val signals = patternEngine.analyze(bookNow, AppState.trades.toList(), AppState.liquidations.toList())
                    AppState.activePatterns.clear()
                    AppState.activePatterns.addAll(signals)
                    _signals.value = signals

                    val pools = poolSim.getPools(mid, AppState.cvd, _symbol.value)
                    _liqPools.value = pools
                    _liqPressure.value = liqPressureCalc.calculate(pools, mid)
                }

                if (t - lastSecondaryT >= 1000) {
                    lastSecondaryT = t
                    val sec = patternEngine.analyzeSecondary(_flowCandles.value, AppState.liquidations.toList())
                    for (sig in sec) emitSignal(sig)
                    _narrative.value = NarrativeEngine.synthesize(AppState.activePatterns + sec)
                }

                if (t - lastPlanT >= 1000) {
                    lastPlanT = t
                    val plan = metaEngine.evaluate(
                        patterns = AppState.activePatterns, book = bookNow, trades = AppState.trades.toList(),
                        liquidations = AppState.liquidations.toList(), symbol = _symbol.value, pools = _liqPools.value
                    ) ?: tradePlanGen.generatePlan(AppState.activePatterns, bookNow)

                    val effective = applyDragOverrides(plan)
                    _tradePlan.value = effective
                    if (effective.direction != Direction.NEUTRAL) {
                        recalcMicro(effective)
                        emitSignal(
                            PatternSignal(
                                id = "META_${effective.direction}_$t",
                                type = effective.webhookPayload["strategyId"] as? String ?: "PLAN",
                                title = "🔥 " + (effective.webhookPayload["strategyName"] ?: "TRADE PLAN"),
                                bias = if (effective.direction == Direction.LONG) Bias.BULL else Bias.BEAR,
                                price = effective.entryMid, confidence = effective.confidence,
                                severity = Severity.HIGH, timeframe = "1-5dk", explanation = effective.reasoning
                            )
                        )
                    }
                }

                if (t - lastArbT >= 5000) {
                    lastArbT = t
                    val ex = _exchanges.value
                    val list = mutableListOf<ArbitrageSkew>()
                    val bin = ex["binance"]
                    if (bin?.bestBid != null && bin.bestAsk != null) {
                        val bMid = (bin.bestBid + bin.bestAsk) / 2
                        listOf("bybit" to ex["bybit"], "okx" to ex["okx"], "mexc" to ex["mexc"]).forEach { (name, other) ->
                            if (other?.bestBid != null && other.bestAsk != null) {
                                list += ArbitrageSkew("Binance", name.replaceFirstChar { it.uppercase() }, bMid, (other.bestBid + other.bestAsk) / 2)
                            }
                        }
                    }
                    _arbitrage.value = list
                }

                val feedCut = t - 600_000
                AppState.signals.removeAll { it.t < feedCut }
                _feedSignals.value = AppState.signals.toList()

                // Widget push (~10sn)
                if (t - lastWidgetPushMs >= 10_000) {
                    lastWidgetPushMs = t
                    pushWidget()
                }
            }
        }
    }

    private fun applyDragOverrides(plan: TradePlan): TradePlan {
        if (_dragStop.value == null && _dragTp1.value == null && _dragTp2.value == null) return plan
        return plan.copy(
            stopLoss = _dragStop.value?.let { PriceZone(it, it, "Sürüklenmiş SL") } ?: plan.stopLoss,
            tp1 = _dragTp1.value?.let { PriceZone(it, it, "Sürüklenmiş TP1") } ?: plan.tp1,
            tp2 = _dragTp2.value?.let { PriceZone(it, it, "Sürüklenmiş TP2") } ?: plan.tp2
        )
    }

    /** Drag-to-trade güncelleme (BookTab canvas'ından). */
    fun updateDragPrices(stop: Double?, tp1: Double?, tp2: Double?) {
        _dragStop.value = stop
        _dragTp1.value = tp1
        _dragTp2.value = tp2
        val plan = _tradePlan.value
        if (plan != null) {
            val effective = applyDragOverrides(plan)
            _tradePlan.value = effective
            recalcMicro(effective)
        }
    }

    private fun startFlowLoop() {
        flowJob?.cancel()
        flowJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(500)
                if (_replayPlaying.value) continue
                flowBuilder.update(_book.value, AppState.trades.toList(), AppState.activePatterns, AppState.liquidations.toList())
                _flowCandles.value = flowBuilder.getCandles()

                val price = AppState.lastPrice
                if (price != null) {
                    val bucket = VpinCalculator.adaptiveBucketFor(price)
                    if (vpinCalc.bucketVolumeUsd != bucket) vpinCalc.setBucketVolume(bucket)
                    for (tr in AppState.trades.takeLast(30)) vpinCalc.update(tr)
                    AppState.lastVPIN = vpinCalc.getVPIN()
                    _vpin.value = AppState.lastVPIN
                }
            }
        }
    }

    private fun startVerifyLoop() {
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(15_000)
                if (_replayPlaying.value) continue
                verifier.verifyAll()
                AppState.conflictActive = verifier.detectConflict()
                _accuracy.value = verifier.getRollingAccuracy()
                _manipIndex.value = verifier.updateManipulationRadar()
            }
        }
    }

    private fun updateWallTracker(book: Book) {
        val walls = patternEngine.identifyWalls(book)
        val t = System.currentTimeMillis()
        val currentKeys = walls.map { "${it.side}:${it.price}" }.toSet()

        for (key in AppState.wallTracker.keys.toList()) {
            if (!currentKeys.contains(key)) {
                AppState.wallTracker.remove(key)
                AppState.wallEvents.addLast(t)
            }
        }
        for (w in walls) {
            val key = "${w.side}:${w.price}"
            val existing = AppState.wallTracker[key]
            if (existing != null) {
                existing.notional = w.notional
                existing.lastSeen = t
                existing.maxNotional = maxOf(existing.maxNotional, w.notional)
            } else {
                AppState.wallTracker[key] = w
                AppState.wallEvents.addLast(t)
            }
        }
        while (AppState.wallEvents.size > 100) AppState.wallEvents.removeFirst()
    }

    // ======================= SİNYAL EMİSYONU =======================

    private fun emitSignal(sig: PatternSignal) {
        val now = System.currentTimeMillis()
        if (AppState.signals.any { it.type == sig.type && abs(now - it.t) < 3000 }) return

        val legacy = LegacySignal(
            id = sig.id, type = sig.type,
            bias = when (sig.bias) { Bias.BULL -> "bull"; Bias.BEAR -> "bear"; Bias.WARN -> "warn"; Bias.NEUTRAL -> "warn" },
            icon = when (sig.bias) { Bias.BULL -> "🟢"; Bias.BEAR -> "🔴"; Bias.WARN -> "⚠️"; Bias.NEUTRAL -> "🌐" },
            title = sig.title, desc = sig.explanation, price = sig.price, confidence = sig.confidence,
            severity = sig.severity.name.lowercase(), timeframe = sig.timeframe,
            metadata = sig.metadata.mapValues { it.value as Any? }
        )
        AppState.signals.addFirst(legacy)
        while (AppState.signals.size > 200) AppState.signals.removeLast()
        _feedSignals.value = AppState.signals.toList()
        verifier.record(sig)
        if (_isRecording.value) recordBuf.add(SessionEvent(now, "signal", sig.price, sig))

        // TTS anons + kritikse bildirim/Morse
        val cfg = AppState.config
        if (cfg.soundOn && cfg.voiceAnnounce && sig.confidence >= cfg.minSignalConfidence && now - lastVoiceAt > 8000) {
            lastVoiceAt = now
            val dir = when (sig.bias) { Bias.BULL -> "alım"; Bias.BEAR -> "satış"; else -> "uyarı" }
            speech?.announce("Bozok: ${sig.title}, $dir sinyali, güven ${sig.confidence}")
        }
        if (cfg.notifications && (sig.severity == Severity.HIGH || sig.severity == Severity.CRITICAL) && now - lastNotifAt > 15_000) {
            lastNotifAt = now
            BozokNotifications.notifyCritical(getApplication(), sig.title, sig.explanation)
        }
    }

    // ======================= REPLAY =======================

    fun toggleRecording() {
        if (_isRecording.value) {
            if (recordBuf.isNotEmpty()) {
                val t0 = recordBuf.first().t
                val t1 = recordBuf.last().t
                val ses = SessionEntity(
                    id = "bozok_${System.currentTimeMillis()}", t0 = t0, t1 = t1,
                    symbol = _symbol.value, eventsJson = eventsToJson(recordBuf)
                )
                viewModelScope.launch { sessionDao.upsert(ses); sessionDao.trimTo(100); refreshReplaySessions() }
            }
            _isRecording.value = false
            recordBuf.clear()
            Toast.makeText(getApplication(), "⏺️ Oturum kaydedildi", Toast.LENGTH_SHORT).show()
        } else {
            _isRecording.value = true
            recordBuf.clear()
            Toast.makeText(getApplication(), "🔴 Kayıt başladı", Toast.LENGTH_SHORT).show()
        }
    }

    fun playReplay(session: SessionEntity) {
        if (_replayPlaying.value) { _replayPlaying.value = false; _replayPosition.value = 0f; return }
        val events = eventsFromJson(session.eventsJson)
        if (events.isEmpty()) { Toast.makeText(getApplication(), "Oturumda veri yok", Toast.LENGTH_SHORT).show(); return }

        _replayPlaying.value = true
        binance.disconnect()
        viewModelScope.launch(Dispatchers.Default) {
            val span = maxOf(1L, session.t1 - session.t0)
            var idx = 0
            val startWall = System.currentTimeMillis()
            val duration = (span / 20).coerceIn(5_000L, 120_000L) // 20x hız, 5-120sn
            while (_replayPlaying.value && idx < events.size) {
                val progress = ((System.currentTimeMillis() - startWall).toDouble() / duration).coerceIn(0.0, 1.0)
                val targetT = session.t0 + (progress * span).toLong()
                while (idx < events.size && events[idx].t <= targetT) {
                    val ev = events[idx]
                    when (ev.type) {
                        "price" -> {
                            // Book modelinde midPrice yok; bid/ask'ı fiyat civarına kur
                            val tick = Fmt.tickSizeFor(ev.value)
                            AppState.book = Book(
                                bids = listOf(BookLevel(ev.value - tick, 1.0)),
                                asks = listOf(BookLevel(ev.value + tick, 1.0)),
                                ts = System.currentTimeMillis(), label = "REPLAY"
                            )
                            AppState.lastPrice = ev.value
                            _book.value = AppState.book
                        }
                        "signal" -> ev.sig?.let { emitSignal(it) }
                        else -> {}
                    }
                    idx++
                }
                _replayPosition.value = progress.toFloat()
                delay(50)
            }
            _replayPlaying.value = false
            _replayPosition.value = 0f
            binance.connect(_symbol.value)
            Toast.makeText(getApplication(), "⏹ Replay bitti — canlı akış geri döndü", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch { sessionDao.delete(id); refreshReplaySessions() }
    }

    private fun eventsToJson(events: List<SessionEvent>): String {
        val arr = JSONArray()
        for (ev in events) {
            val o = JSONObject().put("t", ev.t).put("type", ev.type).put("v", ev.value)
            if (ev.sig != null) {
                o.put("sig", JSONObject().put("type", ev.sig.type).put("bias", ev.sig.bias.name)
                    .put("title", ev.sig.title).put("price", ev.sig.price).put("confidence", ev.sig.confidence))
            }
            arr.put(o)
        }
        return arr.toString()
    }

    private fun eventsFromJson(json: String): List<SessionEvent> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val sig = o.optJSONObject("sig")?.let { s ->
                PatternSignal(
                    id = "REPLAY_$i", type = s.optString("type"), title = s.optString("title"),
                    bias = try { Bias.valueOf(s.optString("bias", "WARN")) } catch (e: Exception) { Bias.WARN },
                    price = s.optDouble("price"), confidence = s.optInt("confidence", 60)
                )
            }
            SessionEvent(o.optLong("t"), o.optString("type"), o.optDouble("v"), sig)
        }
    } catch (_: Exception) { emptyList() }

    private suspend fun refreshReplaySessions() { _replaySessions.value = sessionDao.latestSessions() }

    // ======================= İNFAZ / WEBHOOK =======================

    fun executeOrderViaWebhook(context: Context) {
        val plan = _tradePlan.value ?: return
        if (plan.direction == Direction.NEUTRAL) {
            Toast.makeText(context, "Nötr plan — emir gönderilmedi", Toast.LENGTH_SHORT).show()
            return
        }
        val payload = WebhookPayload(
            strategyId = plan.webhookPayload["strategyId"] as? String ?: "PLAN",
            strategyName = plan.webhookPayload["strategyName"] as? String ?: "BOZOK PLAN",
            direction = plan.direction.name, symbol = _symbol.value, confidence = plan.confidence,
            entry = plan.entryMid, stopLoss = plan.stopLoss?.low ?: plan.entryMid,
            takeProfit1 = plan.tp1?.high ?: plan.entryMid, takeProfit2 = plan.tp2?.high,
            leverage = AppState.config.microMaxLeverage,
            kellyRiskPct = microOptimizer.kellyRiskPct(plan.confidence),
            positionSizeUsd = _microResult.value?.positionNotionalUsd ?: AppState.config.microBalance
        )
        val json = payload.toJson()

        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("Bozok Webhook", json))

        viewModelScope.launch {
            val result = botClient.executeOrder(json)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    BozokNotifications.notifyCritical(context, "🔥 İNFAZ BAŞARILI", "${payload.strategyName} (${payload.direction})")
                    Toast.makeText(context, "🔥 İNFAZ BAŞARILI: ${payload.strategyName}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "⚠️ Bot Hatası: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exportSignalsCsv(context: Context) {
        val sb = StringBuilder("type,bias,title,confidence,price,timeframe,time\n")
        _feedSignals.value.forEach { s ->
            sb.append("${s.type},${s.bias},\"${s.title}\",${s.confidence},${s.price},${s.timeframe},${s.t}\n")
        }
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("Bozok Signals CSV", sb.toString()))
        Toast.makeText(context, "Sinyaller CSV olarak panoya kopyalandı", Toast.LENGTH_SHORT).show()
    }

    fun exportSettingsJson(context: Context) {
        val cfg = AppState.config
        val json = JSONObject()
            .put("symbol", _symbol.value)
            .put("sensitivity", cfg.sensitivity.name)
            .put("theme", cfg.theme)
            .put("colorblind", cfg.colorblind)
            .put("soundOn", cfg.soundOn)
            .put("voiceAnnounce", cfg.voiceAnnounce)
            .put("notifications", cfg.notifications)
            .put("multiExchange", cfg.multiExchange)
            .put("flowTimeframeMs", cfg.flowTimeframeMs)
            .put("flowCandleMode", cfg.flowCandleMode)
            .put("microBalance", cfg.microBalance)
            .put("microRiskPct", cfg.microRiskPct)
            .toString(2)
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("Bozok Settings JSON", json))
        Toast.makeText(context, "📥 Ayarlar JSON olarak panoya kopyalandı", Toast.LENGTH_SHORT).show()
    }

    fun importSettingsJson(jsonText: String) {
        try {
            val o = JSONObject(jsonText)
            val cfg = AppState.config
            if (o.has("sensitivity")) cfg.sensitivity = try { SensitivityPreset.valueOf(o.getString("sensitivity")) } catch (e: Exception) { cfg.sensitivity }
            if (o.has("theme")) cfg.theme = o.getString("theme")
            if (o.has("colorblind")) cfg.colorblind = o.getBoolean("colorblind")
            if (o.has("soundOn")) cfg.soundOn = o.getBoolean("soundOn")
            if (o.has("voiceAnnounce")) cfg.voiceAnnounce = o.getBoolean("voiceAnnounce")
            if (o.has("notifications")) cfg.notifications = o.getBoolean("notifications")
            if (o.has("multiExchange")) setMultiExchange(o.getBoolean("multiExchange"))
            if (o.has("flowTimeframeMs")) setFlowPeriod(o.getLong("flowTimeframeMs"))
            if (o.has("flowCandleMode")) setFlowMode(o.getString("flowCandleMode"))
            if (o.has("microBalance")) cfg.microBalance = o.getDouble("microBalance")
            if (o.has("microRiskPct")) cfg.microRiskPct = o.getDouble("microRiskPct")
            _config.value = cfg
            patternEngine.reset()
            Toast.makeText(getApplication(), "📤 Ayarlar yüklendi", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(getApplication(), "⚠️ JSON geçersiz: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ======================= AYARLAR =======================

    fun perfTracker(): StrategyPerformanceTracker = perfTracker

    fun updateConfig(mutate: (AppConfig) -> Unit) {
        val cfg = AppState.config
        mutate(cfg)
        _config.value = cfg
    }

    fun setSensitivity(preset: SensitivityPreset) {
        updateConfig { it.sensitivity = preset }
        patternEngine.reset()
    }

    fun setFlowPeriod(ms: Long) {
        updateConfig { it.flowTimeframeMs = ms.coerceAtLeast(1000) }
        flowBuilder.rebuild(timeframeMs = ms.coerceAtLeast(1000))
        _flowCandles.value = emptyList()
    }

    fun setFlowMode(mode: String, target: Double = 1_000_000.0) {
        updateConfig {
            it.flowCandleMode = mode
            if (mode == "volume") it.flowVolumeTarget = target
        }
        flowBuilder.rebuild(mode = mode, target = target)
    }

    fun setColorblind(on: Boolean) { updateConfig { it.colorblind = on } }
    fun setTheme(theme: String) { updateConfig { it.theme = theme } }
    fun setSound(on: Boolean) { updateConfig { it.soundOn = on }; if (!on) speech?.stop() }
    fun setVoice(on: Boolean) { updateConfig { it.voiceAnnounce = on }; if (!on) speech?.stop() }
    fun setNotifications(on: Boolean) { updateConfig { it.notifications = on } }

    fun setMultiExchange(on: Boolean) {
        updateConfig { it.multiExchange = on }
        if (on) {
            bybit.connect(_symbol.value); okx.connect(_symbol.value); mexc.connect(_symbol.value)
        } else {
            bybit.disconnect(); okx.disconnect(); mexc.disconnect()
        }
    }

    private fun pushWidget() {
        val ob = AppState.book
        val plan = _tradePlan.value
        val dir = plan?.direction?.name ?: "LONG"
        val strategy = plan?.webhookPayload?.get("strategyName") as? String ?: "—"
        BozokWidgetReceiver.pushSnapshot(
            context = getApplication(),
            symbol = _symbol.value,
            price = ob.mid?.let { "$${"%,.2f".format(it)}" } ?: "—",
            vpinText = "VPIN ${_vpin.value?.let { "%d".format(it.toInt()) } ?: "—"}",
            strategyText = "${if (dir == "LONG") "AL" else "SAT"} · $strategy",
            returnText = "R:R ${plan?.riskReward1?.let { "%.1f".format(it) } ?: "—"}"
        )
    }

    private fun recalcMicro(plan: TradePlan) {
        val sl = plan.stopLoss ?: return
        _microResult.value = microOptimizer.calculate(plan.entryMid, sl.low, plan.direction, plan.confidence)
    }

    override fun onCleared() {
        super.onCleared()
        binance.disconnect(); bybit.disconnect(); okx.disconnect(); mexc.disconnect()
        liquidationSource.disconnect()
        speech?.shutdown()
    }
}
