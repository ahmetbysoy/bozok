package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.pattern.LegacySignal
import com.example.model.*
import com.example.ui.theme.*

/* ============================================================================
 * SİNYAL AKIŞI — istatistik çipleri (toplam/bull/bear/uyarı/doğruluk/manip),
 * filtreler (yön/onaylı/liq/yüksek), CSV export, kart tıklayınca LEVELS odak
 * ========================================================================== */

@Composable
fun SignalsTab(
    feed: List<LegacySignal>,
    accuracy: RollingAccuracy?,
    manipIndex: Int,
    onExportCsv: () -> Unit,
    onNavigateLevels: (Double) -> Unit
) {
    var biasFilter by remember { mutableStateOf("all") }
    var verifiedOnly by remember { mutableStateOf(false) }
    var highOnly by remember { mutableStateOf(false) }

    val filtered = feed.filter { s ->
        val biasOk = when (biasFilter) {
            "bull" -> s.bias == "bull"; "bear" -> s.bias == "bear"; "warn" -> s.bias == "warn"; else -> true
        }
        biasOk && (!verifiedOnly || s.verified != null) && (!highOnly || s.confidence >= 75)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Panel),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 80.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip("toplam", "${feed.size}", TextPrimary)
                StatChip("bullish", "${feed.count { it.bias == "bull" }}", Bull)
                StatChip("bearish", "${feed.count { it.bias == "bear" }}", Bear)
                StatChip("uyarı", "${feed.count { it.bias == "warn" }}", Signal)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip(
                    "doğruluk",
                    accuracy?.let { acc ->
                        buildString {
                            acc.dirAcc?.let { append("$it% yön (${acc.dirN})") }
                            acc.volAcc?.let { if (isNotEmpty()) append(" · "); append("$it% vol (${acc.volN})") }
                        }
                    } ?: "—",
                    when {
                        accuracy == null -> TextDim
                        (accuracy.dirAcc ?: 0) >= 65 -> Bull
                        (accuracy.dirAcc ?: 0) >= 45 -> Signal
                        else -> Bear
                    }
                )
                StatChip("manip radar", "$manipIndex/100", when { manipIndex < 35 -> Bull; manipIndex < 65 -> Signal; else -> Bear })
            }
        }

        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChipL("Tümü", biasFilter == "all") { biasFilter = "all" }
                FilterChipL("🟢", biasFilter == "bull") { biasFilter = "bull" }
                FilterChipL("🔴", biasFilter == "bear") { biasFilter = "bear" }
                FilterChipL("⚠️", biasFilter == "warn") { biasFilter = "warn" }
                FilterChipL("✓ onaylı", verifiedOnly) { verifiedOnly = !verifiedOnly }
                FilterChipL("⭐ %75+", highOnly) { highOnly = !highOnly }
                FilterChipL("⬇ CSV", false) { onExportCsv() }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text("Henüz sinyal yok. Book verisi akmaya başladığında desenler burada listelenecek.", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
            }
        }

        items(filtered, key = { it.id }) { sig ->
            SignalCard(sig, onClick = { onNavigateLevels(sig.price) })
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatChip(label: String, value: String, color: Color) {
    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(label, color = TextDim, fontSize = 8.5.sp)
        }
    }
}

@Composable
private fun FilterChipL(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Accent else Bg)
            .border(1.dp, if (active) Accent else Border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = if (active) Bg else TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SignalCard(sig: LegacySignal, onClick: (() -> Unit)? = null) {
    val color = when (sig.bias) { "bull" -> Bull; "bear" -> Bear; else -> Signal }
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Panel2),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(sig.icon, fontSize = 14.sp)
                    Text(sig.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (sig.verified != null) {
                        Text(if (sig.verified!!.hit) "✓ onaylı" else "✗ yanlış", color = if (sig.verified!!.hit) Bull else Bear, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("%${sig.confidence}", color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(sig.desc, color = TextDim, fontSize = 10.5.sp, lineHeight = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("${sig.type} · ${Fmt.price(sig.price)} · ${sig.timeframe} · ${sig.t.ago()}", color = TextFaint, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun Long.ago(): String {
    val s = (System.currentTimeMillis() - this) / 1000
    return when { s < 60 -> "${s}sn önce"; s < 3600 -> "${s / 60}dk önce"; else -> "${s / 3600}sa önce" }
}
