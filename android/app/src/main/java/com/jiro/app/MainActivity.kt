package com.jiro.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.components.JiroBottomBar
import com.jiro.app.screens.*
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTheme
import com.jiro.app.theme.JiroTypography
import com.jiro.core.Money
import com.jiro.core.SolanaPay
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val activityResultSender = ActivityResultSender(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSolanaPayDeepLink(intent)

        setContent {
            JiroTheme {
                val state by viewModel.uiState.collectAsState()

                JiroMainApp(
                    state = state,
                    onTabSelected = { viewModel.selectTab(it) },
                    onPayClicked = { viewModel.showPaySheet(true) },
                    onInvestClicked = { viewModel.showInvestSheet(true) },
                    onSettingsClicked = { viewModel.showSettings(true) },
                    onConnectWallet = { viewModel.connectWallet(activityResultSender) },
                    onStepChange = { viewModel.setOnboardingStep(it) },
                    onTryDemo = { viewModel.startDemoMode() },
                    onCompleteOnboarding = { style, step, boost, threshold ->
                        viewModel.completeOnboarding(style, step, boost, threshold)
                    },
                    onPaymentInputChanged = { viewModel.updatePaymentInput(it) },
                    onMerchantAddressChanged = { viewModel.updateMerchantAddress(it) },
                    onRoundWholeDollarChanged = { viewModel.setRoundWholeDollarUp(it) },
                    onConfirmPayment = { viewModel.executePayment(activityResultSender) },
                    onConfirmInvest = { viewModel.executeInvest(activityResultSender) },
                    onWithdrawAll = { viewModel.withdrawAll(activityResultSender) },
                    onCloseJar = { viewModel.closeJar(activityResultSender) },
                    onDisconnectWallet = { viewModel.disconnectWallet(activityResultSender) },
                    onDemoModeToggle = { viewModel.toggleDemoMode(it) },
                    onRulesChanged = { step, boost, cap -> viewModel.setRules(step, boost, cap) },
                    onBasketChanged = { viewModel.updateBasket(it) },
                    onRetryCatalog = { viewModel.retryCatalog() },
                    onDismissPay = { viewModel.showPaySheet(false) },
                    onDismissInvest = { viewModel.showInvestSheet(false) },
                    onDismissSettings = { viewModel.showSettings(false) },
                    onClearMessages = { viewModel.clearMessages() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSolanaPayDeepLink(intent)
    }

    private fun handleSolanaPayDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val uriStr = data.toString()
        if (uriStr.startsWith("solana:", ignoreCase = true)) {
            try {
                val request = SolanaPay.parse(uriStr)
                viewModel.updateMerchantAddress(request.recipient.toBase58())
                request.amount?.let { parsedAmount ->
                    val formatted = String.format("%.2f", parsedAmount.baseUnits / 1_000_000.0)
                    viewModel.updatePaymentInput(formatted)
                    // Run preflight so the user sees an error if they can't afford it,
                    // rather than letting them discover it at signing time.
                    viewModel.validateDeepLinkedAmount(parsedAmount.baseUnits)
                }
                viewModel.showPaySheet(true)
            } catch (e: Exception) {
                // Ignore invalid deep links silently — don't crash on malformed URIs
            }
        }
    }
}

@Composable
fun JiroMainApp(
    state: AppUiState,
    onTabSelected: (AppTab) -> Unit,
    onPayClicked: () -> Unit,
    onInvestClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onConnectWallet: () -> Unit,
    onStepChange: (OnboardingStep) -> Unit,
    onTryDemo: () -> Unit,
    onCompleteOnboarding: (InvestmentStyle, Double, Int, Double) -> Unit,
    onPaymentInputChanged: (String) -> Unit,
    onMerchantAddressChanged: (String) -> Unit,
    onRoundWholeDollarChanged: (Boolean) -> Unit,
    onConfirmPayment: () -> Unit,
    onConfirmInvest: () -> Unit,
    onWithdrawAll: () -> Unit,
    onCloseJar: () -> Unit,
    onDisconnectWallet: () -> Unit,
    onDemoModeToggle: (Boolean) -> Unit,
    onRulesChanged: (Double, Int, Double) -> Unit,
    onBasketChanged: (List<com.jiro.core.BasketHolding>) -> Unit,
    onRetryCatalog: () -> Unit,
    onDismissPay: () -> Unit,
    onDismissInvest: () -> Unit,
    onDismissSettings: () -> Unit,
    onClearMessages: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JiroColors.Ink)
    ) {
        if (!state.isOnboardingComplete && !state.isDemoMode) {
            OnboardingScreen(
                currentStep = state.onboardingStep,
                onStepChange = onStepChange,
                onConnectWallet = onConnectWallet,
                onTryDemo = onTryDemo,
                onCreateJar = { style, step, boost, threshold ->
                    onCompleteOnboarding(style, step, boost, threshold)
                }
            )
        } else {
            Scaffold(
                containerColor = JiroColors.Ink,
                bottomBar = {
                    JiroBottomBar(
                        currentTab = state.currentTab,
                        onTabSelected = onTabSelected,
                        onPayClick = onPayClicked
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (state.currentTab) {
                        AppTab.HOME -> {
                            HomeScreen(
                                jarBalance = state.vaultUsdcBalance,
                                threshold = state.vaultThreshold,
                                nextStockTicker = state.nextStockTicker,
                                skippedStockMessage = state.skippedStockMessage,
                                recentDrops = state.recentDrops,
                                isDropping = state.isDroppingCoin,
                                isDemoMode = state.isDemoMode,
                                onPayClick = onPayClicked,
                                onInvestClick = onInvestClicked,
                                onWithdrawClick = onWithdrawAll,
                                onSettingsClick = onSettingsClicked
                            )
                        }

                        AppTab.PORTFOLIO -> {
                            PortfolioScreen(
                                holdings = state.holdings,
                                totalValueUsdc = state.totalPortfolioValueUsdc,
                                totalContributionsUsdc = state.totalContributionsUsdc
                            )
                        }

                        AppTab.PICK -> {
                            PickScreen(
                                catalog = state.catalog,
                                isLoading = state.isCatalogLoading,
                                loadError = state.catalogError,
                                snapshotAgeMinutes = state.snapshotAgeMinutes,
                                isOffline = state.isDemoMode,
                                currentBasket = state.userBasket,
                                onBasketChanged = onBasketChanged,
                                onRetryCatalog = onRetryCatalog
                            )
                        }

                        AppTab.ACTIVITY -> {
                            ActivityScreen(
                                activities = state.activityList,
                                owedRoundUpUsdc = state.owedRoundUpUsdc
                            )
                        }
                    }
                }
            }
        }

        // Modal Overlays
        if (state.isPaySheetVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(JiroColors.Ink)
            ) {
                PayScreen(
                    paymentInput = state.paymentInput,
                    onPaymentInputChange = onPaymentInputChanged,
                    merchantAddress = state.merchantAddress,
                    onMerchantAddressChange = onMerchantAddressChanged,
                    roundWholeDollarUp = state.roundWholeDollarUp,
                    onRoundWholeDollarUpChange = onRoundWholeDollarChanged,
                    userUsdcBalance = state.ownerUsdcBalance,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    onConfirmPay = onConfirmPayment,
                    onScanQrClick = {},
                    onBackClick = onDismissPay
                )
            }
        }

        if (state.isInvestSheetVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                InvestSheet(
                    ticker = state.nextStockTicker,
                    stockName = state.nextStockName,
                    investAmount = Money(state.vaultUsdcBalance),
                    estimatedUnits = (state.vaultUsdcBalance.toDouble() / 1_000_000.0) / 450.0,
                    priceImpactBps = 12,
                    quoteSecondsRemaining = state.quoteSecondsRemaining,
                    isMarketOpen = true,
                    selectionReason = state.selectionReason,
                    isLoading = state.isLoading,
                    onConfirmInvest = onConfirmInvest,
                    onDismiss = onDismissInvest
                )
            }
        }

        if (state.isSettingsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(JiroColors.Ink)
            ) {
                SettingsScreen(
                    roundUpStepUsd = state.roundUpStepUsd,
                    onRoundUpStepChange = { s -> onRulesChanged(s, state.multiplierBoost, state.thresholdUsd) },
                    multiplierBoost = state.multiplierBoost,
                    onMultiplierBoostChange = { b -> onRulesChanged(state.roundUpStepUsd, b, state.thresholdUsd) },
                    thresholdUsd = state.thresholdUsd,
                    onThresholdChange = { t -> onRulesChanged(state.roundUpStepUsd, state.multiplierBoost, t) },
                    isDemoMode = state.isDemoMode,
                    onDemoModeToggle = onDemoModeToggle,
                    onWithdrawAll = onWithdrawAll,
                    onCloseJar = onCloseJar,
                    onDisconnectWallet = onDisconnectWallet,
                    onBackClick = onDismissSettings
                )
            }
        }

        // Global status or error snackbar banner
        if (state.statusMessage != null || state.errorMessage != null) {
            val isErr = state.errorMessage != null
            val msg = state.errorMessage ?: state.statusMessage ?: ""
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 560.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isErr) JiroColors.Coral else JiroColors.Mint, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = JiroColors.SurfaceElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            color = if (isErr) JiroColors.Coral else JiroColors.Mint,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearMessages) {
                            Text(text = "✕", color = JiroColors.Muted, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}
