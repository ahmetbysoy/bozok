package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.SessionEntity
import com.example.model.*
import com.example.ui.theme.*

/* ============================================================================
 * AYARLAR — tema/colorblind, hassasiyet, flow, ses/voice/notif, mikro,
 * JSON export/import, replay yönetimi (HTML SETTINGS birebir)
 * ========================================================================== */

@Composable
fun SettingsTab(
    config: AppConfig,
    isRecording: Boolean,
    replaySessions: List<SessionEntity>,
    replayPlaying: Boolean,
    onToggleRecord: () -> Unit,
    onPlayReplay: (SessionEntity) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSensitivityChange: (SensitivityPreset) -> Unit,
    onFlowPeriodChange: (Long) -> Unit,
    onFlowModeChange: (String) -> Unit,
    onColorblindChange: (Boolean) -> Unit,
    onThemeChange: (String) -> Unit,
    onMultiExchangeChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onVoiceChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: (String) -> Unit,
    onUpdateConfig: ((AppConfig) -> Unit) -> Unit
) {
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Panel)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("⚙️ BOZOK PRO AYARLARI", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        // Kayıt & Replay
        SectionCard("💾 ODA KAYDI & REPLAY") {
            Button(
                onClick = onToggleRecord,
                colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Bear else Bull, contentColor = Bg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRecording) "⏹ Kaydı Durdur ve Kaydet" else "🔴 Oturum Kaydı Başlat", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (replaySessions.isEmpty()) {
                Text("Kayıtlı oturum yok. Kayıt başlatıp birkaç dakika bekleyin.", color = TextDim, fontSize = 11.sp)
            } else {
                replaySessions.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(s.symbol + " · " + ((s.t1 - s.t0) / 1000) + "sn", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("${s.eventsJson.length} bayt", color = TextDim, fontSize = 9.sp)
                        }
                        Row {
                            TextButton(onClick = { onPlayReplay(s) }) {
                                Text(if (replayPlaying) "⏹ Durdur" else "▶ Oynat", color = Accent, fontSize = 10.sp)
                            }
                            TextButton(onClick = { onDeleteSession(s.id) }) { Text("Sil", color = Bear, fontSize = 10.sp) }
                        }
                    }
                }
            }
        }

        // Görünüm: tema + colorblind
        SectionCard("🎨 GÖRÜNÜM (Tema)") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("professional", "neon", "minimal").forEach { t ->
                    SelectChip(t.uppercase(), config.theme == t) { onThemeChange(t) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Mavi/Turuncu Renk Körlüğü Modu", color = TextPrimary, fontSize = 11.5.sp)
                Switch(checked = config.colorblind, onCheckedChange = onColorblindChange, colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Panel))
            }
        }

        // Hassasiyet
        SectionCard("🎯 PATTERN HASSASİYETİ") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SensitivityPreset.entries.forEach { p ->
                    SelectChip(p.displayName, config.sensitivity == p) { onSensitivityChange(p) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Duvar çarpanı ${config.wallMult} · Spoof penceresi ${config.spoofWindowMs}ms · Imbalance ${config.imbalanceThresh}", color = TextDim, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
        }

        // Flow
        SectionCard("🕯️ FLOW PERİYODU & MODU") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1_000L to "1s", 5_000L to "5s", 15_000L to "15s", 60_000L to "1dk", 300_000L to "5dk").forEach { (ms, label) ->
                    SelectChip(label, config.flowTimeframeMs == ms) { onFlowPeriodChange(ms) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip("Zaman", config.flowCandleMode == "time") { onFlowModeChange("time") }
                SelectChip("Hacim", config.flowCandleMode == "volume") { onFlowModeChange("volume") }
            }
        }

        // Borsalar
        SectionCard("🌐 ÇOKLU BORSA") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Bybit + OKX + MEXC (Global Book & Arbitraj)", color = TextPrimary, fontSize = 11.5.sp)
                Switch(checked = config.multiExchange, onCheckedChange = onMultiExchangeChange, colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Panel))
            }
        }

        // Mikro hesap
        SectionCard("💰 MİKRO HESAP PARAMETRELERİ") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.microBalance.toString(),
                    onValueChange = { v -> v.toDoubleOrNull()?.let { d -> onUpdateConfig { it.microBalance = d.coerceIn(1.0, 100_000.0) } } },
                    label = { Text("Bakiye ($)") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = "${(config.microRiskPct * 100).toInt()}",
                    onValueChange = { v -> v.toDoubleOrNull()?.let { d -> onUpdateConfig { it.microRiskPct = (d / 100.0).coerceIn(0.005, 0.05) } } },
                    label = { Text("Risk %") }, singleLine = true, modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Kelly benzeri: güven arttıkça risk %0.5-%5 arası otomatik ayarlanır.", color = TextDim, fontSize = 9.5.sp)
        }

        // Ses & bildirim
        SectionCard("🔔 BİLDİRİMLER & ANONS") {
            ToggleRow("Sesli uyarı", config.soundOn, onSoundChange)
            ToggleRow("Türkçe sesli anons (TTS)", config.voiceAnnounce, onVoiceChange)
            ToggleRow("Kritik sinyal bildirimi + titreşim", config.notifications, onNotificationsChange)
        }

        // Yedekleme
        SectionCard("💾 AYAR YEDEKLEME (JSON)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExportSettings, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg), modifier = Modifier.weight(1f)) {
                    Text("📥 Dışa Aktar", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Button(onClick = { showImport = true }, colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = Accent), border = BorderStroke(1.dp, Accent), modifier = Modifier.weight(1f)) {
                    Text("📤 İçe Aktar", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            if (showImport) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = importText, onValueChange = { importText = it },
                    label = { Text("JSON yapıştır") }, modifier = Modifier.fillMaxWidth().height(90.dp)
                )
                Button(
                    onClick = { onImportSettings(importText); importText = ""; showImport = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Yükle", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            }
        }

        Text("BOZOK PRO v1.0.0 — Orderflow/mikroyapı scalping terminali. Tüm analiz cihazda çalışır.", color = TextFaint, fontSize = 9.sp)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextPrimary, fontSize = 11.5.sp)
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Panel))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Panel2),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(title, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SelectChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Accent else Bg)
            .border(1.dp, if (active) Accent else Border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (active) Bg else TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
