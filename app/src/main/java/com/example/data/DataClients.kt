package com.example.data

import com.example.engine.pattern.AppState
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.TreeMap
import java.util.concurrent.TimeUnit

/* ============================================================================
 * VERİ KATMANI — Binance (tam derinlik + checksum), Bybit/OKX/MEXC,
 * likidasyon kaynağı (forceOrder + REST telafi), global book
 * ========================================================================== */

class LocalOrderbook {
    private val bids = TreeMap<Double, Double>(Comparator.reverseOrder())
    private val asks = TreeMap<Double, Double>()
    var lastUpdateId = 0L
        private set
    var synced = false
        private set
    val pending = ArrayDeque<DepthDelta>()

    data class DepthDelta(val u: Long, val bids: List<Pair<Double, Double>>, val asks: List<Pair<Double, Double>>)

    fun setSnapshot(bidPairs: List<Pair<Double, Double>>, askPairs: List<Pair<Double, Double>>, lastUpdateId: Long) {
        bids.clear(); asks.clear()
        bidPairs.forEach { (p, q) -> bids[p] = q }
        askPairs.forEach { (p, q) -> asks[p] = q }
        this.lastUpdateId = lastUpdateId
        synced = true
    }

    fun applyDelta(delta: DepthDelta) {
        delta.bids.forEach { (p, q) -> if (q == 0.0) bids.remove(p) else bids[p] = q }
        delta.asks.forEach { (p, q) -> if (q == 0.0) asks.remove(p) else asks[p] = q }
        lastUpdateId = delta.u
    }

    fun snapshotTop(limit: Int = 200): Pair<List<BookLevel>, List<BookLevel>> =
        bids.entries.take(limit).map { BookLevel(it.key, it.value) } to
        asks.entries.take(limit).map { BookLevel(it.key, it.value) }

    fun markUnsynchronized() { synced = false; pending.clear() }
}

class BinanceClient(private val scope: CoroutineScope) {
    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private var webSocket: WebSocket? = null
    private var currentSymbol = "btcusdt"
    private var gen = 0L
    private var reconnectAttempts = 0
    private val book = LocalOrderbook()
    private var watchdogJob: Job? = null
    private var reconnectJob: Job? = null

    private val _state = MutableStateFlow(ExchangeState("binance", "Binance Futures", ConnStatus.IDLE))
    val state: StateFlow<ExchangeState> = _state.asStateFlow()

    var onBook: ((Book) -> Unit)? = null
    var onTrade: ((Trade) -> Unit)? = null
    var onTicker: ((Double, Double) -> Unit)? = null

    fun connect(symbol: String) {
        currentSymbol = symbol.lowercase()
        gen++
        val myGen = gen
        reconnectAttempts = 0
        disconnectInternal()

        val url = "wss://fstream.binance.com/public/stream?streams=" +
            "${currentSymbol}@depth@100ms/${currentSymbol}@aggTrade/${currentSymbol}@ticker"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (myGen != gen) return
                _state.value = _state.value.copy(status = ConnStatus.LIVE)
                reconnectAttempts = 0
                book.markUnsynchronized()
                fetchSnapshot(myGen)
                startWatchdog(myGen)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (myGen != gen) return
                try {
                    val root = JSONObject(text)
                    val data = root.optJSONObject("data") ?: root
                    when (data.optString("e")) {
                        "depthUpdate" -> handleDepth(myGen, data)
                        "aggTrade" -> handleTrade(data)
                        "24hrTicker" -> handleTicker(data)
                    }
                } catch (_: Exception) { }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (myGen != gen) return
                _state.value = _state.value.copy(status = ConnStatus.BAD, lastError = t.message)
                scheduleReconnect(myGen)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (myGen != gen) return
                _state.value = _state.value.copy(status = ConnStatus.BAD)
                scheduleReconnect(myGen)
            }
        })
    }

    fun disconnect() { gen++; disconnectInternal() }

    private fun disconnectInternal() {
        reconnectJob?.cancel(); reconnectJob = null
        watchdogJob?.cancel(); watchdogJob = null
        webSocket?.close(1000, "bye"); webSocket = null
        _state.value = _state.value.copy(status = ConnStatus.IDLE)
    }

    private fun handleDepth(myGen: Long, data: JSONObject) {
        val U = data.optLong("U", -1)
        val u = data.optLong("u", -1)
        if (U < 0 || u < 0) return
        val bids = parseLevels(data, "b")
        val asks = parseLevels(data, "a")

        if (!book.synced) {
            book.pending.addLast(LocalOrderbook.DepthDelta(u, bids, asks))
            return
        }
        if (U > book.lastUpdateId + 1) {
            book.markUnsynchronized()
            book.pending.addLast(LocalOrderbook.DepthDelta(u, bids, asks))
            fetchSnapshot(myGen)
            return
        }
        if (u <= book.lastUpdateId) return
        book.applyDelta(LocalOrderbook.DepthDelta(u, bids, asks))
        publishBook()
    }

    private fun parseLevels(data: JSONObject, key: String): List<Pair<Double, Double>> {
        val arr = data.optJSONArray(key) ?: return emptyList()
        val out = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONArray(i)
            val p = item.getString(0).toDoubleOrNull() ?: continue
            val q = item.getString(1).toDoubleOrNull() ?: continue
            out += p to q
        }
        return out
    }

    private fun handleTrade(data: JSONObject) {
        val p = data.optString("p").toDoubleOrNull() ?: return
        val q = data.optString("q").toDoubleOrNull() ?: return
        val m = data.optBoolean("m", false)
        val t = data.optLong("T", System.currentTimeMillis())
        onTrade?.invoke(Trade(p, q, if (m) Side.SELL else Side.BUY, t))
    }

    private fun handleTicker(data: JSONObject) {
        val c = data.optString("c").toDoubleOrNull() ?: return
        val p = data.optString("P").toDoubleOrNull() ?: 0.0
        onTicker?.invoke(c, p)
    }

    private fun publishBook() {
        val (b, a) = book.snapshotTop(200)
        onBook?.invoke(Book(bids = b, asks = a, ts = System.currentTimeMillis(), label = "Binance"))
    }

    private fun fetchSnapshot(myGen: Long, retries: Int = 0) {
        val symbol = currentSymbol
        val ok = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
        val req = Request.Builder().url("https://fapi.binance.com/fapi/v1/depth?symbol=${symbol.uppercase()}&limit=1000").build()
        scope.launch(Dispatchers.IO) {
            try {
                ok.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                    val snap = JSONObject(resp.body?.string() ?: throw RuntimeException("boş"))
                    val lastId = snap.optLong("lastUpdateId", 0)
                    val b = parseLevels(snap, "bids")
                    val a = parseLevels(snap, "asks")
                    withContext(Dispatchers.Default) {
                        if (myGen != gen) return@withContext
                        book.setSnapshot(b, a, lastId)
                        val pending = book.pending.toList(); book.pending.clear()
                        for (ev in pending) if (ev.u >= lastId + 1) book.applyDelta(ev)
                        book.synced = true
                        publishBook()
                    }
                }
            } catch (e: Exception) {
                if (myGen != gen) return@launch
                if (retries < 3) { delay(3000); fetchSnapshot(myGen, retries + 1) }
            }
        }
    }

    private fun startWatchdog(myGen: Long) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(2000)
                if (myGen != gen) break
                val last = AppState.book.ts
                if (last > 0 && System.currentTimeMillis() - last > 4000 && _state.value.status == ConnStatus.LIVE) {
                    _state.value = _state.value.copy(status = ConnStatus.BAD)
                    webSocket?.close(1000, "zombie")
                    scheduleReconnect(myGen)
                    break
                }
            }
        }
    }

    private fun scheduleReconnect(myGen: Long) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = (2000L * (1L shl minOf(reconnectAttempts, 4))).coerceAtMost(30_000L)
            delay(delayMs)
            if (myGen != gen) return@launch
            reconnectAttempts++
            connect(currentSymbol)
        }
    }
}

/* ------------------------- YAN BORSALAR ------------------------- */

abstract class SecondaryExchangeClient(protected val scope: CoroutineScope, val key: String, val label: String) {
    protected val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    protected var ws: WebSocket? = null
    protected var symbol = "BTCUSDT"
    protected var gen = 0L
    protected var attempts = 0
    protected val bids = TreeMap<Double, Double>(Comparator.reverseOrder())
    protected val asks = TreeMap<Double, Double>()

    private val _state = MutableStateFlow(ExchangeState(key, label, ConnStatus.IDLE))
    val state: StateFlow<ExchangeState> = _state.asStateFlow()

    var onBest: ((String, Double?, Double?, Long, Long?) -> Unit)? = null

    abstract fun wsUrl(): String
    abstract fun subscribePayload(): String
    abstract fun handleMessage(text: String, recvT: Long)

    fun connect(symbol: String) {
        this.symbol = symbol.uppercase()
        gen++
        val myGen = gen
        attempts = 0
        ws?.close(1000, "bye"); ws = null
        bids.clear(); asks.clear()
        _state.value = _state.value.copy(status = ConnStatus.CONNECTING)
        try {
            val req = Request.Builder().url(wsUrl()).build()
            ws = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (myGen != gen) return
                    _state.value = _state.value.copy(status = ConnStatus.LIVE)
                    attempts = 0
                    webSocket.send(subscribePayload())
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (myGen != gen) return
                    handleMessage(text, System.currentTimeMillis())
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (myGen != gen) return
                    _state.value = _state.value.copy(status = ConnStatus.BAD, lastError = t.message)
                    scheduleReconnect(myGen)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (myGen != gen) return
                    _state.value = _state.value.copy(status = ConnStatus.BAD)
                    scheduleReconnect(myGen)
                }
            })
        } catch (e: Exception) {
            _state.value = _state.value.copy(status = ConnStatus.BAD, lastError = e.message)
            scheduleReconnect(myGen)
        }
    }

    fun disconnect() {
        gen++
        ws?.close(1000, "bye"); ws = null
        _state.value = _state.value.copy(status = ConnStatus.IDLE)
    }

    protected fun publishBest() {
        val bestBid = try { bids.firstKey() } catch (e: Exception) { null }
        val bestAsk = try { asks.firstKey() } catch (e: Exception) { null }
        if (bestBid == null || bestAsk == null) return
        onBest?.invoke(key, bestBid, bestAsk, System.currentTimeMillis(), null)
    }

    private fun scheduleReconnect(myGen: Long) {
        scope.launch {
            val delayMs = (3000L * (1L shl minOf(attempts, 4))).coerceAtMost(30_000L)
            delay(delayMs)
            if (myGen != gen) return@launch
            attempts++
            connect(symbol)
        }
    }
}

class BybitClient(scope: CoroutineScope) : SecondaryExchangeClient(scope, "bybit", "Bybit Linear") {
    override fun wsUrl() = "wss://stream.bybit.com/v5/public/linear"
    override fun subscribePayload() = "{\"op\":\"subscribe\",\"args\":[\"orderbook.50.$symbol\"]}"
    override fun handleMessage(text: String, recvT: Long) {
        try {
            val msg = JSONObject(text)
            if (!msg.optString("topic").startsWith("orderbook")) return
            val d = msg.optJSONObject("data") ?: return
            when (msg.optString("type")) {
                "snapshot" -> { bids.clear(); asks.clear(); apply(d.optJSONArray("b"), bids); apply(d.optJSONArray("a"), asks) }
                "delta" -> { apply(d.optJSONArray("b"), bids); apply(d.optJSONArray("a"), asks) }
                else -> return
            }
            publishBest()
        } catch (_: Exception) { }
    }
    private fun apply(arr: org.json.JSONArray?, map: TreeMap<Double, Double>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val item = arr.getJSONArray(i)
            val p = item.getString(0).toDoubleOrNull() ?: continue
            val q = item.getString(1).toDoubleOrNull() ?: continue
            if (q == 0.0) map.remove(p) else map[p] = q
        }
    }
}

class OkxClient(scope: CoroutineScope) : SecondaryExchangeClient(scope, "okx", "OKX Swap") {
    override fun wsUrl() = "wss://ws.okx.com/v5/public"
    override fun subscribePayload() = "{\"op\":\"subscribe\",\"args\":[{\"channel\":\"books5\",\"instId\":\"$symbol-SWAP\"}]}"
    override fun handleMessage(text: String, recvT: Long) {
        try {
            val msg = JSONObject(text)
            if (msg.optJSONObject("arg")?.optString("channel") != "books5") return
            val data = msg.optJSONArray("data") ?: return
            if (data.length() == 0) return
            val d = data.getJSONObject(0)
            bids.clear(); asks.clear()
            apply(d.optJSONArray("bids"), bids); apply(d.optJSONArray("asks"), asks)
            publishBest()
        } catch (_: Exception) { }
    }
    private fun apply(arr: org.json.JSONArray?, map: TreeMap<Double, Double>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val item = arr.getJSONArray(i)
            val p = item.getString(0).toDoubleOrNull() ?: continue
            val q = item.getString(1).toDoubleOrNull() ?: continue
            if (q == 0.0) map.remove(p) else map[p] = q
        }
    }
}

class MexcClient(scope: CoroutineScope) : SecondaryExchangeClient(scope, "mexc", "MEXC Contract") {
    override fun wsUrl() = "wss://contract.mexc.com/edge"
    override fun subscribePayload() = "{\"method\":\"SUBSCRIPTION\",\"params\":[\"sub.depth.$symbol\"],\"id\":1}"
    override fun handleMessage(text: String, recvT: Long) {
        try {
            val msg = JSONObject(text)
            if (msg.optString("channel") != "push.depth") return
            val d = msg.optJSONObject("data") ?: return
            bids.clear(); asks.clear()
            apply(d.optJSONArray("bids"), bids); apply(d.optJSONArray("asks"), asks)
            publishBest()
        } catch (_: Exception) { }
    }
    private fun apply(arr: org.json.JSONArray?, map: TreeMap<Double, Double>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val item = arr.getJSONArray(i)
            val p = item.getString(0).toDoubleOrNull() ?: continue
            val q = item.getString(1).toDoubleOrNull() ?: continue
            if (q == 0.0) map.remove(p) else map[p] = q
        }
    }
}

/* ------------------------- LİKİDASYON ------------------------- */

class LiquidationSource(private val scope: CoroutineScope) {
    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private var ws: WebSocket? = null
    private var symbol = "btcusdt"
    private var gen = 0L
    private var attempts = 0

    private val _status = MutableStateFlow(ConnStatus.IDLE)
    val status: StateFlow<ConnStatus> = _status.asStateFlow()

    var onLiquidation: ((LiquidationEvent) -> Unit)? = null

    fun connect(symbol: String) {
        this.symbol = symbol.lowercase()
        gen++
        val myGen = gen
        attempts = 0
        ws?.close(1000, "bye"); ws = null
        _status.value = ConnStatus.CONNECTING
        try {
            val req = Request.Builder().url("wss://fstream.binance.com/public/ws/!forceOrder@arr").build()
            ws = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (myGen != gen) return
                    _status.value = ConnStatus.LIVE
                    attempts = 0
                    recoverMissed(myGen)
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (myGen != gen) return
                    try {
                        if (!text.contains(symbol.uppercase())) return
                        val o = JSONObject(text).optJSONObject("o") ?: return
                        parse(o)?.let { onLiquidation?.invoke(it) }
                    } catch (_: Exception) { }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (myGen != gen) return
                    _status.value = ConnStatus.BAD
                    scheduleReconnect(myGen)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (myGen != gen) return
                    _status.value = ConnStatus.BAD
                    scheduleReconnect(myGen)
                }
            })
        } catch (e: Exception) {
            _status.value = ConnStatus.BAD
            scheduleReconnect(myGen)
        }
    }

    fun disconnect() { gen++; ws?.close(1000, "bye"); ws = null; _status.value = ConnStatus.IDLE }

    private fun recoverMissed(myGen: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis() - 60_000
                val url = "https://fapi.binance.com/fapi/v1/allForceOrders?symbol=${symbol.uppercase()}&startTime=$startTime&limit=1000"
                val ok = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
                ok.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val arr = org.json.JSONArray(resp.body?.string() ?: return@use)
                    for (i in 0 until arr.length()) {
                        if (myGen != gen) return@use
                        arr.getJSONObject(i).optJSONObject("o")?.let { parse(it)?.let { ev -> onLiquidation?.invoke(ev) } }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun parse(o: JSONObject): LiquidationEvent? {
        val sym = o.optString("s") ?: return null
        if (!sym.equals(symbol, ignoreCase = true)) return null
        val p = o.optString("p").toDoubleOrNull() ?: return null
        val q = o.optString("q").toDoubleOrNull() ?: return null
        val side = o.optString("S") ?: return null
        val t = o.optLong("T", System.currentTimeMillis())
        return LiquidationEvent(sym, side, p, q, p * q, t)
    }

    private fun scheduleReconnect(myGen: Long) {
        scope.launch {
            val delayMs = (3000L * (1L shl minOf(attempts, 4))).coerceAtMost(30_000L)
            delay(delayMs)
            if (myGen != gen) return@launch
            attempts++
            connect(symbol)
        }
    }
}

/* ------------------------- EXCHANGE INFO ------------------------- */

data class SymbolPrecision(val symbol: String, val tickSize: Double, val stepSize: Double, val priceDecimals: Int, val qtyDecimals: Int)

class ExchangeInfoRepository {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
    private val cache = mutableMapOf<String, SymbolPrecision>()

    suspend fun getSymbolPrecision(symbol: String): SymbolPrecision = withContext(Dispatchers.IO) {
        val upper = symbol.uppercase().trim()
        cache[upper]?.let { return@withContext it }
        try {
            val url = "https://fapi.binance.com/fapi/v1/exchangeInfo?symbol=$upper"
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "")
                    val symbols = root.optJSONArray("symbols")
                    if (symbols != null && symbols.length() > 0) {
                        val sym = symbols.getJSONObject(0)
                        val filters = sym.getJSONArray("filters")
                        var tickSize = 0.01; var stepSize = 0.001
                        for (i in 0 until filters.length()) {
                            val f = filters.getJSONObject(i)
                            when (f.optString("filterType")) {
                                "PRICE_FILTER" -> tickSize = f.optString("tickSize", "0.01").toDoubleOrNull() ?: 0.01
                                "LOT_SIZE" -> stepSize = f.optString("stepSize", "0.001").toDoubleOrNull() ?: 0.001
                            }
                        }
                        val p = SymbolPrecision(upper, tickSize, stepSize, decimalsOf(tickSize), decimalsOf(stepSize))
                        cache[upper] = p
                        return@withContext p
                    }
                }
            }
        } catch (_: Exception) { }
        val fb = fallback(upper)
        cache[upper] = fb
        fb
    }

    private fun decimalsOf(value: Double): Int {
        if (value <= 0) return 2
        val str = String.format("%.8f", value).trimEnd('0')
        val dot = str.indexOf('.')
        return if (dot >= 0) str.length - dot - 1 else 0
    }

    private fun fallback(symbol: String): SymbolPrecision = when {
        symbol.startsWith("PEPE") || symbol.startsWith("SHIB") -> SymbolPrecision(symbol, 0.0000001, 1000.0, 7, 0)
        symbol.startsWith("1000") || symbol.startsWith("FLOKI") -> SymbolPrecision(symbol, 0.0001, 1.0, 4, 0)
        symbol.startsWith("BTC") -> SymbolPrecision(symbol, 0.1, 0.001, 1, 3)
        symbol.startsWith("ETH") -> SymbolPrecision(symbol, 0.05, 0.01, 2, 2)
        symbol.startsWith("SOL") -> SymbolPrecision(symbol, 0.01, 0.1, 2, 1)
        else -> SymbolPrecision(symbol, 0.01, 0.001, 2, 3)
    }
}
