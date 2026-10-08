package com.jiro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentTxBuilderTest {

    private val PROGRAM_ID = PublicKey("AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc")
    private val OWNER = PublicKey("9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM")
    private val USDC_MINT = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")
    private val MERCHANT = PublicKey("2WMvCS7UnQPk75qjqudfyoxnZx24Rcxsq58RZd2Zau5o")
    private val RECENT_BLOCKHASH = "EkSnNWid2cvwEVnVx9aBqawnmiCNiDcg3qwZVxWDauMe"

    @Test
    fun testShortVecEncoding() {
        val bytes0 = TransactionBuilder.encodeShortVec(0)
        assertEquals(1, bytes0.size)
        assertEquals(0.toByte(), bytes0[0])

        val bytes1 = TransactionBuilder.encodeShortVec(1)
        assertEquals(1, bytes1.size)
        assertEquals(1.toByte(), bytes1[0])

        val bytes128 = TransactionBuilder.encodeShortVec(128)
        assertEquals(2, bytes128.size)
        assertEquals(0x80.toByte(), bytes128[0])
        assertEquals(1.toByte(), bytes128[1])
    }

    @Test
    fun testBuildBundledPaymentTransactionUnderMtuLimit() {
        val paymentAmount = 33_500_000L // $33.50 USDC
        val roundUpAmount = RoundUp.calculate(paymentAmount) // $0.50 USDC = 500_000L

        val txBytes = PaymentTxBuilder.buildBundledPaymentTx(
            owner = OWNER,
            merchant = MERCHANT,
            usdcMint = USDC_MINT,
            programId = PROGRAM_ID,
            paymentAmount = paymentAmount,
            roundUpAmount = roundUpAmount,
            recentBlockhash = RECENT_BLOCKHASH
        )

        // Must be non-empty and well under 1232 bytes MTU
        assertTrue("Transaction size was ${txBytes.size}", txBytes.isNotEmpty())
        assertTrue("Transaction size ${txBytes.size} exceeds 1232 MTU limit", txBytes.size <= 1232)

        // First byte is number of signatures (1)
        assertEquals(1.toByte(), txBytes[0])
        // 64 bytes of placeholder signature follows (bytes 1..64)
        for (i in 1..64) {
            assertEquals(0.toByte(), txBytes[i])
        }

        // Header starts at offset 65:
        // num_required_signatures = 1
        assertEquals(1.toByte(), txBytes[65])
        // num_readonly_signed_accounts = 0
        assertEquals(0.toByte(), txBytes[66])
    }
}
