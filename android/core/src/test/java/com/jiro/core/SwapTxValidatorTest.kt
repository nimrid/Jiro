package com.jiro.core

import org.junit.Assert.*
import org.junit.Test

class SwapTxValidatorTest {

    private val user = PublicKey("9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM")
    private val attacker = PublicKey("11111111111111111111111111111111")
    private val usdc = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
    private val stockMint = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")
    private val dummyBlockhash = ByteArray(32) { 1 }

    @Test
    fun testValidStaticAndSimulationPasses() {
        val swapIx = Instructions.mockSwap(
            owner = user,
            stockMint = stockMint,
            usdcMint = usdc,
            usdcIn = 5_000_000L,
            minOut = 50_000L
        )

        val msg = MessageV0.compile(
            payer = user,
            instructions = listOf(
                Instructions.setComputeUnitLimit(300_000),
                swapIx
            ),
            recentBlockhash = dummyBlockhash
        )

        // Static validation passes
        SwapTxValidator.validateStatic(msg, user)

        // Simulation validation passes
        val sim = SimulationResult(
            err = null,
            preSolLamports = 10_000_000L,
            postSolLamports = 9_995_000L, // 5000 lamports fee
            tokenBalances = listOf(
                SimulatedTokenBalance(
                    account = Ata.deriveAssociatedTokenAddress(user, usdc),
                    mint = usdc,
                    owner = user,
                    preAmount = 10_000_000L,
                    postAmount = 5_000_000L // -5_000_000 delta
                ),
                SimulatedTokenBalance(
                    account = Ata.deriveAssociatedTokenAddress(user, stockMint),
                    mint = stockMint,
                    owner = user,
                    preAmount = 0L,
                    postAmount = 50_000L // +50_000 delta
                )
            )
        )

        SwapTxValidator.validateSimulationDeltas(
            user = user,
            usdcMint = usdc,
            stockMint = stockMint,
            expectedUsdcIn = 5_000_000L,
            minStockOut = 50_000L,
            maxSolFeeLamports = 50_000L,
            simulation = sim
        )
    }

    @Test(expected = SwapValidationError.WrongFeePayer::class)
    fun testRejectWrongFeePayer() {
        val msg = MessageV0.compile(
            payer = attacker, // Attacker is fee payer!
            instructions = listOf(Instructions.setComputeUnitLimit(200_000)),
            recentBlockhash = dummyBlockhash
        )
        SwapTxValidator.validateStatic(msg, user)
    }

    @Test(expected = SwapValidationError.DisallowedProgram::class)
    fun testRejectUnknownProgram() {
        val unknownProg = PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5D1")
        val maliciousIx = TransactionInstruction(
            programId = unknownProg,
            keys = listOf(AccountMeta(user, isSigner = true, isWritable = true)),
            data = byteArrayOf(1, 2, 3)
        )
        val msg = MessageV0.compile(
            payer = user,
            instructions = listOf(maliciousIx),
            recentBlockhash = dummyBlockhash
        )
        SwapTxValidator.validateStatic(msg, user)
    }

    @Test(expected = SwapValidationError.ForbiddenInstruction::class)
    fun testRejectApproveInstruction() {
        val approveIx = TransactionInstruction(
            programId = PublicKey.TOKEN_PROGRAM_ID,
            keys = listOf(AccountMeta(user, isSigner = true, isWritable = true)),
            data = byteArrayOf(SwapTxValidator.IX_APPROVE, 1, 2)
        )
        val msg = MessageV0.compile(
            payer = user,
            instructions = listOf(approveIx),
            recentBlockhash = dummyBlockhash
        )
        SwapTxValidator.validateStatic(msg, user)
    }

    @Test(expected = SwapValidationError.ForbiddenInstruction::class)
    fun testRejectSetAuthorityInstruction() {
        val setAuthIx = TransactionInstruction(
            programId = PublicKey.TOKEN_PROGRAM_ID,
            keys = listOf(AccountMeta(user, isSigner = true, isWritable = true)),
            data = byteArrayOf(SwapTxValidator.IX_SET_AUTHORITY, 1)
        )
        val msg = MessageV0.compile(
            payer = user,
            instructions = listOf(setAuthIx),
            recentBlockhash = dummyBlockhash
        )
        SwapTxValidator.validateStatic(msg, user)
    }

    @Test(expected = SwapValidationError.UnauthorizedTokenDrain::class)
    fun testRejectUnauthorizedTokenDrain() {
        val victimMint = PublicKey("2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a")
        val sim = SimulationResult(
            err = null,
            preSolLamports = 10_000_000L,
            postSolLamports = 9_995_000L,
            tokenBalances = listOf(
                SimulatedTokenBalance(Ata.deriveAssociatedTokenAddress(user, usdc), usdc, user, 10_000_000L, 5_000_000L),
                SimulatedTokenBalance(Ata.deriveAssociatedTokenAddress(user, stockMint), stockMint, user, 0L, 50_000L),
                // Another token drained!
                SimulatedTokenBalance(Ata.deriveAssociatedTokenAddress(user, victimMint), victimMint, user, 100_000L, 0L)
            )
        )

        SwapTxValidator.validateSimulationDeltas(
            user = user,
            usdcMint = usdc,
            stockMint = stockMint,
            expectedUsdcIn = 5_000_000L,
            minStockOut = 50_000L,
            maxSolFeeLamports = 50_000L,
            simulation = sim
        )
    }

    @Test(expected = SwapValidationError.SlippageViolation::class)
    fun testRejectSlippageViolation() {
        val sim = SimulationResult(
            err = null,
            preSolLamports = 10_000_000L,
            postSolLamports = 9_995_000L,
            tokenBalances = listOf(
                SimulatedTokenBalance(Ata.deriveAssociatedTokenAddress(user, usdc), usdc, user, 10_000_000L, 5_000_000L),
                // Received only 40_000 stock tokens, less than 50_000 minOut!
                SimulatedTokenBalance(Ata.deriveAssociatedTokenAddress(user, stockMint), stockMint, user, 0L, 40_000L)
            )
        )

        SwapTxValidator.validateSimulationDeltas(
            user = user,
            usdcMint = usdc,
            stockMint = stockMint,
            expectedUsdcIn = 5_000_000L,
            minStockOut = 50_000L,
            maxSolFeeLamports = 50_000L,
            simulation = sim
        )
    }
}
