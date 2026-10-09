package com.jiro.core

sealed class PreflightCheckResult {
    data object Passed : PreflightCheckResult()
    data class Failed(val reason: String) : PreflightCheckResult()
}

object PreflightChecker {

    const val MIN_SOL_FOR_TX_FEE_LAMPORTS = 5_000L
    const val ESTIMATED_ATA_RENT_LAMPORTS = 2_157_600L
    const val MAX_QUOTE_AGE_MS = 30_000L // 30 seconds
    const val MAX_CATALOG_AGE_MS = 86_400_000L // 24 hours

    fun checkBalancesForPayment(
        userUsdcBalance: Long,
        totalRequiredUsdc: Long,
        userSolLamports: Long,
        estimatedFeesLamports: Long = 10_000L
    ): PreflightCheckResult {
        if (userUsdcBalance < totalRequiredUsdc) {
            val missing = totalRequiredUsdc - userUsdcBalance
            return PreflightCheckResult.Failed(
                "Insufficient USDC: need ${Money(totalRequiredUsdc).formatUsdc()}, missing ${Money(missing).formatUsdc()}"
            )
        }

        if (userSolLamports < estimatedFeesLamports) {
            return PreflightCheckResult.Failed(
                "Insufficient SOL to cover transaction network fees ($userSolLamports < $estimatedFeesLamports lamports)"
            )
        }

        return PreflightCheckResult.Passed
    }

    fun checkVaultCap(
        currentVaultBalance: Long,
        depositAmount: Long,
        maxBalance: Long
    ): PreflightCheckResult {
        val newBalance = currentVaultBalance + depositAmount
        if (newBalance > maxBalance) {
            return PreflightCheckResult.Failed(
                "Deposit would exceed vault cap: current ${Money(currentVaultBalance)}, deposit ${Money(depositAmount)}, cap ${Money(maxBalance)}"
            )
        }
        return PreflightCheckResult.Passed
    }

    fun checkQuoteFreshness(
        quoteTimestampMs: Long,
        currentTimeMs: Long = System.currentTimeMillis()
    ): PreflightCheckResult {
        val age = currentTimeMs - quoteTimestampMs
        if (age > MAX_QUOTE_AGE_MS) {
            return PreflightCheckResult.Failed(
                "Swap quote expired (${age / 1000}s old > max ${MAX_QUOTE_AGE_MS / 1000}s). Refreshing quote..."
            )
        }
        return PreflightCheckResult.Passed
    }
}
