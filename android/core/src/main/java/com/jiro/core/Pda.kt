package com.jiro.core

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

object Pda {
    private val PDA_MARKER = "ProgramDerivedAddress".toByteArray(Charsets.UTF_8)

    /**
     * Finds a valid program derived address by searching for a valid off-curve bump seed (255 down to 0).
     */
    fun findProgramAddress(seeds: List<ByteArray>, programId: PublicKey): Pair<PublicKey, Int> {
        val digest = MessageDigest.getInstance("SHA-256")

        for (bump in 255 downTo 0) {
            val stream = ByteArrayOutputStream()
            for (seed in seeds) {
                require(seed.size <= 32) { "Max seed length is 32 bytes, got ${seed.size}" }
                stream.write(seed)
            }
            stream.write(bump)
            stream.write(programId.pubkey)
            stream.write(PDA_MARKER)

            val hash = digest.digest(stream.toByteArray())
            if (!PublicKey.isOnCurve(hash)) {
                return Pair(PublicKey(hash), bump)
            }
        }

        throw IllegalStateException("Unable to find viable program address nonce")
    }

    /**
     * Derives a vault config PDA for a given owner and vaultId.
     * Seeds: [b"vault", owner.pubkey, [vaultId]]
     */
    fun deriveVaultPda(
        owner: PublicKey,
        vaultId: Byte,
        programId: PublicKey = PublicKey.VAULT_PROGRAM_ID
    ): Pair<PublicKey, Int> {
        val seeds = listOf(
            "vault".toByteArray(Charsets.UTF_8),
            owner.pubkey,
            byteArrayOf(vaultId)
        )
        return findProgramAddress(seeds, programId)
    }

    /**
     * Derives a mock config PDA for a given stock mint.
     * Seeds: [b"mock", stockMint.pubkey]
     */
    fun deriveMockPda(
        stockMint: PublicKey,
        programId: PublicKey = PublicKey.VAULT_PROGRAM_ID
    ): Pair<PublicKey, Int> {
        val seeds = listOf(
            "mock".toByteArray(Charsets.UTF_8),
            stockMint.pubkey
        )
        return findProgramAddress(seeds, programId)
    }
}
