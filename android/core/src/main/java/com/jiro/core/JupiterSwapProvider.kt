package com.jiro.core

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * JupiterSwapProvider integrates with Jupiter Swap API V2 (/build endpoint)
 * for composing raw swap instructions with custom vault instructions.
 *
 * Official Documentation:
 * - Jupiter Swap API Overview: https://developers.jup.ag/docs/swap
 * - Jupiter Router /build: https://developers.jup.ag/docs/swap/build
 * - Jupiter Developer Platform: https://developers.jup.ag/portal
 *
 * Program ID: JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4
 */
sealed class JupiterException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoRouteFoundException(message: String) : JupiterException(message)
    class QuoteExpiredException(message: String) : JupiterException(message)
    class PriceImpactExceededException(
        message: String,
        val impactBps: Int,
        val maxImpactBps: Int
    ) : JupiterException(message)
    class OutputProgramMismatchException(
        message: String,
        val expectedProgram: PublicKey,
        val actualProgram: PublicKey
    ) : JupiterException(message)
    class SimulationMismatchException(message: String) : JupiterException(message)
    class PartialFailureException(
        message: String,
        val withdrawnAmount: Money,
        val step2Error: String
    ) : JupiterException(message)
    class JupiterApiException(
        message: String,
        val statusCode: Int,
        val errorBody: String
    ) : JupiterException(message)
}

class JupiterSwapProvider(
    private val apiKey: String = "",
    val baseUrl: String = "https://api.jup.ag/swap/v2",
    val maxSlippageBps: Int = 100, // Safety cap: max 100 bps (1%)
    val maxPriceImpactBps: Int = 100, // Safety cap: max 100 bps (1%)
    val quoteTtlMs: Long = 30_000L, // 30s quote expiry policy
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SwapProvider {

    companion object {
        // Fallback dummy public key for pure quote requests (System Program address)
        val DUMMY_TAKER = PublicKey("11111111111111111111111111111111")
        val JUPITER_PROGRAM_ID = PublicKey("JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4")
    }

    override suspend fun getQuote(
        inputMint: PublicKey,
        outputMint: PublicKey,
        amount: Money,
        slippageBps: Int
    ): SwapQuote {
        val clampedSlippage = slippageBps.coerceIn(1, maxSlippageBps)
        val json = fetchBuildJson(
            inputMint = inputMint,
            outputMint = outputMint,
            amount = amount.baseUnits,
            taker = DUMMY_TAKER,
            slippageBps = clampedSlippage
        )

        return parseQuoteFromJson(json, inputMint, outputMint, amount)
    }

    override suspend fun buildSwapPlan(
        user: PublicKey,
        quote: SwapQuote,
        slippageBps: Int
    ): SwapPlan {
        // 1. Quote Policy: Check quote expiry (30s TTL)
        if (quote.isExpired(ttlMs = quoteTtlMs)) {
            throw JupiterException.QuoteExpiredException(
                "Swap quote has expired (TTL ${quoteTtlMs / 1000}s exceeded). Refresh quote before signing."
            )
        }

        // 2. Quote Policy: Reject price impact over safety cap
        if (quote.priceImpactBps > maxPriceImpactBps) {
            throw JupiterException.PriceImpactExceededException(
                "Price impact (${quote.priceImpactBps} bps) exceeds security cap ($maxPriceImpactBps bps)",
                impactBps = quote.priceImpactBps,
                maxImpactBps = maxPriceImpactBps
            )
        }

        // 3. Fetch full instructions from /build endpoint for user wallet
        val clampedSlippage = slippageBps.coerceIn(1, maxSlippageBps)
        val json = fetchBuildJson(
            inputMint = quote.inputMint,
            outputMint = quote.outputMint,
            amount = quote.inputAmount.baseUnits,
            taker = user,
            slippageBps = clampedSlippage
        )

        return parseBuildResponse(json, quote)
    }

    /**
     * Executes GET /swap/v2/build
     */
    private fun fetchBuildJson(
        inputMint: PublicKey,
        outputMint: PublicKey,
        amount: Long,
        taker: PublicKey,
        slippageBps: Int
    ): JSONObject {
        val urlBuilder = "$baseUrl/build".toHttpUrl().newBuilder()
            .addQueryParameter("inputMint", inputMint.toBase58())
            .addQueryParameter("outputMint", outputMint.toBase58())
            .addQueryParameter("amount", amount.toString())
            .addQueryParameter("taker", taker.toBase58())
            .addQueryParameter("slippageBps", slippageBps.toString())
            .addQueryParameter("maxAccounts", "64")
            .addQueryParameter("transactionVersion", "0")

        val reqBuilder = Request.Builder()
            .url(urlBuilder.build())
            .get()

        if (apiKey.isNotBlank()) {
            reqBuilder.header("x-api-key", apiKey.trim())
        }

        val request = reqBuilder.build()
        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                if (response.code == 400 && bodyString.contains("No routes found", ignoreCase = true)) {
                    throw JupiterException.NoRouteFoundException(
                        "No Jupiter swap route found between ${inputMint.toBase58()} and ${outputMint.toBase58()}"
                    )
                }
                throw JupiterException.JupiterApiException(
                    message = "Jupiter API error HTTP ${response.code}: $bodyString",
                    statusCode = response.code,
                    errorBody = bodyString
                )
            }

            return JSONObject(bodyString)
        }
    }

    /**
     * Parses SwapQuote metadata from a Jupiter /build response.
     */
    fun parseQuoteFromJson(
        json: JSONObject,
        inputMint: PublicKey,
        outputMint: PublicKey,
        amount: Money
    ): SwapQuote {
        if (json.has("error")) {
            val err = json.optString("error")
            if (err.contains("No routes found", ignoreCase = true)) {
                throw JupiterException.NoRouteFoundException("No routes found: $err")
            }
            throw JupiterException.JupiterApiException(err, 400, err)
        }

        val outAmountStr = json.getString("outAmount")
        val expectedOut = outAmountStr.toLong()

        val otherAmountThresholdStr = json.optString("otherAmountThreshold", outAmountStr)
        val minOut = otherAmountThresholdStr.toLongOrNull() ?: expectedOut

        // Price impact is provided as a percentage (e.g. 0.05 or "0.05" or 0)
        val priceImpactPct = when {
            json.isNull("priceImpactPct") -> 0.0
            else -> json.optDouble("priceImpactPct", 0.0)
        }
        val priceImpactBps = (priceImpactPct * 100.0).roundToInt()

        if (priceImpactBps > maxPriceImpactBps) {
            throw JupiterException.PriceImpactExceededException(
                "Price impact ($priceImpactBps bps) exceeds cap ($maxPriceImpactBps bps)",
                impactBps = priceImpactBps,
                maxImpactBps = maxPriceImpactBps
            )
        }

        return SwapQuote(
            inputMint = inputMint,
            outputMint = outputMint,
            inputAmount = amount,
            expectedOutputAmount = expectedOut,
            minOutputAmount = minOut,
            priceImpactBps = priceImpactBps,
            timestampMs = System.currentTimeMillis()
        )
    }

    /**
     * Parses a complete /build JSON response into a SwapPlan.
     */
    fun parseBuildResponse(json: JSONObject, quote: SwapQuote): SwapPlan {
        if (json.has("error")) {
            val err = json.optString("error")
            if (err.contains("No routes found", ignoreCase = true)) {
                throw JupiterException.NoRouteFoundException("No routes found: $err")
            }
            throw JupiterException.JupiterApiException(err, 400, err)
        }

        // 1. Setup instructions (e.g. create ATA)
        val setupInstructions = mutableListOf<TransactionInstruction>()
        val setupArray = json.optJSONArray("setupInstructions")
        if (setupArray != null) {
            for (i in 0 until setupArray.length()) {
                setupInstructions.add(parseInstruction(setupArray.getJSONObject(i)))
            }
        }

        // 2. Core swap instruction
        val swapIxJson = json.getJSONObject("swapInstruction")
        val swapInstruction = parseInstruction(swapIxJson)

        // 3. Cleanup instruction (e.g. close temp wSOL)
        val cleanupInstruction = if (!json.isNull("cleanupInstruction")) {
            parseInstruction(json.getJSONObject("cleanupInstruction"))
        } else {
            null
        }

        // 4. Compute budget instructions
        val computeBudgetInstructions = mutableListOf<TransactionInstruction>()
        val computeBudgetArray = json.optJSONArray("computeBudgetInstructions")
        if (computeBudgetArray != null) {
            for (i in 0 until computeBudgetArray.length()) {
                computeBudgetInstructions.add(parseInstruction(computeBudgetArray.getJSONObject(i)))
            }
        }

        // 5. Address Lookup Tables
        val lookupTables = mutableListOf<AddressLookupTableAccount>()
        val altsJson = json.optJSONObject("addressesByLookupTableAddress")
        if (altsJson != null) {
            val keys = altsJson.keys()
            while (keys.hasNext()) {
                val tableKeyStr = keys.next()
                val addrArray = altsJson.getJSONArray(tableKeyStr)
                val addresses = mutableListOf<PublicKey>()
                for (j in 0 until addrArray.length()) {
                    addresses.add(PublicKey(addrArray.getString(j)))
                }
                lookupTables.add(
                    AddressLookupTableAccount(
                        key = PublicKey(tableKeyStr),
                        addresses = addresses
                    )
                )
            }
        }

        // 6. Recent blockhash metadata
        val blockhashBytes = json.optJSONObject("blockhashWithMetadata")?.let { meta ->
            val bhArray = meta.optJSONArray("blockhash")
            if (bhArray != null && bhArray.length() == 32) {
                ByteArray(32) { i -> bhArray.getInt(i).toByte() }
            } else null
        }

        return SwapPlan(
            quote = quote,
            setupInstructions = setupInstructions,
            swapInstruction = swapInstruction,
            cleanupInstruction = cleanupInstruction,
            computeBudgetInstructions = computeBudgetInstructions,
            lookupTables = lookupTables,
            recentBlockhash = blockhashBytes
        )
    }

    private fun parseInstruction(ixJson: JSONObject): TransactionInstruction {
        val programId = PublicKey(ixJson.getString("programId"))
        val accountsArray = ixJson.getJSONArray("accounts")
        val keys = mutableListOf<AccountMeta>()

        for (i in 0 until accountsArray.length()) {
            val acc = accountsArray.getJSONObject(i)
            keys.add(
                AccountMeta(
                    pubkey = PublicKey(acc.getString("pubkey")),
                    isSigner = acc.getBoolean("isSigner"),
                    isWritable = acc.getBoolean("isWritable")
                )
            )
        }

        val dataBase64 = ixJson.getString("data")
        val data = Base64.getDecoder().decode(dataBase64)

        return TransactionInstruction(
            programId = programId,
            keys = keys,
            data = data
        )
    }
}
