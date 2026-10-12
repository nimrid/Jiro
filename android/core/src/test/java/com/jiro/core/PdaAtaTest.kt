package com.jiro.core

import org.junit.Assert.*
import org.junit.Test

class PdaAtaTest {

    private val owner = PublicKey("9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM")
    private val programId = PublicKey("AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc")
    private val usdcMainnet = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
    private val usdcDevnet = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")

    @Test
    fun testVault0DerivationAgainstGoldenVectors() {
        val (vaultPda0, bump0) = Pda.deriveVaultPda(owner, 0.toByte(), programId)
        assertEquals("9n5xJe4G7n7H4exGHT1y6wVzqyEVBWB3CRVjd6ty9n4A", vaultPda0.toBase58())
        assertEquals(254, bump0)

        // Mainnet ATA
        val ataMainnet0 = Ata.deriveAssociatedTokenAddress(vaultPda0, usdcMainnet)
        assertEquals("G5W8KvAB1DMa6Nxzuseu44eTV31e2Z3gkyTMrRu8ekz8", ataMainnet0.toBase58())

        // Devnet ATA
        val ataDevnet0 = Ata.deriveAssociatedTokenAddress(vaultPda0, usdcDevnet)
        assertEquals("HgGYh3Ei2XbwdZ2ahf2WwEu7n3oFiQNj9Z2cnonJCu2j", ataDevnet0.toBase58())
    }

    @Test
    fun testVault1DerivationAgainstGoldenVectors() {
        val (vaultPda1, bump1) = Pda.deriveVaultPda(owner, 1.toByte(), programId)
        assertEquals("7gLhqhcPfJGdfohCtpoGJB5YCJkYJSQncGhgFJGBB6rw", vaultPda1.toBase58())
        assertEquals(255, bump1)

        // Mainnet ATA
        val ataMainnet1 = Ata.deriveAssociatedTokenAddress(vaultPda1, usdcMainnet)
        assertEquals("HPLXox1W9456zFcgCn7oRuubYyiPFxW1ENp2REMmDSxv", ataMainnet1.toBase58())

        // Devnet ATA
        val ataDevnet1 = Ata.deriveAssociatedTokenAddress(vaultPda1, usdcDevnet)
        assertEquals("CkTT855PkF9AGXeFQd6V1eVQeRB3tFYoi6Ps2YKUWmVp", ataDevnet1.toBase58())
    }

    @Test
    fun testToken2022AtaDerivation() {
        // Mock stock token using Token-2022 program
        val stockMint = PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")
        val userAta = Ata.deriveAssociatedTokenAddress(
            walletAddress = owner,
            mint = stockMint,
            tokenProgramId = PublicKey.TOKEN_2022_PROGRAM_ID
        )
        assertNotNull(userAta)
        assertEquals(32, userAta.pubkey.size)
    }

    @Test
    fun testMockPdaDerivation() {
        val stockMint = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")
        val (mockPda, bump) = Pda.deriveMockPda(stockMint, programId)
        assertNotNull(mockPda)
        assertTrue(bump in 0..255)
    }

    @Test
    fun testConnectedWalletAta() {
        val user = PublicKey("D83F1fDwmcuaJDrjnq6ECkUCvSaYyuHSpcVBmqoQ2a2F")
        val ata = Ata.deriveAssociatedTokenAddress(user, usdcDevnet)
        assertEquals("A21C9ZqgxHyGtcpg9yysFjcaCKuvadYnDz28khvvihZj", ata.toBase58())
    }
}
