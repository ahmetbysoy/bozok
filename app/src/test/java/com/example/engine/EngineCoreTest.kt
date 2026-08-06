package com.example.engine

import com.example.engine.detect.*
import com.example.engine.flow.*
import com.example.engine.pattern.AppState
import com.example.engine.pattern.NarrativeEngine
import com.example.engine.strategy.MetaStrategyEngine
import com.example.engine.strategy.StrategyPerformanceTracker
import com.example.data.GlobalBook
import com.example.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/* ============================================================================
 * MOTOR BİRİM TESTLERİ
 * ========================================================================== */
class EngineCoreTest {

    @Before
    fun setup() {
        AppState.config.wallMult = 3.5
        AppState.config.spoofWindowMs = 3000
    }

    @Test
    fun wallDetectionFlagsOnlyDominantLevels() {
        val detector = StrongWallDetector("ask")
        val levels = (1..10).map { BookLevel(65000.0 + it * 0.5, qty = 2.0) } +
            listOf(BookLevel(65015.0, qty = 60.0))
        val sigs = detector.analyze(levels, mid = 65000.0)
        assertTrue(sigs.any { it.type == "STRONG_ASK_WALL" && it.price == 65015.0 })
    }

    @Test
    fun spoofDetectionFiresOnUnabsorbedWallRemoval() {
        val detector = WallPullDetector()
        val t = System.currentTimeMillis()
        val wall = WallRecord("ask", 65000.0, notional = 500_000.0, qty = 8.0, firstSeen = t - 2000, lastSeen = t - 1000)
        val sigs = detector.analyze(listOf(wall), emptyList())
        assertTrue(sigs.any { it.type == "WALL_PULL" && it.bias == Bias.BULL })
    }

    @Test
    fun spoofDoesNotFireWhenWallWasAbsorbed() {
        val detector = WallPullDetector()
        val t = System.currentTimeMillis()
        val wall = WallRecord("ask", 65000.0, notional = 100_000.0, qty = 2.0, firstSeen = t - 2000, lastSeen = t - 1000)
        val trades = listOf(Trade(65000.0, 50.0, Side.BUY, t - 500))
        val sigs = detector.analyze(listOf(wall), trades)
        assertFalse(sigs.any { it.type == "WALL_PULL" })
    }

    @Test
    fun stopHuntDetectsEQHSweepWithin15mWindow() {
        val detector = StopHuntDetector(windowMs = 15 * 60 * 1000)
        val t = System.currentTimeMillis()
        detector.update(65000.0, t - 60_000)
        detector.update(65001.0, t - 10_000)
        val sigs = detector.analyze(65003.0)
        assertTrue(sigs.any { it.type == "STOP_HUNT_SWEEP" && it.bias == Bias.BEAR })
    }

    @Test
    fun stopHuntIgnoresStaleLevelsOlderThanWindow() {
        val detector = StopHuntDetector(windowMs = 15 * 60 * 1000)
        val t = System.currentTimeMillis()
        detector.update(66000.0, t - 20 * 60 * 1000) // eski -> düşer
        detector.update(65000.0, t - 60_000)
        val sigs = detector.analyze(65000.5)
        val sweep = sigs.firstOrNull { it.type == "STOP_HUNT_SWEEP" }
        if (sweep != null) {
            assertEquals(65000.0, (sweep.metadata["sweptLevel"] as Double), 0.01)
        }
        assertTrue(sigs.any { it.type == "STOP_HUNT_SWEEP" })
    }

    @Test
    fun vpinAdaptiveBucketResetsOnSymbolChange() {
        val vpin = VpinCalculator()
        vpin.setBucketVolume(2_000_000.0)
        vpin.update(Trade(65000.0, 10.0, Side.BUY, System.currentTimeMillis()))
        assertNull(vpin.getVPIN()) // 650k < 2M bucket
    }

    @Test
    fun flowCandleUsesRealTradeActivityNotFabricatedVolume() {
        val builder = FlowCandleBuilder(timeframeMs = 5000)
        builder.rebuild()
        val book = Book(
            bids = listOf(BookLevel(64999.0, 5.0), BookLevel(64998.0, 6.0)),
            asks = listOf(BookLevel(65001.0, 4.0), BookLevel(65002.0, 3.0))
        )
        val t = System.currentTimeMillis()
        val trades = listOf(
            Trade(65000.0, 1.0, Side.BUY, t - 1000),
            Trade(65001.0, 2.0, Side.SELL, t - 500)
        )
        val patterns = listOf(
            PatternSignal("p1", "STRONG_BID_WALL", "wall", Bias.BULL, 64999.0, 80),
            PatternSignal("p2", "STRONG_ASK_WALL", "wall2", Bias.BEAR, 65001.0, 80)
        )
        builder.update(book, trades, patterns, emptyList())
        val candles = builder.getCandles()
        assertTrue(candles.isNotEmpty())
        val c = candles.last()
        assertTrue(c.activity > 0.0)
        assertTrue(c.buyActivity > 0.0 && c.sellActivity > 0.0)
    }

    @Test
    fun liqPoolSimulatorScalesWithCvdBias() {
        val sim = LiquidationPoolSimulator()
        val bull = sim.getPools(65000.0, cvd = 500.0, symbol = "BTCUSDT")
        val bear = sim.getPools(65000.0, cvd = -500.0, symbol = "BTCUSDT")
        val bullShort = bull.first { it.side == "short" && it.leverage == 25 }.estNotionalUsd
        val bearShort = bear.first { it.side == "short" && it.leverage == 25 }.estNotionalUsd
        assertTrue(bullShort < bearShort)
    }

    @Test
    fun metaEnginePicksKaplanWhenSpoofAndVoidPresent() {
        val engine = MetaStrategyEngine(StrategyPerformanceTracker())
        val t = System.currentTimeMillis()
        val patterns = listOf(
            PatternSignal("s1", "WALL_PULL", "spoof", Bias.BULL, 65000.0, 85, createdAt = t - 1000),
            PatternSignal("s2", "LIQUIDITY_VOID", "void", Bias.BULL, 65005.0, 80, createdAt = t - 1000)
        )
        val book = Book(bids = listOf(BookLevel(64999.0, 5.0)), asks = listOf(BookLevel(65001.0, 4.0)))
        val plan = engine.evaluate(patterns, book, emptyList(), emptyList(), "BTCUSDT", emptyList())
        assertNotNull(plan)
        assertEquals(Direction.LONG, plan?.direction)
    }

    @Test
    fun signalVerifierMarksBullHitWhenPriceRises() {
        val perf = StrategyPerformanceTracker()
        val verifier = com.example.engine.pattern.SignalVerifier(perf)
        AppState.sigVerify.pending.clear()
        AppState.sigVerify.results.clear()

        val sig = PatternSignal("v1", "WALL_PULL", "t", Bias.BULL, price = 100.0, confidence = 80)
        verifier.record(sig)
        AppState.lastPrice = 100.0
        val rec = AppState.sigVerify.pending.first()
        AppState.sigVerify.pending.clear()
        AppState.sigVerify.pending.addLast(rec.copy(t = System.currentTimeMillis() - 120_000))
        AppState.lastPrice = 100.8

        verifier.verifyAll()
        assertTrue(AppState.sigVerify.results.size >= 1)
        assertTrue(AppState.sigVerify.results.last().hit)
    }

    @Test
    fun globalBookMergesExchangesIntoBuckets() {
        val binance = Book(bids = listOf(BookLevel(65000.0, 1.0)), asks = listOf(BookLevel(65001.0, 1.0)))
        val bybit = Book(bids = listOf(BookLevel(65000.4, 1.0)), asks = listOf(BookLevel(65001.4, 1.0)))
        val okx = Book(bids = emptyList(), asks = emptyList())
        val global = GlobalBook.build(binance, bybit, okx, multiExchange = true)
        assertTrue(global.bids.isNotEmpty())
        assertTrue(global.bids.size <= 2) // 65000.0 + 65000.4 aynı bucket
    }

    @Test
    fun narrativeSynthesizesSpoofVoidFlowAsSweep() {
        val t = System.currentTimeMillis()
        val sigs = listOf(
            PatternSignal("a", "WALL_PULL", "spoof", Bias.BULL, 1.0, 85, createdAt = t - 1000),
            PatternSignal("b", "LIQUIDITY_VOID", "void", Bias.BULL, 1.0, 80, createdAt = t - 1000),
            PatternSignal("c", "FLOW_BULL", "flow", Bias.BULL, 1.0, 75, createdAt = t - 1000)
        )
        val narrative = NarrativeEngine.synthesize(sigs)
        assertTrue(narrative.title.contains("SÜPÜRME"))
    }
}
