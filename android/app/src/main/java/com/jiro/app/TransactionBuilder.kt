package com.jiro.app

import java.io.ByteArrayOutputStream

object TransactionBuilder {

    /**
     * Encodes a length as a Solana short_vec (LEB128-like varint up to 16 bits).
     */
    fun encodeShortVec(len: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var rem = len
        while (rem >= 0x80) {
            out.write((rem and 0x7F) or 0x80)
            rem = rem ushr 7
        }
        out.write(rem and 0x7F)
        return out.toByteArray()
    }

    /**
     * Compiles instructions and fee payer into a Solana Legacy Message byte array.
     */
    fun compileMessage(
        instructions: List<TransactionInstruction>,
        feePayer: PublicKey,
        recentBlockhash: String
    ): ByteArray {
        // Collect all accounts and combine flags
        class KeyMeta(val pubkey: PublicKey, var isSigner: Boolean, var isWritable: Boolean)

        val keyMap = LinkedHashMap<PublicKey, KeyMeta>()

        // Fee payer is always signer and writable at index 0
        keyMap[feePayer] = KeyMeta(feePayer, isSigner = true, isWritable = true)

        for (ix in instructions) {
            for (acc in ix.accounts) {
                val existing = keyMap[acc.publicKey]
                if (existing == null) {
                    keyMap[acc.publicKey] = KeyMeta(acc.publicKey, acc.isSigner, acc.isWritable)
                } else {
                    if (acc.isSigner) existing.isSigner = true
                    if (acc.isWritable) existing.isWritable = true
                }
            }
            // Program ID is always non-signer, readonly
            val prog = keyMap[ix.programId]
            if (prog == null) {
                keyMap[ix.programId] = KeyMeta(ix.programId, isSigner = false, isWritable = false)
            }
        }

        // Group according to Solana message layout rules:
        // 1. signed writable (fee payer first)
        // 2. signed readonly
        // 3. unsigned writable
        // 4. unsigned readonly
        val signedWritable = ArrayList<PublicKey>()
        val signedReadonly = ArrayList<PublicKey>()
        val unsignedWritable = ArrayList<PublicKey>()
        val unsignedReadonly = ArrayList<PublicKey>()

        // Ensure fee payer is first
        signedWritable.add(feePayer)

        for ((pubkey, meta) in keyMap) {
            if (pubkey == feePayer) continue
            when {
                meta.isSigner && meta.isWritable -> signedWritable.add(pubkey)
                meta.isSigner && !meta.isWritable -> signedReadonly.add(pubkey)
                !meta.isSigner && meta.isWritable -> unsignedWritable.add(pubkey)
                else -> unsignedReadonly.add(pubkey)
            }
        }

        val allKeys = ArrayList<PublicKey>()
        allKeys.addAll(signedWritable)
        allKeys.addAll(signedReadonly)
        allKeys.addAll(unsignedWritable)
        allKeys.addAll(unsignedReadonly)

        val keyIndexMap = HashMap<PublicKey, Int>()
        for (i in allKeys.indices) {
            keyIndexMap[allKeys[i]] = i
        }

        val numRequiredSignatures = signedWritable.size + signedReadonly.size
        val numReadonlySigned = signedReadonly.size
        val numReadonlyUnsigned = unsignedReadonly.size

        val out = ByteArrayOutputStream()

        // 1. Header (3 bytes)
        out.write(numRequiredSignatures)
        out.write(numReadonlySigned)
        out.write(numReadonlyUnsigned)

        // 2. Account Keys
        out.write(encodeShortVec(allKeys.size))
        for (key in allKeys) {
            out.write(key.pubkey)
        }

        // 3. Recent Blockhash
        val blockhashBytes = Base58.decode(recentBlockhash)
        require(blockhashBytes.size == 32) { "Recent blockhash must be 32 bytes" }
        out.write(blockhashBytes)

        // 4. Instructions
        out.write(encodeShortVec(instructions.size))
        for (ix in instructions) {
            val progIndex = keyIndexMap[ix.programId]
                ?: throw IllegalStateException("Program ID missing from account keys")
            out.write(progIndex)

            out.write(encodeShortVec(ix.accounts.size))
            for (acc in ix.accounts) {
                val accIndex = keyIndexMap[acc.publicKey]
                    ?: throw IllegalStateException("Account ${acc.publicKey} missing from account keys")
                out.write(accIndex)
            }

            out.write(encodeShortVec(ix.data.size))
            out.write(ix.data)
        }

        return out.toByteArray()
    }

    /**
     * Builds a wire-format transaction with placeholder empty signatures.
     * Ready for MWA signAndSendTransactions.
     */
    fun buildWireTransaction(
        instructions: List<TransactionInstruction>,
        feePayer: PublicKey,
        recentBlockhash: String
    ): ByteArray {
        val messageBytes = compileMessage(instructions, feePayer, recentBlockhash)

        // Count required signatures (header byte 0)
        val numSignatures = messageBytes[0].toInt() and 0xFF

        val out = ByteArrayOutputStream()
        out.write(encodeShortVec(numSignatures))
        // Write 64 zero bytes for each placeholder signature
        for (i in 0 until numSignatures) {
            out.write(ByteArray(64))
        }
        out.write(messageBytes)

        return out.toByteArray()
    }
}
