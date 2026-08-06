package com.aistudio.bozokpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.bozokpro.ui.theme.bz

/**
 * HTML `#topbar` (L438-464) karşılığı.
 * Sol: sembol input + connDot + VPIN rozeti  |  Sağ: fiyat + %24s
 * Bu turda mock görünüm — sonraki turlarda gerçek StateFlow'lara bağlanacak.
 */
@Composable
fun BozokTopBar(
    symbol: String,
    price: String,
    change24hPct: Double?,
    vpin: Double?,
    onSymbolClick: () -> Unit
) {
    val c = bz()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(c.panel)
            .border(0.5.dp, c.border)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sembol butonu
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(c.panel2)
                .border(1.dp, c.border, RoundedCornerShape(7.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                symbol,
                color = c.text,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        // ConnDot placeholder
        Box(
            modifier = Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(c.bull)
        )
        Spacer(Modifier.width(8.dp))
        // VPIN badge
        val vpinPct = vpin?.let { (it * 100).toInt() }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(c.accent.copy(alpha = 0.10f))
                .border(1.dp, c.accent.copy(alpha = 0.28f), RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (vpinPct != null) "VPIN %$vpinPct" else "VPIN —",
                color = c.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.weight(1f))
        // Fiyat + değişim
        Column(horizontalAlignment = Alignment.End) {
            Text(price, color = c.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val chColor = when {
                change24hPct == null -> c.textDim
                change24hPct >= 0 -> c.bull
                else -> c.bear
            }
            val chStr = change24hPct?.let { "%+.2f%%".format(java.util.Locale.US, it) } ?: "—"
            Text("24s $chStr", color = chColor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}
