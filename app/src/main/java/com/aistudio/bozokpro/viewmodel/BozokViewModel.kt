package com.aistudio.bozokpro.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.aistudio.bozokpro.model.AppConfig
import com.aistudio.bozokpro.model.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * BOZOK PRO — Ana ViewModel (iskelet).
 *
 * NOT: Bu ilk turda sadece config + tab state var. Sonraki turlarda:
 *  - Faz 2: Veri katmanı entegrasyonu (Binance / Bybit / OKX / MEXC WS)
 *  - Faz 3-6: Motor iş akışı (PatternEngine, MetaStrategy, Flow, Micro)
 *  - Faz 8-9: Tab-spesifik StateFlow'lar
 */
class BozokViewModel(app: Application) : AndroidViewModel(app) {

    // -------- Config (immutable copy-on-update, StateFlow uyumlu) --------
    private val _config = MutableStateFlow(AppConfig.DEFAULT)
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    fun updateConfig(transform: (AppConfig) -> AppConfig) {
        _config.update(transform)
    }

    // -------- Aktif sekme --------
    private val _activeTab = MutableStateFlow(Tab.BOOK)
    val activeTab: StateFlow<Tab> = _activeTab.asStateFlow()

    fun selectTab(t: Tab) { _activeTab.value = t }

    // -------- Sembol --------
    private val _symbol = MutableStateFlow("BTCUSDT")
    val symbol: StateFlow<String> = _symbol.asStateFlow()

    fun selectSymbol(s: String) {
        val u = s.uppercase().trim()
        if (u.isEmpty()) return
        _symbol.value = u
        // Sonraki fazda: bağlantıları yeniden kur + AppState.resetForSymbolChange()
    }
}
