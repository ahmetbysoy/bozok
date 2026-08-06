package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Book
import com.example.ui.theme.*

/* ============================================================================
 * TOP BAR — logo, bağlantı noktası, sembol seçici, fiyat/24s, VPIN rozeti
 * (HTML #topbar birebir)
 * ========================================================================== */

@Composable
fun TopBar(
    symbol: String,
    book: Book,
    vpin: Double?,
    chg24: Double,
    onSymbolSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    val vpinColor = when {
        vpin == null -> TextDim
        vpin >= 60 -> Bear
        vpin >= 30 -> Signal
        else -> Bull
    }

    Surface(color = Panel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Signal))
                    Text("BOZOK ", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text("PRO", color = TextDim, fontSize = 13.sp)
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(if (book.bestBid != null) Bull else Bear)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Panel2)
                        .border(1.dp, Border, RoundedCornerShape(6.dp))
                        .clickable { showDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(symbol, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = book.mid?.let { "$${"%,.2f".format(it)}" } ?: "—",
                        color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "24s " + (if (chg24 >= 0) "+" else "") + "%.2f".format(chg24) + "%",
                        color = if (chg24 > 0) Bull else if (chg24 < 0) Bear else TextDim,
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(vpinColor.copy(alpha = 0.15f))
                        .border(1.dp, vpinColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        "VPIN " + (vpin?.let { "%d".format(it.toInt()) } ?: "—") + "% · " + when {
                            vpin == null -> "—"; vpin >= 60 -> "TOKSİK!"; vpin >= 30 -> "ORTA"; else -> "DÜŞÜK"
                        },
                        color = vpinColor, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Spread " + (book.spreadBps?.let { "%.2f".format(it) } ?: "—") + " bps · " + book.label,
                    color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Sembol Ara (Binance Futures)", color = Accent, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        label = { Text("örn. BTCUSDT, WIFUSDT") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "PEPEUSDT", "WIFUSDT").forEach { s ->
                            AssistChip(
                                onClick = { onSymbolSelected(s); showDialog = false },
                                label = { Text(s, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (input.isNotBlank()) onSymbolSelected(input); showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
                ) { Text("Bağlan", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("İptal") } },
            containerColor = Panel
        )
    }
}

/* ============================================================================
 * ALT SEKME BAR — 7 sekme (HTML #tabbar birebir)
 * ========================================================================== */

private data class TabItem(val id: String, val label: String, val icon: String)

@Composable
fun BozokTabBar(
    activeTab: String,
    unreadSignalsCount: Int = 0,
    replayActive: Boolean = false,
    onTabSelected: (String) -> Unit
) {
    val tabs = listOf(
        TabItem("BOOK", "BOOK", "📊"),
        TabItem("FLOW", "FLOW", "🕯️"),
        TabItem("DEPTH", "DEPTH", "📚"),
        TabItem("SIGNALS", "SİNYAL", "🎯"),
        TabItem("LEVELS", "SEVİYE", "📍"),
        TabItem("MARKETS", "PİYASA", "🌐"),
        TabItem("SETTINGS", "AYAR", "⚙️")
    )

    Surface(color = Panel, tonalElevation = 8.dp) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isActive = activeTab == tab.id
                    val bg by animateColorAsState(if (isActive) Accent.copy(alpha = 0.18f) else Color.Transparent)
                    val fg by animateColorAsState(if (isActive) Accent else TextDim)

                    Box(
                        modifier = Modifier
                            .heightIn(min = 46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .border(1.dp, if (isActive) Accent.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onTabSelected(tab.id) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(tab.icon, fontSize = 13.sp)
                                Text(tab.label, color = fg, fontSize = 11.sp, fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold)
                                if (tab.id == "SIGNALS" && unreadSignalsCount > 0) {
                                    Box(Modifier.clip(CircleShape).background(Bear).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text("$unreadSignalsCount", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (isActive) {
                                Spacer(Modifier.height(3.dp))
                                Box(Modifier.height(3.dp).width(26.dp).clip(RoundedCornerShape(1.5.dp)).background(Accent))
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
            if (replayActive) {
                Box(Modifier.fillMaxWidth().background(Bear.copy(alpha = 0.2f)).padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                    Text("🔴 KAYIT AKTİF", color = Bear, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
