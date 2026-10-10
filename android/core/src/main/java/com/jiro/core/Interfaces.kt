package com.jiro.core

data class SwapQuote(
    val inputMint: PublicKey,
    val outputMint: PublicKey,
    val inputAmount: Money,
    val expectedOutputAmount: Long,
    val minOutputAmount: Long,
    val priceImpactBps: Int,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis(), ttlMs: Long = 30_000L): Boolean {
        return (nowMs - timestampMs) > ttlMs
    }
}

data class SwapPlan(
    val quote: SwapQuote,
    val setupInstructions: List<TransactionInstruction> = emptyList(),
    val swapInstruction: TransactionInstruction,
    val cleanupInstruction: TransactionInstruction? = null,
    val computeBudgetInstructions: List<TransactionInstruction> = emptyList(),
    val lookupTables: List<AddressLookupTableAccount> = emptyList(),
    val recentBlockhash: ByteArray? = null
)

interface SwapProvider {
    suspend fun getQuote(
        inputMint: PublicKey,
        outputMint: PublicKey,
        amount: Money,
        slippageBps: Int = 100
    ): SwapQuote

    suspend fun buildSwapPlan(
        user: PublicKey,
        quote: SwapQuote,
        slippageBps: Int = 100
    ): SwapPlan

    suspend fun buildSwapInstruction(
        user: PublicKey,
        quote: SwapQuote
    ): TransactionInstruction = buildSwapPlan(user, quote).swapInstruction
}

class MockSwapProvider(
    private val programId: PublicKey = PublicKey.VAULT_PROGRAM_ID,
    private val usdcMint: PublicKey = PublicKey.USDC_MINT_DEVNET,
    private val mockPriceMicroUsdcPerToken: Long = 100_000_000L // default $100
) : SwapProvider {

    override suspend fun getQuote(
        inputMint: PublicKey,
        outputMint: PublicKey,
        amount: Money,
        slippageBps: Int
    ): SwapQuote {
        // Output token base units: (usdc_in * 10^decimals) / price
        val usdcIn = amount.baseUnits
        val expectedOut = (usdcIn.toBigInteger() * 1_000_000.toBigInteger() / mockPriceMicroUsdcPerToken.toBigInteger()).toLong()
        val minOut = expectedOut - ((expectedOut * slippageBps.toLong()) / 10_000L)

        return SwapQuote(
            inputMint = inputMint,
            outputMint = outputMint,
            inputAmount = amount,
            expectedOutputAmount = expectedOut,
            minOutputAmount = minOut,
            priceImpactBps = 10,
            timestampMs = System.currentTimeMillis()
        )
    }

    override suspend fun buildSwapPlan(
        user: PublicKey,
        quote: SwapQuote,
        slippageBps: Int
    ): SwapPlan {
        val swapIx = Instructions.mockSwap(
            owner = user,
            stockMint = quote.outputMint,
            usdcMint = usdcMint,
            usdcIn = quote.inputAmount.baseUnits,
            minOut = quote.minOutputAmount,
            stockTokenProgram = PublicKey.TOKEN_PROGRAM_ID,
            programId = programId
        )
        return SwapPlan(
            quote = quote,
            swapInstruction = swapIx
        )
    }

    override suspend fun buildSwapInstruction(
        user: PublicKey,
        quote: SwapQuote
    ): TransactionInstruction {
        return buildSwapPlan(user, quote).swapInstruction
    }
}

interface PriceProvider {
    suspend fun getPriceInMicroUsdc(mint: PublicKey): Long
}

interface RoundUpSource {
    data class PaymentRecord(
        val id: String,
        val merchantName: String,
        val amount: Money,
        val timestampMs: Long
    )

    suspend fun getRecentPayments(): List<PaymentRecord>
}

interface EligibilityGate {
    fun isEligible(user: PublicKey): Boolean
}

class AlwaysEligibleGate : EligibilityGate {
    override fun isEligible(user: PublicKey): Boolean = true
}

enum class Cluster {
    LOCALNET,
    DEVNET,
    MAINNET
}

data class ClusterConfig(
    val cluster: Cluster,
    val rpcUrl: String,
    val vaultProgramId: PublicKey,
    val usdcMint: PublicKey
) {
    companion object {
        val DEVNET = ClusterConfig(
            cluster = Cluster.DEVNET,
            rpcUrl = "https://api.devnet.solana.com",
            vaultProgramId = PublicKey.VAULT_PROGRAM_ID,
            usdcMint = PublicKey.USDC_MINT_DEVNET
        )

        val MAINNET = ClusterConfig(
            cluster = Cluster.MAINNET,
            rpcUrl = "https://api.mainnet-beta.solana.com",
            vaultProgramId = PublicKey.VAULT_PROGRAM_ID,
            usdcMint = PublicKey.USDC_MINT_MAINNET
        )
    }
}

interface CatalogRepository {
    suspend fun getCatalog(): List<CatalogEntry>
    fun getSnapshotAgeMs(): Long
}

class FakeCatalogRepository(
    private val entries: List<CatalogEntry>
) : CatalogRepository {
    private val loadedAt = System.currentTimeMillis()

    override suspend fun getCatalog(): List<CatalogEntry> = entries
    override fun getSnapshotAgeMs(): Long = System.currentTimeMillis() - loadedAt
}
