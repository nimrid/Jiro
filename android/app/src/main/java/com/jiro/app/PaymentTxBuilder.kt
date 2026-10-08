package com.jiro.app

object PaymentTxBuilder {

    /**
     * Builds the bundled single-signature transaction:
     * 1. SPL USDC transfer from owner's ATA to merchant's ATA of [paymentAmount].
     * 2. Vault deposit into user's vault PDA ATA of [roundUpAmount].
     *
     * Total user experience: 1 single signature approving both payment and investment.
     */
    fun buildBundledPaymentTx(
        owner: PublicKey,
        merchant: PublicKey,
        usdcMint: PublicKey,
        programId: PublicKey,
        paymentAmount: Long,
        roundUpAmount: Long,
        recentBlockhash: String
    ): ByteArray {
        require(paymentAmount > 0) { "Payment amount must be greater than zero" }
        require(roundUpAmount >= 0) { "Round-up amount cannot be negative" }

        val ownerAta = PublicKey.findAssociatedTokenAddress(owner, usdcMint)
        val merchantAta = PublicKey.findAssociatedTokenAddress(merchant, usdcMint)

        val (vaultPda, _) = PublicKey.findProgramAddress(
            listOf("vault".toByteArray(Charsets.UTF_8), owner.pubkey),
            programId
        )
        val vaultAta = PublicKey.findAssociatedTokenAddress(vaultPda, usdcMint)

        val instructions = mutableListOf<TransactionInstruction>()

        // Instruction 1: SPL Transfer to merchant
        val transferIx = SplToken.createTransferCheckedInstruction(
            source = ownerAta,
            mint = usdcMint,
            destination = merchantAta,
            owner = owner,
            amount = paymentAmount,
            decimals = 6
        )
        instructions.add(transferIx)

        // Instruction 2: Vault deposit (if roundUpAmount > 0)
        if (roundUpAmount > 0) {
            val depositIx = VaultInstructions.createDepositInstruction(
                programId = programId,
                owner = owner,
                vaultConfig = vaultPda,
                mint = usdcMint,
                vaultTokenAccount = vaultAta,
                ownerTokenAccount = ownerAta,
                amount = roundUpAmount
            )
            instructions.add(depositIx)
        }

        return TransactionBuilder.buildWireTransaction(
            instructions = instructions,
            feePayer = owner,
            recentBlockhash = recentBlockhash
        )
    }
}
