package com.jiro.core

import org.junit.Assert.*
import org.junit.Test

class PreBuyVerifierTest {

    private val mint = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")
    private val tokenProgram = PublicKey.TOKEN_2022_PROGRAM_ID
    private val fingerprint = "9ebc4171ad8979b4b67120d5bc536e26674b7de40989d704a41c01b8290541a5"

    private val catalogEntry = CatalogEntry(
        symbol = "AAPL.US",
        name = "Apple Inc.",
        mint = mint,
        tokenProgram = tokenProgram,
        decimals = 6,
        extensions = listOf("tokenMetadata", "permanentDelegate"),
        extensionFingerprint = fingerprint,
        buyable = BuyableStatus(true, emptyList()),
        policy = PolicyStatus("WARN", emptyList(), emptyList()),
        ataRentLamports = 2157600L,
        marketStatus = "open"
    )

    @Test
    fun testSuccessfulVerification() {
        val onChain = OnChainMintState(
            mint = mint,
            ownerProgram = tokenProgram,
            decimals = 6,
            extensionFingerprint = fingerprint
        )
        val res = PreBuyVerifier.verify(catalogEntry, onChain)
        assertTrue(res is PreBuyVerificationResult.Success)
    }

    @Test
    fun testProgramMismatchFails() {
        val onChain = OnChainMintState(
            mint = mint,
            ownerProgram = PublicKey.TOKEN_PROGRAM_ID, // wrong program
            decimals = 6,
            extensionFingerprint = fingerprint
        )
        val res = PreBuyVerifier.verify(catalogEntry, onChain)
        assertTrue(res is PreBuyVerificationResult.Mismatch)
        assertTrue((res as PreBuyVerificationResult.Mismatch).reason.contains("owner program mismatch"))
    }

    @Test
    fun testDecimalsMismatchFails() {
        val onChain = OnChainMintState(
            mint = mint,
            ownerProgram = tokenProgram,
            decimals = 9, // wrong decimals
            extensionFingerprint = fingerprint
        )
        val res = PreBuyVerifier.verify(catalogEntry, onChain)
        assertTrue(res is PreBuyVerificationResult.Mismatch)
        assertTrue((res as PreBuyVerificationResult.Mismatch).reason.contains("decimals mismatch"))
    }

    @Test
    fun testFingerprintMismatchFails() {
        val onChain = OnChainMintState(
            mint = mint,
            ownerProgram = tokenProgram,
            decimals = 6,
            extensionFingerprint = "tampered_fingerprint_hash"
        )
        val res = PreBuyVerifier.verify(catalogEntry, onChain)
        assertTrue(res is PreBuyVerificationResult.Mismatch)
        assertTrue((res as PreBuyVerificationResult.Mismatch).reason.contains("extension fingerprint mismatch"))
    }
}
