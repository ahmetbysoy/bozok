package com.aistudio.bozokpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.bozokpro.model.Tab
import com.aistudio.bozokpro.ui.theme.bz

/**
 * HTML `#tabbar` (L818-828) karşılığı — 7 sekme, sıra: BOOK, FLOW, DEPTH, SIGNALS, LEVELS, MARKETS, SETTINGS.
 * Aktif sekme accent renginde alt border alır.
 */
@Composable
fun BozokTabBar(
    active: Tab,
    signalBadgeCount: Int = 0,
    onSelect: (Tab) -> Unit
) {
    val c = bz()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(c.panel)
            .border(0.5.dp, c.border),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (t in Tab.entries) {
            val isActive = t == active
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(t) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Text(t.icon, fontSize = 18.sp)
                    if (t == Tab.SIGNALS && signalBadgeCount > 0) {
                        Box(
                            modifier = Modifier
                                .offset(x = 10.dp, y = (-4).dp)
                                .clip(RoundedCornerShape(50))
                                .background(c.bear)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                signalBadgeCount.toString().take(2),
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    t.label,
                    color = if (isActive) c.accent else c.textDim,
                    fontSize = 10.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(24.dp)
                        .background(if (isActive) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
}
