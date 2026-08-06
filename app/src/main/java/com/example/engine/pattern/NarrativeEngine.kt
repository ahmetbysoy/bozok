package com.example.engine.pattern

import com.example.model.*

/* ============================================================================
 * NARRATIVE ENGINE — Türkçe meta-yorum (HTML X2 — 7 senaryo)
 * ========================================================================== */
object NarrativeEngine {

    data class Narrative(val icon: String, val title: String, val tone: String, val text: String)

    fun synthesize(activeSignals: List<PatternSignal>): Narrative {
        val now = System.currentTimeMillis()
        fun recent(types: Set<String>, maxAgeMs: Long = 120_000): List<PatternSignal> =
            activeSignals.filter { it.type in types && now - it.createdAt < maxAgeMs }

        val iceberg = recent(setOf("ICEBERG"))
        val smd = recent(setOf("SMART_MONEY_DISTRIBUTION"))
        val spoof = recent(setOf("WALL_PULL"))
        val voidUp = recent(setOf("LIQUIDITY_VOID")).filter { it.bias == Bias.BULL }
        val flowBull = recent(setOf("FLOW_BULL", "FLOW_REV_UP"))
        val herding = recent(setOf("LADDER_ORDERS"))
        val hiddenAbs = recent(setOf("HIDDEN_ABSORPTION"))
        val skew = recent(setOf("BOOK_SKEW")).firstOrNull()
        val vacuum = recent(setOf("LIQUIDITY_VOID")).filter { it.metadata["vacuumFill"] == true }

        if (iceberg.isNotEmpty() && smd.isNotEmpty()) {
            return Narrative("🐋", "ÇELİŞKİLİ AKIŞ", "bear",
                "Balinalar fiyatı yükseltmeden mal boşaltıyor (SMD), alt kademedeki gizli alıcı (Iceberg) muhtemelen MM'in kendi duvarı. Long girmek tehlikeli.")
        }
        if (spoof.isNotEmpty() && voidUp.isNotEmpty() && flowBull.isNotEmpty()) {
            return Narrative("🚀", "SÜPÜRME BEKLENTİSİ", "bull",
                "Satış duvarları sahteydi (Spoof) ve çekildi. Yukarıda likidite boşluğu var ve taker akışı boğa yönünde. Sert yukarı süpürme (Vacuum) bekleniyor.")
        }
        if (hiddenAbs.isNotEmpty() && skew != null && skew.metadata["rapidShift"] == true) {
            val delta = (skew.metadata["delta10s"] as? Double) ?: 0.0
            val bull = skew.bias == Bias.BULL
            return Narrative("🫥", "GİZLİ BİRİKİM", if (bull) "bull" else "bear",
                "Fiyat sabitken agresif ${if (hiddenAbs.first().bias == Bias.BULL) "alım" else "satım"} emiliyor ve book ${if (delta > 0) "bid" else "ask"} tarafına hızla kayıyor — ${if (bull) "birikim" else "dağıtım"} sinyali.")
        }
        if (vacuum.isNotEmpty()) {
            return Narrative("🌪️", "VAKUUM DOLUYOR", "bull",
                "Açık likidite boşluğu taker emirlerle şiddetle dolduruluyor — güçlü devam sinyali.")
        }
        if (herding.isNotEmpty()) {
            return Narrative("🐑", "DUVAR SÜRÜLÜYOR", "warn",
                "Duvarlar iptal edilip aynı hacimle yakına taşınıyor — fiyat hedefe sürülüyor olabilir.")
        }
        if (smd.isNotEmpty()) {
            return Narrative("🐋", "AKILLI PARA DAĞITIYOR", "bear",
                "Fiyat yükselirken balina CVD düşüyor, retail alıyor. Dağıtım aşaması — yukarı hareket sürdürülemez.")
        }
        if (iceberg.isNotEmpty()) {
            val bull = iceberg.first().bias == Bias.BULL
            return Narrative("🧊", "GİZLİ BİRİKİM", if (bull) "bull" else "bear",
                "Uzun süredir ${if (bull) "alım" else "satım"} duvarı az dokunuşla duruyor — kurumsal ${if (bull) "birikim" else "dağıtım"} olabilir.")
        }
        return Narrative("🌐", "NÖTR / BEKLE", "neu",
            "Şu an meta-sentez için yeterli çapraz sinyal yok. Güçlü desen kombinasyonlarını bekliyorum.")
    }
}
