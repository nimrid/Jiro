package com.jiro.core

sealed class PreBuyVerificationResult {
    data object Success : PreBuyVerificationResult()
    data class Mismatch(val reason: String) : PreBuyVerificationResult()
}

data class OnChainMintState(
    val mint: PublicKey,
    val ownerProgram: PublicKey,
    val decimals: Int,
    val extensionFingerprint: String
)

object PreBuyVerifier {

    /**
     * Verifies that the live on-chain mint state matches the verified catalog entry.
     */
    fun verify(
        catalogEntry: CatalogEntry,
        onChainState: OnChainMintState
    ): PreBuyVerificationResult {
        if (catalogEntry.mint != onChainState.mint) {
            return PreBuyVerificationResult.Mismatch(
                "Mint pubkey mismatch: expected ${catalogEntry.mint.toBase58()}, got ${onChainState.mint.toBase58()}"
            )
        }

        if (catalogEntry.tokenProgram != onChainState.ownerProgram) {
            return PreBuyVerificationResult.Mismatch(
                "token changed since verification: owner program mismatch (expected ${catalogEntry.tokenProgram.toBase58()}, got ${onChainState.ownerProgram.toBase58()})"
            )
        }

        if (catalogEntry.decimals != onChainState.decimals) {
            return PreBuyVerificationResult.Mismatch(
                "token changed since verification: decimals mismatch (expected ${catalogEntry.decimals}, got ${onChainState.decimals})"
            )
        }

        if (!catalogEntry.extensionFingerprint.equals(onChainState.extensionFingerprint, ignoreCase = true)) {
            return PreBuyVerificationResult.Mismatch(
                "token changed since verification: extension fingerprint mismatch"
            )
        }

        return PreBuyVerificationResult.Success
    }
}
