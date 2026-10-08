package com.jiro.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class KeeperApi(
    private val baseUrl: String = BuildConfig.KEEPER_URL,
    private val client: OkHttpClient = OkHttpClient()
) {
    data class KeeperStatus(
        val healthy: Boolean,
        val minimumDepositUsdc: Long,
        val sweepThresholdUsdc: Long,
        val lastSweepTx: String?,
        val lastBuyOrder: String?
    )

    suspend fun getStatus(): KeeperStatus? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/status")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                KeeperStatus(
                    healthy = json.optBoolean("healthy", true),
                    minimumDepositUsdc = json.optLong("minimumDepositUsdc", 5_000_000L),
                    sweepThresholdUsdc = json.optLong("sweepThresholdUsdc", 5_000_000L),
                    lastSweepTx = if (json.isNull("lastSweepTx")) null else json.optString("lastSweepTx"),
                    lastBuyOrder = if (json.isNull("lastBuyOrder")) null else json.optString("lastBuyOrder")
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
