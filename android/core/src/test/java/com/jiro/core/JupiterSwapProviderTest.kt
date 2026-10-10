package com.jiro.core

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.InputStreamReader

class JupiterSwapProviderTest {

    private val user = PublicKey("BBNyzG9Kn1xf8ZFbwK2nKr3XW4MGr4XE8pQ9iJ1rsi57")
    private val usdcMint = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
    private val wsolMint = PublicKey("So11111111111111111111111111111111111111112")
    private val dummyRecentBlockhash = ByteArray(32) { 1 }

    private fun loadFixtureJson(): JSONObject {
        val stream = javaClass.classLoader.getResourceAsStream("fixtures/jupiter_build_v2.json")
            ?: throw IllegalStateException("Fixture fixtures/jupiter_build_v2.json not found")
        val content = InputStreamReader(stream).use { it.readText() }
        return JSONObject(content)
    }

    @Test
    fun testParseBuildResponseFromRecordedFixture() {
        val json = loadFixtureJson()
        val provider = JupiterSwapProvider()

        val quote = provider.parseQuoteFromJson(
            json = json,
            inputMint = usdcMint,
            outputMint = wsolMint,
            amount = Money.fromDollars(5L)
        )

        assertEquals(usdcMint, quote.inputMint)
        assertEquals(wsolMint, quote.outputMint)
        assertEquals(5_000_000L, quote.inputAmount.baseUnits)
        assertEquals(45_561_796L, quote.expectedOutputAmount)
        assertEquals(0, quote.priceImpactBps)
        assertFalse(quote.isExpired())

        val plan = provider.parseBuildResponse(json, quote)
        assertEquals(1, plan.setupInstructions.size)
        assertEquals(
            PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"),
            plan.setupInstructions[0].programId
        )

        assertEquals(
            PublicKey("JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4"),
            plan.swapInstruction.programId
        )
        assertEquals(46, plan.swapInstruction.keys.size)

        assertNotNull(plan.cleanupInstruction)
        assertEquals(
            PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"),
            plan.cleanupInstruction!!.programId
        )

        assertEquals(1, plan.computeBudgetInstructions.size)
        assertEquals(
            PublicKey.COMPUTE_BUDGET_PROGRAM_ID,
            plan.computeBudgetInstructions[0].programId
        )

        assertEquals(2, plan.lookupTables.size)
        assertEquals(252, plan.lookupTables[0].addresses.size)
        assertEquals(252, plan.lookupTables[1].addresses.size)

        assertNotNull(plan.recentBlockhash)
        assertEquals(32, plan.recentBlockhash!!.size)
    }

    @Test
    fun testInvestTxBuilderComposesAtomicPlanWithALTs() {
        val json = loadFixtureJson()
        val provider = JupiterSwapProvider()
        val quote = provider.parseQuoteFromJson(
            json = json,
            inputMint = usdcMint,
            outputMint = wsolMint,
            amount = Money.fromDollars(5L)
        )
        val swapPlan = provider.parseBuildResponse(json, quote)

        val plan = InvestTxBuilder.buildInvestPlan(
            user = user,
            usdcMint = usdcMint,
            stockMint = wsolMint,
            vaultId = 0,
            investAmount = Money.fromDollars(5L),
            swapPlan = swapPlan,
            recentBlockhash = swapPlan.recentBlockhash!!
        )

        // Must succeed as a single atomic transaction thanks to Address Lookup Tables
        assertTrue(
            "Expected Single atomic transaction plan with ALTs, but got $plan",
            plan is TransactionPlan.Single
        )

        val singlePlan = plan as TransactionPlan.Single
        val wireBytes = singlePlan.message.serializeWireTransaction()
        assertTrue(
            "Transaction size (${wireBytes.size} bytes) must be <= 1232 bytes",
            wireBytes.size <= MessageV0.MAX_TRANSACTION_SIZE
        )

        // Security validation: Must pass static inspection without errors
        SwapTxValidator.validatePlan(plan, user)
    }

    @Test
    fun testQuotePolicyExpiryRejection() {
        val oldQuote = SwapQuote(
            inputMint = usdcMint,
            outputMint = wsolMint,
            inputAmount = Money.fromDollars(5L),
            expectedOutputAmount = 1_000_000L,
            minOutputAmount = 990_000L,
            priceImpactBps = 10,
            timestampMs = System.currentTimeMillis() - 35_000L // 35 seconds ago (> 30s TTL)
        )

        assertTrue(oldQuote.isExpired(ttlMs = 30_000L))

        val provider = JupiterSwapProvider()
        try {
            runBlocking {
                provider.buildSwapPlan(user, oldQuote, slippageBps = 50)
            }
            fail("Expected QuoteExpiredException")
        } catch (e: JupiterException.QuoteExpiredException) {
            assertTrue(e.message!!.contains("expired"))
        }
    }

    @Test
    fun testQuotePolicyPriceImpactCapRejection() {
        val highImpactJson = JSONObject().apply {
            put("outAmount", "40000000")
            put("otherAmountThreshold", "39000000")
            put("priceImpactPct", 1.85) // 1.85% = 185 bps (cap is 100 bps)
        }

        val provider = JupiterSwapProvider(maxPriceImpactBps = 100)
        try {
            provider.parseQuoteFromJson(
                json = highImpactJson,
                inputMint = usdcMint,
                outputMint = wsolMint,
                amount = Money.fromDollars(5L)
            )
            fail("Expected PriceImpactExceededException")
        } catch (e: JupiterException.PriceImpactExceededException) {
            assertEquals(185, e.impactBps)
            assertEquals(100, e.maxImpactBps)
        }
    }

    @Test
    fun testQuotePolicyOutputProgramMismatch() {
        val fakeCatalogEntry = CatalogEntry(
            symbol = "AAPL.US",
            name = "Apple",
            mint = PublicKey("AAPLEDt8RpzPgXyhvFzkMBofvFSQw9gpeMCoUdPdLnB8"),
            tokenProgram = PublicKey.TOKEN_2022_PROGRAM_ID,
            decimals = 6,
            extensions = emptyList(),
            extensionFingerprint = "test",
            buyable = BuyableStatus(true, emptyList()),
            policy = PolicyStatus("OK", emptyList(), emptyList()),
            ataRentLamports = 2157600L,
            marketStatus = "open"
        )

        // Route output program is standard Token program instead of Token-2022
        try {
            QuotePolicy.verifyOutputProgram(
                catalogEntry = fakeCatalogEntry,
                actualProgram = PublicKey.TOKEN_PROGRAM_ID
            )
            fail("Expected OutputProgramMismatchException")
        } catch (e: JupiterException.OutputProgramMismatchException) {
            assertEquals(PublicKey.TOKEN_2022_PROGRAM_ID, e.expectedProgram)
            assertEquals(PublicKey.TOKEN_PROGRAM_ID, e.actualProgram)
        }
    }

    @Test
    fun testQuotePolicyTransferFeeDeduction() {
        val rawExpected = 10_000_000L
        val effectiveNoFee = QuotePolicy.calculateEffectiveOutput(rawExpected, transferFeeBps = 0)
        assertEquals(rawExpected, effectiveNoFee)

        // 100 bps (1%) transfer fee
        val effectiveWithFee = QuotePolicy.calculateEffectiveOutput(rawExpected, transferFeeBps = 100)
        assertEquals(9_900_000L, effectiveWithFee)
    }

    @Test
    fun testTwoStepFallbackWhenTransactionExceedsLimit() {
        // Create an artificially oversized swap plan with many static accounts and big data
        val manyAccounts = (0 until 22).map { accIndex ->
            val keyBytes = ByteArray(32) { 0 }
            keyBytes[0] = (accIndex + 1).toByte()
            AccountMeta(PublicKey(keyBytes), isSigner = false, isWritable = true)
        }
        val hugeSwapIx = TransactionInstruction(
            programId = PublicKey.TOKEN_PROGRAM_ID,
            keys = manyAccounts,
            data = ByteArray(150) { 0x55 }
        )
        val swapPlan = SwapPlan(
            quote = SwapQuote(
                inputMint = usdcMint,
                outputMint = wsolMint,
                inputAmount = Money.fromDollars(5L),
                expectedOutputAmount = 100_000L,
                minOutputAmount = 90_000L,
                priceImpactBps = 10
            ),
            swapInstruction = hugeSwapIx,
            lookupTables = emptyList() // No ALTs forces static accounts
        )

        val plan = InvestTxBuilder.buildInvestPlan(
            user = user,
            usdcMint = usdcMint,
            stockMint = wsolMint,
            vaultId = 0,
            investAmount = Money.fromDollars(5L),
            swapPlan = swapPlan,
            recentBlockhash = dummyRecentBlockhash
        )

        assertTrue(
            "Expected TwoStep plan due to size overflow, got $plan",
            plan is TransactionPlan.TwoStep
        )

        val twoStep = plan as TransactionPlan.TwoStep
        assertTrue(twoStep.step1.serializeWireTransaction().size <= MessageV0.MAX_TRANSACTION_SIZE)
        assertTrue(twoStep.step2.serializeWireTransaction().size <= MessageV0.MAX_TRANSACTION_SIZE)

        // Both steps must pass safety validation
        SwapTxValidator.validatePlan(plan, user)
    }

    @Test
    fun testPartialFailureRecoveryState() {
        val failure = InvestState.Failure.PartialFailure(
            withdrawnAmount = Money.fromDollars(5L),
            step1Signature = "5ABC...XYZ",
            step2Error = "Route expired during swap execution",
            canRetrySwap = true
        )

        assertTrue(failure.canRetrySwap)
        assertTrue(failure.recoveryMessage.contains("Jar withdrawal of $5.00 succeeded"))
        assertTrue(failure.recoveryMessage.contains("USDC wallet"))
    }

    @Test
    fun testLiveJupiterApiCallWhenFlagEnabled() {
        val runLive = System.getenv("RUN_LIVE_TESTS") == "true" ||
                System.getProperty("live") == "true"

        // Also check if .env file in parent has JUPITER_API_KEY
        val envFile = File("../../.env")
        val apiKeyFromEnv = if (envFile.exists()) {
            envFile.readLines()
                .find { it.startsWith("JUPITER_API_KEY=") }
                ?.substringAfter("JUPITER_API_KEY=")
                ?.trim()
        } else null

        val apiKey = System.getenv("JUPITER_API_KEY") ?: apiKeyFromEnv ?: ""

        if (!runLive && apiKey.isBlank()) {
            println("Skipping live test (set RUN_LIVE_TESTS=true or provide JUPITER_API_KEY)")
            return
        }

        println("Running live Jupiter test with API key: ${apiKey.take(8)}...")
        val provider = JupiterSwapProvider(apiKey = apiKey)

        runBlocking {
            // Live quote USDC -> SOL ($5)
            val quote = provider.getQuote(
                inputMint = usdcMint,
                outputMint = wsolMint,
                amount = Money.fromDollars(5L),
                slippageBps = 50
            )

            assertTrue(quote.expectedOutputAmount > 0)
            assertTrue(quote.minOutputAmount > 0)
            assertFalse(quote.isExpired())

            val plan = provider.buildSwapPlan(
                user = user,
                quote = quote,
                slippageBps = 50
            )

            assertNotNull(plan.swapInstruction)
            assertTrue(plan.lookupTables.isNotEmpty())

            val investPlan = InvestTxBuilder.buildInvestPlan(
                user = user,
                usdcMint = usdcMint,
                stockMint = wsolMint,
                vaultId = 0,
                investAmount = Money.fromDollars(5L),
                swapPlan = plan,
                recentBlockhash = plan.recentBlockhash ?: dummyRecentBlockhash
            )

            SwapTxValidator.validatePlan(investPlan, user)
        }
    }
}
