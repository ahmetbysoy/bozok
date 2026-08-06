package com.aistudio.bozokpro.engine

import com.aistudio.bozokpro.engine.pattern.AppState
import com.aistudio.bozokpro.model.*
import com.aistudio.bozokpro.util.Fmt
import com.aistudio.bozokpro.util.medianOrNull
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Faz 1 smoke test — modeller + AppState + Fmt sağlıklı çalışıyor mu?
 * Motor detay testleri sonraki fazlarda eklenecek.
 */
class ModelSmokeTest {

    @Before
    fun setup() {
        AppState.resetForSymbolChange()
    }

    @Test
    fun defaultConfigMatchesHtmlSpec() {
        val d = AppConfig.DEFAULT
        assertEquals("btcusdt", d.symbol)
        assertEquals(3.5, d.wallMult, 0.0)
        assertEquals(3000L, d.spoofWindowMs)
        assertEquals(2.2, d.imbalanceThresh, 0.0)
        assertEquals(60, d.heatmapWindowSec)
        assertEquals(SensitivityPreset.NORMAL, d.sensitivity)
        assertEquals(Theme.PROFESSIONAL, d.theme)
        assertEquals(5000L, d.flowTimeframeMs)
        assertEquals(20, d.microMaxLeverage)
        assertTrue(d.multiExchange)
        assertTrue(d.soundOn)
        assertTrue(d.voiceAnnounce)
        assertFalse(d.notifications)
    }

    @Test
    fun tabsInHtmlOrder() {
        val order = Tab.entries.map { it.htmlView }
        assertEquals(
            listOf("bookView", "flowView", "depthView", "signalsView", "levelsView", "marketsView", "settingsView"),
            order
        )
    }

    @Test
    fun sensitivityPresetsMirrorHtml() {
        assertEquals(4.5, SensitivityPreset.CONSERVATIVE.wallMult, 0.0)
        assertEquals(2.5, SensitivityPreset.AGGRESSIVE.wallMult, 0.0)
        assertEquals(2000L, SensitivityPreset.CONSERVATIVE.spoofWindowMs)
        assertEquals(5000L, SensitivityPreset.AGGRESSIVE.spoofWindowMs)
    }

    @Test
    fun bookObiComputedCorrectly() {
        val book = Book(
            bids = listOf(BookLevel(100.0, 10.0), BookLevel(99.5, 5.0)),
            asks = listOf(BookLevel(100.5, 3.0), BookLevel(101.0, 2.0))
        )
        // bidDepth = 1000+497.5 = 1497.5; askDepth = 301.5+202 = 503.5
        // obi = (1497.5-503.5)/(1497.5+503.5)*100 = 49.65%
        assertEquals(49.65, book.obi, 0.1)
        assertEquals(100.25, book.mid!!, 0.001)
    }

    @Test
    fun appStatePushTradeCapsAt5000() {
        for (i in 0 until 6000) {
            AppState.pushTrade(Trade(65000.0, 0.001, Side.BUY, System.currentTimeMillis()))
        }
        assertTrue(AppState.trades.size <= 5000)
    }

    @Test
    fun appStateRecentTradesFiltersWindow() {
        val now = System.currentTimeMillis()
        AppState.pushTrade(Trade(65000.0, 1.0, Side.BUY, now - 200_000))  // eski
        AppState.pushTrade(Trade(65100.0, 1.0, Side.SELL, now - 1_000))
        AppState.pushTrade(Trade(65200.0, 1.0, Side.BUY, now - 500))
        val recent = AppState.recentTrades(60_000)
        assertEquals(2, recent.size)  // 200sn'lik olan düşer
    }

    @Test
    fun fmtIsAlwaysUsLocale() {
        val prev = Locale.getDefault()
        try {
            // TR locale'yi zorla — String.format tuzağını yakala
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("65000.1234", Fmt.price(65000.1234, tick = 0.0001))
            assertEquals("1.2M$", Fmt.notional(1_200_000.0))
            assertEquals("+2.35%", Fmt.signedPct(2.35))
        } finally {
            Locale.setDefault(prev)
        }
    }

    @Test
    fun medianOrNullWorks() {
        assertNull(emptyList<Double>().medianOrNull())
        assertEquals(3.0, listOf(1.0, 3.0, 5.0).medianOrNull()!!, 0.0)
        assertEquals(2.5, listOf(1.0, 2.0, 3.0, 4.0).medianOrNull()!!, 0.0)
    }

    @Test
    fun resetForSymbolChangeClearsState() {
        AppState.lastPrice = 65000.0
        AppState.pushTrade(Trade(65000.0, 1.0, Side.BUY, System.currentTimeMillis()))
        AppState.cvd = 42.0
        AppState.manipIndex = 55
        AppState.resetForSymbolChange()
        assertNull(AppState.lastPrice)
        assertTrue(AppState.trades.isEmpty())
        assertEquals(0.0, AppState.cvd, 0.0)
        assertEquals(0, AppState.manipIndex)
    }
}
