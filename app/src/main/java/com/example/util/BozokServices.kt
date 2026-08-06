package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.util.Locale

/* ============================================================================
 * YARDIMCI SERVİSLER — biyometri (fail-closed), bildirim+SOS Morse titreşim,
 * TTS Türkçe anons, haptik
 * ========================================================================== */

class BiometricAuthHelper(private val activity: FragmentActivity) {

    fun canAuthenticate(): Boolean {
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateExecution(
        title: String = "BOZOK İNFAZ KALKANI",
        subtitle: String = "Emri bota göndermek için doğrulayın",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!canAuthenticate()) {
            onError("Biyometrik donanım/parmak izi kayıtlı değil. İnfaz engellendi (fail-closed).")
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                activity.runOnUiThread { onSuccess() }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                activity.runOnUiThread { onError(errString.toString()) }
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title).setSubtitle(subtitle)
                .setDescription("BOZOK PRO otomatik infaz onayı")
                .setNegativeButtonText("İptal Et")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }
}

object BozokNotifications {
    const val CHANNEL_ID = "bozok_alerts"
    const val CHANNEL_NAME = "BOZOK Sinyal Uyarıları"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Kritik scalping sinyalleri ve SOS uyarıları"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 100, 100, 100, 100, 300, 300, 100, 300, 100, 300, 300, 100, 100, 100, 100, 100)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /** Kritik sinyal bildirimi + SOS Morse titreşimi. */
    fun notifyCritical(context: Context, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🚨 $title")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 10000).toInt(), notif)

        // SOS Morse titreşimi: ... --- ...
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                val sos = longArrayOf(0, 120, 80, 120, 80, 120, 250, 300, 100, 300, 100, 300, 250, 120, 80, 120, 80, 120)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(sos, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(sos, -1)
                }
            }
        } catch (_: Exception) { }
    }
}

/** Türkçe sesli anons (HTML speechSynthesis karşılığı). */
class BozokSpeech(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale("tr", "TR"))
            ready = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun announce(text: String) {
        if (!ready) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bozok")
        } catch (_: Exception) { }
    }

    fun stop() {
        try { tts?.stop() } catch (_: Exception) { }
    }

    fun shutdown() {
        try { tts?.shutdown() } catch (_: Exception) { }
    }
}

/** Drag-to-trade mekanik tik (45ms throttle). */
class HapticManager(private val context: Context) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (_: Exception) { null }
    }
    private var lastTick = 0L

    fun mechanicalTick() {
        val now = System.currentTimeMillis()
        if (now - lastTick < 45) return
        lastTick = now
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(15, 120))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(15)
            }
        } catch (_: Exception) { }
    }
}
