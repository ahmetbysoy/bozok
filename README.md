# 🐆 BOZOK PRO — Orderflow Scalping Terminali (Android)

**Vur-kaç odaklı, order-flow / mikro-yapı sinyal motoru.** Binance Futures (ve Bybit/OKX/MEXC) emir defteri + işlem akışını gerçek zamanlı analiz eder; spoof, iceberg, stop-hunt, likidasyon havuzları, CVD diverjansı gibi kurumsal desenleri tespit edip **vur-kaç (scalp) trade planı** üretir.

> Referans: `bozok_chartshell_v2` (web terminali) — tüm motorlar mobil uygulamaya doğru kurguyla taşındı.

## 🎯 Amacı

- Scalper için **anlık desen + plan**: giriş, SL, TP1/TP2, risk yönetimi tek ekranda
- Kurgusal veri yok: tüm sinyaller **gerçek borsa verisinden** türetilir
- **Vur-kaç:** düşük zaman dilimi sinyalleri (1-5dk), dar stop, hızlı TP

## ✨ Öne Çıkan Özellikler

| Özellik | Açıklama |
|---|---|
| 📊 **Canlı Isı Haritası** | 60sn zaman eksenli derinlik haritası; Likidite/Hız/İşlemler/Duvarlar/Liq Havuzları katmanları ayrı ayrı açılıp kapatılır |
| 👆 **Drag-To-Trade** | Canvas üzerinde SL/TP1/TP2 çizgilerini sürükle; fiyatlar ve R:R anında güncellenir (haptik tik) |
| 🧠 **4 Avcı Strateji** | Kaplan Kapan, Kelle Avcısı, Balina Tuzağı, Işık Hızı Arbitrajı — canlı market durumundan skorlanır, kazanan seçilir |
| 💰 **Kelly Sizing** | Güven skoruna göre risk %0.5-5 + balance-aware kaldıraç + pozisyon boyutu |
| 🕯️ **Flow Mumları** | Gerçek tape'ten inşa edilen basınç mumları (zaman/hacim modu); POC + footprint cluster |
| 💥 **Tasfiye Basıncı** | Havuz mesafesi × notional → 0-100 şelale riski |
| 📡 **4 Borsa** | Binance tam derinlik (checksum) + Bybit/OKX/MEXC arbitraj + Global Book |
| 🔐 **Biyometrik İnfaz Kalkanı** | Emir bota gönderilmeden önce parmak izi doğrulaması (fail-closed) |
| 🤖 **Python İnfaz Botu** | FastAPI + Binance Futures bracket order + trailing stop + Telegram |
| 💾 **Kayıt & Replay** | Room DB oturum kareleri; oynatıcıda fiyat/sinyal canlandırılır |
| 🔔 **Ses & Bildirim** | Türkçe TTS anons + kritik sinyalde SOS bildirimi + Morse titreşim |
| 🏠 **Widget** | Canlı fiyat/VPIN/strateji home-screen widget'ı |
| 🎨 **3 Tema + Renk Körlüğü** | Professional / Neon / Minimal + Okabe-Ito paleti |

## 🏗 Mimari

```
Binance/Bybit/OKX/MEXC WS ─► AppState (book, trades, cvd, walls, liq)
        │ 250ms quant loop
        ▼
PatternEngineV2 ─► aktif sinyaller ─► MetaStrategyEngine ─► TradePlan (vur-kaç)
        │                 │                     │
        ▼                 ▼                     ▼
FlowBuilder(500ms)  SignalVerifier(15sn)  MicroOptimizer (bakiye-aware)
        │                 │
        ▼                 ▼
  FLOW mumları      Rolling accuracy + manip radarı
```

Her sinyal doğrulanır (fiyat hareketiyle), strateji performansı gerçek veriden beslenir.

## 📡 Veri Kaynakları

| Kaynak | Kullanım |
|---|---|
| Binance Futures WS | Tam derinlik diff stream (`@depth@100ms`) + REST snapshot + **Local Orderbook Checksum** senkronu, `@aggTrade`, `@ticker` (24s değişim dahil) |
| Binance REST | `exchangeInfo` (gerçek tickSize), `depth?limit=1000` snapshot, `allForceOrders` telafi |
| Bybit / OKX / MEXC WS | Çapraz borsa arbitrajı + Global Book (bucket birleştirme) |
| Binance `!forceOrder@arr` | Canlı likidasyon akışı (şelale/ters dönüş sinyalleri) |
| Room DB | Replay oturumları (max 100, FIFO) |
| Python Bot | `POST /webhook` (X-BOZOK-SECRET) → bracket order |

## 🧠 Motorlar (engine/)

| Paket | Sınıflar |
|---|---|
| `detect/` | StrongWallDetector, WallPullDetector (spoof), AbsorptionDetector, LiquidityVoidDetector (+vacuum), LadderDetectorV2, CompressionDetector, IcebergDetector, OrderbookSkewDetector (+10sn kayma), OFISpikeDetector (gizli absorpsiyon), StopHuntDetector (**gerçek 15dk pencere**), VpinCalculator (**adaptif kova** $100k-2M), CvdDivergenceDetector (balina/retail SMD) |
| `flow/` | PressureCalculator, FlowCandleBuilder (zaman/hacim modu — **kurgusal hacim YOK**), FlowCandlePatternDetector (momentum/dönüş/tükenme/sıkışma), LiquidationPoolSimulator (CVD yanlılı), LiquidationPressureCalculator, LiquidationPatternDetector (şelale/tükenme) |
| `pattern/` | PatternEngineV2 (orkestratör), NarrativeEngine (Türkçe meta-yorum — 7 senaryo), SignalVerifier (rolling accuracy + manip radarı), AppState |
| `strategy/` | MetaStrategyEngine (**4 avcı** + sembol bazlı tuning: BTC/ETH 6bps, altcoin 12bps arbitraj eşiği), TradePlanGenerator (volatilite adaptif buffer + fee-adjusted R:R + trailing önerisi), MicroAccountOptimizer (balance-aware kaldıraç + Kelly), StrategyPerformanceTracker (form bonusu) |

## 🦁 4 Avcı Strateji

1. **🔥 KAPLAN KAPAN** — Spoof Trap & Void Sweep: sahte satıcı duvarı çekilince üstteki boşluğa süpürme → LONG (tersi SHORT). Besleyen: WallPullDetector, LiquidityVoidDetector.
2. **🩸 KELLE AVCISI** — Liquidation Cascade Reversal: tasfiye şelalesi tükenince gizli absorpsiyonla V-dönüş. Besleyen: LiquidationPatternDetector, AbsorptionDetector.
3. **🐋 BALİNA TUZAĞI** — Smart Money Distribution: fiyat yükselirken balina CVD düşüyor → SHORT. Besleyen: CvdDivergenceDetector, IcebergDetector.
4. **⚡ IŞIK HIZI ARBİTRAJI** — Latency Front-Running: borsalar arası sapma ≥ eşik (BTC 6bps / altcoin 12bps) → öncü borsa yönünde koş.

Kazanan plan: yön + Entry/SL/TP1/TP2 + R:R + Kelly % + pozisyon boyutu → `TradePlan` olarak UI'a düşer; kullanıcı drag-to-trade ile elle değiştirebilir; biyometrik onayla webhook'a gönderilir.

## 📱 7 Sekme

- **📊 BOOK** — ısı haritası (60sn, 7 katman), pattern etiketleri, liq havuzu mıknatıs bantları, plan çizgileri + **drag-to-trade**, tasfiye basıncı HUD'u, stale/sync göstergesi
- **🕯️ FLOW** — basınç mumları + footprint + Altın POC + olay ikonları + lejant
- **📚 DEPTH** — OBI/CVD gauge + sparkline, tasfiye basıncı, son tasfiyeler, merdiven (kademeye dokununca BOOK'ta odaklan)
- **🎯 SİNYAL** — istatistik çipleri (toplam/bull/bear/uyarı/**doğruluk**/**manip radarı**), filtreler, CSV export, kart tıklayınca LEVELS'te odaklan
- **📍 SEVİYE** — Narrative meta-analiz + plan kartı + mikro optimizör + equity eğrisi + aktif desenler
- **🌐 PİYASA** — 4 borsa durumu/latency + arbitraj sapmaları (bps)
- **⚙️ AYAR** — 3 tema + renk körlüğü, hassasiyet preset'leri (motora bağlı), flow periyot/mod, mikro bakiye/risk, ses/TTS/bildirim, JSON yedekleme, **replay oynatıcı**

## 🔐 Güvenlik

- **Fail-closed biyometri:** donanım/parmak izi yoksa infaz engellenir
- **Webhook koruması:** secret + zaman damgası replay koruması + kaldıraç/notional limitleri (Python bot)
- **Dürüst hata:** sahte "başarılı" bildirimi yok
- **SIM şeffaflığı:** canlı bağlantı yoksa arayüzde durum açıkça gösterilir

## 🔨 Derleme & Test

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew assembleRelease      # env'de KEYSTORE_PATH yoksa debug anahtarıyla imzalanır
./gradlew testDebugUnitTest    # motor testleri (12 test)
```

CI: GitHub Actions her push'ta test + APK üretir → Actions → `bozok-apk` artifact.

## 🤖 Python İnfaz Botu

`python/bozok_execution_bot.py` — FastAPI webhook → Binance Futures bracket order (entry+SL+TP1+TP2), **trailing stop** (0.5R→+0.25R, 1R→+0.75R), Telegram bildirimi, testnet varsayılan.

```bash
cd python && pip install fastapi uvicorn aiohttp
cp .env.example .env   # BINANCE_API_KEY / BINANCE_API_SECRET / TELEGRAM_TOKEN
python bozok_execution_bot.py
```

## ⚠️ Yasal Uyarı

Eğitim/araştırma amaçlıdır; finansal tavsiye değildir. Kaldıraçlı kripto işlemleri yüksek risk içerir. Gerçek para kullanımı tamamen kullanıcının sorumluluğundadır.
