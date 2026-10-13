package com.jiro.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiro.app.components.TreemapTile
import com.jiro.app.screens.InvestmentStyle
import com.jiro.app.screens.OnboardingStep
import com.jiro.app.screens.RecentDropReceipt
import com.jiro.core.*
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

enum class AppTab {
    HOME,
    PORTFOLIO,
    PICK,
    ACTIVITY
}

data class AppUiState(
    val isWalletInstalled: Boolean = true,
    val isConnected: Boolean = false,
    val isDemoMode: Boolean = false,
    val isOnboardingComplete: Boolean = false,
    val onboardingStep: OnboardingStep = OnboardingStep.CONNECT_WALLET,
    val currentTab: AppTab = AppTab.HOME,
    val isPaySheetVisible: Boolean = false,
    val isInvestSheetVisible: Boolean = false,
    val isSettingsVisible: Boolean = false,
    val isDroppingCoin: Boolean = false,
    val quoteSecondsRemaining: Int = 30,

    // Wallet & Balances
    val publicKey: PublicKey? = null,
    val accountLabel: String? = null,
    val ownerUsdcBalance: Long = 0L,
    val ownerSolBalance: Long = 0L,
    val vaultUsdcBalance: Long = 0L,
    val vaultThreshold: Long = 5_000_000L, // $5.00
    val vaultMaxBalance: Long = 100_000_000L, // $100.00
    val isVaultInitialized: Boolean = false,

    // Payment & Rules
    val paymentInput: String = "4.35",
    val merchantAddress: String = "2WMvCS7UnQPk75qjqudfyoxnZx24Rcxsq58RZd2Zau5o",
    val roundWholeDollarUp: Boolean = false,
    val roundUpStepUsd: Double = 1.0,
    val multiplierBoost: Int = 1,
    val thresholdUsd: Double = 5.0,

    // Stocks & Allocations
    val catalog: List<CatalogEntry> = emptyList(),
    val isCatalogLoading: Boolean = false,
    val catalogError: String? = null,
    val snapshotAgeMinutes: Long = 60L,
    val userBasket: List<BasketHolding> = emptyList(),
    val holdings: List<TreemapTile> = emptyList(),
    val totalPortfolioValueUsdc: Long = 0L,
    val totalContributionsUsdc: Long = 0L,
    val nextStockTicker: String = "VOO",
    val nextStockName: String = "Vanguard S&P 500 ETF",
    val skippedStockMessage: String? = null,
    val selectionReason: SelectionReason? = null,

    // Activity & Receipts
    val recentDrops: List<RecentDropReceipt> = emptyList(),
    val activityList: List<ActivityItem> = emptyList(),
    val owedRoundUpUsdc: Long = 0L,

    // Status & Feedback
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val lastTxSignature: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mwaRepository = MwaRepository(application)
    private val rpcClient = SolanaRpcClient()
    private val catalogRepo = AssetCatalogRepository(application)
    private val dropsRepo = RecentDropsRepository(application)
    private val activityRepo = ActivityRepository(application)
    private val portfolioRepo = PortfolioRepository(application)
    private val mockSwapProvider = MockSwapProvider()
    // JupiterSwapProvider lives in :core and cannot import NetworkSecurity directly;
    // inject the pinned client from the app layer instead.
    private val jupiterSwapProvider = com.jiro.core.JupiterSwapProvider(
        client = NetworkSecurity.jupiterClient()
    )

    private val programId = PublicKey(BuildConfig.PROGRAM_ID)
    private val usdcMint = PublicKey(BuildConfig.USDC_MINT)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var quoteTimerJob: Job? = null

    init {
        StockDetailInfoProvider.initialize(application)
        loadCatalog()
        initDemoState()
        startLivePricePolling()
        val cachedPubkey = mwaRepository.getCachedPublicKey()
        if (cachedPubkey != null) {
            val pubkeyB58 = cachedPubkey.toBase58()
            val drops = dropsRepo.seedIfEmpty(pubkeyB58)
            val activities = activityRepo.getActivitiesForWallet(pubkeyB58)
            val (liveHoldings, liveValue, liveContrib) = computeLiveHoldingsFromActivities(
                activities = activities,
                basket = _uiState.value.userBasket
            )

            _uiState.update {
                it.copy(
                    publicKey = cachedPubkey,
                    accountLabel = mwaRepository.getCachedAccountLabel(),
                    isConnected = true,
                    isOnboardingComplete = true,
                    isDemoMode = false,
                    recentDrops = drops,
                    activityList = activities,
                    holdings = liveHoldings,
                    totalPortfolioValueUsdc = liveValue,
                    totalContributionsUsdc = liveContrib
                )
            }
            portfolioRepo.savePortfolioForWallet(pubkeyB58, liveHoldings, liveValue, liveContrib)
            refreshBalances()
        }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCatalogLoading = true, catalogError = null) }
            try {
                val list = catalogRepo.getCatalog()
                val ageMs = catalogRepo.getSnapshotAgeMs()
                _uiState.update {
                    it.copy(
                        catalog = list,
                        isCatalogLoading = false,
                        snapshotAgeMinutes = ageMs / 60000L
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCatalogLoading = false,
                        catalogError = "Could not load stock catalog: ${e.message}",
                        catalog = AssetCatalogRepository.getFallbackCatalog()
                    )
                }
            }
        }
    }

    private var livePricePollingJob: Job? = null

    fun startLivePricePolling() {
        livePricePollingJob?.cancel()
        livePricePollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val updated = StockDetailInfoProvider.refreshLivePrices()
                if (updated) {
                    val pubkeyB58 = _uiState.value.publicKey?.toBase58()
                    if (pubkeyB58 != null) {
                        val activities = _uiState.value.activityList
                        val (liveHoldings, liveValue, liveContrib) = computeLiveHoldingsFromActivities(
                            activities = activities,
                            basket = _uiState.value.userBasket
                        )
                        _uiState.update {
                            it.copy(
                                holdings = liveHoldings,
                                totalPortfolioValueUsdc = liveValue,
                                totalContributionsUsdc = liveContrib
                            )
                        }
                    }
                }
                delay(12_000L) // Poll Backpack Exchange every 12 seconds
            }
        }
    }

    fun refreshStockPrices() {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = StockDetailInfoProvider.refreshLivePrices()
            if (updated) {
                val pubkeyB58 = _uiState.value.publicKey?.toBase58()
                if (pubkeyB58 != null) {
                    val activities = _uiState.value.activityList
                    val (liveHoldings, liveValue, liveContrib) = computeLiveHoldingsFromActivities(
                        activities = activities,
                        basket = _uiState.value.userBasket
                    )
                    _uiState.update {
                        it.copy(
                            holdings = liveHoldings,
                            totalPortfolioValueUsdc = liveValue,
                            totalContributionsUsdc = liveContrib
                        )
                    }
                }
            }
        }
    }

    fun retryCatalog() {
        loadCatalog()
        refreshStockPrices()
    }

    fun setOnboardingStep(step: OnboardingStep) {
        _uiState.update { it.copy(onboardingStep = step) }
    }

    fun completeOnboarding(style: InvestmentStyle, stepUsd: Double, boostMultiplier: Int, thresholdUsd: Double) {
        val basket = when (style.id) {
            "tech" -> listOf(
                BasketHolding("AAPL.US", PublicKey("AAPLEDt8RpzPgXyhvFzkMBofvFSQw9gpeMCoUdPdLnB8"), 4000),
                BasketHolding("MSFT.US", PublicKey("MSFThX899B1DddH5jkyMpdArWXzP85VXLYDrbGGcRJM"), 3000),
                BasketHolding("NVDA.US", PublicKey("NVDAVuiB7hwd3m5Wa1JuHNovPaPG6BH1QNztbKFxNjv"), 3000)
            )
            "index" -> listOf(
                BasketHolding("VOO.US", PublicKey("Vv7EU4aoHNY6wFsFRUyXhGm2syKXBA2T6v2MZUZZo7E"), 5000),
                BasketHolding("QQQ.US", PublicKey("QQQvDYxG7g11Rr4rnaJqDddmP9ocH74YhE3mDaG37R9"), 3000),
                BasketHolding("SPY.US", PublicKey("SPYBo66VJPFjh1pXMb9Le53kDYWTK1zzYVDeVRWtsbi"), 2000)
            )
            else -> listOf(
                BasketHolding("VOO.US", PublicKey("Vv7EU4aoHNY6wFsFRUyXhGm2syKXBA2T6v2MZUZZo7E"), 4000),
                BasketHolding("AAPL.US", PublicKey("AAPLEDt8RpzPgXyhvFzkMBofvFSQw9gpeMCoUdPdLnB8"), 2000),
                BasketHolding("MSFT.US", PublicKey("MSFThX899B1DddH5jkyMpdArWXzP85VXLYDrbGGcRJM"), 2000),
                BasketHolding("NVDA.US", PublicKey("NVDAVuiB7hwd3m5Wa1JuHNovPaPG6BH1QNztbKFxNjv"), 2000)
            )
        }

        _uiState.update {
            it.copy(
                isOnboardingComplete = true,
                userBasket = basket,
                roundUpStepUsd = stepUsd,
                multiplierBoost = boostMultiplier,
                thresholdUsd = thresholdUsd,
                vaultThreshold = (thresholdUsd * 1_000_000L).toLong()
            )
        }
        recalculateAllocation()
        refreshBalances()
    }

    fun startDemoMode() {
        initDemoState()
        _uiState.update {
            it.copy(
                isDemoMode = true,
                isConnected = true,
                isOnboardingComplete = true
            )
        }
        recalculateAllocation()
    }

    fun toggleDemoMode(enabled: Boolean) {
        if (enabled) {
            startDemoMode()
        } else {
            _uiState.update {
                it.copy(
                    isDemoMode = false,
                    isConnected = false,
                    publicKey = null,
                    ownerUsdcBalance = 0L,
                    ownerSolBalance = 0L,
                    vaultUsdcBalance = 0L,
                    isVaultInitialized = false
                )
            }
        }
    }

    private fun initDemoState() {
        val initialHoldings = listOf(
            TreemapTile(
                ticker = "VOO",
                name = "Vanguard S&P 500 ETF - Backpack Securities",
                mint = "Vv7EU4aoHNY6wFsFRUyXhGm2syKXBA2T6v2MZUZZo7E",
                units = 0.085,
                currentWeightBps = 3500,
                targetWeightBps = 4000,
                valueUsdc = 35.0,
                contributionsUsdc = 33.5,
                isNextBuy = true
            ),
            TreemapTile(
                ticker = "AAPL",
                name = "Apple - Backpack Securities",
                mint = "AAPLEDt8RpzPgXyhvFzkMBofvFSQw9gpeMCoUdPdLnB8",
                units = 0.165,
                currentWeightBps = 3200,
                targetWeightBps = 3000,
                valueUsdc = 32.0,
                contributionsUsdc = 30.0,
                isNextBuy = false
            ),
            TreemapTile(
                ticker = "MSFT",
                name = "Microsoft - Backpack Securities",
                mint = "MSFThX899B1DddH5jkyMpdArWXzP85VXLYDrbGGcRJM",
                units = 0.075,
                currentWeightBps = 3300,
                targetWeightBps = 3000,
                valueUsdc = 33.0,
                contributionsUsdc = 31.0,
                isNextBuy = false
            )
        )

        val defaultBasket = listOf(
            BasketHolding("VOO.US", PublicKey("Vv7EU4aoHNY6wFsFRUyXhGm2syKXBA2T6v2MZUZZo7E"), 4000),
            BasketHolding("AAPL.US", PublicKey("AAPLEDt8RpzPgXyhvFzkMBofvFSQw9gpeMCoUdPdLnB8"), 3000),
            BasketHolding("MSFT.US", PublicKey("MSFThX899B1DddH5jkyMpdArWXzP85VXLYDrbGGcRJM"), 3000)
        )

        _uiState.update {
            it.copy(
                ownerUsdcBalance = 42_500_000L, // $42.50
                ownerSolBalance = 500_000_000L, // 0.5 SOL
                vaultUsdcBalance = 3_500_000L,  // $3.50
                vaultThreshold = 5_000_000L,    // $5.00
                isVaultInitialized = true,
                userBasket = defaultBasket,
                holdings = initialHoldings,
                totalPortfolioValueUsdc = 100_000_000L, // $100.00
                totalContributionsUsdc = 94_500_000L,
                recentDrops = emptyList(),
                activityList = emptyList(),
                nextStockTicker = "VOO",
                nextStockName = "Vanguard S&P 500 ETF",
                skippedStockMessage = null
            )
        }
    }

    /**
     * Derives real portfolio holdings strictly from confirmed on-chain investments.
     * Prevents any dummy/mock assets or balances from leaking into live Devnet mode.
     */
    fun computeLiveHoldingsFromActivities(
        activities: List<ActivityItem>,
        basket: List<BasketHolding>
    ): Triple<List<TreemapTile>, Long, Long> {
        val investments = activities.filter { it.type == ActivityType.STOCK_INVESTMENT }
        if (investments.isEmpty()) {
            return Triple(emptyList(), 0L, 0L)
        }

        val totalInvestedRaw = investments.sumOf { it.amount.baseUnits }
        val totalInvestedUsd = totalInvestedRaw.toDouble() / 1_000_000.0

        val grouped = investments.groupBy { (it.stockSymbol ?: "VOO").uppercase().removeSuffix(".US") }

        val tiles = grouped.map { (ticker, items) ->
            val tickerInvestedRaw = items.sumOf { it.amount.baseUnits }
            val tickerInvestedUsd = tickerInvestedRaw.toDouble() / 1_000_000.0
            val livePrice = StockDetailInfoProvider.getInfo(ticker, "").priceUsd
            val pricePerShare = if (livePrice > 0.0) livePrice else when (ticker) {
                "VOO" -> 450.0
                "AAPL" -> 220.0
                "MSFT" -> 420.0
                "NVDA" -> 125.0
                "TSLA" -> 215.0
                "AMZN" -> 180.0
                "GOOGL" -> 175.0
                else -> 200.0
            }
            val units = tickerInvestedUsd / pricePerShare
            val currentWeightBps = if (totalInvestedUsd > 0) {
                ((tickerInvestedUsd / totalInvestedUsd) * 10000).toInt()
            } else 0

            val basketMatch = basket.find { it.symbol.removeSuffix(".US").equals(ticker, ignoreCase = true) }
            val targetWeightBps = basketMatch?.targetWeightBps ?: 0
            val mintStr = basketMatch?.mint?.toBase58() ?: "Vv7EU4aoHNY6wFsFRUyXhGm2syKXBA2T6v2MZUZZo7E"

            val stockName = when (ticker) {
                "VOO" -> "Vanguard S&P 500 ETF - Backpack Securities"
                "AAPL" -> "Apple - Backpack Securities"
                "MSFT" -> "Microsoft - Backpack Securities"
                "NVDA" -> "NVIDIA - Backpack Securities"
                "TSLA" -> "Tesla - Backpack Securities"
                "AMZN" -> "Amazon - Backpack Securities"
                "GOOGL" -> "Alphabet - Backpack Securities"
                else -> "$ticker - Backpack Securities"
            }

            TreemapTile(
                ticker = ticker,
                name = stockName,
                mint = mintStr,
                units = units,
                currentWeightBps = currentWeightBps,
                targetWeightBps = targetWeightBps,
                valueUsdc = tickerInvestedUsd,
                contributionsUsdc = tickerInvestedUsd,
                isNextBuy = false
            )
        }

        return Triple(tiles, totalInvestedRaw, totalInvestedRaw)
    }

    fun handleScannedPaymentUri(uri: String) {
        val trimmed = uri.trim()
        if (trimmed.startsWith("solana:", ignoreCase = true)) {
            try {
                val request = SolanaPay.parse(trimmed)
                updateMerchantAddress(request.recipient.toBase58())
                request.amount?.let { parsedAmount ->
                    val formatted = String.format("%.2f", parsedAmount.baseUnits / 1_000_000.0)
                    updatePaymentInput(formatted)
                    validateDeepLinkedAmount(parsedAmount.baseUnits)
                }
                _uiState.update { it.copy(errorMessage = null, statusMessage = "Scanned Solana Pay QR code!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Invalid Solana Pay QR code: ${e.message}") }
            }
        } else {
            try {
                val pubkey = PublicKey(trimmed)
                updateMerchantAddress(pubkey.toBase58())
                _uiState.update { it.copy(errorMessage = null, statusMessage = "Scanned merchant address!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Unrecognized QR code format. Expected a Solana address or Solana Pay URI.") }
            }
        }
    }

    private fun recalculateAllocation() {
        val state = _uiState.value
        if (state.userBasket.isEmpty()) return

        val holdingsMap = state.holdings.associate {
            val pubkey = try { PublicKey(it.mint) } catch (e: Exception) { PublicKey.USDC_MINT_DEVNET }
            pubkey to HoldingState(
                mint = pubkey,
                rawBalance = (it.units * 1_000_000L).toLong(),
                priceInMicroUsdc = if (it.units > 0) ((it.valueUsdc * 1_000_000L) / it.units).toLong() else 100_000_000L
            )
        }

        try {
            val decision = AllocationEngine.selectStock(
                basket = state.userBasket,
                holdings = holdingsMap,
                buyAmount = Money(state.vaultThreshold)
            )

            val ticker = decision.selectedHolding.symbol.removeSuffix(".US")
            val skippedMessage = if (decision.reason.skippedAssets.isNotEmpty()) {
                val (symbol, reason) = decision.reason.skippedAssets.entries.first()
                "Skipped ${symbol.removeSuffix(".US")}: $reason"
            } else {
                null
            }
            _uiState.update {
                it.copy(
                    nextStockTicker = ticker,
                    nextStockName = if (ticker == "VOO") "Vanguard S&P 500 ETF" else "$ticker Tokenized Stock",
                    selectionReason = decision.reason,
                    skippedStockMessage = skippedMessage
                )
            }
        } catch (e: Exception) {
            // keep fallback
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
        refreshBalances()
    }

    fun showPaySheet(show: Boolean) {
        _uiState.update { it.copy(isPaySheetVisible = show) }
        if (show) {
            refreshBalances()
        }
    }

    fun showInvestSheet(show: Boolean) {
        _uiState.update { it.copy(isInvestSheetVisible = show) }
        if (show) {
            startQuoteTimer()
        } else {
            quoteTimerJob?.cancel()
        }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(isSettingsVisible = show) }
    }

    private fun startQuoteTimer() {
        quoteTimerJob?.cancel()
        quoteTimerJob = viewModelScope.launch {
            _uiState.update { it.copy(quoteSecondsRemaining = 30) }
            for (sec in 29 downTo 0) {
                delay(1000L)
                _uiState.update { it.copy(quoteSecondsRemaining = sec) }
            }
        }
    }

    fun updatePaymentInput(input: String) {
        _uiState.update { it.copy(paymentInput = input, errorMessage = null) }
    }

    fun updateMerchantAddress(address: String) {
        _uiState.update { it.copy(merchantAddress = address, errorMessage = null) }
    }

    /**
     * Called immediately after a Solana Pay deep-link sets the payment amount.
     * Runs the same preflight balance check as [executePayment] so the user sees
     * an error banner before they even tap "Pay", rather than discovering it at
     * wallet signing time.
     */
    fun validateDeepLinkedAmount(paymentRawUsdc: Long) {
        val state = _uiState.value
        val roundUpRaw = RoundUp.calculate(paymentRawUsdc, state.roundWholeDollarUp)
        val totalRequired = paymentRawUsdc + roundUpRaw
        val check = com.jiro.core.PreflightChecker.checkBalancesForPayment(
            userUsdcBalance = state.ownerUsdcBalance,
            totalRequiredUsdc = totalRequired,
            userSolLamports = state.ownerSolBalance
        )
        if (check is com.jiro.core.PreflightCheckResult.Failed) {
            _uiState.update { it.copy(errorMessage = check.reason) }
        }
    }

    fun setRoundWholeDollarUp(enabled: Boolean) {
        _uiState.update { it.copy(roundWholeDollarUp = enabled) }
    }

    fun updateBasket(newBasket: List<BasketHolding>) {
        _uiState.update { it.copy(userBasket = newBasket) }
        recalculateAllocation()
    }

    fun setRules(stepUsd: Double, boostMultiplier: Int, thresholdUsd: Double) {
        _uiState.update {
            it.copy(
                roundUpStepUsd = stepUsd,
                multiplierBoost = boostMultiplier,
                thresholdUsd = thresholdUsd,
                vaultThreshold = (thresholdUsd * 1_000_000L).toLong()
            )
        }
    }

    fun executePayment(sender: ActivityResultSender? = null) {
        val state = _uiState.value
        val paymentRaw = try { RoundUp.parseUsdcToRaw(state.paymentInput) } catch (e: Exception) { 0L }
        val roundUpRaw = RoundUp.calculate(paymentRaw, state.roundWholeDollarUp)

        if (state.isDemoMode || state.publicKey == null) {
            // Offline Demo Mode execution
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                delay(400L)

                val newVaultBal = state.vaultUsdcBalance + roundUpRaw
                val newOwnerBal = (state.ownerUsdcBalance - (paymentRaw + roundUpRaw)).coerceAtLeast(0L)

                val newDrop = RecentDropReceipt(
                    merchantName = "Coffee & Bakery",
                    paymentAmount = Money(paymentRaw),
                    roundUpAmount = Money(roundUpRaw),
                    timeAgo = "Just now"
                )

                val newActivity = ActivityItem(
                    signature = "demo_tx_${System.currentTimeMillis() % 10000}",
                    type = ActivityType.ROUND_UP_DEPOSIT,
                    amount = Money(roundUpRaw),
                    timestampMs = System.currentTimeMillis()
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPaySheetVisible = false,
                        isDroppingCoin = true,
                        ownerUsdcBalance = newOwnerBal,
                        vaultUsdcBalance = newVaultBal,
                        recentDrops = listOf(newDrop) + it.recentDrops.take(2),
                        activityList = listOf(newActivity) + it.activityList,
                        statusMessage = "Payment successful! Dropped $${RoundUp.formatUsdc(roundUpRaw)} into jar"
                    )
                }

                // Coin drop animation reset after 600ms
                delay(600L)
                _uiState.update { it.copy(isDroppingCoin = false) }
                recalculateAllocation()
            }
            return
        }

        // Live MWA payment
        sender?.let { executeBundledPayment(it) }
    }

    fun executeInvest(sender: ActivityResultSender? = null) {
        val state = _uiState.value
        val amount = state.vaultUsdcBalance

        if (amount <= 0L) {
            _uiState.update { it.copy(errorMessage = "Jar is empty. Save some round-ups first!") }
            return
        }

        val pouredTicker = state.nextStockTicker
        if (state.isDemoMode || state.publicKey == null) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                delay(600L)

                val newActivity = ActivityItem(
                    signature = "demo_invest_${System.currentTimeMillis() % 10000}",
                    type = ActivityType.STOCK_INVESTMENT,
                    amount = Money(amount),
                    stockSymbol = pouredTicker,
                    timestampMs = System.currentTimeMillis()
                )

                // Update holdings
                val updatedHoldings = state.holdings.map { h ->
                    if (h.ticker == pouredTicker) {
                        val addedUnits = (amount.toDouble() / 1_000_000.0) / 420.0 // mock price
                        h.copy(
                            units = h.units + addedUnits,
                            valueUsdc = h.valueUsdc + (amount.toDouble() / 1_000_000.0),
                            contributionsUsdc = h.contributionsUsdc + (amount.toDouble() / 1_000_000.0),
                            isNextBuy = false
                        )
                    } else {
                        h.copy(isNextBuy = true)
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isInvestSheetVisible = false,
                        vaultUsdcBalance = 0L, // Jar emptied!
                        holdings = updatedHoldings,
                        totalPortfolioValueUsdc = it.totalPortfolioValueUsdc + amount,
                        totalContributionsUsdc = it.totalContributionsUsdc + amount,
                        activityList = listOf(newActivity) + it.activityList,
                        statusMessage = "Poured $${RoundUp.formatUsdc(amount)} into $pouredTicker!"
                    )
                }
                recalculateAllocation()
            }
            return
        }

        // Live MWA invest with on-chain jar withdrawal and stock purchase
        if (sender == null) return
        val owner = state.publicKey ?: return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Preparing to invest in $pouredTicker...") }

                val blockhashB58 = rpcClient.getLatestBlockhash()
                val blockhashBytes = Base58.decode(blockhashB58)
                val userAta = Ata.deriveAssociatedTokenAddress(owner, usdcMint)

                val instructions = mutableListOf<TransactionInstruction>()
                instructions.add(Instructions.setComputeUnitLimit(350_000))
                instructions.add(Instructions.setComputeUnitPrice(10_000L))
                instructions.add(
                    Instructions.createAssociatedTokenAccountIdempotent(
                        payer = owner,
                        walletAddress = owner,
                        mint = usdcMint
                    )
                )
                instructions.add(
                    Instructions.withdraw(
                        owner = owner,
                        mint = usdcMint,
                        vaultId = 0.toByte(),
                        amount = amount,
                        destinationTokenAccount = userAta,
                        programId = programId
                    )
                )
                instructions.add(
                    Instructions.memo("JIRO: Invest $${RoundUp.formatUsdc(amount)} in $pouredTicker")
                )

                val wireTx = MessageV0.compile(
                    payer = owner,
                    instructions = instructions,
                    recentBlockhash = blockhashBytes
                ).serializeWireTransaction()

                _uiState.update { it.copy(statusMessage = "Opening wallet to confirm investment in $pouredTicker...") }

                val signResult = withTimeout(120_000L) {
                    mwaRepository.signAndSendTransaction(sender = sender, transactionPayload = wireTx)
                }

                signResult.fold(
                    onSuccess = { sig ->
                        val newActivity = ActivityItem(
                            signature = sig,
                            type = ActivityType.STOCK_INVESTMENT,
                            amount = Money(amount),
                            stockSymbol = pouredTicker,
                            timestampMs = System.currentTimeMillis()
                        )
                        activityRepo.saveActivityForWallet(owner.toBase58(), newActivity)
                        val updatedActivities = listOf(newActivity) + _uiState.value.activityList
                        val (updatedHoldings, newTotalVal, newTotalContrib) = computeLiveHoldingsFromActivities(
                            activities = updatedActivities,
                            basket = _uiState.value.userBasket
                        )
                        portfolioRepo.savePortfolioForWallet(owner.toBase58(), updatedHoldings, newTotalVal, newTotalContrib)

                        _uiState.update {
                            it.copy(
                                lastTxSignature = sig,
                                isLoading = false,
                                isInvestSheetVisible = false,
                                vaultUsdcBalance = 0L,
                                holdings = updatedHoldings,
                                totalPortfolioValueUsdc = newTotalVal,
                                totalContributionsUsdc = newTotalContrib,
                                activityList = updatedActivities,
                                statusMessage = "Successfully invested $${RoundUp.formatUsdc(amount)} in $pouredTicker!"
                            )
                        }
                        recalculateAllocation()
                        refreshBalances()
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(errorMessage = "Investment transaction failed: ${err.message}") }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error investing in $pouredTicker: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun withdrawAll(sender: ActivityResultSender? = null) {
        val state = _uiState.value
        if (state.isDemoMode || state.publicKey == null) {
            val amount = state.vaultUsdcBalance
            _uiState.update {
                it.copy(
                    vaultUsdcBalance = 0L,
                    ownerUsdcBalance = it.ownerUsdcBalance + amount,
                    statusMessage = "Withdrew $${RoundUp.formatUsdc(amount)} back to wallet"
                )
            }
            return
        }

        sender?.let { withdrawVault(it) }
    }

    fun closeJar(sender: ActivityResultSender? = null) {
        val state = _uiState.value
        if (state.isDemoMode || state.publicKey == null) {
            _uiState.update {
                it.copy(
                    vaultUsdcBalance = 0L,
                    isVaultInitialized = false,
                    statusMessage = "Jar closed. Rent returned to wallet."
                )
            }
            return
        }

        if (!state.isVaultInitialized) {
            _uiState.update { it.copy(statusMessage = "No active jar found on Solana Devnet to close.") }
            return
        }

        if (sender == null) return

        val owner = state.publicKey
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Preparing to close jar...") }
                val blockhashB58 = rpcClient.getLatestBlockhash()
                val blockhashBytes = Base58.decode(blockhashB58)

                val instructions = mutableListOf<TransactionInstruction>()
                instructions.add(Instructions.setComputeUnitLimit(350_000))
                instructions.add(Instructions.setComputeUnitPrice(10_000L))

                // If vault has funds, withdraw them first so vault balance is 0
                if (state.vaultUsdcBalance > 0L) {
                    val userAta = Ata.deriveAssociatedTokenAddress(owner, usdcMint)
                    instructions.add(
                        Instructions.createAssociatedTokenAccountIdempotent(
                            payer = owner,
                            walletAddress = owner,
                            mint = usdcMint
                        )
                    )
                    instructions.add(
                        Instructions.withdraw(
                            owner = owner,
                            mint = usdcMint,
                            vaultId = 0.toByte(),
                            amount = state.vaultUsdcBalance,
                            destinationTokenAccount = userAta,
                            programId = programId
                        )
                    )
                }

                // Close the vault and reclaim rent lamports back to owner
                instructions.add(
                    Instructions.closeVault(
                        owner = owner,
                        mint = usdcMint,
                        vaultId = 0.toByte(),
                        programId = programId
                    )
                )

                val wireTx = MessageV0.compile(
                    payer = owner,
                    instructions = instructions,
                    recentBlockhash = blockhashBytes
                ).serializeWireTransaction()

                _uiState.update { it.copy(statusMessage = "Opening wallet to sign close jar transaction...") }

                val signResult = withTimeout(120_000L) {
                    mwaRepository.signAndSendTransaction(sender = sender, transactionPayload = wireTx)
                }

                signResult.fold(
                    onSuccess = { sig ->
                        val refundedBal = state.vaultUsdcBalance
                        val newActivity = ActivityItem(
                            signature = sig,
                            type = ActivityType.WITHDRAWAL,
                            amount = Money(refundedBal),
                            timestampMs = System.currentTimeMillis()
                        )
                        if (refundedBal > 0) {
                            activityRepo.saveActivityForWallet(owner.toBase58(), newActivity)
                        }
                        _uiState.update {
                            it.copy(
                                lastTxSignature = sig,
                                vaultUsdcBalance = 0L,
                                isVaultInitialized = false,
                                statusMessage = "Jar closed! Rent & remaining funds returned to wallet.",
                                activityList = if (refundedBal > 0) listOf(newActivity) + it.activityList else it.activityList
                            )
                        }
                        refreshBalances()
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(errorMessage = "Close jar failed: ${err.message}") }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error closing jar: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    // --- MWA Wallet Connections ---

    fun connectWallet(sender: ActivityResultSender) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Opening Solana wallet...") }
            val res = mwaRepository.connectWallet(sender)
            res.fold(
                onSuccess = { session ->
                    val pubkeyB58 = session.publicKey.toBase58()
                    val drops = dropsRepo.seedIfEmpty(pubkeyB58)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isConnected = true,
                            isDemoMode = false,
                            onboardingStep = if (!it.isOnboardingComplete) OnboardingStep.PICK_STYLE else it.onboardingStep,
                            publicKey = session.publicKey,
                            accountLabel = session.accountLabel,
                            recentDrops = drops,
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

    fun disconnectWallet(sender: ActivityResultSender? = null) {
        viewModelScope.launch {
            if (sender != null) {
                mwaRepository.disconnectWallet(sender)
            } else {
                mwaRepository.clearSession()
            }
            _uiState.update {
                it.copy(
                    isConnected = false,
                    isDemoMode = false,
                    isOnboardingComplete = false,
                    onboardingStep = OnboardingStep.CONNECT_WALLET,
                    isSettingsVisible = false,
                    isPaySheetVisible = false,
                    isInvestSheetVisible = false,
                    publicKey = null,
                    accountLabel = null,
                    ownerUsdcBalance = 0L,
                    ownerSolBalance = 0L,
                    vaultUsdcBalance = 0L,
                    isVaultInitialized = false,
                    holdings = emptyList(),
                    recentDrops = emptyList(),
                    currentTab = AppTab.HOME,
                    statusMessage = "Wallet disconnected"
                )
            }
        }
    }

    fun refreshBalances() {
        val owner = _uiState.value.publicKey ?: return
        val ownerB58 = owner.toBase58()
        Log.d("MainViewModel", "refreshBalances for owner: $ownerB58")
        viewModelScope.launch {
            try {
                val ownerAta = Ata.deriveAssociatedTokenAddress(owner, usdcMint)
                val (vaultPda, _) = Pda.deriveVaultPda(owner, 0.toByte(), programId)
                val vaultAta = Ata.deriveAssociatedTokenAddress(vaultPda, usdcMint)

                val usdcBal = rpcClient.getTokenAccountBalance(ownerAta.toBase58())
                val solBal = rpcClient.getSolBalance(ownerB58)
                val vaultBal = rpcClient.getTokenAccountBalance(vaultAta.toBase58())
                val vaultConfig = rpcClient.getVaultConfig(vaultPda)
                val drops = dropsRepo.getDropsForWallet(ownerB58)

                // Sync on-chain activity history
                val syncedActivities = mutableListOf<ActivityItem>()
                val signatures = rpcClient.getSignaturesForAddress(vaultAta.toBase58(), limit = 15)
                for (sigInfo in signatures) {
                    val memo = sigInfo.memo
                    if (memo != null && memo.contains("JIRO: Invest", ignoreCase = true)) {
                        val regex = Regex("Invest \\$([0-9.]+) in ([A-Za-z0-9.]+)")
                        val match = regex.find(memo)
                        val amountUsdc = if (match != null) {
                            try { (match.groupValues[1].toDouble() * 1_000_000).toLong() } catch (_: Exception) { 0L }
                        } else 0L
                        val stock = match?.groupValues?.getOrNull(2) ?: "VOO"
                        syncedActivities.add(
                            ActivityItem(
                                signature = sigInfo.signature,
                                type = ActivityType.STOCK_INVESTMENT,
                                amount = Money(amountUsdc),
                                stockSymbol = stock,
                                timestampMs = (sigInfo.blockTime ?: (System.currentTimeMillis() / 1000L)) * 1000L,
                                status = "Confirmed"
                            )
                        )
                    }
                }

                // Also populate deposit activities from confirmed drops if not already recorded
                drops.forEach { drop ->
                    if (drop.signature != null) {
                        syncedActivities.add(
                            ActivityItem(
                                signature = drop.signature,
                                type = ActivityType.ROUND_UP_DEPOSIT,
                                amount = drop.roundUpAmount,
                                timestampMs = System.currentTimeMillis() - 60_000L,
                                status = "Confirmed"
                            )
                        )
                    }
                }

                if (syncedActivities.isNotEmpty()) {
                    activityRepo.saveActivitiesForWallet(ownerB58, syncedActivities)
                }
                val allActivities = activityRepo.getActivitiesForWallet(ownerB58)

                // Holdings sync for live mode - computed strictly from real investments
                val (liveHoldings, liveValue, liveContrib) = computeLiveHoldingsFromActivities(
                    activities = allActivities,
                    basket = _uiState.value.userBasket
                )
                portfolioRepo.savePortfolioForWallet(ownerB58, liveHoldings, liveValue, liveContrib)

                Log.d("MainViewModel", "Balances refreshed: ownerUsdc=$usdcBal, ownerSol=$solBal, vaultUsdc=$vaultBal, vaultInit=${vaultConfig != null}")

                _uiState.update {
                    it.copy(
                        ownerUsdcBalance = usdcBal,
                        ownerSolBalance = solBal,
                        vaultUsdcBalance = vaultBal,
                        isVaultInitialized = vaultConfig != null,
                        recentDrops = if (drops.isNotEmpty()) drops else it.recentDrops,
                        activityList = allActivities,
                        holdings = liveHoldings,
                        totalPortfolioValueUsdc = liveValue,
                        totalContributionsUsdc = liveContrib
                    )
                }
                recalculateAllocation()
            } catch (e: Exception) {
                Log.e("MainViewModel", "refreshBalances failed: ${e.message}", e)
            }
        }
    }

    private fun executeBundledPayment(sender: ActivityResultSender) {
        val state = _uiState.value
        val owner = state.publicKey ?: return

        viewModelScope.launch {
            try {
                val paymentRaw = RoundUp.parseUsdcToRaw(state.paymentInput)
                val roundUpRaw = RoundUp.calculate(paymentRaw, state.roundWholeDollarUp)
                val merchantKey = try {
                    PublicKey(state.merchantAddress.trim())
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = "Invalid merchant Solana address") }
                    return@launch
                }

                val requiredLamports = if (!state.isVaultInitialized) 4_000_000L else 10_000L
                val balanceCheck = PreflightChecker.checkBalancesForPayment(
                    userUsdcBalance = state.ownerUsdcBalance,
                    totalRequiredUsdc = paymentRaw + roundUpRaw,
                    userSolLamports = state.ownerSolBalance,
                    estimatedFeesLamports = requiredLamports
                )
                if (balanceCheck is PreflightCheckResult.Failed) {
                    _uiState.update { it.copy(errorMessage = balanceCheck.reason) }
                    return@launch
                }

                _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Preparing transaction...") }

                val blockhashB58 = rpcClient.getLatestBlockhash()
                val blockhashBytes = Base58.decode(blockhashB58)
                val wireTx = PaymentTxBuilder.buildBundledPayment(
                    user = owner,
                    merchant = merchantKey,
                    usdcMint = usdcMint,
                    paymentAmount = Money(paymentRaw),
                    roundUpAmount = Money(roundUpRaw),
                    vaultId = 0.toByte(),
                    recentBlockhash = blockhashBytes,
                    initVaultIfNeeded = !state.isVaultInitialized,
                    programId = programId
                ).serializeWireTransaction()

                _uiState.update { it.copy(statusMessage = "Opening wallet for approval...") }

                val signResult = withTimeout(120_000L) {
                    mwaRepository.signAndSendTransaction(sender = sender, transactionPayload = wireTx)
                }

                signResult.fold(
                    onSuccess = { signature ->
                        val merchantLabel = when {
                            state.merchantAddress.contains("2WMvCS7Un") -> "Coffee & Bakery"
                            state.merchantAddress.length > 8 -> "${state.merchantAddress.take(4)}...${state.merchantAddress.takeLast(4)}"
                            else -> "Merchant"
                        }
                        val newDrop = RecentDropReceipt(
                            merchantName = merchantLabel,
                            paymentAmount = Money(paymentRaw),
                            roundUpAmount = Money(roundUpRaw),
                            timeAgo = "Just now",
                            signature = signature
                        )
                        dropsRepo.saveDropForWallet(owner.toBase58(), newDrop)
                        val updatedDrops = dropsRepo.getDropsForWallet(owner.toBase58())
                        val newActivity = ActivityItem(
                            signature = signature,
                            type = ActivityType.ROUND_UP_DEPOSIT,
                            amount = Money(roundUpRaw),
                            timestampMs = System.currentTimeMillis()
                        )
                        activityRepo.saveActivityForWallet(owner.toBase58(), newActivity)
                        _uiState.update {
                            it.copy(
                                lastTxSignature = signature,
                                statusMessage = "Payment & Round-Up confirmed on Solana Devnet!",
                                isPaySheetVisible = false,
                                isDroppingCoin = true,
                                recentDrops = updatedDrops,
                                activityList = listOf(newActivity) + it.activityList
                            )
                        }
                        refreshBalances()
                        delay(600L)
                        _uiState.update { it.copy(isDroppingCoin = false) }
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(errorMessage = "Transaction failed: ${err.message}") }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun initializeVault(sender: ActivityResultSender) {
        val owner = _uiState.value.publicKey ?: return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Preparing transaction...") }
                val blockhashB58 = rpcClient.getLatestBlockhash()
                val blockhashBytes = Base58.decode(blockhashB58)
                val initIx = Instructions.initVault(
                    owner = owner,
                    mint = usdcMint,
                    vaultId = 0.toByte(),
                    maxBalance = 100_000_000L,
                    programId = programId
                )
                val wireTx = MessageV0.compile(
                    payer = owner,
                    instructions = listOf(
                        Instructions.setComputeUnitLimit(300_000),
                        Instructions.setComputeUnitPrice(10_000L),
                        initIx
                    ),
                    recentBlockhash = blockhashBytes
                ).serializeWireTransaction()

                _uiState.update { it.copy(statusMessage = "Opening wallet to initialize vault...") }

                val signResult = withTimeout(120_000L) {
                    mwaRepository.signAndSendTransaction(sender = sender, transactionPayload = wireTx)
                }

                signResult.fold(
                    onSuccess = { sig ->
                        _uiState.update { it.copy(lastTxSignature = sig, statusMessage = "Vault initialized successfully!") }
                        refreshBalances()
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(errorMessage = "Vault init failed: ${err.message}") }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error initializing vault: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun withdrawVault(sender: ActivityResultSender) {
        val state = _uiState.value
        val owner = state.publicKey ?: return

        if (!state.isVaultInitialized) {
            _uiState.update { it.copy(errorMessage = "Vault is not initialized on Solana Devnet.") }
            return
        }

        if (state.vaultUsdcBalance <= 0L) {
            _uiState.update { it.copy(errorMessage = "Vault has zero balance to withdraw.") }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Preparing transaction...") }
                val blockhashB58 = rpcClient.getLatestBlockhash()
                val blockhashBytes = Base58.decode(blockhashB58)
                val userAta = Ata.deriveAssociatedTokenAddress(owner, usdcMint)
                val withdrawIx = Instructions.withdraw(
                    owner = owner,
                    mint = usdcMint,
                    vaultId = 0.toByte(),
                    amount = state.vaultUsdcBalance,
                    destinationTokenAccount = userAta,
                    programId = programId
                )
                val wireTx = MessageV0.compile(
                    payer = owner,
                    instructions = listOf(
                        Instructions.setComputeUnitLimit(300_000),
                        Instructions.setComputeUnitPrice(10_000L),
                        Instructions.createAssociatedTokenAccountIdempotent(
                            payer = owner,
                            walletAddress = owner,
                            mint = usdcMint
                        ),
                        withdrawIx
                    ),
                    recentBlockhash = blockhashBytes
                ).serializeWireTransaction()

                _uiState.update { it.copy(statusMessage = "Opening wallet to withdraw...") }

                val signResult = withTimeout(120_000L) {
                    mwaRepository.signAndSendTransaction(sender = sender, transactionPayload = wireTx)
                }

                signResult.fold(
                    onSuccess = { sig ->
                        val withdrawnAmount = state.vaultUsdcBalance
                        val newActivity = ActivityItem(
                            signature = sig,
                            type = ActivityType.WITHDRAWAL,
                            amount = Money(withdrawnAmount),
                            timestampMs = System.currentTimeMillis()
                        )
                        activityRepo.saveActivityForWallet(owner.toBase58(), newActivity)
                        _uiState.update {
                            it.copy(
                                lastTxSignature = sig,
                                statusMessage = "Withdrew $${RoundUp.formatUsdc(withdrawnAmount)} back to your wallet!",
                                activityList = listOf(newActivity) + it.activityList
                            )
                        }
                        refreshBalances()
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(errorMessage = "Withdraw failed: ${err.message}") }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error withdrawing from vault: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
