package com.jiro.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SolanaRpcClient(
    val rpcUrl: String = BuildConfig.DEVNET_RPC_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getLatestBlockhash(): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getLatestBlockhash")
            put("params", JSONArray().put(JSONObject().put("commitment", "confirmed")))
        }

        val json = postRpc(payload)
        val result = json.optJSONObject("result")
            ?: throw IOException("No result in getLatestBlockhash: $json")
        val value = result.getJSONObject("value")
        value.getString("blockhash")
    }

    suspend fun getSolBalance(address: String): Long = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getBalance")
            put("params", JSONArray().put(address).put(JSONObject().put("commitment", "confirmed")))
        }

        val json = postRpc(payload)
        val result = json.optJSONObject("result") ?: return@withContext 0L
        result.optLong("value", 0L)
    }

    suspend fun getTokenAccountBalance(tokenAccountAddress: String): Long = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getTokenAccountBalance")
            put("params", JSONArray().put(tokenAccountAddress).put(JSONObject().put("commitment", "confirmed")))
        }

        try {
            val json = postRpc(payload)
            val result = json.optJSONObject("result") ?: return@withContext 0L
            val value = result.optJSONObject("value") ?: return@withContext 0L
            val amountStr = value.optString("amount", "0")
            amountStr.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            // Account does not exist or has no balance yet
            0L
        }
    }

    data class VaultConfigAccount(
        val owner: PublicKey,
        val destination: PublicKey,
        val keeper: PublicKey,
        val threshold: Long,
        val maxBalance: Long,
        val bump: Int
    )

    suspend fun getVaultConfig(vaultConfigPda: PublicKey): VaultConfigAccount? = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getAccountInfo")
            val configObj = JSONObject().apply {
                put("encoding", "base64")
                put("commitment", "confirmed")
            }
            put("params", JSONArray().put(vaultConfigPda.toBase58()).put(configObj))
        }

        try {
            val json = postRpc(payload)
            val result = json.optJSONObject("result") ?: return@withContext null
            val value = result.optJSONObject("value") ?: return@withContext null
            val dataArray = value.optJSONArray("data") ?: return@withContext null
            val base64Data = dataArray.getString(0)
            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)

            // Layout in Anchor:
            // 8 bytes discriminator
            // 32 bytes owner
            // 32 bytes destination
            // 32 bytes keeper
            // 8 bytes threshold
            // 8 bytes max_balance
            // 1 byte bump
            if (bytes.size < 8 + 32 + 32 + 32 + 8 + 8 + 1) return@withContext null

            var offset = 8
            val ownerBytes = bytes.copyOfRange(offset, offset + 32); offset += 32
            val destBytes = bytes.copyOfRange(offset, offset + 32); offset += 32
            val keeperBytes = bytes.copyOfRange(offset, offset + 32); offset += 32

            fun readU64(start: Int): Long {
                var v = 0L
                for (i in 0 until 8) {
                    v = v or ((bytes[start + i].toLong() and 0xFF) shl (i * 8))
                }
                return v
            }

            val threshold = readU64(offset); offset += 8
            val maxBalance = readU64(offset); offset += 8
            val bump = bytes[offset].toInt() and 0xFF

            VaultConfigAccount(
                owner = PublicKey(ownerBytes),
                destination = PublicKey(destBytes),
                keeper = PublicKey(keeperBytes),
                threshold = threshold,
                maxBalance = maxBalance,
                bump = bump
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun postRpc(payload: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(rpcUrl)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("RPC request failed with HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string() ?: throw IOException("Empty RPC response")
            return JSONObject(body)
        }
    }
}
