package com.jiro.app

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Arrays

class PublicKey(val pubkey: ByteArray) {

    constructor(base58String: String) : this(Base58.decode(base58String))

    init {
        require(pubkey.size == 32) { "Public key must be 32 bytes, got ${pubkey.size}" }
    }

    fun toBase58(): String = Base58.encode(pubkey)

    fun toByteArray(): ByteArray = pubkey.clone()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicKey) return false
        return pubkey.contentEquals(other.pubkey)
    }

    override fun hashCode(): Int = Arrays.hashCode(pubkey)

    override fun toString(): String = toBase58()

    companion object {
        val TOKEN_PROGRAM_ID = PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
        val ASSOCIATED_TOKEN_PROGRAM_ID = PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
        val SYSTEM_PROGRAM_ID = PublicKey("11111111111111111111111111111111")

        private val PDA_MARKER = "ProgramDerivedAddress".toByteArray(Charsets.UTF_8)

        // Ed25519 Curve constants:
        // p = 2^255 - 19
        private val P = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
        // d = -121665 * 121666^(-1) mod p
        private val D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P))
            .mod(P)

        /**
         * Checks if a 32-byte array represents a point on the Ed25519 curve.
         * In Ed25519, the equation is: -x^2 + y^2 = 1 + d * x^2 * y^2 (mod p)
         * Rearranging: x^2 * (d * y^2 + 1) = y^2 - 1 (mod p)
         * Let u = y^2 - 1, v = d * y^2 + 1.
         * x^2 = u / v (mod p).
         * A point is on the curve iff u/v is a quadratic residue in GF(p).
         */
        fun isOnCurve(bytes: ByteArray): Boolean {
            if (bytes.size != 32) return false

            // Decode y coordinate from little-endian bytes, clearing highest bit (sign of x)
            val yBytes = bytes.clone()
            yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()

            // Reverse for BigInteger (BigInteger expects big-endian)
            yBytes.reverse()
            val y = BigInteger(1, yBytes)

            if (y >= P) return false

            val y2 = y.modPow(BigInteger.valueOf(2), P)
            val u = y2.subtract(BigInteger.ONE).mod(P)
            val v = D.multiply(y2).add(BigInteger.ONE).mod(P)

            if (v == BigInteger.ZERO) {
                return u == BigInteger.ZERO
            }

            // x2 = u * v^(p-2) mod p
            val vInv = v.modPow(P.subtract(BigInteger.valueOf(2)), P)
            val x2 = u.multiply(vInv).mod(P)

            if (x2 == BigInteger.ZERO) return true

            // Euler's criterion: x2^((p-1)/2) == 1 mod p iff x2 is a square
            val exp = P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(2))
            val legendre = x2.modPow(exp, P)
            return legendre == BigInteger.ONE
        }

        /**
         * Finds a program derived address (PDA) for given seeds and program ID.
         * Decrements bump seed from 255 to 0.
         */
        fun findProgramAddress(seeds: List<ByteArray>, programId: PublicKey): Pair<PublicKey, Int> {
            val digest = MessageDigest.getInstance("SHA-256")

            for (bump in 255 downTo 0) {
                val stream = ByteArrayOutputStream()
                for (seed in seeds) {
                    require(seed.size <= 32) { "Max seed length is 32 bytes" }
                    stream.write(seed)
                }
                stream.write(bump)
                stream.write(programId.pubkey)
                stream.write(PDA_MARKER)

                val hash = digest.digest(stream.toByteArray())
                if (!isOnCurve(hash)) {
                    return Pair(PublicKey(hash), bump)
                }
            }

            throw IllegalStateException("Unable to find viable program address nonce")
        }

        /**
         * Derives Associated Token Account address for given wallet and mint.
         * Seeds: [walletAddress, tokenProgramId, mint]
         */
        fun findAssociatedTokenAddress(
            walletAddress: PublicKey,
            mint: PublicKey,
            tokenProgramId: PublicKey = TOKEN_PROGRAM_ID
        ): PublicKey {
            val seeds = listOf(
                walletAddress.pubkey,
                tokenProgramId.pubkey,
                mint.pubkey
            )
            val (ata, _) = findProgramAddress(seeds, ASSOCIATED_TOKEN_PROGRAM_ID)
            return ata
        }
    }
}
