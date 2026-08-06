# 🐆 BOZOK PRO — Orderflow Scalping Terminali (Android)

**Referans:** `bozok_chartshell_v2` (web/HTML terminal) — Kotlin/Compose ile Android'e taşınıyor.

> **Durum: FAZ 1 tamamlandı.** İskelet + veri modelleri + AppState + Tema hazır. Motorlar & UI sekmeleri sonraki fazlarda gelecek.

## 🎯 Amacı

Web referansının **birebir Android kopyası**: order-flow/mikro-yapı sinyal motoru, spoof/iceberg/stop-hunt/likidasyon havuzları tespiti, vur-kaç trade planı.

## 🏗 Yol Haritası (10 Faz)

| Faz | İçerik | Durum |
|---|---|---|
| **0** | Temizlik + master plan | ✅ |
| **1** | Modeller + AppState + CFG + Tema + UI iskelet | ✅ |
| 2 | Veri katmanı: Binance depth/aggTrade + Bybit/OKX/MEXC + Liquidation WS | ⏳ |
| 3 | 8 Detector: StrongWall, WallPull, Absorption, LiquidityVoid, LadderV2, OFISpike, Compression, Iceberg | ⏳ |
| 4 | Stop-hunt + VPIN + CVD divergence + PatternEngineV2 + NarrativeEngine | ⏳ |
| 5 | Flow: Pressure + FlowCandleBuilder (time+volume mod) + Pattern + LiqPattern | ⏳ |
| 6 | Strateji: MetaStrategy (4 avcı) + TradePlanGen + MicroOptimizer + PerfTracker | ⏳ |
| 7 | Canvas render: Heatmap (7 katman), FlowCandle, Equity chart | ⏳ |
| 8 | UI: BookTab (drag-to-trade), DepthTab, FlowTab (HUD+Legend) | ⏳ |
| 9 | SignalsTab, LevelsTab, MarketsTab, SettingsTab | ⏳ |
| 10 | ViewModel bağlantı, notif, TTS, son testler | ⏳ |

## 📁 Paket Yapısı

```
com.aistudio.bozokpro/
├── model/          — Enums, MarketModels, SignalModels, AppConfig
├── engine/
│   ├── detect/     — (Faz 3) Pattern dedektörleri
│   ├── flow/       — (Faz 5) Flow motorları
│   ├── pattern/    — AppState + (Faz 4) PatternEngine, Narrative
│   └── strategy/   — (Faz 6) MetaStrategy, TradePlan, MicroOptimizer
├── data/           — (Faz 2) WS/REST istemcileri
├── ui/
│   ├── theme/      — BozokColors + BozokProTheme (3 tema + colorblind)
│   ├── tabs/       — 7 sekme Composable'ı
│   ├── canvas/     — (Faz 7) Heatmap + FlowCandle renderer'ları
│   └── (root)      — TopBar, TabBar
├── util/           — Fmt, nowMs, medianOrNull
└── viewmodel/      — BozokViewModel
```

## 🔨 Derleme & Test

```bash
./gradlew testDebugUnitTest     # 9 smoke test
./gradlew assembleDebug         # APK: app/build/outputs/apk/debug/
./gradlew assembleRelease       # env'de KEYSTORE_PATH yoksa debug key ile imzalanır
```

**Gereksinim:** JDK 17, Android SDK 36.

CI: GitHub Actions her push'ta test + APK üretir → Actions → `bozok-apk` artifact.

## 📱 7 Sekme (referans HTML sırası birebir)

1. **📊 BOOK** — Isı haritası (60sn, 5 katman), pattern etiketleri, plan çizgileri, drag-to-trade
2. **🕯️ FLOW** — Baskı mumları + footprint + Altın POC + olay ikonları + lejant
3. **📚 DEPTH** — OBI/CVD gauge + sparkline + kademeli merdiven
4. **🎯 SIGNALS** — Pattern feed + istatistik çipleri + CSV export
5. **📍 LEVELS** — Narrative meta-analiz + trade plan + mikro optimizör + equity
6. **🌐 MARKETS** — 4 borsa durumu + arbitraj sapmaları
7. **⚙️ SETTINGS** — 3 tema + renk körlüğü, hassasiyet, flow, mikro, ses/TTS/bildirim

## 🧪 Referans HTML uyumu

Her Kotlin dosyasının başında hangi HTML bölümünden geldiği yorumla belirtildi. Sensitivity preset değerleri, CFG varsayılanları, sekme sırası, katman anahtarları — hepsi HTML L858+ ile birebir eşleşiyor.

## ⚠️ Yasal Uyarı

Eğitim/araştırma amaçlıdır; finansal tavsiye değildir. Kaldıraçlı kripto işlemleri yüksek risk içerir.
