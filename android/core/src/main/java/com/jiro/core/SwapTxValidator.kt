package com.jiro.core

sealed class SwapValidationError(message: String) : Exception(message) {
    class WrongFeePayer(message: String) : SwapValidationError(message)
    class DisallowedProgram(message: String) : SwapValidationError(message)
    class ForbiddenInstruction(message: String) : SwapValidationError(message)
    class SimulationFailed(message: String) : SwapValidationError(message)
    class UsdcDeltaMismatch(message: String) : SwapValidationError(message)
    class SlippageViolation(message: String) : SwapValidationError(message)
    class UnauthorizedTokenDrain(message: String) : SwapValidationError(message)
    class ExcessiveSolDecrease(message: String) : SwapValidationError(message)
}

data class SimulatedTokenBalance(
    val account: PublicKey,
    val mint: PublicKey,
    val owner: PublicKey,
    val preAmount: Long,
    val postAmount: Long
) {
    val delta: Long get() = postAmount - preAmount
}

data class SimulationResult(
    val err: String?,
    val preSolLamports: Long,
    val postSolLamports: Long,
    val tokenBalances: List<SimulatedTokenBalance>
)

object SwapTxValidator {

    val JUPITER_PROGRAM_ID = PublicKey("JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4")

    val ALLOWED_PROGRAMS = setOf(
        PublicKey.COMPUTE_BUDGET_PROGRAM_ID,
        PublicKey.SYSTEM_PROGRAM_ID,
        PublicKey.TOKEN_PROGRAM_ID,
        PublicKey.TOKEN_2022_PROGRAM_ID,
        PublicKey.ASSOCIATED_TOKEN_PROGRAM_ID,
        PublicKey.MEMO_PROGRAM_ID,
        PublicKey.VAULT_PROGRAM_ID,
        JUPITER_PROGRAM_ID
    )

    // Token instruction indexes
    const val IX_APPROVE = 4.toByte()
    const val IX_SET_AUTHORITY = 6.toByte()
    const val IX_CLOSE_ACCOUNT = 9.toByte()
    const val IX_APPROVE_CHECKED = 13.toByte()

    /**
     * Statically inspects the compiled transaction before simulating or prompting wallet.
     */
    fun validateStatic(
        message: MessageV0,
        user: PublicKey
    ) {
        // 1. Fee payer must be user (first account key in static keys)
        val feePayer = message.staticAccountKeys.firstOrNull()
            ?: throw SwapValidationError.WrongFeePayer("Message has no static account keys")
        if (feePayer != user) {
            throw SwapValidationError.WrongFeePayer(
                "Fee payer must be user (${user.toBase58()}), got: ${feePayer.toBase58()}"
            )
        }

        // 2. All invoked programs must be in the allowlist
        for (ix in message.compiledInstructions) {
            val programKey = message.staticAccountKeys.getOrNull(ix.programIdIndex)
                ?: throw SwapValidationError.DisallowedProgram("Invalid program index: ${ix.programIdIndex}")

            if (!ALLOWED_PROGRAMS.contains(programKey)) {
                throw SwapValidationError.DisallowedProgram(
                    "Program ${programKey.toBase58()} is not in the security allowlist"
                )
            }

            // 3. Inspect Token program instructions for forbidden operations
            if (programKey == PublicKey.TOKEN_PROGRAM_ID || programKey == PublicKey.TOKEN_2022_PROGRAM_ID) {
                if (ix.data.isNotEmpty()) {
                    when (ix.data[0]) {
                        IX_APPROVE, IX_APPROVE_CHECKED -> {
                            throw SwapValidationError.ForbiddenInstruction(
                                "Approve instruction is forbidden on user accounts"
                            )
                        }
                        IX_SET_AUTHORITY -> {
                            throw SwapValidationError.ForbiddenInstruction(
                                "SetAuthority instruction is forbidden on user accounts"
                            )
                        }
                        IX_CLOSE_ACCOUNT -> {
                            // Close account is only allowed for temp accounts (e.g. temp wSOL) where lamports are refunded to user
                            val destinationIdx = ix.accountKeyIndexes.getOrNull(1)
                                ?: throw SwapValidationError.ForbiddenInstruction("CloseAccount missing destination account")
                            val destinationKey = message.staticAccountKeys.getOrNull(destinationIdx)
                            if (destinationKey != user) {
                                throw SwapValidationError.ForbiddenInstruction(
                                    "CloseAccount destination must be user to refund lamports, got: ${destinationKey?.toBase58()}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Statically inspects all transactions in a TransactionPlan (Single or TwoStep).
     */
    fun validatePlan(
        plan: TransactionPlan,
        user: PublicKey
    ) {
        when (plan) {
            is TransactionPlan.Single -> validateStatic(plan.message, user)
            is TransactionPlan.TwoStep -> {
                validateStatic(plan.step1, user)
                validateStatic(plan.step2, user)
            }
        }
    }

    /**
     * Validates simulation results to enforce exact balance delta invariants.
     */
    fun validateSimulationDeltas(
        user: PublicKey,
        usdcMint: PublicKey,
        stockMint: PublicKey,
        expectedUsdcIn: Long,
        minStockOut: Long,
        maxSolFeeLamports: Long,
        simulation: SimulationResult
    ) {
        if (simulation.err != null) {
            throw SwapValidationError.SimulationFailed("Simulation returned on-chain error: ${simulation.err}")
        }

        // Assert SOL decrease <= maxSolFeeLamports
        val solDecrease = simulation.preSolLamports - simulation.postSolLamports
        if (solDecrease > maxSolFeeLamports) {
            throw SwapValidationError.ExcessiveSolDecrease(
                "SOL decrease ($solDecrease lamports) exceeds maximum allowed ($maxSolFeeLamports lamports)"
            )
        }

        var foundUsdc = false
        var foundStock = false

        for (tb in simulation.tokenBalances) {
            if (tb.owner != user) continue

            when (tb.mint) {
                usdcMint -> {
                    foundUsdc = true
                    if (tb.delta != -expectedUsdcIn) {
                        throw SwapValidationError.UsdcDeltaMismatch(
                            "USDC delta mismatch: expected -$expectedUsdcIn, got ${tb.delta}"
                        )
                    }
                }
                stockMint -> {
                    foundStock = true
                    if (tb.delta < minStockOut) {
                        throw SwapValidationError.SlippageViolation(
                            "Stock output delta ${tb.delta} is less than minimum quoted $minStockOut"
                        )
                    }
                }
                else -> {
                    // For any other token owned by the user, balance must not decrease!
                    if (tb.delta < 0L) {
                        throw SwapValidationError.UnauthorizedTokenDrain(
                            "Unauthorized token drain: mint ${tb.mint.toBase58()} decreased by ${-tb.delta}"
                        )
                    }
                }
            }
        }

        if (!foundUsdc && expectedUsdcIn > 0) {
            throw SwapValidationError.UsdcDeltaMismatch("No USDC balance change detected in simulation")
        }

        if (!foundStock && minStockOut > 0) {
            throw SwapValidationError.SlippageViolation("No stock token output balance change detected in simulation")
        }
    }
}
