package com.example.engine.pattern

import com.example.engine.strategy.StrategyPerformanceTracker
import com.example.model.*
import kotlin.math.abs

/* ============================================================================
 * SİNYAL DOĞRULAMA + ROLLING ACCURACY + MANİPÜLASYON RADARI
 * ========================================================================== */
class SignalVerifier(private val perfTracker: StrategyPerformanceTracker) {

    companion object {
        const val VERIFY_THRESHOLD_PCT = 0.25
        const val VERIFY_WINDOW_MS = 60_000L
        const val VERIFY_MAX_PENDING_MS = 600_000L
        const val CONFLICT_WINDOW_MS = 30_000L
        const val RADAR_WINDOW_MS = 60_000L
    }

    fun record(sig: PatternSignal) {
        if (!sig.price.isFinite()) return
        val bias = when (sig.bias) { Bias.BULL -> "bull"; Bias.BEAR -> "bear"; else -> "warn" }
        AppState.sigVerify.pending.addLast(VerifyRecord(sig.id, bias, sig.price, System.currentTimeMillis()))
        while (AppState.sigVerify.pending.size > 200) AppState.sigVerify.pending.removeFirst()
    }

    fun verifyAll() {
        val store = AppState.sigVerify
        if (store.pending.isEmpty()) return
        val now = System.currentTimeMillis()
        val price = AppState.lastPrice ?: return
        val threshold = computeVerifyThreshold()

        val stillPending = ArrayDeque<VerifyRecord>()
        for (rec in store.pending) {
            if (rec.verified) continue
            val age = now - rec.t
            if (age < VERIFY_WINDOW_MS) { stillPending.addLast(rec); continue }
            if (age > VERIFY_MAX_PENDING_MS) continue
            if (!price.isFinite() || !rec.entryPrice.isFinite() || rec.entryPrice <= 0) { stillPending.addLast(rec); continue }

            val pct = (price - rec.entryPrice) / rec.entryPrice * 100.0
            val hit = when (rec.bias) {
                "bull" -> pct >= threshold
                "bear" -> pct <= -threshold
                else -> abs(pct) >= threshold
            }
            store.results.addLast(VerifyResult(hit, pct, rec.bias))
            while (store.results.size > 60) store.results.removeFirst()

            val sig = AppState.signals.find { it.id == rec.id }
            if (sig != null) sig.verified = VerifiedResult(hit, pct)
            rec.verified = true

            val stType = sig?.type ?: "KAPLAN_KAPAN"
            perfTracker.addTrade(stType, hit, if (hit) 1.6 else -1.0)
        }
        store.pending.clear()
        store.pending.addAll(stillPending)
    }

    fun computeVerifyThreshold(): Double {
        val st = AppState
        if (st.heatHistory.size < 3) return VERIFY_THRESHOLD_PCT
        val recent = st.heatHistory.takeLast(20)
        var minP = Double.MAX_VALUE
        var maxP = -Double.MAX_VALUE
        for (snap in recent) {
            for (b in snap.bids) if (b.price < minP) minP = b.price
            for (a in snap.asks) if (a.price > maxP) maxP = a.price
        }
        if (!minP.isFinite() || !maxP.isFinite() || minP <= 0) return VERIFY_THRESHOLD_PCT
        val rangePct = (maxP - minP) / minP * 100.0
        return Fmt.clamp(VERIFY_THRESHOLD_PCT + rangePct * 0.15, VERIFY_THRESHOLD_PCT, 2.0)
    }

    fun getRollingAccuracy(): RollingAccuracy? {
        val results = AppState.sigVerify.results.toList()
        if (results.isEmpty()) return null
        val last20 = results.takeLast(20)
        val dir = last20.filter { it.bias != "warn" }
        val vol = last20.filter { it.bias == "warn" }
        val dirAcc = if (dir.isNotEmpty()) dir.count { it.hit } * 100 / dir.size else null
        val volAcc = if (vol.isNotEmpty()) vol.count { it.hit } * 100 / vol.size else null
        return RollingAccuracy(dirAcc, volAcc, dir.size, vol.size)
    }

    fun detectConflict(): Boolean {
        val now = System.currentTimeMillis()
        val bull = AppState.signals.any { (it.bias == "bull" || it.bias == "bullish") && now - it.t < CONFLICT_WINDOW_MS }
        val bear = AppState.signals.any { (it.bias == "bear" || it.bias == "bearish") && now - it.t < CONFLICT_WINDOW_MS }
        return bull && bear
    }

    fun updateManipulationRadar(): Int {
        val st = AppState
        val now = System.currentTimeMillis()
        val spoofCount = st.signals.count { (it.type.uppercase() == "WALL_PULL" || it.type.uppercase() == "SPOOF") && now - it.t < RADAR_WINDOW_MS }
        val churn = st.wallEvents.count { now - it < 5000 }
        val spoofScore = Fmt.clamp(spoofCount / 4.0 * 100.0, 0.0, 100.0)
        val churnScore = Fmt.clamp(churn / 40.0 * 100.0, 0.0, 100.0)
        val conflictScore = Fmt.clamp((if (st.conflictActive) 1 else 0) * 100.0, 0.0, 100.0)
        st.manipIndex = (spoofScore * 0.4 + churnScore * 0.3 + conflictScore * 0.3).toInt()
        return st.manipIndex
    }
}
