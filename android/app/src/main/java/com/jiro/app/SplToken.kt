package com.jiro.app

import java.io.ByteArrayOutputStream

object SplToken {
    /**
     * Creates an SPL Token TransferChecked instruction (instruction index 12).
     * Validates mint and decimals on-chain.
     */
    fun createTransferCheckedInstruction(
        source: PublicKey,
        mint: PublicKey,
        destination: PublicKey,
        owner: PublicKey,
        amount: Long,
        decimals: Byte = 6
    ): TransactionInstruction {
        val stream = ByteArrayOutputStream()
        stream.write(12) // TransferChecked
        stream.write(amount.toLittleEndianBytes())
        stream.write(decimals.toInt())

        val accounts = listOf(
            AccountMeta(source, isSigner = false, isWritable = true),
            AccountMeta(mint, isSigner = false, isWritable = false),
            AccountMeta(destination, isSigner = false, isWritable = true),
            AccountMeta(owner, isSigner = true, isWritable = false)
        )

        return TransactionInstruction(PublicKey.TOKEN_PROGRAM_ID, accounts, stream.toByteArray())
    }
}
