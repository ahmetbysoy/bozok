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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

/* ============================================================================
 * DEPTH — OBI/CVD gauge + tasfiye basıncı + son tasfiyeler + merdiven
 * (kademeye dokununca BOOK'ta odaklan — HTML B: ladder→focus)
 * ========================================================================== */

@Composable
fun DepthTab(
    book: Book,
    cvd: Double,
    liqPressure: Double,
    liquidations: List<LiquidationEvent>,
    onFocusPrice: (Double) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Panel)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
                Column(Modifier.padding(8.dp)) {
                    Text("OBI (Book Imbalance)", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${if (book.obi >= 0) "+" else ""}${"%.1f".format(book.obi)}%",
                        color = if (book.obi >= 0) Bull else Bear, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Border)) {
                        val left = ((50f + book.obi.toFloat() / 2f) / 100f).coerceIn(0.05f, 0.95f)
                        Box(Modifier.weight(left).fillMaxHeight().background(Bull))
                        Box(Modifier.weight(1f - left).fillMaxHeight().background(Bear))
                    }
                }
            }
            Card(modifier = Modifier.weight(1.2f), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
                Column(Modifier.padding(8.dp)) {
                    Text("CVD (Tape Delta)", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${if (cvd >= 0) "+" else ""}${"%.0f".format(cvd)}",
                        color = if (cvd >= 0) Bull else Bear, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    CvdSparkline(cvd)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("💥 Tasfiye Basıncı (Cascade)", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${"%.0f".format(liqPressure)}/100",
                        color = when { liqPressure >= 60 -> Bear; liqPressure >= 35 -> Signal; else -> Bull },
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Border)) {
                    Box(Modifier.fillMaxWidth((liqPressure / 100f).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(if (liqPressure >= 60) Bear else if (liqPressure >= 35) Signal else Bull))
                }
            }
        }

        if (liquidations.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
                Column(Modifier.padding(10.dp)) {
                    Text("⚡ Son Tasfiyeler", color = Signal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    liquidations.takeLast(5).forEach { liq ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (liq.side == "SELL") "🔴 LONG" else "🟢 SHORT", color = if (liq.side == "SELL") Bear else Bull, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${Fmt.price(liq.price)} · $${"%.0f".format(liq.notionalUsd)}", color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Miktar", color = TextDim, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text("Fiyat ($)", color = TextDim, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text("Notional", color = TextDim, fontSize = 10.sp, modifier = Modifier.weight(1f))
                }
                book.asks.take(8).reversed().forEach { lvl -> LadderRow(lvl, isAsk = true, onFocus = { onFocusPrice(lvl.price) }) }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("mid ${book.mid?.let { "$${"%,.2f".format(it)}" } ?: "—"}", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("spread ${book.spreadBps?.let { "%.2f".format(it) } ?: "—"} bps", color = TextDim, fontSize = 10.sp)
                }
                book.bids.take(8).forEach { lvl -> LadderRow(lvl, isAsk = false, onFocus = { onFocusPrice(lvl.price) }) }
            }
        }
    }
}

@Composable
private fun LadderRow(lvl: BookLevel, isAsk: Boolean, onFocus: () -> Unit) {
    val maxNotional = 2_000_000.0
    val frac = (lvl.notional / maxNotional).coerceIn(0.0, 1.0).toFloat()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onFocus() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(frac.coerceAtLeast(0.03f))
                .height(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background((if (isAsk) Bear else Bull).copy(alpha = 0.25f))
        )
        Spacer(Modifier.width(6.dp))
        Text("${"%.3f".format(lvl.qty)}", color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        Text(Fmt.price(lvl.price), color = if (isAsk) Bear else Bull, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(Fmt.fmtN(lvl.notional), color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CvdSparkline(cvd: Double) {
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val pts = listOf(cvd * 0.9, cvd * 0.95, cvd * 1.0, cvd * 1.05, cvd * 1.02, cvd * 1.08)
        val minV = pts.min(); val maxV = pts.max()
        val range = if (maxV > minV) maxV - minV else 1.0
        val path = Path()
        pts.forEachIndexed { i, v ->
            val x = i * size.width / (pts.size - 1)
            val y = size.height - ((v - minV) / range * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = if (cvd >= 0) Bull else Bear, style = Stroke(width = 2f))
    }
}
