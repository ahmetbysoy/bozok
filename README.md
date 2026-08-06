# 🐆 BOZOK PRO v4.0 — Orderbook Intelligence & Market Microstructure Analysis

> **"Tam Otonom Avcı Zinciri"** — Binance Futures emir defteri mikro-yapısını (order flow) gerçek zamanlı analiz eden, kurumsal piyasa manipülasyonu desenlerini (spoof, iceberg, stop-hunt, tasfiye havuzları) tespit eden ve 4 avcı stratejiyi yarıştıran Android (Kotlin/Jetpack Compose) HFT karar destek sistemi.
>
> Tüm analiz **cihazda** çalışır; veri dışarı gönderilmez. İsteğe bağlı olarak onaylanmış işlem planı, kendi Python infaz botuna webhook ile iletilir.

---

## 📑 İçindekiler

1. [Projenin Amacı](#-projenin-amacı)
2. [Öne Çıkan Özellikler](#-öne-çıkan-özellikler)
3. [Mimari Genel Bakış](#-mimari-genel-bakış)
4. [Veri Kaynakları](#-veri-kaynakları)
5. [Sınıf ve Motor Envanteri](#-sınıf-ve-motor-envanteri)
6. [4 Avcı Strateji](#-4-avcı-strateji)
7. [Otomatik İşlem Kurgusu (Pipeline)](#-otomatik-işlem-kurgusu-pipeline)
8. [Uygulama Ekranları (7 Sekme)](#-uygulama-ekranları-7-sekme)
9. [Android Altyapı Katmanı](#-android-altyapı-katmanı)
10. [Python İnfaz Botu (bozok_execution_bot.py)](#-python-i̇nfaz-botu)
11. [Teknoloji Yığını](#-teknoloji-yığını)
12. [Derleme ve Çalıştırma](#-derleme-ve-çalıştırma)
13. [Testler](#-testler)
14. [Güvenlik Tasarımı](#-güvenlik-tasarımı)
15. [Bilinen Sınırlamalar ve Yol Haritası](#-bilinen-sınırlamalar-ve-yol-haritası)

---

## 🎯 Projenin Amacı

Bozok Pro, **perakende yatırımcıyı kurumsal piyasa yapıcılarıyla eşitlemeyi** hedefler. Kripto vadeli işlem borsalarındaki (öncelikle Binance Futures) emir defteri davranışını saniyenin kesirleriyle ölçerek:

- 🧱 **Duvar/spoof tespiti** — sahte emirlerin kurulup çekilmesi,
- 🧊 **Iceberg tespiti** — görünür derinliğin arkasına gizlenmiş kurumsal emilim,
- 🪤 **Stop-hunt tespiti** — tepe/dip likiditesinin iğnelenip avlanması,
- 💥 **Tasfiye havuzu analizi** — kaldıraçlı pozisyonların likidasyon mıknatıs bantları,
- 🕯️ **Flow mumları & footprint** — işlem akışından inşa edilen basınç mumları,
- 📉 **CVD divergansı** — fiyat ile kümülatif hacim deltası arasındaki "akıllı para" uyumsuzluğu

…gibi **mikro-yapı desenlerini** yakalar, bunları 4 "avcı strateji" altında birleştirir ve kullanıcıya **Entry / Stop Loss / TP1 / TP2 + Kelly risk boyutu** içeren otomatik bir işlem planı sunar.

---

## ✨ Öne Çıkan Özellikler

| Özellik | Açıklama |
|---|---|
| 📊 **Canlı Emir Defteri Isı Haritası** | 16×24 hücreli derinlik haritası; likidite, hız, işlemler, duvarlar ve liq havuzu katmanları ayrı ayrı açılıp kapatılabilir |
| 👆 **Drag-To-Trade** | Canvas üzerinde Entry/SL/TP1 çizgilerini sürükle; fiyatlar ve R:R anında yeniden hesaplanır, haptik mekanik tik hissi verir |
| 🧠 **4 Avcı Strateji Yarışı** | Kaplan Kapan, Kelle Avcısı, Balina Tuzağı, Işık Hızı Arbitrajı — canlı market durumundan skorlanır, en yüksek skor kazanır |
| 💰 **Kelly Criterion Sizing** | Win-rate ve R:R'e göre çeyrek-Kelly risk yüzdesi + pozisyon boyutu |
| 🕯️ **Flow Mumları (5s)** | Gerçek trade tape'inden inşa edilen basınç mumları; POC (Point of Control), footprint buy/sell cluster'ları |
| 💥 **Tasfiye Basıncı Skoru** | Havuz mesafesi ve büyüklüğüne göre 0-100 şelale riski göstergesi |
| 📡 **Çoklu Borsa Arbitraj Görünümü** | Binance/Bybit/OKX/MEXC fiyat sapmaları (bps) ve fırsat bayrakları |
| 🔐 **Biyometrik İnfaz Kalkanı** | Emir bota gönderilmeden önce parmak izi doğrulaması (fail-closed) |
| 🤖 **Python İnfaz Botu** | FastAPI + Binance Futures bracket order + trailing stop + Telegram bildirimi |
| 💾 **Oturum Kaydı & Replay** | Room DB'ye 1sn'lik kareler kaydedilir; backtest oynatıcıda fiyat/VPIN/derinlik canlandırılır |
| 🏠 **Canlı Home-Screen Widget** | Seçili sembol, fiyat, VPIN, strateji ve R:R bilgisi ~10sn'de bir güncellenir |
| 👁 **Renk Körlüğü Modu** | Okabe-Ito mavi/turuncu paleti tek dokunuşla tüm uygulamaya uygulanır |
| 🎯 **Hassasiyet Presetleri** | CONSERVATIVE / NORMAL / AGGRESSIVE — gerçek motor eşiklerini değiştirir |

---

## 🏗 Mimari Genel Bakış

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        BOZOK PRO v4.0 — ANDROID                           │
│                                                                          │
│  ┌─────────────┐   ┌──────────────┐   ┌───────────────────────────────┐  │
│  │  UI Katmanı │   │  ViewModel   │   │        Quant Motorları        │  │
│  │  (Compose)  │◄─►│ BozokViewModel│◄─►│ StrongWallDetector            │  │
│  │  7 Sekme    │   │  StateFlow   │   │ WallPullDetector (spoof)      │  │
│  │  Canvas'lar │   │  + Replay    │   │ IcebergDetector               │  │
│  └──────┬──────┘   └──────┬───────┘   │ StopHuntDetector (15dk pencere)│  │
│         │                 │           │ VpinCalculator (dinamik kova)  │  │
│         │                 │           │ LiquidationPoolSimulator       │  │
│         │                 │           │ CvdDivergenceDetector*         │  │
│         │                 │           │ FlowCandleBuilder*             │  │
│         │                 │           │ LiquidationPressureCalculator* │  │
│         │                 │           │ MetaStrategyEngine (4 yarış)   │  │
│         │                 │           │ MicroAccountOptimizer (Kelly)  │  │
│         │                 │           │ StrategyPerformanceTracker     │  │
│         └─────┐           │           └───────────────┬───────────────┘  │
│               │           │                           │                  │
│  ┌────────────┴──┐   ┌────┴─────────┐   ┌─────────────┴──────────────┐   │
│  │   Ağ Katmanı  │   │  Yerel Veri  │   │   Yerel Hizmetler           │   │
│  │ Binance WS    │   │ Room DB      │   │ Foreground Service (SOS)    │   │
│  │ Binance REST  │   │ Widget Push  │   │ Biometric Gate (fail-closed) │   │
│  │ Exec Bot HTTP │   │ JSON Ayar    │   │ Haptic Drag, PiP             │   │
│  └───────┬───────┘   └──────────────┘   └─────────────┬───────────────┘   │
└──────────┼────────────────────────────────────────────┼───────────────────┘
           ▼                                            ▼
  Binance Futures (fstream)                     Python İnfaz Botu
  @ticker / @depth20@100ms / @aggTrade          FastAPI + Binance API + Telegram
```

> `*` = v4.0 mimari dokümanında tanımlanıp repoda **eksik olan ve bu revizyonda yazılan** motorlar.

---

## 📡 Veri Kaynakları

| Kaynak | Tip | Kullanım | Detay |
|---|---|---|---|
| **Binance Futures WS** (`wss://fstream.binance.com/ws`) | WebSocket (canlı) | `SYMBOL@ticker` → anlık fiyat + 24s değişim (`P`); `SYMBOL@depth20@100ms` → 20 kademe derinlik; `SYMBOL@aggTrade` → birleşik işlem kaseti | Otomatik **exponential-backoff reconnect** (2sn→30sn); bağlantı yoksa açıkça işaretli **SIM modu** devreye girer |
| **Binance Futures REST** (`fapi.binance.com`) | HTTP | `GET /fapi/v1/exchangeInfo?symbol=X` → gerçek `tickSize` / `stepSize` | 5sn timeout + sembol bazlı cache + akıllı fallback (PEPE/SHIB/BTC/ETH/SOL heuristiği) |
| **Trade Tape (bellek içi)** | Akış | Son 30 işlem → VPIN, CVD, flow mumu beslemesi | `TradeTapeItem` kuyruğu; whale eşiği $50k |
| **Room DB** (`bozok_hft_db`) | SQLite | Oturum kareleri (1sn'lik): sembol, fiyat, VPIN, strateji, orderbook JSON | 500 kayıtla sınırlı; replay veri kaynağı |
| **Python İnfaz Botu** | HTTP webhook | `POST /webhook` (X-BOZOK-SECRET) → Binance bracket order | Emülatör `10.0.2.2:8000`, cihazda LAN IP |

---

## 🧩 Sınıf ve Motor Envanteri

### Veri Modelleri (`model/BozokModels.kt`)

| Model | Açıklama |
|---|---|
| `OrderbookState` | Sembol, mid fiyat, best bid/ask, spread (bps), bid/ask kademeleri, **24s değişim**, **SIM/LIVE bayrağı** |
| `OrderbookLevel` | Fiyat, miktar, notional, `isWall`, `isSpoof` bayrakları |
| `TradeTapeItem` | İşlem: fiyat, miktar, notional, alıcı/satıcı tarafı, whale eşiği |
| `VpinState` | VPIN %, seviye (DÜŞÜK/ORTA/TOKSİK), alış/satış hacimleri, toksisite bayrağı (**eşik: 60**) |
| `CvdState` | Kümülatif hacim deltası, divergans bayrağı, divergans yüzdesi |
| `LiquidationPool` | Kaldıraç (10x/25x/50x/100x), fiyat, tahmini notional, short/long havuz |
| `PatternSignal` | Sinyal: tip, başlık, yön, güven %, fiyat, kritiklik bayrağı |
| `MetaStrategyPlan` | Kazanan strateji, yön, entry/SL/TP1/TP2, R:R, Kelly %, pozisyon $, leverage, gerekçe, magnet bonusu |
| `FlowCandle` + `FootprintCluster` | Basınç mumu: OHLC, POC, hacimler, delta, fiyat kademesi cluster'ları |
| `StrategyPerformance` | Strateji karnesi: trade sayısı, win-rate, Net R, Profit Factor, Sharpe, form bonusu |
| `ArbitrageSkew` | Borsalar arası sapma (bps), öncü borsa, fırsat bayrağı |
| `SensitivityPreset` | CONSERVATIVE/NORMAL/AGGRESSIVE/CUSTOM — motor eşiklerini taşır (duvar $, stddev çarpanı, VPIN eşiği, iceberg çarpanı) |

### Dedektör & Motor Sınıfları (`engine/`)

| Sınıf | Görev | Önemli Algoritma |
|---|---|---|
| `WelfordAccumulator` | Çevrimiçi ortalama/std sapma | Sayısal olarak kararlı Welford (tek geçiş) |
| `StrongWallDetector` | Duvarları tespit et | Welford ortalama + `2.5×std` (preset'e göre) ve $ eşiği |
| `WallPullDetector` | Spoof tespiti | **Kademe-indeksi bazlı** önceki/şimdiki karşılaştırma (0.1% fiyat toleransı); duvar $100k+ → %20 altına düşünce sinyal; bellek sınırlı (40 kademe) |
| `IcebergDetector` | Gizli emir tespiti | Trade fiyatındaki gerçek görünür miktarın preset-çarpanı kadar (2.5-6x) işlem + $75k+ notional |
| `StopHuntDetector` | Tepe/dip süpürme | **Gerçek 15 dk kayar pencere** (zaman damgalı deque); tick×2 üzeri iğne → EQH/EQL sweep |
| `VpinCalculator` | Toksisite ölçümü | Hacim kovaları (sembole göre **dinamik**: BTC $2M, altcoin $500k, meme $100k); son 10 kovanın ortalama toksisitesi |
| `LiquidationPoolSimulator` | Havuz mıknatıs bandı | 100x/50x/25x × short/long simülasyon; **CVD yanlılığı**: alış baskısı short-havuzları büyütür |
| `MicroAccountOptimizer` | Pozisyon boyutlandırma | Quarter-Kelly, %1-8 aralığı, 20x kaldıraç varsayımı |
| `StrategyPerformanceTracker` | Strateji karnesi | Win-rate ≥%70 → +10 form bonusu |
| `MetaStrategyEngine` | Üst akıl | 4 stratejiyi market state'ten skorlar; VPIN toksisite +10, havuz yakınlığı +5 magnet, kazanan seçilir |
| `CvdDivergenceDetector` ⭐ | Akıllı para dağıtımı | 40 noktalık pencere; fiyat ↑ CVD ↓ (veya tersi) → `SMART_MONEY_DISTRIBUTION`; 30sn soğuma |
| `FlowCandleBuilder` ⭐ | Basınç mumu üretimi | 5sn kova: OHLC + footprint cluster (tick hizalı) + POC (maks hacim kademesi) |
| `LiquidationPressureCalculator` ⭐ | Şelale riski | Havuz mesafesi × notional ağırlığı → 0-100 skor |
| `SessionReplayEngine` | Geri oynatma | Kare indeksi, hız (0.5x-10x), seek; kareler arası gecikme `250ms/hız` |

> ⭐ = Bu revizyonda TODO dokümanındaki tanımlarından yola çıkılarak **sıfırdan yazılan** motorlar.

### Ağ Sınıfları (`network/`)

| Sınıf | Görev |
|---|---|
| `BinanceFuturesWebSocket` | Kombine WS akışı (ticker+depth+aggTrade), SIM fallback, auto-reconnect, 24s değişim parse |
| `ExchangeInfoRepository` | tickSize/stepSize REST çekimi + cache + sembol heuristik fallback |
| `ExecutionBotClient` | Python botuna webhook POST; **dürüst hata** (sahte başarı yok); `10.0.2.2:8000` varsayılan |

### Yerel Hizmetler (`native/`, `db/`, `widget/`)

| Sınıf | Görev |
|---|---|
| `BozokForegroundService` | Yüksek öncelikli HFT uyarı kanalı; SOS bildirimi + **Morse titreşim deseni** |
| `BiometricAuthHelper` | `androidx.biometric` tabanlı infaz kalkanı — **fail-closed** |
| `HapticDragHelper` | 15ms mekanik tik (45ms throttle) |
| `PiPController` | 16:9 Picture-in-Picture |
| `BozokDatabase` + `SessionDao` + `SessionEntity` | Room: oturum kareleri; `deleteOldest(500)` sınırı |
| `BozokWidgetReceiver` | Home-screen widget; `pushSnapshot()` ile canlı veri |

---

## 🦁 4 Avcı Strateji

Her strateji canlı market durumundan bir **güven skoru** alır; en yüksek skor kazanan stratejidir. Skorlara **form bonusu** (son dönem win-rate) ve **VPIN toksisite bonusu** (+10) eklenir.

### 1. 🔥 KAPLAN KAPAN — Spoof Trap & Void Sweep
- **Mantık:** Sahte satıcı duvarı (ask wall) aniden çekildiğinde, üstteki likidite boşluğu vakumlanır → **LONG**. Sahte alıcı duvarı çekilirse → **SHORT**.
- **Besleyen motorlar:** `WallPullDetector` (duvar çekilme sinyali), `StrongWallDetector` (aktif duvarlar).
- **Bonus:** Aktif duvar varlığında +18; TP1 yakınında 25x havuz varsa +5 **magnet bonusu**.

### 2. 🩸 KELLE AVCISI — Liquidation Cascade Reversal
- **Mantık:** Tasfiye şelalesi tükendiğinde gizli absorpsiyonla ters dönüş. Fiyatın 25x havuzlarına olan mesafesi ölçülür; yakın olan taraf (long/short) seçilir.
- **Besleyen motorlar:** `LiquidationPoolSimulator`, `LiquidationPressureCalculator`.
- **Bonus:** Havuz < %1.5 uzaktaysa +22, < %3 uzaktaysa +12; düşük VPIN'de +8.

### 3. 🐋 BALİNA TUZAĞI — Smart Money Distribution Scalp
- **Mantık:** Fiyat yükselirken CVD düşüyorsa retail alıyor, balina dağıtıyor demektir → **SHORT**. Ters durumda **LONG**.
- **Besleyen motorlar:** `CvdDivergenceDetector`, `IcebergDetector`.
- **Bonus:** Divergans tespitinde +20.

### 4. ⚡ IŞIK HIZI ARBİTRAJI — Latency Front-Running
- **Mantık:** Borsalar arası fiyat sapması `≥ 8 bps` olduğunda öncü borsanın yönünde milisaniyelik ön koşu.
- **Besleyen motorlar:** `ArbitrageSkew` listesi (MARKETS sekmesi).
- **Bonus:** Sapma bps'i kadar +30'a kadar ek.

### Kazanan Plan Çıktısı
Kazanan strateji; yön, `tick × 14` stop, `tick × 32` TP1, `tick × 52` TP2, gerçek R:R, **Quarter-Kelly** risk %'si ve pozisyon boyutu ($) ile birlikte `MetaStrategyPlan` olarak UI'a düşer. Kullanıcı çizgileri sürükleyerek planı elle değiştirebilir (Drag-To-Trade).

---

## ⚙️ Otomatik İşlem Kurgusu (Pipeline)

```
[Borsa WS Akışı] ──► [VPINCalculator] ──► [LiquidationPoolSimulator]
        │                    │                      │
        ▼                    ▼                      ▼
 [StrongWallDetector]  [CvdDivergenceDetector]  [LiquidationPressure]
        │                    │                      │
        └─────────► [MetaStrategyEngine] ◄── [StrategyPerformanceTracker Bonus]
                          │
                          ▼
              [TradePlanGenerator (Entry/SL/TP1/TP2 + Kelly)]
                          │
                          ▼
             [Drag-To-Trade Canvas (kullanıcı onayı)]
                          │
                          ▼
              [Biyometrik İnfaz Kalkanı (fail-closed)]
                          │
                          ▼
        [Webhook JSON Payload] ──► [bozok_execution_bot.py]
                                          │
                                          ▼
                        [Binance Futures API] ──► [Telegram Bildirimi]
```

1. **Toplama:** Binance WS akışı (ticker/depth/aggTrade) 100ms'lik kademelerle çekilir.
2. **Toksisite:** `VpinCalculator` piyasa toksisitesini ölçer; havuzlar CVD yanlılığıyla çizilir.
3. **Tarama:** Duvar/spoof/iceberg/stop-hunt dedektörleri 250ms'lik quant döngüsünde çalışır; flow mumları her 5sn'de tape'ten inşa edilir.
4. **Üst Akıl:** `MetaStrategyEngine` 4 stratejiyi yarıştırır; form bonusu + magnet bonusu uygulanır.
5. **Onay:** Kullanıcı canvas üzerinde SL/TP çizgilerini sürükler; payload anında güncellenir.
6. **İnfaz:** Biyometrik doğrulama → webhook JSON → Python botu Binance'e bracket order basar, Telegram'a rapor fırlatır.

---

## 📱 Uygulama Ekranları (7 Sekme)

| Sekme | İçerik |
|---|---|
| 📊 **BOOK** | 16×24 derinlik ısı haritası (Likidite/Hız/İşlemler/Duvarlar/Liq Havuzları katmanları), orta fiyat çizgisi, liq havuzu mıknatıs bantları, **Drag-To-Trade** (Entry/SL/TP1), strateji plan kartı, mikro scalp hesaplayıcı |
| 🕯️ **FLOW** | 5sn'lik basınç mumları: wick/gövde, footprint buy-sell mikro barlar, **Altın POC çizgisi**, açılır/kapanır lejant |
| 📚 **DEPTH** | OBI göstergesi, CVD kartı + sparkline, fiyat-CVD divergans rozeti, **tasfiye basıncı göstergesi (0-100)**, bid/ask merdiveni (miktar/fiyat/notional), mid fiyat & spread barı |
| 🎯 **SIGNALS** | Sinyal akış kartları (yön/VPIN/liq filtreleri, "✓ onaylı", "⭐ yüksek"), kritik sinyal rozetleri |
| 📍 **LEVELS** | Meta-analiz piyasa yorumu kartı (narrative), equity eğrisi (Net R), strateji plan kartı, mikro optimizör |
| 🌐 **MARKETS** | Veri kalite skoru, borsa kaynak durumu, Binance/Bybit/OKX/MEXC arbitraj sapma listesi |
| ⚙️ **SETTINGS** | Replay oynatıcı (kayıt/oynat/hız/seek), tema + renk körlüğü, book modu, **hassasiyet presetleri (motora bağlı)**, flow periyodu/modu, mikro bakiye & Kelly girdileri, JSON ayar dışa aktarımı |

---

## 🤖 Python İnfaz Botu

`python/bozok_execution_bot.py` — Android uygulamasının webhook'u konuştuğu uç. Mimari dokümanda tanımlanıp eksik olan bu modül bu revizyonda yazıldı.

### Özellikler
- **`POST /webhook`** — `X-BOZOK-SECRET` doğrulaması, zaman damgası replay koruması (±60sn), kaldıraç limiti (varsayılan 5x), notional limiti (varsayılan $200)
- **Bracket Order** — MARKET entry + `STOP_MARKET` (SL) + `TAKE_PROFIT_MARKET` ×2 (TP1/TP2, ikinci yarı miktarla)
- **Trailing Stop görevi** — 5sn'de bir mark price kontrolü: kâr ≥ 0.5R → stop `entry+0.25×TP`, kâr ≥ 1R → stop `entry+0.75×TP` (`PUT /fapi/v1/order`)
- **Telegram** — İnfaz başarı/hata raporları
- **`GET /status`** — bot bağlantı durumu + aktif pozisyonlar (Android BotStatus modeliyle uyumlu)
- **Testnet desteği** — `BOZOK_TESTNET=1` varsayılan (önerilir!)

### Kurulum & Çalıştırma
```bash
cd python
pip install fastapi uvicorn aiohttp python-dotenv
cp .env.example .env   # BINANCE_API_KEY, BINANCE_API_SECRET, TELEGRAM_TOKEN doldur
python bozok_execution_bot.py
# → http://0.0.0.0:8000/webhook dinler
```
> Android emülatör bot'a `10.0.2.2:8000` ile, gerçek cihaz LAN IP'si ile ulaşır. Cihaz testi için `usesCleartextTraffic=true` manifest'te açıktır.

---

## 🧰 Teknoloji Yığını

### Android
| Bileşen | Sürüm |
|---|---|
| Kotlin | 2.2.10 |
| Android Gradle Plugin | 9.1.1 |
| Compose BOM | 2024.09.00 (Material3) |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| Room (KSP) | 2.7.0 |
| OkHttp / Retrofit / Moshi | 4.10.0 / 2.12.0 / 1.15.2 |
| Coroutines | 1.10.2 |
| androidx.biometric | 1.1.0 |
| Robolectric / Roborazzi (test) | 4.16.1 / 1.59.0 |
| Firebase BOM (Gemini AI opsiyonel) | 34.15.0 |

### Python Bot
FastAPI · uvicorn · aiohttp · pydantic · Binance Futures API (testnet/mainnet)

---

## 🔨 Derleme ve Çalıştırma

```bash
# 1) Repoyu klonla
git clone https://github.com/ahmetbysoy/bozok.git
cd bozok

# 2) Debug APK üret (Gradle wrapper dahildir — imzalama sorunsuz)
./gradlew assembleDebug
# Çıktı: app/build/outputs/apk/debug/app-debug.apk

# 3) Testler
./gradlew testDebugUnitTest

# 4) Android Studio
#    "Open" → bozok klasörü → Gradle sync → Run ▶
```

> **Not:** Release imzası için `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` ortam değişkenleri gerekir; verilmezse debug anahtarıyla imzalanır (geliştirme için yeterli).
> `.env` dosyası yoksa Secrets plugin sessizce devre dışı kalır — Gemini AI dışındaki her şey anahtarsız çalışır.

---

## 🧪 Testler

`app/src/test/java/com/example/engine/QuantEngineTest.kt` — **11 test, hepsi geçiyor ✅**

| Test | Doğruladığı |
|---|---|
| `testStopHuntSweepDetection` | EQH sweep sinyali üretimi, yön, kritiklik |
| `testStopHuntWindowExpiresOldLevels` | 15 dk pencerenin eski seviyeleri düşürmesi (regresyon) |
| `testWallPullDetectorDetectsAskSpoof` | Kademe-bazlı spoof tespiti; ask duvarı çekilince LONG |
| `testVpinToxicityCalculator` | Kova birikimi + toksisite yüzdesi |
| `testLiquidationPoolSimulator` | 6 havuz + CVD yanlılığı (alış baskısı → short havuz büyür) |
| `testMicroAccountKellyOptimizer` | Kelly % aralığı (1-8), pozitif pozisyon |
| `testMetaStrategyEnginePicksKaplanWhenAskWallPresent` | Ask duvarı varken Kaplan Kapan kazanır ve LONG'dur |
| `testMetaStrategyEnginePicksKelleWhenNoWall` | Duvarsız ortamda farklı stratejiler yarışabilir |
| `testCvdDivergenceDetector` | Fiyat ↑ / CVD ↓ → divergans bayrağı |
| `testFlowCandleBuilderRollsOver` | 5sn kova dönüşü, OHLC, footprint cluster |
| `testLiquidationPressureCalculator` | 0-100 skor aralığı |

Ek olarak: `ExampleRobolectricTest`, `GreetingScreenshotTest` (Roborazzi görüntü testi), `ExampleInstrumentedTest`.

---

## 🔒 Güvenlik Tasarımı

- **Fail-closed biyometri:** Parmak izi doğrulaması yoksa veya hata olursa infaz **engellenir** — hiçbir hata kodu sessizce onaylamaz.
- **Webhook koruması:** `X-BOZOK-SECRET` başlığı + zaman damgası kontrolü (replay koruması) + kaldıraç/notional tavanları.
- **Dürüst hata bildirimi:** Bot erişilemezse uygulama sahte başarı göstermez; "İNFAZ BAŞARILI" bildirimi yalnızca bot gerçekten onayladığında çıkar.
- **Veri gizliliği:** Tüm analiz cihazda; yalnızca kullanıcının onayladığı webhook dışarı gider.
- **SIM modu şeffaflığı:** Canlı bağlantı yoksa arayüzde "SIM" rozeti görünür; sahte veri gerçekmiş gibi sunulmaz.
- **Token kullanımı:** Bu projede sabitlenmiş gizli anahtar yoktur; tüm sırlar `.env`/ortam değişkeninden gelir.

---

## 🗺 Bilinen Sınırlamalar ve Yol Haritası

**Mevcut sınırlamalar:**
- Arbitraj verileri (Bybit/OKX/MEXC) simülasyon tabanlıdır; canlı çoklu borsa WS bağlantıları için API anahtarları + ek modüller gerekir.
- `NarrativeEngine` (Türkçe otomatik anlatı üretimi) mimari dokümanda tanımlıdır, henüz ayrı bir modül olarak yazılmamıştır (şu an plan gerekçesi kullanılıyor).
- Gemini AI entegrasyonu build'de hazırdır ancak `.env`'de anahtar gerektirir; opsiyoneldir.
- Web Shell sürümündeki sesli anons (TTS) Android'de henüz yok.

**Yol haritası (mimari dokümandan):**
- [ ] Web Worker / multi-threading eşdeğeri: quant döngüsünün `QuantWorker` gibi ayrı işlemlere bölünmesi (Android'de `Process`/`Service` tabanlı)
- [ ] Canlı likidasyon verisi (`forceOrder` WS + REST telafi)
- [ ] VPIN kova boyutunun 24s ortalama hacme göre tam adaptif hale getirilmesi
- [ ] Trailing stop'un WebSocket ticker üzerinden milisaniye hassasiyetinde güncellenmesi
- [ ] Circuit Breaker: max drawdown %15 → otomatik risk yarılaması
- [ ] NarrativeEngine: sinyal setinden Türkçe piyasa anlatısı üretimi

---

## 📄 Lisans ve Notlar

- **Eğitim/araştırma amaçlıdır.** Bu yazılım finansal tavsiye değildir; kaldıraçlı kripto işlemleri yüksek risk içerir. Gerçek para kullanımı tamamen kullanıcının sorumluluğundadır.
- Proje, BOZOK PRO v4.0 mimari ve TODO dokümanına (`BOZOK_PRO_V4_ARCHITECTURE_AND_TODO.md`) dayanır.
- Analiz ve düzeltme geçmişi: `BOZOK_ANALIZ_RAPORU.md` (26 bulgu, 27 dosya, +2100 satır değişiklik).
