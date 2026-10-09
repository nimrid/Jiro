package com.jiro.core

import java.math.BigInteger
import java.util.Arrays

class PublicKey(bytes: ByteArray) : Comparable<PublicKey> {
    val pubkey: ByteArray = bytes.clone()

    constructor(base58String: String) : this(Base58.decode(base58String))

    init {
        require(pubkey.size == 32) { "Public key must be 32 bytes, got ${pubkey.size}" }
    }

    fun toBase58(): String = Base58.encode(pubkey.clone())

    fun toByteArray(): ByteArray = pubkey.clone()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicKey) return false
        return pubkey.contentEquals(other.pubkey)
    }

    override fun hashCode(): Int = Arrays.hashCode(pubkey)

    override fun toString(): String = toBase58()

    override fun compareTo(other: PublicKey): Int {
        for (i in 0 until 32) {
            val a = pubkey[i].toInt() and 0xFF
            val b = other.pubkey[i].toInt() and 0xFF
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    companion object {
        val TOKEN_PROGRAM_ID = PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
        val TOKEN_2022_PROGRAM_ID = PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")
        val ASSOCIATED_TOKEN_PROGRAM_ID = PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
        val SYSTEM_PROGRAM_ID = PublicKey("11111111111111111111111111111111")
        val MEMO_PROGRAM_ID = PublicKey("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr")
        val COMPUTE_BUDGET_PROGRAM_ID = PublicKey("ComputeBudget111111111111111111111111111111")
        val VAULT_PROGRAM_ID = PublicKey("AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc")
        val USDC_MINT_MAINNET = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
        val USDC_MINT_DEVNET = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")

        // Ed25519 Curve constants:
        // p = 2^255 - 19
        private val P = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
        // d = -121665 * 121666^(-1) mod p
        private val D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P))
            .mod(P)

        /**
         * Checks if a 32-byte array represents a point on the Ed25519 curve.
         */
        fun isOnCurve(bytes: ByteArray): Boolean {
            if (bytes.size != 32) return false

            val yBytes = bytes.clone()
            yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
            yBytes.reverse()
            val y = BigInteger(1, yBytes)

            if (y >= P) return false

            val y2 = y.modPow(BigInteger.valueOf(2), P)
            val u = y2.subtract(BigInteger.ONE).mod(P)
            val v = D.multiply(y2).add(BigInteger.ONE).mod(P)

            if (v == BigInteger.ZERO) {
                return u == BigInteger.ZERO
            }

            val vInv = v.modPow(P.subtract(BigInteger.valueOf(2)), P)
            val x2 = u.multiply(vInv).mod(P)

            if (x2 == BigInteger.ZERO) return true

            val exp = P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(2))
            val legendre = x2.modPow(exp, P)
            return legendre == BigInteger.ONE
        }
    }
}
