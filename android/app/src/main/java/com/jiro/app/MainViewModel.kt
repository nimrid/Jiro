package com.jiro.app

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val isWalletInstalled: Boolean = true,
    val isConnected: Boolean = false,
    val publicKey: PublicKey? = null,
    val accountLabel: String? = null,
    val ownerUsdcBalance: Long = 0L,
    val ownerSolBalance: Long = 0L,
    val vaultUsdcBalance: Long = 0L,
    val vaultThreshold: Long = 5_000_000L, // $5.00
    val vaultMaxBalance: Long = 100_000_000L, // $100.00
    val isVaultInitialized: Boolean = false,
    val paymentInput: String = "4.35",
    val merchantAddress: String = "2WMvCS7UnQPk75qjqudfyoxnZx24Rcxsq58RZd2Zau5o",
    val roundWholeDollarUp: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val lastTxSignature: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mwaRepository = MwaRepository(application)
    private val rpcClient = SolanaRpcClient()
    private val keeperApi = KeeperApi()

    private val programId = PublicKey(BuildConfig.PROGRAM_ID)
    private val usdcMint = PublicKey(BuildConfig.USDC_MINT)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        val installed = mwaRepository.isWalletInstalled()
        val cachedPubkey = mwaRepository.getCachedPublicKey()
        _uiState.update {
            it.copy(
                isWalletInstalled = installed,
                isConnected = cachedPubkey != null,
                publicKey = cachedPubkey
            )
        }
        if (cachedPubkey != null) {
            refreshBalances()
        }
    }

    fun updatePaymentInput(input: String) {
        _uiState.update { it.copy(paymentInput = input, errorMessage = null) }
    }

    fun updateMerchantAddress(address: String) {
        _uiState.update { it.copy(merchantAddress = address, errorMessage = null) }
    }

    fun setRoundWholeDollarUp(enabled: Boolean) {
        _uiState.update { it.copy(roundWholeDollarUp = enabled) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    fun connectWallet(activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Opening Solana wallet...") }
            val res = mwaRepository.connectWallet(activity)
            res.fold(
                onSuccess = { session ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isConnected = true,
                            publicKey = session.publicKey,
                            accountLabel = session.accountLabel,
                            statusMessage = "Connected to wallet successfully!"
                        )
                    }
                    refreshBalances()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Wallet connection failed"
                        )
                    }
                }
            )
        }
    }

    fun disconnectWallet() {
        mwaRepository.clearSession()
        _uiState.update {
            it.copy(
                isConnected = false,
                publicKey = null,
                accountLabel = null,
                ownerUsdcBalance = 0L,
                ownerSolBalance = 0L,
                vaultUsdcBalance = 0L,
                isVaultInitialized = false,
                statusMessage = "Wallet disconnected"
            )
        }
    }

    fun refreshBalances() {
        val owner = _uiState.value.publicKey ?: return
        viewModelScope.launch {
            try {
                val ownerAta = PublicKey.findAssociatedTokenAddress(owner, usdcMint)
                val (vaultPda, _) = PublicKey.findProgramAddress(
                    listOf("vault".toByteArray(Charsets.UTF_8), owner.pubkey),
                    programId
                )
                val vaultAta = PublicKey.findAssociatedTokenAddress(vaultPda, usdcMint)

                val usdcBal = rpcClient.getTokenAccountBalance(ownerAta.toBase58())
                val solBal = rpcClient.getSolBalance(owner.toBase58())
                val vaultBal = rpcClient.getTokenAccountBalance(vaultAta.toBase58())
                val vaultConfig = rpcClient.getVaultConfig(vaultPda)

                _uiState.update {
                    it.copy(
                        ownerUsdcBalance = usdcBal,
                        ownerSolBalance = solBal,
                        vaultUsdcBalance = vaultBal,
                        isVaultInitialized = vaultConfig != null,
                        vaultThreshold = vaultConfig?.threshold ?: 5_000_000L,
                        vaultMaxBalance = vaultConfig?.maxBalance ?: 100_000_000L
                    )
                }
            } catch (e: Exception) {
                // Silently keep last balances or report error if needed
            }
        }
    }

    fun executeBundledPayment(activity: Activity) {
        val state = _uiState.value
        val owner = state.publicKey ?: run {
            _uiState.update { it.copy(errorMessage = "Please connect wallet first") }
            return
        }

        viewModelScope.launch {
            try {
                val paymentRaw = RoundUp.parseUsdcToRaw(state.paymentInput)
                val roundUpRaw = RoundUp.calculate(paymentRaw, state.roundWholeDollarUp)
                val merchantKey = PublicKey(state.merchantAddress.trim())

                // 1. Preflight check
                val preflight = PreflightCheck.validateBundledPayment(
                    ownerUsdcBalance = state.ownerUsdcBalance,
                    ownerSolBalance = state.ownerSolBalance,
                    vaultUsdcBalance = state.vaultUsdcBalance,
                    paymentAmount = paymentRaw,
                    roundUpAmount = roundUpRaw,
                    maxBalanceCap = state.vaultMaxBalance
                )

                if (preflight is PreflightCheck.PreflightResult.Error) {
                    _uiState.update { it.copy(errorMessage = preflight.message) }
                    return@launch
                }

                _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Fetching latest blockhash...") }

                // 2. Fetch recent blockhash
                val blockhash = rpcClient.getLatestBlockhash()

                // 3. Assemble bundled single-signature payment + round-up transaction
                _uiState.update { it.copy(statusMessage = "Building bundled payment transaction...") }
                val txBytes = PaymentTxBuilder.buildBundledPaymentTx(
                    owner = owner,
                    merchant = merchantKey,
                    usdcMint = usdcMint,
                    programId = programId,
                    paymentAmount = paymentRaw,
                    roundUpAmount = roundUpRaw,
                    recentBlockhash = blockhash
                )

                // 4. Request signature from connected real wallet
                _uiState.update { it.copy(statusMessage = "Awaiting approval in wallet...") }
                val signResult = mwaRepository.signAndSendTransaction(activity, txBytes)

                signResult.fold(
                    onSuccess = { signature ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                lastTxSignature = signature,
                                statusMessage = "Payment & Round-Up confirmed on Solana Devnet!",
                                paymentInput = ""
                            )
                        }
                        refreshBalances()
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Transaction failed: ${err.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun initializeVault(activity: Activity) {
        val owner = _uiState.value.publicKey ?: return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Fetching blockhash for vault init...") }
                val blockhash = rpcClient.getLatestBlockhash()

                val (vaultPda, _) = PublicKey.findProgramAddress(
                    listOf("vault".toByteArray(Charsets.UTF_8), owner.pubkey),
                    programId
                )
                val vaultAta = PublicKey.findAssociatedTokenAddress(vaultPda, usdcMint)

                val destinationAta = PublicKey.findAssociatedTokenAddress(owner, usdcMint)
                val keeperKey = PublicKey("BcxWJ2P5M7EwLhV6gZ8Y8P5aM4qN3rK2jT1yU9vX0wZ1") // Devnet keeper authority

                val ix = VaultInstructions.createInitVaultInstruction(
                    programId = programId,
                    owner = owner,
                    keeper = keeperKey,
                    mint = usdcMint,
                    destination = destinationAta,
                    vaultConfig = vaultPda,
                    vaultTokenAccount = vaultAta,
                    threshold = 5_000_000L, // $5.00
                    maxBalance = 50_000_000L // $50.00
                )

                val txBytes = TransactionBuilder.buildWireTransaction(
                    instructions = listOf(ix),
                    feePayer = owner,
                    recentBlockhash = blockhash
                )

                _uiState.update { it.copy(statusMessage = "Approve vault initialization in wallet...") }
                val signResult = mwaRepository.signAndSendTransaction(activity, txBytes)
                signResult.fold(
                    onSuccess = { sig ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                lastTxSignature = sig,
                                statusMessage = "Vault initialized successfully!"
                            )
                        }
                        refreshBalances()
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Vault init failed: ${err.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Error initializing vault: ${e.message}")
                }
            }
        }
    }

    fun withdrawVault(activity: Activity) {
        val state = _uiState.value
        val owner = state.publicKey ?: return
        if (state.vaultUsdcBalance <= 0L) {
            _uiState.update { it.copy(errorMessage = "Vault balance is empty") }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Fetching blockhash for withdraw...") }
                val blockhash = rpcClient.getLatestBlockhash()

                val (vaultPda, _) = PublicKey.findProgramAddress(
                    listOf("vault".toByteArray(Charsets.UTF_8), owner.pubkey),
                    programId
                )
                val vaultAta = PublicKey.findAssociatedTokenAddress(vaultPda, usdcMint)
                val ownerAta = PublicKey.findAssociatedTokenAddress(owner, usdcMint)

                val ix = VaultInstructions.createWithdrawInstruction(
                    programId = programId,
                    owner = owner,
                    vaultConfig = vaultPda,
                    mint = usdcMint,
                    vaultTokenAccount = vaultAta,
                    destinationTokenAccount = ownerAta,
                    amount = state.vaultUsdcBalance
                )

                val txBytes = TransactionBuilder.buildWireTransaction(
                    instructions = listOf(ix),
                    feePayer = owner,
                    recentBlockhash = blockhash
                )

                _uiState.update { it.copy(statusMessage = "Approve withdraw in wallet...") }
                val signResult = mwaRepository.signAndSendTransaction(activity, txBytes)
                signResult.fold(
                    onSuccess = { sig ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                lastTxSignature = sig,
                                statusMessage = "Withdrew $${RoundUp.formatUsdc(state.vaultUsdcBalance)} back to your wallet!"
                            )
                        }
                        refreshBalances()
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Withdraw failed: ${err.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Error withdrawing from vault: ${e.message}")
                }
            }
        }
    }
}
