package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.ui.theme.Bg
import com.example.ui.theme.BozokProTheme
import com.example.util.BiometricAuthHelper
import com.example.util.BozokNotifications
import com.example.viewmodel.BozokViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: BozokViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BozokNotifications.ensureChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        setContent {
            val config by viewModel.config.collectAsStateWithLifecycle()
            BozokProTheme(theme = config.theme, colorblind = config.colorblind) {
                BozokMainScreen(viewModel)
            }
        }
    }
}

@Composable
fun BozokMainScreen(viewModel: BozokViewModel) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()
    val signals by viewModel.signals.collectAsStateWithLifecycle()
    val feed by viewModel.feedSignals.collectAsStateWithLifecycle()
    val plan by viewModel.tradePlan.collectAsStateWithLifecycle()
    val flowCandles by viewModel.flowCandles.collectAsStateWithLifecycle()
    val narrative by viewModel.narrative.collectAsStateWithLifecycle()
    val liqPools by viewModel.liqPools.collectAsStateWithLifecycle()
    val liqPressure by viewModel.liqPressure.collectAsStateWithLifecycle()
    val liquidations by viewModel.liquidations.collectAsStateWithLifecycle()
    val cvd by viewModel.cvd.collectAsStateWithLifecycle()
    val vpin by viewModel.vpin.collectAsStateWithLifecycle()
    val accuracy by viewModel.accuracy.collectAsStateWithLifecycle()
    val manip by viewModel.manipIndex.collectAsStateWithLifecycle()
    val exchanges by viewModel.exchanges.collectAsStateWithLifecycle()
    val arbitrage by viewModel.arbitrage.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val symbol by viewModel.symbol.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val replaySessions by viewModel.replaySessions.collectAsStateWithLifecycle()
    val replayPlaying by viewModel.replayPlaying.collectAsStateWithLifecycle()
    val microResult by viewModel.microResult.collectAsStateWithLifecycle()
    val focusPrice by viewModel.focusPrice.collectAsStateWithLifecycle()
    val chg24 = com.example.engine.pattern.AppState.tickerChangePct ?: 0.0

    // Biyometrik infaz kalkanı (fail-closed)
    val executeWithBiometric: () -> Unit = {
        val frag = activity
        if (frag != null) {
            val auth = BiometricAuthHelper(frag)
            auth.authenticateExecution(
                onSuccess = { viewModel.executeOrderViaWebhook(context) },
                onError = { err ->
                    android.widget.Toast.makeText(context, "🔒 İNFAZ ENGELLENDİ: $err", android.widget.Toast.LENGTH_LONG).show()
                }
            )
        } else viewModel.executeOrderViaWebhook(context)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Bg,
        topBar = {
            TopBar(
                symbol = symbol, book = book, vpin = vpin, chg24 = chg24,
                onSymbolSelected = { viewModel.selectSymbol(it) }
            )
        },
        bottomBar = {
            BozokTabBar(
                activeTab = activeTab,
                unreadSignalsCount = signals.count { it.severity == com.example.model.Severity.HIGH },
                replayActive = isRecording,
                onTabSelected = { viewModel.selectTab(it) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Bg)
        ) {
            when (activeTab) {
                "BOOK" -> BookTab(
                    book = book, liqPools = liqPools, plan = plan, liqPressure = liqPressure,
                    focusPrice = focusPrice, config = config, microResult = microResult,
                    onExecute = executeWithBiometric,
                    onDragPrices = { stop, tp1, tp2 -> viewModel.updateDragPrices(stop, tp1, tp2) },
                    onTapPrice = { viewModel.setFocusPrice(it) }
                )
                "DEPTH" -> DepthTab(
                    book = book, cvd = cvd, liqPressure = liqPressure, liquidations = liquidations,
                    onFocusPrice = {
                        viewModel.setFocusPrice(it)
                        viewModel.selectTab("BOOK")
                    }
                )
                "FLOW" -> FlowTab(candles = flowCandles, plan = plan, onExecute = executeWithBiometric)
                "SIGNALS" -> SignalsTab(
                    feed = feed, accuracy = accuracy, manipIndex = manip,
                    onExportCsv = { viewModel.exportSignalsCsv(context) },
                    onNavigateLevels = {
                        viewModel.setFocusPrice(it)
                        viewModel.selectTab("LEVELS")
                    }
                )
                "LEVELS" -> LevelsTab(
                    narrative = narrative, plan = plan, microResult = microResult,
                    signals = signals, config = config,
                    perfTracker = viewModel.perfTracker(),
                    onExecute = executeWithBiometric
                )
                "MARKETS" -> MarketsTab(exchanges = exchanges, arbitrage = arbitrage, symbol = symbol)
                "SETTINGS" -> SettingsTab(
                    config = config, isRecording = isRecording, replaySessions = replaySessions,
                    replayPlaying = replayPlaying,
                    onToggleRecord = { viewModel.toggleRecording() },
                    onPlayReplay = { viewModel.playReplay(it) },
                    onDeleteSession = { viewModel.deleteSession(it) },
                    onSensitivityChange = { viewModel.setSensitivity(it) },
                    onFlowPeriodChange = { viewModel.setFlowPeriod(it) },
                    onFlowModeChange = { viewModel.setFlowMode(it) },
                    onColorblindChange = { viewModel.setColorblind(it) },
                    onThemeChange = { viewModel.setTheme(it) },
                    onMultiExchangeChange = { viewModel.setMultiExchange(it) },
                    onSoundChange = { viewModel.setSound(it) },
                    onVoiceChange = { viewModel.setVoice(it) },
                    onNotificationsChange = { viewModel.setNotifications(it) },
                    onExportSettings = { viewModel.exportSettingsJson(context) },
                    onImportSettings = { viewModel.importSettingsJson(it) },
                    onUpdateConfig = { mutate -> viewModel.updateConfig(mutate) }
                )
            }
        }
    }
}
