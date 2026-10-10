package com.jiro.core

/**
 * Quote policy rules and validation per JIRO security architecture:
 * - 30s TTL expiry before signing
 * - Max price impact cap (100 bps / 1.0%)
 * - Max slippage cap (100 bps / 1.0%)
 * - Output mint program verification against verified catalog
 * - Transfer fee and scaled UI adjustments
 */
object QuotePolicy {
    const val MAX_PRICE_IMPACT_BPS = 100 // 1.0% cap
    const val MAX_SLIPPAGE_BPS = 100 // 1.0% cap
    const val QUOTE_TTL_MS = 30_000L // 30s TTL

    /**
     * Rejects quotes whose output mint program does not match the catalog entry.
     */
    fun verifyOutputProgram(catalogEntry: CatalogEntry, actualProgram: PublicKey) {
        if (catalogEntry.tokenProgram != actualProgram) {
            throw JupiterException.OutputProgramMismatchException(
                "Output token program ${actualProgram.toBase58()} does not match catalog token program ${catalogEntry.tokenProgram.toBase58()}",
                expectedProgram = catalogEntry.tokenProgram,
                actualProgram = actualProgram
            )
        }
    }

    /**
     * Calculates net expected output accounting for transfer fee extensions.
     */
    fun calculateEffectiveOutput(expectedOutput: Long, transferFeeBps: Int = 0): Long {
        if (transferFeeBps <= 0) return expectedOutput
        val feeAmount = (expectedOutput * transferFeeBps.toLong()) / 10_000L
        return (expectedOutput - feeAmount).coerceAtLeast(0L)
    }
}

/**
 * Investing flow execution states and failure recovery.
 */
sealed class InvestState {
    data object Idle : InvestState()
    data class FetchingQuote(val targetStock: CatalogEntry, val amount: Money) : InvestState()
    data class QuoteReady(val plan: TransactionPlan, val quote: SwapQuote) : InvestState()
    data class Simulating(val plan: TransactionPlan) : InvestState()
    data class AwaitingWalletApproval(val plan: TransactionPlan) : InvestState()
    data class ExecutingSingle(val tx: MessageV0) : InvestState()
    data class ExecutingTwoStepWithdraw(val step1Tx: MessageV0) : InvestState()
    data class ExecutingTwoStepSwap(val step2Tx: MessageV0, val withdrawnAmount: Money) : InvestState()
    data class Success(val signature: String, val investedAmount: Money, val receivedTokens: Long) : InvestState()

    sealed class Failure : InvestState() {
        data class RouteDisappeared(val reason: String) : Failure()
        data class QuoteExpired(val reason: String) : Failure()
        data class BlockhashExpired(val reason: String) : Failure()
        data class SimulationMismatch(val reason: String) : Failure()
        data class UserDeclined(val reason: String = "User declined transaction") : Failure()

        /**
         * Partial failure in a two-transaction plan:
         * Step 1 (vault withdraw) succeeded and confirmed on-chain,
         * but Step 2 (Jupiter swap) failed or was aborted.
         *
         * Funds are guaranteed safe in the user's wallet USDC account.
         */
        data class PartialFailure(
            val withdrawnAmount: Money,
            val step1Signature: String,
            val step2Error: String,
            val canRetrySwap: Boolean = true
        ) : Failure() {
            val recoveryMessage: String
                get() = "Jar withdrawal of ${withdrawnAmount.formatUsdc()} succeeded (tx: $step1Signature), but the stock swap failed: $step2Error. Funds are currently in your USDC wallet."
        }
    }
}
