package com.jiro.app

object PreflightCheck {
    const val HARD_MAX_BALANCE: Long = 100_000_000L // 100 USDC hard cap

    sealed class PreflightResult {
        object Ok : PreflightResult()
        data class Error(val message: String) : PreflightResult()
    }

    /**
     * Preflight validation before wallet signing.
     */
    fun validateBundledPayment(
        ownerUsdcBalance: Long,
        ownerSolBalance: Long,
        vaultUsdcBalance: Long,
        paymentAmount: Long,
        roundUpAmount: Long,
        maxBalanceCap: Long = HARD_MAX_BALANCE
    ): PreflightResult {
        if (paymentAmount <= 0L) {
            return PreflightResult.Error("Payment amount must be greater than zero")
        }

        val totalUsdcNeeded = paymentAmount + roundUpAmount
        if (ownerUsdcBalance < totalUsdcNeeded) {
            val neededStr = RoundUp.formatUsdc(totalUsdcNeeded)
            val currentStr = RoundUp.formatUsdc(ownerUsdcBalance)
            return PreflightResult.Error("Insufficient USDC balance. Required: $$neededStr, available: $$currentStr")
        }

        if (ownerSolBalance <= 0L) {
            return PreflightResult.Error("Insufficient SOL balance for transaction fee. Please fund wallet with Devnet SOL.")
        }

        if (roundUpAmount > 0L) {
            val projectedVaultBalance = vaultUsdcBalance + roundUpAmount
            if (projectedVaultBalance > maxBalanceCap) {
                return PreflightResult.Error(
                    "Deposit of $${RoundUp.formatUsdc(roundUpAmount)} exceeds vault cap of $${RoundUp.formatUsdc(maxBalanceCap)} (current: $${RoundUp.formatUsdc(vaultUsdcBalance)})"
                )
            }
        }

        return PreflightResult.Ok
    }
}
