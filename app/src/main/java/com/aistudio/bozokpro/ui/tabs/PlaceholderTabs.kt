package com.aistudio.bozokpro.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.bozokpro.ui.theme.bz

/**
 * Faz 1 iskelet. Her sekme "🚧 yapım aşamasında" placeholder gösteriyor.
 * Faz 7-9'da gerçek içeriklerle değiştirilecek.
 */

@Composable
private fun ComingSoon(title: String, subtitle: String) {
    val c = bz()
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🚧", fontSize = 48.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = c.textDim, fontSize = 12.sp)
    }
}

@Composable fun BookTabPlaceholder()     = ComingSoon("BOOK",     "Derinlik ısı haritası (Faz 7-8)")
@Composable fun FlowTabPlaceholder()     = ComingSoon("FLOW",     "Akış mumları (Faz 7-8)")
@Composable fun DepthTabPlaceholder()    = ComingSoon("DEPTH",    "OBI/CVD gauge + merdiven (Faz 8)")
@Composable fun SignalsTabPlaceholder()  = ComingSoon("SIGNALS",  "Pattern feed + doğruluk (Faz 9)")
@Composable fun LevelsTabPlaceholder()   = ComingSoon("LEVELS",   "Narrative + plan + mikro + equity (Faz 9)")
@Composable fun MarketsTabPlaceholder()  = ComingSoon("MARKETS",  "Çapraz borsa + arbitraj (Faz 9)")

@Composable
fun SettingsTabPlaceholder() {
    val c = bz()
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("⚙️ SETTINGS", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ayarlar paneli Faz 9'da gelecek.\n\nMevcut CFG değerleri (varsayılan):\n" +
                "• Hassasiyet: NORMAL\n" +
                "• Tema: Professional\n" +
                "• Flow periyot: 5s\n" +
                "• Micro bakiye: 5\$ · Risk: %0.20\n" +
                "• Bildirim / TTS: aç/kapa",
            color = c.textDim, fontSize = 12.sp
        )
    }
}
