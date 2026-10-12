package com.jiro.core

import org.junit.Assert.*
import org.junit.Test

class TransactionBuildersTest {

    private val user = PublicKey("9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM")
    private val merchant = PublicKey("11111111111111111111111111111111")
    private val usdc = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
    private val stockMint = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")
    private val dummyBlockhash = ByteArray(32) { 1 }

    @Test
    fun testPaymentTxBuilderSizeAndSerialization() {
        val message = PaymentTxBuilder.buildBundledPayment(
            user = user,
            merchant = merchant,
            usdcMint = usdc,
            paymentAmount = Money.parseUsdc("12.50"),
            roundUpAmount = Money.parseUsdc("0.50"),
            vaultId = 0.toByte(),
            recentBlockhash = dummyBlockhash,
            memoText = "Lunch"
        )

        assertNotNull(message)
        assertEquals(user, message.staticAccountKeys[0]) // Fee payer is first
        assertTrue(message.header.numRequiredSignatures >= 1)

        val wire = message.serializeWireTransaction()
        assertTrue(wire.size in 100..MessageV0.MAX_TRANSACTION_SIZE)
        // Check v0 prefix
        assertEquals(0x80.toByte(), message.serialize()[0])

        // Also test with initVaultIfNeeded = true
        val messageWithInit = PaymentTxBuilder.buildBundledPayment(
            user = user,
            merchant = merchant,
            usdcMint = usdc,
            paymentAmount = Money.parseUsdc("12.50"),
            roundUpAmount = Money.parseUsdc("0.50"),
            vaultId = 0.toByte(),
            recentBlockhash = dummyBlockhash,
            initVaultIfNeeded = true
        )
        assertNotNull(messageWithInit)
        val wireWithInit = messageWithInit.serializeWireTransaction()
        assertTrue(wireWithInit.size in 100..MessageV0.MAX_TRANSACTION_SIZE)
    }

    @Test
    fun testInvestTxBuilderSinglePlan() {
        val swapIx = Instructions.mockSwap(
            owner = user,
            stockMint = stockMint,
            usdcMint = usdc,
            usdcIn = 5_000_000L,
            minOut = 50_000L
        )

        val plan = InvestTxBuilder.buildInvestPlan(
            user = user,
            usdcMint = usdc,
            stockMint = stockMint,
            vaultId = 0.toByte(),
            investAmount = Money.parseUsdc("5.00"),
            minOut = 50_000L,
            swapInstruction = swapIx,
            recentBlockhash = dummyBlockhash
        )

        assertTrue(plan is TransactionPlan.Single)
        val single = plan as TransactionPlan.Single
        val wireSize = single.message.serializeWireTransaction().size
        assertTrue(wireSize <= MessageV0.MAX_TRANSACTION_SIZE)
    }

    @Test
    fun testSetupTxBuilder() {
        val stockMints = listOf(
            Pair(stockMint, PublicKey.TOKEN_PROGRAM_ID),
            Pair(PublicKey("2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a"), PublicKey.TOKEN_2022_PROGRAM_ID)
        )

        val txs = SetupTxBuilder.buildSetupTransactions(
            user = user,
            usdcMint = usdc,
            vaultId = 0.toByte(),
            maxBalance = 100_000_000L,
            stockMints = stockMints,
            recentBlockhash = dummyBlockhash
        )

        assertFalse(txs.isEmpty())
        for (tx in txs) {
            assertTrue(tx.serializeWireTransaction().size <= MessageV0.MAX_TRANSACTION_SIZE)
        }
    }
}
