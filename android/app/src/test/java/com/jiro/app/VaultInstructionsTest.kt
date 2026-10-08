package com.jiro.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultInstructionsTest {

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private val PROGRAM_ID = PublicKey("AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc")
    private val OWNER = PublicKey("9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM")
    private val USDC_MINT = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")
    private val VAULT_PDA = PublicKey("AtDBHZDyUj85XWBywGTQTqeeMKvoN1fWubYuZfSqfc33")
    private val VAULT_ATA = PublicKey("6N37n5oDiscZACmxpNQD5GbwRwPP3rhH3Yfj3HtZf3qF")
    private val OWNER_ATA = PublicKey("9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM") // dummy for test

    @Test
    fun testDepositInstructionMatchesGoldenFixture() {
        val amount = 500000L // 0.50 USDC
        val ix = VaultInstructions.createDepositInstruction(
            programId = PROGRAM_ID,
            owner = OWNER,
            vaultConfig = VAULT_PDA,
            mint = USDC_MINT,
            vaultTokenAccount = VAULT_ATA,
            ownerTokenAccount = OWNER_ATA,
            amount = amount
        )

        val expectedHex = "f223c68952e1f2b620a1070000000000"
        assertEquals(expectedHex, ix.data.toHex())
        assertEquals(16, ix.data.size)
        assertEquals(6, ix.accounts.size)

        // Verify account ordering and permissions
        // 0: owner (signer, writable)
        assertEquals(OWNER, ix.accounts[0].publicKey)
        assertEquals(true, ix.accounts[0].isSigner)
        assertEquals(true, ix.accounts[0].isWritable)

        // 1: vault_config (readonly)
        assertEquals(VAULT_PDA, ix.accounts[1].publicKey)
        assertEquals(false, ix.accounts[1].isSigner)
        assertEquals(false, ix.accounts[1].isWritable)

        // 2: mint (readonly)
        assertEquals(USDC_MINT, ix.accounts[2].publicKey)
        assertEquals(false, ix.accounts[2].isSigner)
        assertEquals(false, ix.accounts[2].isWritable)

        // 3: vault_token_account (writable)
        assertEquals(VAULT_ATA, ix.accounts[3].publicKey)
        assertEquals(false, ix.accounts[3].isSigner)
        assertEquals(true, ix.accounts[3].isWritable)

        // 4: owner_token_account (writable)
        assertEquals(OWNER_ATA, ix.accounts[4].publicKey)
        assertEquals(false, ix.accounts[4].isSigner)
        assertEquals(true, ix.accounts[4].isWritable)

        // 5: token_program (readonly)
        assertEquals(PublicKey.TOKEN_PROGRAM_ID, ix.accounts[5].publicKey)
        assertEquals(false, ix.accounts[5].isSigner)
        assertEquals(false, ix.accounts[5].isWritable)
    }

    @Test
    fun testWithdrawInstructionMatchesGoldenFixture() {
        val amount = 5000000L // 5.00 USDC
        val ix = VaultInstructions.createWithdrawInstruction(
            programId = PROGRAM_ID,
            owner = OWNER,
            vaultConfig = VAULT_PDA,
            mint = USDC_MINT,
            vaultTokenAccount = VAULT_ATA,
            destinationTokenAccount = OWNER_ATA,
            amount = amount
        )

        val expectedHex = "b712469c946da122404b4c0000000000"
        assertEquals(expectedHex, ix.data.toHex())
        assertEquals(16, ix.data.size)
        assertEquals(6, ix.accounts.size)
    }

    @Test
    fun testInitVaultInstructionMatchesGoldenFixture() {
        val threshold = 5000000L // 5.00 USDC
        val maxBalance = 50000000L // 50.00 USDC
        val keeper = OWNER // dummy
        val destination = OWNER_ATA // dummy

        val ix = VaultInstructions.createInitVaultInstruction(
            programId = PROGRAM_ID,
            owner = OWNER,
            keeper = keeper,
            mint = USDC_MINT,
            destination = destination,
            vaultConfig = VAULT_PDA,
            vaultTokenAccount = VAULT_ATA,
            threshold = threshold,
            maxBalance = maxBalance
        )

        val expectedHex = "4d4f559621d9346a404b4c000000000080f0fa0200000000"
        assertEquals(expectedHex, ix.data.toHex())
        assertEquals(24, ix.data.size)
        assertEquals(9, ix.accounts.size)
    }
}
