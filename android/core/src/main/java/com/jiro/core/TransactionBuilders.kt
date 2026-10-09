package com.jiro.core

sealed class TransactionPlan {
    data class Single(val message: MessageV0) : TransactionPlan()
    data class TwoStep(val step1: MessageV0, val step2: MessageV0) : TransactionPlan()
}

object PaymentTxBuilder {

    /**
     * Builds a bundled transaction: Merchant Payment + Round-Up Vault Deposit.
     */
    fun buildBundledPayment(
        user: PublicKey,
        merchant: PublicKey,
        usdcMint: PublicKey,
        paymentAmount: Money,
        roundUpAmount: Money,
        vaultId: Byte,
        recentBlockhash: ByteArray,
        memoText: String? = null,
        computeUnitPriceMicroLamports: Long = 10_000L
    ): MessageV0 {
        val instructions = mutableListOf<TransactionInstruction>()

        // 1. Compute budget
        instructions.add(Instructions.setComputeUnitLimit(300_000))
        if (computeUnitPriceMicroLamports > 0) {
            instructions.add(Instructions.setComputeUnitPrice(computeUnitPriceMicroLamports))
        }

        // 2. Merchant payment transfer
        val userUsdcAta = Ata.deriveAssociatedTokenAddress(user, usdcMint)
        val merchantUsdcAta = Ata.deriveAssociatedTokenAddress(merchant, usdcMint)
        instructions.add(
            Instructions.transferChecked(
                source = userUsdcAta,
                mint = usdcMint,
                destination = merchantUsdcAta,
                authority = user,
                amount = paymentAmount.baseUnits,
                decimals = 6.toByte()
            )
        )

        // 3. Round-up deposit (if > 0)
        if (roundUpAmount > Money.ZERO) {
            instructions.add(
                Instructions.deposit(
                    owner = user,
                    mint = usdcMint,
                    vaultId = vaultId,
                    amount = roundUpAmount.baseUnits,
                    ownerTokenAccount = userUsdcAta
                )
            )
        }

        // 4. Memo
        if (!memoText.isNullOrEmpty()) {
            instructions.add(Instructions.memo(memoText))
        }

        val message = MessageV0.compile(
            payer = user,
            instructions = instructions,
            recentBlockhash = recentBlockhash
        )

        val wireSize = message.serializeWireTransaction().size
        require(wireSize <= MessageV0.MAX_TRANSACTION_SIZE) {
            "Payment transaction size $wireSize exceeds max limit ${MessageV0.MAX_TRANSACTION_SIZE}"
        }

        return message
    }
}

object InvestTxBuilder {

    /**
     * Composes an investing plan (withdraw from vault + swap into tokenized stock).
     * If the combined transaction exceeds 1232 bytes, falls back to a TwoStep plan.
     */
    fun buildInvestPlan(
        user: PublicKey,
        usdcMint: PublicKey,
        stockMint: PublicKey,
        vaultId: Byte,
        investAmount: Money,
        minOut: Long,
        swapInstruction: TransactionInstruction,
        recentBlockhash: ByteArray
    ): TransactionPlan {
        // Build withdraw instruction
        val userUsdcAta = Ata.deriveAssociatedTokenAddress(user, usdcMint)
        val withdrawIx = Instructions.withdraw(
            owner = user,
            mint = usdcMint,
            vaultId = vaultId,
            amount = investAmount.baseUnits,
            destinationTokenAccount = userUsdcAta
        )

        // Try single atomic transaction
        val singleIxs = listOf(
            Instructions.setComputeUnitLimit(400_000),
            Instructions.setComputeUnitPrice(10_000L),
            withdrawIx,
            swapInstruction
        )

        val atomicMessage = MessageV0.compile(
            payer = user,
            instructions = singleIxs,
            recentBlockhash = recentBlockhash
        )

        val wireSize = atomicMessage.serializeWireTransaction().size
        if (wireSize <= MessageV0.MAX_TRANSACTION_SIZE) {
            return TransactionPlan.Single(atomicMessage)
        }

        // Fallback: Two-transaction plan
        val step1 = MessageV0.compile(
            payer = user,
            instructions = listOf(
                Instructions.setComputeUnitLimit(200_000),
                withdrawIx
            ),
            recentBlockhash = recentBlockhash
        )

        val step2 = MessageV0.compile(
            payer = user,
            instructions = listOf(
                Instructions.setComputeUnitLimit(300_000),
                swapInstruction
            ),
            recentBlockhash = recentBlockhash
        )

        return TransactionPlan.TwoStep(step1 = step1, step2 = step2)
    }
}

object SetupTxBuilder {

    /**
     * Builds setup transactions: initializes vault PDA + creates stock ATAs in batches if needed.
     */
    fun buildSetupTransactions(
        user: PublicKey,
        usdcMint: PublicKey,
        vaultId: Byte,
        maxBalance: Long,
        stockMints: List<Pair<PublicKey, PublicKey>>, // (mint, tokenProgramId)
        recentBlockhash: ByteArray
    ): List<MessageV0> {
        val transactions = mutableListOf<MessageV0>()
        val currentBatch = mutableListOf<TransactionInstruction>()

        // 1. Init vault
        currentBatch.add(Instructions.setComputeUnitLimit(300_000))
        currentBatch.add(
            Instructions.initVault(
                owner = user,
                mint = usdcMint,
                vaultId = vaultId,
                maxBalance = maxBalance
            )
        )

        // 2. Create ATAs for stocks in basket
        for ((stockMint, programId) in stockMints) {
            val createAtaIx = Instructions.createAssociatedTokenAccountIdempotent(
                payer = user,
                walletAddress = user,
                mint = stockMint,
                tokenProgramId = programId
            )

            // Test if adding exceeds size limit
            val testIxs = currentBatch + createAtaIx
            val testMsg = MessageV0.compile(user, testIxs, recentBlockhash)
            if (testMsg.serializeWireTransaction().size <= MessageV0.MAX_TRANSACTION_SIZE) {
                currentBatch.add(createAtaIx)
            } else {
                // Seal current batch and start new
                transactions.add(MessageV0.compile(user, currentBatch, recentBlockhash))
                currentBatch.clear()
                currentBatch.add(Instructions.setComputeUnitLimit(300_000))
                currentBatch.add(createAtaIx)
            }
        }

        if (currentBatch.isNotEmpty()) {
            transactions.add(MessageV0.compile(user, currentBatch, recentBlockhash))
        }

        return transactions
    }
}
