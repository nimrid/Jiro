package com.jiro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PdaTest {

    private val PROGRAM_ID = "AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc"
    private val OWNER = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
    private val USDC_MINT = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU"

    private val EXPECTED_VAULT_PDA = "AtDBHZDyUj85XWBywGTQTqeeMKvoN1fWubYuZfSqfc33"
    private val EXPECTED_BUMP = 253
    private val EXPECTED_VAULT_ATA = "6N37n5oDiscZACmxpNQD5GbwRwPP3rhH3Yfj3HtZf3qF"

    @Test
    fun testBase58RoundTrip() {
        val pubkeyStr = OWNER
        val bytes = Base58.decode(pubkeyStr)
        assertEquals(32, bytes.size)
        val encoded = Base58.encode(bytes)
        assertEquals(pubkeyStr, encoded)
    }

    @Test
    fun testVaultPdaDerivationMatchesGoldenFixture() {
        val programId = PublicKey(PROGRAM_ID)
        val owner = PublicKey(OWNER)

        val seeds = listOf(
            "vault".toByteArray(Charsets.UTF_8),
            owner.pubkey
        )

        val (pda, bump) = PublicKey.findProgramAddress(seeds, programId)
        assertEquals(EXPECTED_BUMP, bump)
        assertEquals(EXPECTED_VAULT_PDA, pda.toBase58())
    }

    @Test
    fun testAssociatedTokenAddressMatchesGoldenFixture() {
        val vaultPda = PublicKey(EXPECTED_VAULT_PDA)
        val mint = PublicKey(USDC_MINT)

        val ata = PublicKey.findAssociatedTokenAddress(vaultPda, mint)
        assertEquals(EXPECTED_VAULT_ATA, ata.toBase58())
    }
}
