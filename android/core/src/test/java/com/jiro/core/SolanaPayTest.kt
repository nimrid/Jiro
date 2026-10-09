package com.jiro.core

import org.junit.Assert.*
import org.junit.Test

class SolanaPayTest {

    private val merchant = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
    private val usdc = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

    @Test
    fun testValidSolanaPayUri() {
        val uri = "solana:$merchant?amount=10.5&spl-token=$usdc&label=Starbucks&message=Order%20123&memo=Receipt456"
        val req = SolanaPay.parse(uri)

        assertEquals(PublicKey(merchant), req.recipient)
        assertEquals(Money(10_500_000L), req.amount)
        assertEquals(PublicKey(usdc), req.splToken)
        assertEquals("Starbucks", req.label)
        assertEquals("Order 123", req.message)
        assertEquals("Receipt456", req.memo)
        assertTrue(req.references.isEmpty())
    }

    @Test
    fun testMultipleReferences() {
        val ref1 = "11111111111111111111111111111111"
        val ref2 = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        val uri = "solana:$merchant?reference=$ref1&reference=$ref2"
        val req = SolanaPay.parse(uri)

        assertEquals(2, req.references.size)
        assertEquals(PublicKey(ref1), req.references[0])
        assertEquals(PublicKey(ref2), req.references[1])
    }

    @Test(expected = SolanaPayError.InvalidScheme::class)
    fun testRejectInvalidScheme() {
        SolanaPay.parse("https://solanapay.com/$merchant")
    }

    @Test(expected = SolanaPayError.InvalidRecipient::class)
    fun testRejectInvalidRecipient() {
        SolanaPay.parse("solana:invalidPubKey123?amount=1")
    }

    @Test(expected = SolanaPayError.InvalidAmount::class)
    fun testRejectNegativeAmount() {
        SolanaPay.parse("solana:$merchant?amount=-5.00")
    }

    @Test(expected = SolanaPayError.AmbiguousUri::class)
    fun testRejectDuplicateAmount() {
        SolanaPay.parse("solana:$merchant?amount=5.00&amount=10.00")
    }
}
