package com.jiro.core

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

data class BuyableStatus(
    val isBuyable: Boolean,
    val reasons: List<String>
)

data class PolicyStatus(
    val action: String, // "OK", "WARN", "BLOCK"
    val reasons: List<String>,
    val disclosures: List<String>
)

data class CatalogEntry(
    val symbol: String,
    val name: String,
    val mint: PublicKey,
    val tokenProgram: PublicKey,
    val decimals: Int,
    val extensions: List<String>,
    val extensionFingerprint: String,
    val buyable: BuyableStatus,
    val policy: PolicyStatus,
    val ataRentLamports: Long,
    val marketStatus: String
) {
    /**
     * Ticker without .US suffix, e.g. "AAPL" from "AAPL.US".
     */
    val ticker: String get() = symbol.removeSuffix(".US")
}

data class CatalogSnapshot(
    val version: Int,
    val generatedAt: String,
    val totalEntries: Int,
    val buyableEntries: Int,
    val entries: List<CatalogEntry>
)

object Ed25519Verifier {
    /**
     * Verifies an Ed25519 signature over binary data.
     */
    fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            if (signature.size != 64) return false
            val verifier = Ed25519Signer()
            val pubKeyParams = Ed25519PublicKeyParameters(publicKey.pubkey, 0)
            verifier.init(false, pubKeyParams)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }
}
