package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.pattern.AppState
import com.example.engine.strategy.MicroAccountOptimizer
import com.example.model.*
import com.example.ui.theme.*

/* ============================================================================
 * BOOK SEKME — ısı haritası (katmanlar: likidite/hız/işlemler/duvarlar/liq
 * havuzları), pattern etiketleri, CurrentBook kolonu, plan + DRAG-TO-TRADE,
 * tasfiye basıncı, mikro kart
 * ========================================================================== */

@Composable
fun BookTab(
    book: Book,
    liqPools: List<LiquidationPool>,
    plan: TradePlan?,
    liqPressure: Double,
    focusPrice: Double?,
    config: AppConfig,
    microResult: MicroAccountOptimizer.MicroResult?,
    onExecute: () -> Unit,
    onDragPrices: (stop: Double?, tp1: Double?, tp2: Double?) -> Unit,
    onTapPrice: (Double?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HeatmapCanvas(
            book = book, liqPools = liqPools, plan = plan, liqPressure = liqPressure,
            focusPrice = focusPrice, onDragPrices = onDragPrices, onTapPrice = onTapPrice
        )
        PlanCard(plan = plan, onExecute = onExecute)
        MicroScalpCard(result = microResult, config = config, plan = plan)
    }
}

@Composable
fun HeatmapCanvas(
    book: Book,
    liqPools: List<LiquidationPool>,
    plan: TradePlan?,
    liqPressure: Double,
    focusPrice: Double?,
    onDragPrices: (Double?, Double?, Double?) -> Unit,
    onTapPrice: (Double?) -> Unit
) {
    var showLiq by remember { mutableStateOf(true) }
    var showVel by remember { mutableStateOf(true) }
    var showTrades by remember { mutableStateOf(true) }
    var showWalls by remember { mutableStateOf(true) }
    var showPools by remember { mutableStateOf(true) }
    var showPlan by remember { mutableStateOf(true) }
    var showPatterns by remember { mutableStateOf(true) }

    var dragLine by remember { mutableStateOf<String?>(null) } // SL|TP1|TP2
    var dragAcc by remember { mutableStateOf(0f) }

    val mid = book.mid ?: 0.0
    val heat = AppState.heatHistory.toList()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            // Katman barı (HTML .layerBar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel2)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LayerBtn("Likidite", showLiq) { showLiq = !showLiq }
                LayerBtn("Hız", showVel) { showVel = !showVel }
                LayerBtn("İşlemler", showTrades) { showTrades = !showTrades }
                LayerBtn("Duvarlar", showWalls) { showWalls = !showWalls }
                LayerBtn("🧲 Havuzlar", showPools) { showPools = !showPools }
                LayerBtn("📏 Plan", showPlan) { showPlan = !showPlan }
                LayerBtn("🏷 Desen", showPatterns) { showPatterns = !showPatterns }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (plan == null || plan.direction == Direction.NEUTRAL) return@detectDragGestures
                                val h = size.height
                                // Çizgi yakalama: plan çizgilerinin Y'lerini hesapla
                                val (slY, tp1Y, tp2Y) = planLinesY(plan, mid, h)
                                dragLine = when {
                                    tp1Y != null && kotlin.math.abs(offset.y - tp1Y) <= 18f -> "TP1"
                                    tp2Y != null && kotlin.math.abs(offset.y - tp2Y) <= 18f -> "TP2"
                                    slY != null && kotlin.math.abs(offset.y - slY) <= 18f -> "SL"
                                    else -> null
                                }
                                dragAcc = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (dragLine == null) return@detectDragGestures
                                dragAcc += dragAmount.y
                                val price = yToPrice(dragAcc, mid, size.height)
                                when (dragLine) {
                                    "SL" -> onDragPrices(price, null, null)
                                    "TP1" -> onDragPrices(null, price, null)
                                    "TP2" -> onDragPrices(null, null, price)
                                }
                            },
                            onDragEnd = { dragLine = null },
                            onDragCancel = { dragLine = null }
                        )
                    }
                    .clickable {
                        // Tıkla-odakla: dokunulan fiyatı focus yap
                        // (pointerInput içinde tap koordinatı almak zor; basit: focus null)
                        onTapPrice(null)
                    }
            ) {
                val w = size.width
                val h = size.height
                if (mid <= 0) {
                    drawRect(Panel)
                    return@Canvas
                }

                // Fiyat aralığı (HTML: maxDist*1.65)
                val visible = book.bids.take(10) + book.asks.take(10)
                val maxDist = maxOf(Fmt.tickSizeFor(mid) * 24, visible.maxOfOrNull { kotlin.math.abs(it.price - mid) } ?: Fmt.tickSizeFor(mid) * 24)
                val priceRange = maxOf(maxDist * 1.65, Fmt.tickSizeFor(mid) * 24)
                val priceTop = mid + priceRange
                val priceBot = mid - priceRange
                fun yOf(p: Double): Float = (h * (1 - (p - priceBot) / (priceTop - priceBot))).toFloat()

                // ---- 1) Likidite katmanı: 60sn heatmap (zaman ekseni) ----
                if (showLiq && heat.isNotEmpty()) {
                    val winMs = AppState.config.heatmapWindowSec * 1000L
                    val colW = w / heat.size.toFloat()
                    var maxQty = 1.0
                    for (snap in heat) if (snap.maxQty > maxQty) maxQty = snap.maxQty

                    heat.forEachIndexed { i, snap ->
                        for (lvl in snap.bids) {
                            val y = yOf(lvl.price)
                            if (y < 0 || y > h) continue
                            val alpha = (0.05f + (lvl.qty / maxQty * 0.55f).toFloat()).coerceIn(0.04f, 0.6f)
                            drawRect(Bull.copy(alpha = alpha), Offset(i * colW, y - 1f), Size(colW + 1f, 3f))
                        }
                        for (lvl in snap.asks) {
                            val y = yOf(lvl.price)
                            if (y < 0 || y > h) continue
                            val alpha = (0.05f + (lvl.qty / maxQty * 0.55f).toFloat()).coerceIn(0.04f, 0.6f)
                            drawRect(Bear.copy(alpha = alpha), Offset(i * colW, y - 1f), Size(colW + 1f, 3f))
                        }
                    }
                    // Zaman damgası
                    drawContext.canvas.nativeCanvas.drawText(
                        "${winMs / 1000}s", 6f, h - 8f,
                        android.graphics.Paint().apply { color = TextFaint.toArgb(); textSize = 18f }
                    )
                }

                // ---- 2) Hız katmanı: yakın geçmiş fiyat hareketi genliği ----
                if (showVel && heat.size >= 3) {
                    val recent = heat.takeLast(6).mapNotNull { it.bids.firstOrNull()?.price ?: it.asks.firstOrNull()?.price }
                    if (recent.size >= 3) {
                        val range = recent.max() - recent.min()
                        val strength = (range / mid * 1000).coerceIn(0.0, 5.0) // 0..5 (tick bazlı)
                        val alpha = (strength / 5.0 * 0.5f).toFloat()
                        drawRect(Accent.copy(alpha = alpha.coerceIn(0.05f, 0.5f)), Offset(0f, 0f), Size(w, h))
                    }
                }

                // ---- 3) İşlemler katmanı: son trade'ler (nokta) ----
                if (showTrades) {
                    val tNow = System.currentTimeMillis()
                    for (tr in AppState.trades.takeLast(60)) {
                        if (tNow - tr.timestamp > 30_000) continue
                        val age = (tNow - tr.timestamp) / 30_000.0
                        val x = w * (1 - age).toFloat()
                        val y = yOf(tr.price)
                        if (y < 0 || y > h) continue
                        drawCircle(
                            color = (if (tr.side == Side.BUY) Bull else Bear).copy(alpha = 0.7f),
                            radius = 2.5f, center = Offset(x, y)
                        )
                    }
                }

                // ---- 4) Likidite havuzu mıknatıs bantları ----
                if (showPools) {
                    for (pool in liqPools) {
                        val py = yOf(pool.price)
                        if (py < 24 || py > h - 24) continue
                        val col = if (pool.side == "long") PoolLong else PoolShort
                        drawRect(col.copy(alpha = 0.15f), Offset(0f, py - 5f), Size(w, 10f))
                        drawLine(col, Offset(0f, py), Offset(w, py), strokeWidth = 1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                    }
                }

                // ---- 5) Pattern etiketleri + odak çizgisi ----
                if (showPatterns) {
                    for (sig in AppState.patternHistory.takeLast(8)) {
                        if (kotlin.math.abs(sig.price - mid) > priceRange * 1.35) continue
                        val py = yOf(sig.price)
                        if (py < 10 || py > h - 10) continue
                        val col = when (sig.bias) { Bias.BULL -> Bull; Bias.BEAR -> Bear; Bias.WARN -> Signal; Bias.NEUTRAL -> Accent }
                        drawLine(col.copy(alpha = 0.6f), Offset(0f, py), Offset(w, py), strokeWidth = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f)))
                    }
                }

                // ---- 6) Odak fiyat çizgisi ----
                focusPrice?.let { fp ->
                    val fy = yOf(fp)
                    if (fy > 0 && fy < h) {
                        drawLine(Accent.copy(alpha = 0.9f), Offset(0f, fy), Offset(w, fy), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
                    }
                }

                // ---- 7) Orta fiyat çizgisi ----
                val midY = h / 2f
                drawLine(TextPrimary.copy(alpha = 0.8f), Offset(0f, midY), Offset(w, midY), strokeWidth = 1.5f)
                drawContext.canvas.nativeCanvas.drawText(
                    Fmt.price(mid), w - 90f, midY - 6f,
                    android.graphics.Paint().apply { color = TextDim.toArgb(); textSize = 20f }
                )

                // ---- 8) Plan çizgileri (drag-to-trade hedefleri) ----
                if (showPlan && plan != null && plan.direction != Direction.NEUTRAL) {
                    val slY = plan.stopLoss?.let { yOf(it.low) }
                    val tp1Y = plan.tp1?.let { yOf(it.high) }
                    val tp2Y = plan.tp2?.let { yOf(it.high) }

                    drawLine(Accent, Offset(0f, yOf(plan.entryMid)), Offset(w, yOf(plan.entryMid)), strokeWidth = 3f)
                    if (slY != null) {
                        drawLine(Bear, Offset(0f, slY), Offset(w, slY), strokeWidth = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                        drawContext.canvas.nativeCanvas.drawText(
                            "SL " + (plan.stopLoss?.low?.let { Fmt.price(it) } ?: ""), 6f, slY + 18f,
                            android.graphics.Paint().apply { color = Bear.toArgb(); textSize = 20f; isFakeBoldText = true }
                        )
                    }
                    if (tp1Y != null) {
                        drawLine(Bull, Offset(0f, tp1Y), Offset(w, tp1Y), strokeWidth = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                        drawContext.canvas.nativeCanvas.drawText(
                            "TP1 " + (plan.tp1?.high?.let { Fmt.price(it) } ?: ""), 6f, tp1Y + 18f,
                            android.graphics.Paint().apply { color = Bull.toArgb(); textSize = 20f; isFakeBoldText = true }
                        )
                    }
                    if (tp2Y != null) {
                        drawLine(Violet, Offset(0f, tp2Y), Offset(w, tp2Y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                        drawContext.canvas.nativeCanvas.drawText(
                            "TP2 " + (plan.tp2?.high?.let { Fmt.price(it) } ?: ""), 6f, tp2Y + 18f,
                            android.graphics.Paint().apply { color = Violet.toArgb(); textSize = 20f; isFakeBoldText = true }
                        )
                    }
                }

                // ---- 9) Duvarlar ----
                if (showWalls) {
                    val wallQty = maxOf(1.0, (book.bids + book.asks).maxOfOrNull { it.notional } ?: 1.0)
                    for (lvl in book.bids.take(10)) {
                        if (lvl.notional < wallQty * 0.6) continue
                        drawRect(Bull.copy(alpha = 0.3f), Offset(0f, yOf(lvl.price) - 2f), Size(w, 4f))
                    }
                    for (lvl in book.asks.take(10)) {
                        if (lvl.notional < wallQty * 0.6) continue
                        drawRect(Bear.copy(alpha = 0.3f), Offset(0f, yOf(lvl.price) - 2f), Size(w, 4f))
                    }
                }
            }

            // Alt HUD: tasfiye basıncı + kitap özeti (HTML heatmapHud)
            Row(
                Modifier.fillMaxWidth().background(Panel2).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💥 ${"%.0f".format(liqPressure)}/100", color = when { liqPressure >= 60 -> Bear; liqPressure >= 35 -> Signal; else -> Bull }, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${book.bids.size}b/${book.asks.size}a · OBI ${"%.0f".format(book.obi)}%", color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(
                    if (AppState.isStale()) "⚠️ STALE" else "SYNC",
                    color = if (AppState.isStale()) Signal else Bull, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Plan çizgilerinin canvas Y koordinatları (drag yakalama için). */
private fun planLinesY(plan: TradePlan, mid: Double, h: Float): Triple<Float?, Float?, Float?> {
    val visible = AppState.book.bids.take(10) + AppState.book.asks.take(10)
    val maxDist = maxOf(Fmt.tickSizeFor(mid) * 24, visible.maxOfOrNull { kotlin.math.abs(it.price - mid) } ?: Fmt.tickSizeFor(mid) * 24)
    val priceRange = maxOf(maxDist * 1.65, Fmt.tickSizeFor(mid) * 24)
    val priceTop = mid + priceRange
    val priceBot = mid - priceRange
    fun yOf(p: Double): Float = (h * (1 - (p - priceBot) / (priceTop - priceBot))).toFloat()
    return Triple(
        plan.stopLoss?.let { yOf(it.low) },
        plan.tp1?.let { yOf(it.high) },
        plan.tp2?.let { yOf(it.high) }
    )
}

private fun yToPrice(dragAcc: Float, mid: Double, h: Float): Double {
    val visible = AppState.book.bids.take(10) + AppState.book.asks.take(10)
    val maxDist = maxOf(Fmt.tickSizeFor(mid) * 24, visible.maxOfOrNull { kotlin.math.abs(it.price - mid) } ?: Fmt.tickSizeFor(mid) * 24)
    val priceRange = maxOf(maxDist * 1.65, Fmt.tickSizeFor(mid) * 24)
    // 1dp ≈ 1px sürükleme; fiyat değişimi = -dragAcc/h * priceRange
    val delta = -(dragAcc / h) * priceRange
    val price = mid + delta
    return Fmt.roundToTick(price, Fmt.tickSizeFor(mid))
}

@Composable
private fun LayerBtn(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Accent else Bg)
            .border(1.dp, if (active) Accent else Border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = if (active) Bg else TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/* ==================== PLAN KARTI ==================== */

@Composable
fun PlanCard(plan: TradePlan?, onExecute: () -> Unit) {
    if (plan == null) return
    val dirColor = if (plan.direction == Direction.LONG) Bull else if (plan.direction == Direction.SHORT) Bear else Signal

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, dirColor.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(dirColor).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("${plan.direction.name} · %${plan.confidence}", color = Bg, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Text(plan.webhookPayload["strategyName"] as? String ?: "PLAN", color = TextDim, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlanMetric("Entry", if (plan.direction == Direction.NEUTRAL) "—" else "$${"%,.2f".format(plan.entryMid)}")
                PlanMetric("SL", plan.stopLoss?.let { "$${"%,.2f".format(it.low)}" } ?: "—")
                PlanMetric("TP1", plan.tp1?.let { "$${"%,.2f".format(it.high)}" } ?: "—")
                PlanMetric("TP2", plan.tp2?.let { "$${"%,.2f".format(it.high)}" } ?: "—")
                PlanMetric("R:R", "1:${"%.1f".format(plan.riskReward1)}")
                if (plan.trailingStop.active) PlanMetric("Trail", "✅")
            }

            Spacer(Modifier.height(8.dp))
            Text(plan.reasoning, color = TextDim, fontSize = 10.5.sp, lineHeight = 14.sp)

            if (plan.direction != Direction.NEUTRAL) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onExecute,
                    colors = ButtonDefaults.buttonColors(containerColor = dirColor, contentColor = Bg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔥 İNFAZ ET (Biyometrik Onay)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PlanMetric(label: String, value: String) {
    Column {
        Text(label, color = TextDim, fontSize = 9.sp)
        Text(value, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

/* ==================== MİKRO KART ==================== */

@Composable
fun MicroScalpCard(result: MicroAccountOptimizer.MicroResult?, config: AppConfig, plan: TradePlan?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("💰 MICRO-SCALP OPTİMİZÖR (Bakiye $${"%.1f".format(config.microBalance)})", color = GoldPoc, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (result == null || plan == null || plan.direction == Direction.NEUTRAL) {
                Text("Aktif plan yok — nötr piyasa.", color = TextDim, fontSize = 11.sp)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MicroMetric("Risk", "$${"%.2f".format(result.riskAmountUsd)}")
                    MicroMetric("Kaldıraç", "${result.recommendedLeverage}x")
                    MicroMetric("Pozisyon", "$${"%.0f".format(result.positionNotionalUsd)}")
                    MicroMetric("Marj", "$${"%.2f".format(result.requiredMarginUsd)}")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (result.isTradable) "✅ İşleme uygun (marj yeterli)"
                    else "⚠️ Marj yetersiz — min stop ${"%.2f".format((result.minStopPct ?: 0.0) * 100)}%",
                    color = if (result.isTradable) Bull else Signal, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Break-even: " + Fmt.price(result.breakEvenPrice) + " · Tahmini liq: " + Fmt.price(result.estLiquidationPrice) + " · Fee: $" + "%.2f".format(result.feeCostUsd),
                    color = TextDim, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MicroMetric(label: String, value: String) {
    Column {
        Text(label, color = TextDim, fontSize = 9.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}
