package com.jiro.core

import org.junit.Assert.*
import org.junit.Test

class PreflightTest {

    @Test
    fun testBalancesPassWhenSufficient() {
        val res = PreflightChecker.checkBalancesForPayment(
            userUsdcBalance = 20_000_000L, // $20
            totalRequiredUsdc = 15_000_000L, // $15
            userSolLamports = 100_000_000L // 0.1 SOL
        )
        assertTrue(res is PreflightCheckResult.Passed)
    }

    @Test
    fun testBalancesFailWhenUsdcInsufficient() {
        val res = PreflightChecker.checkBalancesForPayment(
            userUsdcBalance = 10_000_000L, // $10
            totalRequiredUsdc = 15_000_000L, // $15
            userSolLamports = 100_000_000L
        )
        assertTrue(res is PreflightCheckResult.Failed)
        assertTrue((res as PreflightCheckResult.Failed).reason.contains("Insufficient USDC"))
    }

    @Test
    fun testBalancesFailWhenSolInsufficient() {
        val res = PreflightChecker.checkBalancesForPayment(
            userUsdcBalance = 20_000_000L,
            totalRequiredUsdc = 15_000_000L,
            userSolLamports = 100L // almost 0 SOL
        )
        assertTrue(res is PreflightCheckResult.Failed)
        assertTrue((res as PreflightCheckResult.Failed).reason.contains("Insufficient SOL"))
    }

    @Test
    fun testVaultCapCheck() {
        // Current $90, deposit $5, cap $100 -> Passed ($95 <= $100)
        val passRes = PreflightChecker.checkVaultCap(
            currentVaultBalance = 90_000_000L,
            depositAmount = 5_000_000L,
            maxBalance = 100_000_000L
        )
        assertTrue(passRes is PreflightCheckResult.Passed)

        // Current $98, deposit $5, cap $100 -> Failed ($103 > $100)
        val failRes = PreflightChecker.checkVaultCap(
            currentVaultBalance = 98_000_000L,
            depositAmount = 5_000_000L,
            maxBalance = 100_000_000L
        )
        assertTrue(failRes is PreflightCheckResult.Failed)
        assertTrue((failRes as PreflightCheckResult.Failed).reason.contains("exceed vault cap"))
    }

    @Test
    fun testQuoteFreshness() {
        val now = System.currentTimeMillis()
        // 10s old -> Fresh
        val freshRes = PreflightChecker.checkQuoteFreshness(now - 10_000L, now)
        assertTrue(freshRes is PreflightCheckResult.Passed)

        // 45s old -> Expired
        val staleRes = PreflightChecker.checkQuoteFreshness(now - 45_000L, now)
        assertTrue(staleRes is PreflightCheckResult.Failed)
        assertTrue((staleRes as PreflightCheckResult.Failed).reason.contains("expired"))
    }
}
