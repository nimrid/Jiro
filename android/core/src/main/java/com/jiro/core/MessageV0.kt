package com.jiro.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

data class AddressLookupTableAccount(
    val key: PublicKey,
    val addresses: List<PublicKey>
)

data class CompiledInstruction(
    val programIdIndex: Int,
    val accountKeyIndexes: List<Int>,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompiledInstruction) return false
        if (programIdIndex != other.programIdIndex) return false
        if (accountKeyIndexes != other.accountKeyIndexes) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = programIdIndex
        result = 31 * result + accountKeyIndexes.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class MessageAddressTableLookup(
    val accountKey: PublicKey,
    val writableIndexes: List<Int>,
    val readonlyIndexes: List<Int>
)

data class MessageHeader(
    val numRequiredSignatures: Int,
    val numReadonlySignedAccounts: Int,
    val numReadonlyUnsignedAccounts: Int
)

data class MessageV0(
    val header: MessageHeader,
    val staticAccountKeys: List<PublicKey>,
    val recentBlockhash: ByteArray,
    val compiledInstructions: List<CompiledInstruction>,
    val addressTableLookups: List<MessageAddressTableLookup>
) {
    init {
        require(recentBlockhash.size == 32) { "Recent blockhash must be 32 bytes" }
    }

    fun serialize(): ByteArray {
        val stream = ByteArrayOutputStream()

        // 1. Prefix: 0x80 indicates v0 message
        stream.write(0x80)

        // 2. Header
        stream.write(header.numRequiredSignatures)
        stream.write(header.numReadonlySignedAccounts)
        stream.write(header.numReadonlyUnsignedAccounts)

        // 3. Static account keys
        CompactU16.write(stream, staticAccountKeys.size)
        for (key in staticAccountKeys) {
            stream.write(key.pubkey)
        }

        // 4. Recent blockhash
        stream.write(recentBlockhash)

        // 5. Instructions
        CompactU16.write(stream, compiledInstructions.size)
        for (ix in compiledInstructions) {
            stream.write(ix.programIdIndex)
            CompactU16.write(stream, ix.accountKeyIndexes.size)
            for (accIdx in ix.accountKeyIndexes) {
                stream.write(accIdx)
            }
            CompactU16.write(stream, ix.data.size)
            stream.write(ix.data)
        }

        // 6. Address table lookups
        CompactU16.write(stream, addressTableLookups.size)
        for (lookup in addressTableLookups) {
            stream.write(lookup.accountKey.pubkey)
            CompactU16.write(stream, lookup.writableIndexes.size)
            for (wIdx in lookup.writableIndexes) {
                stream.write(wIdx)
            }
            CompactU16.write(stream, lookup.readonlyIndexes.size)
            for (rIdx in lookup.readonlyIndexes) {
                stream.write(rIdx)
            }
        }

        return stream.toByteArray()
    }

    /**
     * Serializes complete transaction packet assuming empty signatures (for signing).
     */
    fun serializeWireTransaction(signatures: List<ByteArray> = emptyList()): ByteArray {
        val messageBytes = serialize()
        val stream = ByteArrayOutputStream()

        val sigCount = if (signatures.isEmpty()) header.numRequiredSignatures else signatures.size
        CompactU16.write(stream, sigCount)

        if (signatures.isEmpty()) {
            // Write placeholder empty 64-byte signatures
            val emptySig = ByteArray(64)
            for (i in 0 until sigCount) {
                stream.write(emptySig)
            }
        } else {
            for (sig in signatures) {
                require(sig.size == 64) { "Signature must be 64 bytes" }
                stream.write(sig)
            }
        }

        stream.write(messageBytes)
        return stream.toByteArray()
    }

    companion object {
        const val MAX_TRANSACTION_SIZE = 1232

        /**
         * Compiles instructions into a MessageV0 with canonical account ordering.
         */
        fun compile(
            payer: PublicKey,
            instructions: List<TransactionInstruction>,
            recentBlockhash: ByteArray,
            lookupTables: List<AddressLookupTableAccount> = emptyList()
        ): MessageV0 {
            require(recentBlockhash.size == 32) { "Blockhash must be 32 bytes" }

            // Account map: key -> (isSigner, isWritable)
            val accountMap = mutableMapOf<PublicKey, Pair<Boolean, Boolean>>()
            // Payer is always first signer + writable
            accountMap[payer] = Pair(true, true)

            for (ix in instructions) {
                // Program IDs are non-signer, non-writable
                accountMap.putIfAbsent(ix.programId, Pair(false, false))

                for (meta in ix.keys) {
                    val current = accountMap[meta.pubkey]
                    if (current == null) {
                        accountMap[meta.pubkey] = Pair(meta.isSigner, meta.isWritable)
                    } else {
                        accountMap[meta.pubkey] = Pair(
                            current.first || meta.isSigner,
                            current.second || meta.isWritable
                        )
                    }
                }
            }

            // Group into 4 categories:
            // 1. signer + writable
            // 2. signer + readonly
            // 3. nonsigner + writable
            // 4. nonsigner + readonly
            val signerWritable = mutableListOf<PublicKey>()
            val signerReadonly = mutableListOf<PublicKey>()
            val nonsignerWritable = mutableListOf<PublicKey>()
            val nonsignerReadonly = mutableListOf<PublicKey>()

            // Payer first
            signerWritable.add(payer)

            for ((key, flags) in accountMap) {
                if (key == payer) continue
                val isSigner = flags.first
                val isWritable = flags.second

                when {
                    isSigner && isWritable -> signerWritable.add(key)
                    isSigner && !isWritable -> signerReadonly.add(key)
                    !isSigner && isWritable -> nonsignerWritable.add(key)
                    else -> nonsignerReadonly.add(key)
                }
            }

            // Check if any non-signer non-program accounts can be loaded via lookup tables
            val addressTableLookups = mutableListOf<MessageAddressTableLookup>()
            val staticNonsignerWritable = mutableListOf<PublicKey>()
            val staticNonsignerReadonly = mutableListOf<PublicKey>()

            val programIds = instructions.map { it.programId }.toSet()
            val tableWritableIndices = mutableMapOf<AddressLookupTableAccount, MutableList<Int>>()
            val tableReadonlyIndices = mutableMapOf<AddressLookupTableAccount, MutableList<Int>>()

            for (key in nonsignerWritable) {
                if (programIds.contains(key) || lookupTables.isEmpty()) {
                    staticNonsignerWritable.add(key)
                    continue
                }
                var found = false
                for (table in lookupTables) {
                    val idx = table.addresses.indexOf(key)
                    if (idx != -1) {
                        tableWritableIndices.getOrPut(table) { mutableListOf() }.add(idx)
                        found = true
                        break
                    }
                }
                if (!found) {
                    staticNonsignerWritable.add(key)
                }
            }

            for (key in nonsignerReadonly) {
                if (programIds.contains(key) || lookupTables.isEmpty()) {
                    staticNonsignerReadonly.add(key)
                    continue
                }
                var found = false
                for (table in lookupTables) {
                    val idx = table.addresses.indexOf(key)
                    if (idx != -1) {
                        tableReadonlyIndices.getOrPut(table) { mutableListOf() }.add(idx)
                        found = true
                        break
                    }
                }
                if (!found) {
                    staticNonsignerReadonly.add(key)
                }
            }

            val orderedLoadedWritable = mutableListOf<PublicKey>()
            val orderedLoadedReadonly = mutableListOf<PublicKey>()

            for (table in lookupTables) {
                val w = tableWritableIndices[table] ?: emptyList()
                val r = tableReadonlyIndices[table] ?: emptyList()
                if (w.isNotEmpty() || r.isNotEmpty()) {
                    addressTableLookups.add(
                        MessageAddressTableLookup(
                            accountKey = table.key,
                            writableIndexes = w,
                            readonlyIndexes = r
                        )
                    )
                    for (wIdx in w) {
                        orderedLoadedWritable.add(table.addresses[wIdx])
                    }
                }
            }

            for (table in lookupTables) {
                val r = tableReadonlyIndices[table] ?: emptyList()
                for (rIdx in r) {
                    orderedLoadedReadonly.add(table.addresses[rIdx])
                }
            }

            val staticAccountKeys = mutableListOf<PublicKey>().apply {
                addAll(signerWritable)
                addAll(signerReadonly)
                addAll(staticNonsignerWritable)
                addAll(staticNonsignerReadonly)
            }

            val header = MessageHeader(
                numRequiredSignatures = signerWritable.size + signerReadonly.size,
                numReadonlySignedAccounts = signerReadonly.size,
                numReadonlyUnsignedAccounts = staticNonsignerReadonly.size
            )

            // Compile instructions
            val compiledInstructions = instructions.map { ix ->
                val programIdIndex = staticAccountKeys.indexOf(ix.programId)
                if (programIdIndex == -1) {
                    throw IllegalStateException("Program ID ${ix.programId.toBase58()} not in static account keys")
                }

                val accountKeyIndexes = ix.keys.map { meta ->
                    val staticIdx = staticAccountKeys.indexOf(meta.pubkey)
                    if (staticIdx != -1) {
                        staticIdx
                    } else {
                        val loadedWritableIdx = orderedLoadedWritable.indexOf(meta.pubkey)
                        if (loadedWritableIdx != -1) {
                            staticAccountKeys.size + loadedWritableIdx
                        } else {
                            val loadedReadonlyIdx = orderedLoadedReadonly.indexOf(meta.pubkey)
                            if (loadedReadonlyIdx != -1) {
                                staticAccountKeys.size + orderedLoadedWritable.size + loadedReadonlyIdx
                            } else {
                                throw IllegalStateException("Account key ${meta.pubkey.toBase58()} not found in static or lookup table keys")
                            }
                        }
                    }
                }

                CompiledInstruction(
                    programIdIndex = programIdIndex,
                    accountKeyIndexes = accountKeyIndexes,
                    data = ix.data
                )
            }

            return MessageV0(
                header = header,
                staticAccountKeys = staticAccountKeys,
                recentBlockhash = recentBlockhash,
                compiledInstructions = compiledInstructions,
                addressTableLookups = addressTableLookups
            )
        }
    }
}

object CompactU16 {
    fun write(stream: ByteArrayOutputStream, value: Int) {
        var rem = value
        while (rem >= 0x80) {
            stream.write((rem and 0x7F) or 0x80)
            rem = rem ushr 7
        }
        stream.write(rem and 0x7F)
    }

    fun read(buffer: ByteBuffer): Int {
        var value = 0
        var shift = 0
        while (true) {
            val byte = buffer.get().toInt() and 0xFF
            value = value or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) == 0) break
            shift += 7
        }
        return value
    }
}
