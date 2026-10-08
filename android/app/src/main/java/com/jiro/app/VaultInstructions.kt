package com.jiro.app

import java.io.ByteArrayOutputStream

object VaultInstructions {

    // Anchor instruction discriminators from docs/PROGRAM_INTERFACE.md
    val INIT_VAULT_DISCRIMINATOR = "4d4f559621d9346a".decodeHex()
    val DEPOSIT_DISCRIMINATOR = "f223c68952e1f2b6".decodeHex()
    val SWEEP_DISCRIMINATOR = "2817eaaf0e3d9ab1".decodeHex()
    val WITHDRAW_DISCRIMINATOR = "b712469c946da122".decodeHex()

    fun createInitVaultInstruction(
        programId: PublicKey,
        owner: PublicKey,
        keeper: PublicKey,
        mint: PublicKey,
        destination: PublicKey,
        vaultConfig: PublicKey,
        vaultTokenAccount: PublicKey,
        threshold: Long,
        maxBalance: Long
    ): TransactionInstruction {
        val stream = ByteArrayOutputStream()
        stream.write(INIT_VAULT_DISCRIMINATOR)
        stream.write(threshold.toLittleEndianBytes())
        stream.write(maxBalance.toLittleEndianBytes())

        val accounts = listOf(
            AccountMeta(owner, isSigner = true, isWritable = true),
            AccountMeta(keeper, isSigner = false, isWritable = false),
            AccountMeta(mint, isSigner = false, isWritable = false),
            AccountMeta(destination, isSigner = false, isWritable = false),
            AccountMeta(vaultConfig, isSigner = false, isWritable = true),
            AccountMeta(vaultTokenAccount, isSigner = false, isWritable = true),
            AccountMeta(PublicKey.SYSTEM_PROGRAM_ID, isSigner = false, isWritable = false),
            AccountMeta(PublicKey.TOKEN_PROGRAM_ID, isSigner = false, isWritable = false),
            AccountMeta(PublicKey.ASSOCIATED_TOKEN_PROGRAM_ID, isSigner = false, isWritable = false)
        )

        return TransactionInstruction(programId, accounts, stream.toByteArray())
    }

    fun createDepositInstruction(
        programId: PublicKey,
        owner: PublicKey,
        vaultConfig: PublicKey,
        mint: PublicKey,
        vaultTokenAccount: PublicKey,
        ownerTokenAccount: PublicKey,
        amount: Long
    ): TransactionInstruction {
        val stream = ByteArrayOutputStream()
        stream.write(DEPOSIT_DISCRIMINATOR)
        stream.write(amount.toLittleEndianBytes())

        val accounts = listOf(
            AccountMeta(owner, isSigner = true, isWritable = true),
            AccountMeta(vaultConfig, isSigner = false, isWritable = false),
            AccountMeta(mint, isSigner = false, isWritable = false),
            AccountMeta(vaultTokenAccount, isSigner = false, isWritable = true),
            AccountMeta(ownerTokenAccount, isSigner = false, isWritable = true),
            AccountMeta(PublicKey.TOKEN_PROGRAM_ID, isSigner = false, isWritable = false)
        )

        return TransactionInstruction(programId, accounts, stream.toByteArray())
    }

    fun createWithdrawInstruction(
        programId: PublicKey,
        owner: PublicKey,
        vaultConfig: PublicKey,
        mint: PublicKey,
        vaultTokenAccount: PublicKey,
        destinationTokenAccount: PublicKey,
        amount: Long
    ): TransactionInstruction {
        val stream = ByteArrayOutputStream()
        stream.write(WITHDRAW_DISCRIMINATOR)
        stream.write(amount.toLittleEndianBytes())

        val accounts = listOf(
            AccountMeta(owner, isSigner = true, isWritable = true),
            AccountMeta(vaultConfig, isSigner = false, isWritable = false),
            AccountMeta(mint, isSigner = false, isWritable = false),
            AccountMeta(vaultTokenAccount, isSigner = false, isWritable = true),
            AccountMeta(destinationTokenAccount, isSigner = false, isWritable = true),
            AccountMeta(PublicKey.TOKEN_PROGRAM_ID, isSigner = false, isWritable = false)
        )

        return TransactionInstruction(programId, accounts, stream.toByteArray())
    }

    fun createSweepInstruction(
        programId: PublicKey,
        keeper: PublicKey,
        vaultConfig: PublicKey,
        mint: PublicKey,
        vaultTokenAccount: PublicKey,
        destinationTokenAccount: PublicKey
    ): TransactionInstruction {
        val stream = ByteArrayOutputStream()
        stream.write(SWEEP_DISCRIMINATOR)

        val accounts = listOf(
            AccountMeta(keeper, isSigner = true, isWritable = false),
            AccountMeta(vaultConfig, isSigner = false, isWritable = false),
            AccountMeta(mint, isSigner = false, isWritable = false),
            AccountMeta(vaultTokenAccount, isSigner = false, isWritable = true),
            AccountMeta(destinationTokenAccount, isSigner = false, isWritable = true),
            AccountMeta(PublicKey.TOKEN_PROGRAM_ID, isSigner = false, isWritable = false)
        )

        return TransactionInstruction(programId, accounts, stream.toByteArray())
    }
}

fun Long.toLittleEndianBytes(): ByteArray {
    val b = ByteArray(8)
    for (i in 0 until 8) {
        b[i] = ((this ushr (i * 8)) and 0xFF).toByte()
    }
    return b
}

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Hex string must have even length" }
    val result = ByteArray(length / 2)
    for (i in 0 until length step 2) {
        result[i / 2] = substring(i, i + 2).toInt(16).toByte()
    }
    return result
}
