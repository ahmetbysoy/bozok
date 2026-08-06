package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/* ============================================================================
 * PYTHON İNFAZ BOTU — dürüst hata (sahte başarı yok)
 * ========================================================================== */
class ExecutionBotClient(
    private val serverUrl: String = "http://10.0.2.2:8000/webhook",
    private val secretKey: String = "BOZOK_SECRET_TOKEN_V2",
    private val simulateWhenOffline: Boolean = false
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun executeOrder(payload: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(serverUrl)
                .addHeader("X-BOZOK-SECRET", secretKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (resp.isSuccessful) Result.success(text)
                else Result.failure(Exception("HTTP ${resp.code}: $text"))
            }
        } catch (e: Exception) {
            if (simulateWhenOffline) {
                Result.success("{\"status\":\"SUCCESS_LOCAL_SIMULATED\",\"orderId\":\"EXEC-${System.currentTimeMillis()}\"}")
            } else {
                Result.failure(Exception("Bot erişilemedi ($serverUrl): ${e.message}"))
            }
        }
    }
}
