package com.aistudio.bozokpro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.bozokpro.model.Tab
import com.aistudio.bozokpro.ui.BozokTabBar
import com.aistudio.bozokpro.ui.BozokTopBar
import com.aistudio.bozokpro.ui.tabs.*
import com.aistudio.bozokpro.ui.theme.BozokProTheme
import com.aistudio.bozokpro.ui.theme.bz
import com.aistudio.bozokpro.viewmodel.BozokViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BozokViewModel by viewModels()

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* kullanıcı reddederse: sadece bildirimler kapalı kalır */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeAskNotificationPermission()

        setContent {
            val cfg by viewModel.config.collectAsStateWithLifecycle()
            BozokProTheme(theme = cfg.theme, colorblind = cfg.colorblind) {
                BozokMainScreen(viewModel)
            }
        }
    }

    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun BozokMainScreen(viewModel: BozokViewModel) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val symbol by viewModel.symbol.collectAsStateWithLifecycle()
    val c = bz()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = c.bg,
        topBar = {
            BozokTopBar(
                symbol = symbol,
                price = "—",
                change24hPct = null,
                vpin = null,
                onSymbolClick = { /* Faz 2 */ }
            )
        },
        bottomBar = {
            BozokTabBar(active = activeTab, signalBadgeCount = 0) {
                viewModel.selectTab(it)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(c.bg)) {
            when (activeTab) {
                Tab.BOOK -> BookTabPlaceholder()
                Tab.FLOW -> FlowTabPlaceholder()
                Tab.DEPTH -> DepthTabPlaceholder()
                Tab.SIGNALS -> SignalsTabPlaceholder()
                Tab.LEVELS -> LevelsTabPlaceholder()
                Tab.MARKETS -> MarketsTabPlaceholder()
                Tab.SETTINGS -> SettingsTabPlaceholder()
            }
        }
    }
}
