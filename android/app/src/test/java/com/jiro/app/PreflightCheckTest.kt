package com.jiro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightCheckTest {

    @Test
    fun testValidPreflightPasses() {
        val res = PreflightCheck.validateBundledPayment(
            ownerUsdcBalance = 50_000_000L, // $50
            ownerSolBalance = 1_000_000L,   // 0.001 SOL
            vaultUsdcBalance = 2_000_000L,  // $2
            paymentAmount = 10_000_000L,    // $10
            roundUpAmount = 500_000L        // $0.50
        )
        assertTrue(res is PreflightCheck.PreflightResult.Ok)
    }

    @Test
    fun testInsufficientUsdcFails() {
        val res = PreflightCheck.validateBundledPayment(
            ownerUsdcBalance = 5_000_000L,  // $5
            ownerSolBalance = 1_000_000L,
            vaultUsdcBalance = 0L,
            paymentAmount = 10_000_000L,    // $10
            roundUpAmount = 500_000L
        )
        assertTrue(res is PreflightCheck.PreflightResult.Error)
        val msg = (res as PreflightCheck.PreflightResult.Error).message
        assertTrue(msg.contains("Insufficient USDC balance"))
    }

    @Test
    fun testZeroSolBalanceFails() {
        val res = PreflightCheck.validateBundledPayment(
            ownerUsdcBalance = 50_000_000L,
            ownerSolBalance = 0L,
            vaultUsdcBalance = 0L,
            paymentAmount = 10_000_000L,
            roundUpAmount = 500_000L
        )
        assertTrue(res is PreflightCheck.PreflightResult.Error)
        val msg = (res as PreflightCheck.PreflightResult.Error).message
        assertTrue(msg.contains("Insufficient SOL balance"))
    }

    @Test
    fun testVaultHardCapExceededFails() {
        val res = PreflightCheck.validateBundledPayment(
            ownerUsdcBalance = 50_000_000L,
            ownerSolBalance = 1_000_000L,
            vaultUsdcBalance = 99_800_000L, // $99.80
            paymentAmount = 10_000_000L,
            roundUpAmount = 500_000L,        // $0.50 -> would push to $100.30 > $100 hard cap
            maxBalanceCap = 100_000_000L
        )
        assertTrue(res is PreflightCheck.PreflightResult.Error)
        val msg = (res as PreflightCheck.PreflightResult.Error).message
        assertTrue(msg.contains("exceeds vault cap"))
    }
}
