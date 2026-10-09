package com.jiro.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AccountMeta(
    val pubkey: PublicKey,
    val isSigner: Boolean,
    val isWritable: Boolean
)

data class TransactionInstruction(
    val programId: PublicKey,
    val keys: List<AccountMeta>,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransactionInstruction) return false
        if (programId != other.programId) return false
        if (keys != other.keys) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = programId.hashCode()
        result = 31 * result + keys.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

object Instructions {

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun u64Le(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()

    private fun u32Le(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    // 8-byte Anchor discriminators from IDL
    val DISC_INIT_VAULT = hexToBytes("4d4f559621d9346a")
    val DISC_DEPOSIT = hexToBytes("f223c68952e1f2b6")
    val DISC_WITHDRAW = hexToBytes("b712469c946da122")
    val DISC_CLOSE_VAULT = hexToBytes("8d67117e484b1d1d")
    val DISC_MOCK_INIT = hexToBytes("f458426f74792010")
    val DISC_MOCK_SET_PRICE = hexToBytes("902a9f6e9bb8302c")
    val DISC_MOCK_SWAP = hexToBytes("8476b98619386a0a")

    /**
     * Creates an init_vault instruction.
     */
    fun initVault(
        owner: PublicKey,
        mint: PublicKey,
        vaultId: Byte,
        maxBalance: Long,
        programId: PublicKey = PublicKey.VAULT_PROGRAM_ID
    ): TransactionInstruction {
        val (vaultPda, _) = Pda.deriveVaultPda(owner, vaultId, programId)
        val vaultAta = Ata.deriveAssociatedTokenAddress(vaultPda, mint)

        val stream = ByteArrayOutputStream()
        stream.write(DISC_INIT_VAULT)
        stream.write(byteArrayOf(vaultId))
        stream.write(u64Le(maxBalance))

        val keys = listOf(
            AccountMeta(pubkey = owner, isSigner = true, isWritable = true),
            AccountMeta(pubkey = mint, isSigner = false, isWritable = false),
            AccountMeta(pubkey = vaultPda, isSigner = false, isWritable = true),
            AccountMeta(pubkey = vaultAta, isSigner = false, isWritable = true),
            AccountMeta(pubkey = PublicKey.SYSTEM_PROGRAM_ID, isSigner = false, isWritable = false),
            AccountMeta(pubkey = PublicKey.TOKEN_PROGRAM_ID, isSigner = false, isWritable = false),
            AccountMeta(pubkey = PublicKey.ASSOCIATED_TOKEN_PROGRAM_ID, isSigner = false, isWritable = false)
        )

        return TransactionInstruction(programId = programId, keys = keys, data = stream.toByteArray())
    }

    /**
     * Creates a deposit instruction into the vault ATA.
     */
    fun deposit(
        owner: PublicKey,
        mint: PublicKey,
        vaultId: Byte,
        amount: Long,
        ownerTokenAccount: PublicKey? = null,
        programId: PublicKey = PublicKey.VAULT_PROGRAM_ID
    ): TransactionInstruction {
        val (vaultPda, _) = Pda.deriveVaultPda(owner, vaultId, programId)
        val vaultAta = Ata.deriveAssociatedTokenAddress(vaultPda, mint)
        val sourceAta = ownerTokenAccount ?: Ata.deriveAssociatedTokenAddress(owner, mint)

        val stream = ByteArrayOutputStream()
        stream.write(DISC_DEPOSIT)
        stream.write(u64Le(amount))

        val keys = listOf(
            AccountMeta(pubkey = owner, isSigner = true, isWritable = true),
            AccountMeta(pubkey = vaultPda, isSigner = false, isWritable = false),
            AccountMeta(pubkey = mint, isSigner = false, isWritable = false),
            AccountMeta(pubkey = vaultAta, isSigner = false, isWritable = true),
            AccountMeta(pubkey = sourceAta, isSigner = false, isWritable = true),
            AccountMeta(pubkey = PublicKey.TOKEN_PROGRAM_ID, isSigner = false, isWritable = false)
        )

        return TransactionInstruction(programId = programId, keys = keys, data = stream.toByteArray())
    }

    /**
     * Creates a withdraw instruction from the vault ATA to owner's token account.
     */
    fun withdraw(
        owner: PublicKey,
        mint: PublicKey,
        vaultId: Byte,
        amount: Long,
        destinationTokenAccount: PublicKey? = null,
        programId: PublicKey = PublicKey.VAULT_PROGRAM_ID
    ): TransactionInstruction {
        val (vaultPda, _) = Pda.deriveVaultPda(owner, vaultId, programId)
        val vaultAta = Ata.deriveAssociatedTokenAddress(vaultPda, mint)
        val destAta = destinationTokenAccount ?: Ata.deriveAssociatedTokenAddress(owner, mint)

        val stream = ByteArrayOutputStream()
        stream.write(DISC_WITHDRAW)
        stream.write(u64Le(amount))

        val keys = listOf(
            AccountMeta(pubkey = owner, isSigner = true, isWritable = false),
            AccountMeta(pubkey = vaultPda, isSigner = false, isWritable = false),
            AccountMeta(pubkey = mint, isSigner = false, isWritable = false),
            AccountMeta(pubkey = vaultAta, isSigner = false, isWritable = true),
            AccountMeta(pubkey = destAta, isSigner = false, isWritable = true),
            AccountMeta(pubkey = PublicKey.TOKEN_PROGRAM_ID, isSigner = false, isWritable = false)
        )

        return TransactionInstruction(programId = programId, keys = keys, data = stream.toByteArray())
    }

    /**
     * Creates a close_vault instruction.
     */
    fun closeVault(
        owner: PublicKey,
        mint: PublicKey,
        vaultId: Byte,
        programId: PublicKey = PublicKey.VAULT_PROGRAM_ID
    ): TransactionInstruction {
        val (vaultPda, _) = Pda.deriveVaultPda(owner, vaultId, programId)
        val vaultAta = Ata.deriveAssociatedTokenAddress(vaultPda, mint)

        val keys = listOf(
            AccountMeta(pubkey = owner, isSigner = true, isWritable = true),
            AccountMeta(pubkey = vaultPda, isSigner = false, isWritable = true),
            AccountMeta(pubkey = mint, isSigner = false, isWritable = false),
            AccountMeta(pubkey = vaultAta, isSigner = false, isWritable = true),
            AccountMeta(pubkey = PublicKey.TOKEN_PROGRAM_ID, isSigner = false, isWritable = false)
        )

        return TransactionInstruction(programId = programId, keys = keys, data = DISC_CLOSE_VAULT)
    }

    /**
     * Creates a mock_swap instruction (Feature: mock-swap).
     */
    fun mockSwap(
        owner: PublicKey,
        stockMint: PublicKey,
        usdcMint: PublicKey,
        usdcIn: Long,
        minOut: Long,
        stockTokenProgram: PublicKey = PublicKey.TOKEN_PROGRAM_ID,
        programId: PublicKey = PublicKey.VAULT_PROGRAM_ID
    ): TransactionInstruction {
        val (mockPda, _) = Pda.deriveMockPda(stockMint, programId)
        val sinkAta = Ata.deriveAssociatedTokenAddress(mockPda, usdcMint)
        val treasuryAta = Ata.deriveAssociatedTokenAddress(mockPda, stockMint, stockTokenProgram)
        val ownerUsdcAta = Ata.deriveAssociatedTokenAddress(owner, usdcMint)
        val ownerStockAta = Ata.deriveAssociatedTokenAddress(owner, stockMint, stockTokenProgram)

        val stream = ByteArrayOutputStream()
        stream.write(DISC_MOCK_SWAP)
        stream.write(u64Le(usdcIn))
        stream.write(u64Le(minOut))

        val keys = listOf(
            AccountMeta(pubkey = owner, isSigner = true, isWritable = true),
            AccountMeta(pubkey = mockPda, isSigner = false, isWritable = false),
            AccountMeta(pubkey = usdcMint, isSigner = false, isWritable = false),
            AccountMeta(pubkey = stockMint, isSigner = false, isWritable = false),
            AccountMeta(pubkey = sinkAta, isSigner = false, isWritable = true),
            AccountMeta(pubkey = treasuryAta, isSigner = false, isWritable = true),
            AccountMeta(pubkey = ownerUsdcAta, isSigner = false, isWritable = true),
            AccountMeta(pubkey = ownerStockAta, isSigner = false, isWritable = true),
            AccountMeta(pubkey = PublicKey.TOKEN_PROGRAM_ID, isSigner = false, isWritable = false),
            AccountMeta(pubkey = stockTokenProgram, isSigner = false, isWritable = false)
        )

        return TransactionInstruction(programId = programId, keys = keys, data = stream.toByteArray())
    }

    /**
     * SPL Token transfer_checked instruction.
     */
    fun transferChecked(
        source: PublicKey,
        mint: PublicKey,
        destination: PublicKey,
        authority: PublicKey,
        amount: Long,
        decimals: Byte,
        tokenProgramId: PublicKey = PublicKey.TOKEN_PROGRAM_ID
    ): TransactionInstruction {
        val stream = ByteArrayOutputStream()
        stream.write(12) // TransferChecked index
        stream.write(u64Le(amount))
        stream.write(byteArrayOf(decimals))

        val keys = listOf(
            AccountMeta(pubkey = source, isSigner = false, isWritable = true),
            AccountMeta(pubkey = mint, isSigner = false, isWritable = false),
            AccountMeta(pubkey = destination, isSigner = false, isWritable = true),
            AccountMeta(pubkey = authority, isSigner = true, isWritable = false)
        )

        return TransactionInstruction(programId = tokenProgramId, keys = keys, data = stream.toByteArray())
    }

    /**
     * Associated Token Program create_idempotent instruction.
     */
    fun createAssociatedTokenAccountIdempotent(
        payer: PublicKey,
        walletAddress: PublicKey,
        mint: PublicKey,
        tokenProgramId: PublicKey = PublicKey.TOKEN_PROGRAM_ID
    ): TransactionInstruction {
        val ata = Ata.deriveAssociatedTokenAddress(walletAddress, mint, tokenProgramId)
        val data = byteArrayOf(1) // CreateIdempotent instruction index

        val keys = listOf(
            AccountMeta(pubkey = payer, isSigner = true, isWritable = true),
            AccountMeta(pubkey = ata, isSigner = false, isWritable = true),
            AccountMeta(pubkey = walletAddress, isSigner = false, isWritable = false),
            AccountMeta(pubkey = mint, isSigner = false, isWritable = false),
            AccountMeta(pubkey = PublicKey.SYSTEM_PROGRAM_ID, isSigner = false, isWritable = false),
            AccountMeta(pubkey = tokenProgramId, isSigner = false, isWritable = false)
        )

        return TransactionInstruction(
            programId = PublicKey.ASSOCIATED_TOKEN_PROGRAM_ID,
            keys = keys,
            data = data
        )
    }

    /**
     * SPL Memo instruction.
     */
    fun memo(message: String): TransactionInstruction {
        return TransactionInstruction(
            programId = PublicKey.MEMO_PROGRAM_ID,
            keys = emptyList(),
            data = message.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Compute budget: set compute unit limit.
     */
    fun setComputeUnitLimit(units: Int): TransactionInstruction {
        val stream = ByteArrayOutputStream()
        stream.write(2) // SetComputeUnitLimit discriminator
        stream.write(u32Le(units))
        return TransactionInstruction(
            programId = PublicKey.COMPUTE_BUDGET_PROGRAM_ID,
            keys = emptyList(),
            data = stream.toByteArray()
        )
    }

    /**
     * Compute budget: set compute unit price in micro-lamports.
     */
    fun setComputeUnitPrice(microLamports: Long): TransactionInstruction {
        val stream = ByteArrayOutputStream()
        stream.write(3) // SetComputeUnitPrice discriminator
        stream.write(u64Le(microLamports))
        return TransactionInstruction(
            programId = PublicKey.COMPUTE_BUDGET_PROGRAM_ID,
            keys = emptyList(),
            data = stream.toByteArray()
        )
    }
}
