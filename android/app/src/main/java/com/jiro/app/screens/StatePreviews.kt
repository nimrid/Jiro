package com.jiro.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jiro.app.AssetCatalogRepository
import com.jiro.app.components.*
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTheme
import com.jiro.core.*

/**
 * Visual Previews for all states specified in DESIGN.md:
 * 1. Loading skeletons
 * 2. Catalog failed with retry & snapshot age
 * 3. Jar empty
 * 4. Jar full (ready to pour)
 * 5. Insufficient funds
 * 6. Declined / cancelled
 * 7. Quote expired
 * 8. Thin liquidity (buy blocked / warned, stock stays visible)
 * 9. Market closed
 * 10. Offline snapshot banner
 * 11. Transaction pending / confirmed / failed
 * 12. Owed vs in-jar
 */

@Preview(name = "1. Loading Skeleton Rows", showBackground = true)
@Composable
fun PreviewCatalogLoadingSkeleton() {
    JiroTheme {
        Box(modifier = Modifier.background(JiroColors.Ink).padding(16.dp)) {
            CatalogLoadingSkeleton(count = 5)
        }
    }
}

@Preview(name = "2. Catalog Failed with Retry", showBackground = true)
@Composable
fun PreviewCatalogFailed() {
    JiroTheme {
        PickScreen(
            catalog = emptyList(),
            isLoading = false,
            loadError = "Network timeout fetching verified assets (snapshot is 24h old)",
            snapshotAgeMinutes = 1440L,
            isOffline = true,
            currentBasket = emptyList(),
            onBasketChanged = {},
            onRetryCatalog = {}
        )
    }
}

@Preview(name = "3. Jar Empty", showBackground = true)
@Composable
fun PreviewJarEmpty() {
    JiroTheme {
        HomeScreen(
            jarBalance = 0L,
            threshold = 5_000_000L,
            nextStockTicker = "VOO",
            recentDrops = emptyList(),
            onPayClick = {},
            onInvestClick = {},
            onWithdrawClick = {},
            onSettingsClick = {}
        )
    }
}

@Preview(name = "4. Jar Full (Ready to Pour)", showBackground = true)
@Composable
fun PreviewJarFull() {
    JiroTheme {
        HomeScreen(
            jarBalance = 5_200_000L, // $5.20
            threshold = 5_000_000L,
            nextStockTicker = "VOO",
            recentDrops = listOf(
                RecentDropReceipt("Blue Bottle", Money(4_350_000L), Money(650_000L), "10m ago"),
                RecentDropReceipt("Trader Joe's", Money(18_200_000L), Money(800_000L), "1h ago")
            ),
            onPayClick = {},
            onInvestClick = {},
            onWithdrawClick = {},
            onSettingsClick = {}
        )
    }
}

@Preview(name = "5. Insufficient Funds", showBackground = true)
@Composable
fun PreviewInsufficientFunds() {
    JiroTheme {
        PayScreen(
            paymentInput = "45.00",
            onPaymentInputChange = {},
            merchantAddress = "2WMvCS7UnQPk75qjqudfyoxnZx24Rcxsq58RZd2Zau5o",
            onMerchantAddressChange = {},
            roundWholeDollarUp = false,
            onRoundWholeDollarUpChange = {},
            userUsdcBalance = 10_000_000L, // only $10
            isLoading = false,
            errorMessage = "Insufficient USDC balance ($10.00 available)",
            onConfirmPay = {},
            onScanQrClick = {},
            onBackClick = {}
        )
    }
}

@Preview(name = "6. Wallet Declined / Cancelled", showBackground = true)
@Composable
fun PreviewWalletDeclined() {
    JiroTheme {
        PayScreen(
            paymentInput = "4.35",
            onPaymentInputChange = {},
            merchantAddress = "2WMvCS7UnQPk75qjqudfyoxnZx24Rcxsq58RZd2Zau5o",
            onMerchantAddressChange = {},
            roundWholeDollarUp = false,
            onRoundWholeDollarUpChange = {},
            userUsdcBalance = 50_000_000L,
            isLoading = false,
            errorMessage = "Transaction request was cancelled by user in wallet.",
            onConfirmPay = {},
            onScanQrClick = {},
            onBackClick = {}
        )
    }
}

@Preview(name = "7. Quote Expired", showBackground = true)
@Composable
fun PreviewQuoteExpired() {
    JiroTheme {
        InvestSheet(
            ticker = "VOO",
            stockName = "Vanguard S&P 500 ETF",
            investAmount = Money(5_200_000L),
            estimatedUnits = 0.0115,
            priceImpactBps = 15,
            quoteSecondsRemaining = 0, // EXPIRED
            isMarketOpen = true,
            selectionReason = null,
            isLoading = false,
            onConfirmInvest = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "8. Thin Liquidity (Warn/Block Buy)", showBackground = true)
@Composable
fun PreviewThinLiquidity() {
    JiroTheme {
        InvestSheet(
            ticker = "XYZ",
            stockName = "Thin Liquidity Stock",
            investAmount = Money(5_000_000L),
            estimatedUnits = 0.05,
            priceImpactBps = 180, // 1.80% > 1.00% cap
            quoteSecondsRemaining = 24,
            isMarketOpen = true,
            selectionReason = null,
            isLoading = false,
            onConfirmInvest = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "9. Market Closed", showBackground = true)
@Composable
fun PreviewMarketClosed() {
    JiroTheme {
        InvestSheet(
            ticker = "AAPL",
            stockName = "Apple Inc. Tokenized",
            investAmount = Money(5_000_000L),
            estimatedUnits = 0.021,
            priceImpactBps = 12,
            quoteSecondsRemaining = 28,
            isMarketOpen = false, // CLOSED
            selectionReason = null,
            isLoading = false,
            onConfirmInvest = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "10. Offline Snapshot Banner", showBackground = true)
@Composable
fun PreviewOfflineSnapshot() {
    JiroTheme {
        PickScreen(
            catalog = AssetCatalogRepository.getFallbackCatalog(),
            isLoading = false,
            loadError = null,
            snapshotAgeMinutes = 45L,
            isOffline = true,
            currentBasket = emptyList(),
            onBasketChanged = {},
            onRetryCatalog = {}
        )
    }
}

@Preview(name = "11. Transaction Status Pending / Confirmed / Failed", showBackground = true)
@Composable
fun PreviewActivityStates() {
    JiroTheme {
        ActivityScreen(
            activities = listOf(
                ActivityItem("4fK9aM1x9...", ActivityType.ROUND_UP_DEPOSIT, Money(650_000L), status = "Confirmed"),
                ActivityItem("2pL8vN2y3...", ActivityType.STOCK_INVESTMENT, Money(5_000_000L), stockSymbol = "VOO", status = "Pending"),
                ActivityItem("8kM7wQ1x4...", ActivityType.STOCK_INVESTMENT, Money(5_000_000L), stockSymbol = "TSLA", status = "Failed")
            ),
            owedRoundUpUsdc = 0L
        )
    }
}

@Preview(name = "12. Owed vs In-Jar", showBackground = true)
@Composable
fun PreviewOwedVsInJar() {
    JiroTheme {
        ActivityScreen(
            activities = listOf(
                ActivityItem("4fK9aM1x9...", ActivityType.ROUND_UP_DEPOSIT, Money(650_000L), status = "Confirmed")
            ),
            owedRoundUpUsdc = 1_500_000L // $1.50 owed
        )
    }
}
