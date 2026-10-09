package com.jiro.core

object Ata {
    /**
     * Derives an Associated Token Account (ATA) address for a given wallet and token mint.
     * Works for both classic SPL Token and Token-2022 mints.
     *
     * Seeds: [walletAddress, tokenProgramId, mint]
     * Program: ASSOCIATED_TOKEN_PROGRAM_ID
     *
     * @param walletAddress The owner wallet or PDA of the ATA.
     * @param mint The SPL or Token-2022 token mint.
     * @param tokenProgramId The token program managing the mint (default: classic SPL Token).
     * @param allowOwnerOffCurve Set to true if walletAddress is a PDA (off-curve), as for vault ATAs.
     */
    fun deriveAssociatedTokenAddress(
        walletAddress: PublicKey,
        mint: PublicKey,
        tokenProgramId: PublicKey = PublicKey.TOKEN_PROGRAM_ID,
        allowOwnerOffCurve: Boolean = true
    ): PublicKey {
        if (!allowOwnerOffCurve) {
            require(PublicKey.isOnCurve(walletAddress.pubkey)) {
                "Owner is off-curve and allowOwnerOffCurve is false: ${walletAddress.toBase58()}"
            }
        }

        val seeds = listOf(
            walletAddress.pubkey,
            tokenProgramId.pubkey,
            mint.pubkey
        )
        val (ata, _) = Pda.findProgramAddress(seeds, PublicKey.ASSOCIATED_TOKEN_PROGRAM_ID)
        return ata
    }
}
