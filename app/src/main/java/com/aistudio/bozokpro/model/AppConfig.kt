package com.aistudio.bozokpro.model

/* ============================================================================
 * BOZOK PRO — CONFIG
 * HTML L858-895 DEFAULT_CFG birebir çevirisi.
 * Immutable — güncelleme için `.copy(...)` kullanın (StateFlow'a uyumlu).
 * ========================================================================== */
data class AppConfig(
    val symbol: String = "btcusdt",
    val primaryExchange: String = "binance",
    val bookMode: BookMode = BookMode.BINANCE,
    val multiExchange: Boolean = true,

    // Wall / spoof / imbalance
    val wallMult: Double = 3.5,
    val spoofWindowMs: Long = 3000,
    val imbalanceThresh: Double = 2.2,
    val algoWarEventsPerSec: Double = 6.0,

    // Heatmap
    val heatmapWindowSec: Int = 60,
    val sampleIntervalMs: Long = 300,
    val renderIntervalMs: Long = 150,
    val depthLevels: Int = 20,
    val overlayDensity: OverlayDensity = OverlayDensity.NORMAL,
    val ladderDepth: String = "auto",     // "auto" | "10" | "15" | "20"
    val chartMode: ChartMode = ChartMode.NORMAL,

    // Ses + bildirim
    val soundOn: Boolean = true,
    val voiceAnnounce: Boolean = true,
    val notifications: Boolean = false,

    // Hassasiyet
    val sensitivity: SensitivityPreset = SensitivityPreset.NORMAL,
    val minPatternConfidence: Int = 65,
    val minSignalConfidence: Int = 60,
    val minFlowConfidence: Int = 65,
    val minToastConfidence: Int = 78,

    // Tema
    val theme: Theme = Theme.PROFESSIONAL,
    val colorblind: Boolean = false,

    // Flow
    val flowTimeframeMs: Long = 5000,
    val flowCandleMode: FlowCandleMode = FlowCandleMode.TIME,
    val flowVolumeTarget: Double = 1_000_000.0,

    // Likidasyon
    val minLiquidationNotional: Double = 10_000.0,

    // Fee / funding
    val feeRate: Double = 0.0005,
    val makerFee: Double = 0.0002,
    val takerFee: Double = 0.0004,
    val fundingRate: Double = 0.0001,

    // Micro optimizer
    val microBalance: Double = 5.0,
    val microRiskPct: Double = 0.20,
    val microMaxLeverage: Int = 20,

    // Heatmap katmanları (HTML S.activeLayers)
    val activeLayers: Set<HeatmapLayer> = HeatmapLayer.defaults
) {
    companion object {
        val DEFAULT = AppConfig()
    }
}
