package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

/* ============================================================================
 * MARKETS — 4 borsa durumu + arbitraj sapmaları (HTML renderMarkets)
 * ========================================================================== */

@Composable
fun MarketsTab(exchanges: Map<String, ExchangeState>, arbitrage: List<ArbitrageSkew>, symbol: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Panel),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 80.dp)
    ) {
        item {
            val liveCount = exchanges.values.count { it.status == ConnStatus.LIVE }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
                Column(Modifier.padding(12.dp)) {
                    Text("🌐 PİYASA VERİ KALİTESİ", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$liveCount/4 borsa canlı · Sembol: $symbol",
                        color = if (liveCount >= 3) Bull else if (liveCount >= 1) Signal else Bear,
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item { Text("BORSA DURUMU", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold) }

        items(exchanges.values.toList(), key = { it.key }) { ex ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Panel2),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(
                        when (ex.status) { ConnStatus.LIVE -> Bull; ConnStatus.CONNECTING -> Signal; else -> Bear }
                    ))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ex.label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(ex.bestBid?.let { "bid ${Fmt.price(it)}" } ?: "—", color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(
                        when (ex.status) {
                            ConnStatus.LIVE -> "CANLI${ex.latencyMs?.let { " (${it}ms)" } ?: ""}"
                            ConnStatus.CONNECTING -> "BAĞLANIYOR"
                            ConnStatus.BAD -> "HATA"
                            ConnStatus.IDLE -> "KAPALI"
                        },
                        color = when (ex.status) { ConnStatus.LIVE -> Bull; ConnStatus.CONNECTING -> Signal; else -> Bear },
                        fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item { Text("ARBİTRAJ SAPMALARI (bps)", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold) }

        if (arbitrage.isEmpty()) {
            item { Text("Sapma verisi toplanıyor...", color = TextDim, fontSize = 11.sp) }
        }

        items(arbitrage) { skew ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Panel2),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (skew.isOpportunity) Signal.copy(alpha = 0.7f) else Border)
            ) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${skew.venueA} → ${skew.venueB}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${Fmt.price(skew.priceA)} vs ${Fmt.price(skew.priceB)}", color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${"%.1f".format(skew.deviationBps)} bps", color = if (skew.isOpportunity) Signal else TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        if (skew.isOpportunity) Text("⚡ fırsat (≥8bps)", color = Signal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
