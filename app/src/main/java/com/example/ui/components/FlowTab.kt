package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

/* ============================================================================
 * FLOW — basınç mumları (footprint + POC + likidasyon overlay) + HUD + lejant
 * ========================================================================== */

private val eventPaint = android.graphics.Paint().apply { textSize = 18f }

@Composable
fun FlowTab(candles: List<FlowCandle>, plan: TradePlan?, onExecute: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowCanvas(candles)
        if (plan != null) PlanCard(plan = plan, onExecute = onExecute)
    }
}

@Composable
fun FlowCanvas(candles: List<FlowCandle>) {
    var showLegend by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            // HUD başlığı (HTML flowHud)
            Row(
                Modifier.fillMaxWidth().background(Panel2).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val live = candles.lastOrNull()
                Text(
                    "FLOW · " + (live?.let { if (it.isLive) "canlı" else "kapalı" } ?: "—") +
                        " · liq " + (live?.totalLiquidation?.let { if (it > 50_000) "${"%.0f".format(it / 1000)}k" else "—" } ?: "—"),
                    color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(if (showLegend) Border else Bg)
                        .clickable { showLegend = !showLegend }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(if (showLegend) "Lejant (Gizle)" else "Lejant (Göster)", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                if (candles.isEmpty()) return@Canvas
                val w = size.width
                val h = size.height

                val maxP = candles.maxOf { it.high }
                val minP = candles.minOf { it.low }
                val range = if (maxP > minP) maxP - minP else 1.0

                val cw = (w / candles.size) * 0.72f
                val spacing = (w / candles.size) * 0.28f

                candles.forEachIndexed { i, c ->
                    val x = i * (cw + spacing) + spacing / 2f
                    fun yOf(v: Double): Float = (h - ((v - minP) / range * h)).toFloat()

                    val color = when (c.direction) {
                        "bullish" -> FlowBull
                        "bearish" -> FlowBear
                        else -> FlowNeutral
                    }

                    // Fitil
                    drawLine(color, Offset(x + cw / 2, yOf(c.high)), Offset(x + cw / 2, yOf(c.low)), strokeWidth = 2f)

                    // Gövde
                    val top = yOf(maxOf(c.open, c.close))
                    val bodyH = maxOf(4f, kotlin.math.abs(yOf(c.open) - yOf(c.close)))
                    drawRect(color, Offset(x, top), Size(cw, bodyH))

                    // POC (altın)
                    drawLine(GoldPoc, Offset(x - 2f, yOf(c.poc)), Offset(x + cw + 2f, yOf(c.poc)), strokeWidth = 3.5f)

                    // Footprint
                    if (c.footprint.isNotEmpty()) {
                        val maxCell = maxOf(1.0, c.footprint.values.maxOf { maxOf(it.buy, it.sell) })
                        c.footprint.forEach { (price, cell) ->
                            val cy = yOf(price)
                            val buyW = (cell.buy / maxCell * cw * 0.45f).toFloat()
                            val sellW = (cell.sell / maxCell * cw * 0.45f).toFloat()
                            if (buyW > 0.5f) drawRect(FlowBull.copy(alpha = 0.7f), Offset(x, cy - 1.5f), Size(buyW, 3f))
                            if (sellW > 0.5f) drawRect(FlowBear.copy(alpha = 0.7f), Offset(x + cw - sellW, cy - 1.5f), Size(sellW, 3f))
                        }
                    }

                    // Olay ikonları
                    if (c.events.isNotEmpty()) {
                        drawContext.canvas.nativeCanvas.drawText(
                            c.events.take(2).joinToString("") { it.icon }, x, yOf(c.high) - 6f, eventPaint
                        )
                    }
                }
            }

            // Lejant
            if (showLegend) {
                Row(
                    Modifier.fillMaxWidth().background(Panel2).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegendDot(FlowBull, "Alıcı footprint")
                    LegendDot(FlowBear, "Satıcı footprint")
                    LegendDot(GoldPoc, "Altın POC")
                    LegendDot(FlowNeutral, "Nötr")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color = TextDim, fontSize = 9.sp)
    }
}
