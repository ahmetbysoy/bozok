package com.aistudio.bozokpro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class BozokApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ALERTS,
                "BOZOK Sinyal Uyarıları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kritik pattern/tasfiye uyarıları"
                enableVibration(true)
                enableLights(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ALERTS = "bozok_alerts"
    }
}
