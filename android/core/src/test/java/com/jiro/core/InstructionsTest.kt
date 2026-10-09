package com.jiro.core

import org.junit.Assert.*
import org.junit.Test

class InstructionsTest {

    private val owner = PublicKey("9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM")
    private val mint = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

    @Test
    fun testInitVault0InstructionDataHex() {
        val ix = Instructions.initVault(
            owner = owner,
            mint = mint,
            vaultId = 0.toByte(),
            maxBalance = 50_000_000L
        )
        val hex = Instructions.bytesToHex(ix.data)
        assertEquals("4d4f559621d9346a0080f0fa0200000000", hex)
        assertEquals(17, ix.data.size)
        assertEquals(7, ix.keys.size)
        assertTrue(ix.keys[0].isSigner)
        assertTrue(ix.keys[0].isWritable)
        assertFalse(ix.keys[1].isSigner)
    }

    @Test
    fun testInitVault1InstructionDataHex() {
        val ix = Instructions.initVault(
            owner = owner,
            mint = mint,
            vaultId = 1.toByte(),
            maxBalance = 100_000_000L
        )
        val hex = Instructions.bytesToHex(ix.data)
        assertEquals("4d4f559621d9346a0100e1f50500000000", hex)
        assertEquals(17, ix.data.size)
    }

    @Test
    fun testDepositInstructionDataHex() {
        val ix = Instructions.deposit(
            owner = owner,
            mint = mint,
            vaultId = 0.toByte(),
            amount = 500_000L
        )
        val hex = Instructions.bytesToHex(ix.data)
        assertEquals("f223c68952e1f2b620a1070000000000", hex)
        assertEquals(16, ix.data.size)
        assertEquals(6, ix.keys.size)
    }

    @Test
    fun testWithdrawInstructionDataHex() {
        val ix = Instructions.withdraw(
            owner = owner,
            mint = mint,
            vaultId = 0.toByte(),
            amount = 500_000L
        )
        val hex = Instructions.bytesToHex(ix.data)
        assertEquals("b712469c946da12220a1070000000000", hex)
        assertEquals(16, ix.data.size)
        assertEquals(6, ix.keys.size)
    }

    @Test
    fun testCloseVaultInstructionDataHex() {
        val ix = Instructions.closeVault(
            owner = owner,
            mint = mint,
            vaultId = 0.toByte()
        )
        val hex = Instructions.bytesToHex(ix.data)
        assertEquals("8d67117e484b1d1d", hex)
        assertEquals(8, ix.data.size)
        assertEquals(5, ix.keys.size)
    }

    @Test
    fun testMockSwapInstructionDataHex() {
        val stockMint = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")
        val ix = Instructions.mockSwap(
            owner = owner,
            stockMint = stockMint,
            usdcMint = mint,
            usdcIn = 10_000_000L,
            minOut = 100_000L
        )
        val hex = Instructions.bytesToHex(ix.data)
        assertEquals("8476b98619386a0a8096980000000000a086010000000000", hex)
        assertEquals(24, ix.data.size)
        assertEquals(10, ix.keys.size)
    }

    @Test
    fun testTransferChecked() {
        val source = Ata.deriveAssociatedTokenAddress(owner, mint)
        val dest = Ata.deriveAssociatedTokenAddress(PublicKey.SYSTEM_PROGRAM_ID, mint)
        val ix = Instructions.transferChecked(
            source = source,
            mint = mint,
            destination = dest,
            authority = owner,
            amount = 1_000_000L,
            decimals = 6.toByte()
        )
        assertEquals(10, ix.data.size)
        assertEquals(12.toByte(), ix.data[0]) // TransferChecked index
        assertEquals(4, ix.keys.size)
    }

    @Test
    fun testCreateAtaIdempotent() {
        val ix = Instructions.createAssociatedTokenAccountIdempotent(
            payer = owner,
            walletAddress = owner,
            mint = mint
        )
        assertEquals(1, ix.data.size)
        assertEquals(1.toByte(), ix.data[0])
        assertEquals(6, ix.keys.size)
    }

    @Test
    fun testComputeBudgetAndMemo() {
        val limitIx = Instructions.setComputeUnitLimit(300_000)
        assertEquals(5, limitIx.data.size)
        assertEquals(2.toByte(), limitIx.data[0])

        val priceIx = Instructions.setComputeUnitPrice(10_000L)
        assertEquals(9, priceIx.data.size)
        assertEquals(3.toByte(), priceIx.data[0])

        val memoIx = Instructions.memo("JIRO Round-Up")
        assertEquals("JIRO Round-Up", String(memoIx.data, Charsets.UTF_8))
    }
}
