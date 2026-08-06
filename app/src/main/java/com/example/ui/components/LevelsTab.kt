package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.engine.pattern.NarrativeEngine
import com.example.engine.strategy.MicroAccountOptimizer
import com.example.engine.strategy.StrategyPerformanceTracker
import com.example.model.*
import com.example.ui.theme.*

/* ============================================================================
 * LEVELS — meta-analiz (narrative) + plan + mikro + equity + aktif desenler
 * ========================================================================== */

@Composable
fun LevelsTab(
    narrative: NarrativeEngine.Narrative,
    plan: TradePlan?,
    microResult: MicroAccountOptimizer.MicroResult?,
    signals: List<PatternSignal>,
    config: AppConfig,
    perfTracker: StrategyPerformanceTracker,
    onExecute: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("🌐 META-ANALİZ PİYASA YORUMU", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(narrative.icon, fontSize = 22.sp)
                    Text(narrative.title, color = when (narrative.tone) {
                        "bull" -> Bull; "bear" -> Bear; "warn" -> Signal; else -> Accent
                    }, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(narrative.text, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }

        if (plan != null) PlanCard(plan = plan, onExecute = onExecute)

        MicroScalpCard(result = microResult, config = config, plan = plan)

        // Equity + performans
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("📊 STRATEJİ PERFORMANSI", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val perfs = perfTracker.getAllPerformances()
                if (perfs.isEmpty()) {
                    Text("Sinyal doğrulama sonuçları biriktikçe Net R / win-rate burada birikir.", color = TextDim, fontSize = 10.5.sp)
                } else {
                    perfs.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.strategyId, color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${p.totalTrades} işlem · %${"%.0f".format(p.winRatePct)} WR · ${if (p.netRReturn >= 0) "+" else ""}${"%.1f".format(p.netRReturn)}R",
                                color = if (p.netRReturn >= 0) Bull else Bear, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                EquityCurve(perfs)
            }
        }

        if (signals.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("🎯 AKTİF DESENLER (${signals.size})", color = Signal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    signals.take(8).forEach { sig ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${sig.title} · ${Fmt.price(sig.price)}", color = TextPrimary, fontSize = 10.5.sp)
                            Text("%${sig.confidence}", color = when (sig.bias) { Bias.BULL -> Bull; Bias.BEAR -> Bear; Bias.WARN -> Signal; Bias.NEUTRAL -> Accent }, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquityCurve(perfs: List<StrategyPerformance>) {
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        if (perfs.isEmpty()) return@Canvas
        val all = perfs.flatMap { it.equityHistory }
        if (all.isEmpty()) return@Canvas
        val minV = all.min(); val maxV = all.max()
        val range = if (maxV > minV) maxV - minV else 1.0

        perfs.forEachIndexed { idx, p ->
            if (p.equityHistory.size < 2) return@forEachIndexed
            val path = Path()
            val color = listOf(Bull, Accent, Signal, Violet)[idx % 4]
            p.equityHistory.forEachIndexed { i, v ->
                val x = i * size.width / (p.equityHistory.size - 1)
                val y = size.height - ((v - minV) / range * size.height).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 2.5f))
        }
        drawLine(TextDim.copy(alpha = 0.4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1f)
    }
}
