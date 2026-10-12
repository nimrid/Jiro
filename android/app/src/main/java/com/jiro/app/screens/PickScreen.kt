package com.jiro.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.R
import com.jiro.app.StockDetailInfoProvider
import com.jiro.app.StockFinancialData
import com.jiro.app.components.*
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography
import com.jiro.core.BasketHolding
import com.jiro.core.CatalogEntry

enum class StockFilter {
    ALL,
    BUYABLE,
    INDEX,
    TECH
}

@Composable
fun PickScreen(
    catalog: List<CatalogEntry>,
    isLoading: Boolean,
    loadError: String?,
    snapshotAgeMinutes: Long,
    isOffline: Boolean,
    currentBasket: List<BasketHolding>,
    onBasketChanged: (List<BasketHolding>) -> Unit,
    onRetryCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(StockFilter.ALL) }
    var selectedStock by remember { mutableStateOf<CatalogEntry?>(null) }
    val spec = rememberAdaptiveSpec()

    val filteredEntries = remember(catalog, searchQuery, selectedFilter) {
        catalog.filter { entry ->
            val matchesQuery = searchQuery.isBlank() ||
                entry.symbol.contains(searchQuery, ignoreCase = true) ||
                entry.name.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                StockFilter.ALL -> true
                StockFilter.BUYABLE -> entry.buyable.isBuyable
                StockFilter.INDEX -> entry.symbol.contains("VOO") || entry.symbol.contains("SPY") || entry.symbol.contains("QQQ")
                StockFilter.TECH -> entry.symbol.contains("AAPL") || entry.symbol.contains("MSFT") || entry.symbol.contains("NVDA") || entry.symbol.contains("GOOGL") || entry.symbol.contains("TSLA")
            }

            matchesQuery && matchesFilter
        }
    }

    AdaptiveContainer(
        maxWidth = 600.dp,
        modifier = modifier
            .fillMaxSize()
            .background(JiroColors.Ink)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spec.horizontalPadding, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.pick_title),
                    style = JiroTypography.HeadlineMedium,
                    color = JiroColors.Text
                )

                if (isOffline) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = JiroColors.SurfaceElevated),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.catalog_offline_banner, "${snapshotAgeMinutes}m"),
                            color = JiroColors.Sky,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Search verified stocks input" },
                    placeholder = {
                        Text(text = stringResource(R.string.pick_search_hint), color = JiroColors.Muted, fontSize = 14.sp)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = JiroColors.Text,
                        unfocusedTextColor = JiroColors.Text,
                        focusedBorderColor = JiroColors.Amber,
                        unfocusedBorderColor = JiroColors.Line,
                        focusedContainerColor = JiroColors.Surface,
                        unfocusedContainerColor = JiroColors.Surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter chips (scrollable row so narrow screens never wrap/clip)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                StockFilter.values().forEach { filter ->
                    val isSelected = filter == selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = when (filter) {
                                    StockFilter.ALL -> stringResource(R.string.filter_all)
                                    StockFilter.BUYABLE -> stringResource(R.string.filter_buyable)
                                    StockFilter.INDEX -> stringResource(R.string.filter_index)
                                    StockFilter.TECH -> stringResource(R.string.filter_tech)
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JiroColors.Amber.copy(alpha = 0.2f),
                            selectedLabelColor = JiroColors.Amber,
                            containerColor = JiroColors.Surface,
                            labelColor = JiroColors.Muted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = JiroColors.Line,
                            selectedBorderColor = JiroColors.Amber
                        )
                    )
                }
            }
        }

        HorizontalDivider(color = JiroColors.Line, thickness = 1.dp)

        // Content
        when {
            isLoading -> {
                CatalogLoadingSkeleton(count = 7, modifier = Modifier.fillMaxSize())
            }

            loadError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JiroColors.Coral.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = stringResource(R.string.catalog_failed), color = JiroColors.Coral, fontWeight = FontWeight.Bold)
                            Text(text = loadError, color = JiroColors.Muted, fontSize = 12.sp)
                            Button(
                                onClick = onRetryCatalog,
                                colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Amber)
                            ) {
                                Text(text = stringResource(R.string.catalog_retry), color = JiroColors.Ink, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            filteredEntries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No matching stocks found", color = JiroColors.Muted, fontSize = 14.sp)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredEntries, key = { it.symbol }) { entry ->
                        CatalogStockRow(
                            entry = entry,
                            onClick = { selectedStock = entry }
                        )
                        HorizontalDivider(color = JiroColors.Line, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

    // Stock Detail & Basket Add Sheet
    selectedStock?.let { stock ->
        StockDetailSheet(
            stock = stock,
            currentBasket = currentBasket,
            onBasketChanged = onBasketChanged,
            onDismiss = { selectedStock = null }
        )
    }
}

@Composable
fun CatalogStockRow(
    entry: CatalogEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info = remember(entry.ticker, StockDetailInfoProvider.version) {
        StockDetailInfoProvider.getInfo(entry.ticker, entry.name)
    }

    val isPositive = info.change24hPercent >= 0
    val changeColor = if (isPositive) JiroColors.Mint else JiroColors.Coral
    val changeSign = if (isPositive) "+" else ""

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Official stock logo with fallback
        StockLogo(ticker = entry.ticker, size = 42.dp)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1.3f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = entry.ticker,
                    color = JiroColors.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!entry.buyable.isBuyable) {
                    StatusChip(status = StockStatus.PAUSED)
                }
            }
            Text(
                text = info.name,
                color = JiroColors.Muted,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        // Mini sparkline trend chart
        SparklineChart(
            dataPoints = info.sparklinePoints,
            isPositive = isPositive,
            modifier = Modifier
                .width(54.dp)
                .height(26.dp)
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(0.9f)
        ) {
            Text(
                text = String.format("$%.2f", info.priceUsd),
                color = JiroColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JiroTypography.BodyFamily
            )
            Text(
                text = String.format("%s%.2f%%", changeSign, info.change24hPercent),
                color = changeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun StockDetailSheet(
    stock: CatalogEntry,
    currentBasket: List<BasketHolding>,
    onBasketChanged: (List<BasketHolding>) -> Unit,
    onDismiss: () -> Unit
) {
    val info = remember(stock.ticker, StockDetailInfoProvider.version) {
        StockDetailInfoProvider.getInfo(stock.ticker, stock.name)
    }

    LaunchedEffect(stock.ticker) {
        StockDetailInfoProvider.fetchDescription(stock.ticker)
    }

    val isPositive = info.change24hPercent >= 0
    val changeColor = if (isPositive) JiroColors.Mint else JiroColors.Coral
    val changeSign = if (isPositive) "+" else ""

    val existingHolding = currentBasket.find { it.mint == stock.mint }
    var currentWeightBps by remember {
        mutableStateOf(existingHolding?.targetWeightBps ?: 2000) // 20% default
    }

    var showLegalDetails by remember { mutableStateOf(false) }

    val basketWeightItems = currentBasket.map {
        BasketWeightItem(
            ticker = it.symbol.removeSuffix(".US"),
            weightBps = if (it.mint == stock.mint) currentWeightBps else it.targetWeightBps
        )
    }.toMutableList()

    if (existingHolding == null) {
        basketWeightItems.add(
            BasketWeightItem(
                ticker = stock.ticker,
                weightBps = currentWeightBps
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JiroColors.SurfaceElevated,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Logo, Ticker, Name, Sector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    StockLogo(ticker = stock.ticker, size = 52.dp)

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stock.ticker,
                                color = JiroColors.Text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            MarketBadge(isOpen = stock.marketStatus == "OPEN")
                        }
                        Text(
                            text = info.name,
                            color = JiroColors.Muted,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        Text(
                            text = info.sector,
                            color = JiroColors.Sky,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Price and 24h Trend Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = String.format("$%.2f", info.priceUsd),
                                    style = JiroTypography.HeadlineMedium,
                                    fontSize = 22.sp,
                                    color = JiroColors.Text
                                )
                                Text(
                                    text = String.format("%s%.2f%% 24h", changeSign, info.change24hPercent),
                                    color = changeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = info.marketCap,
                                    color = JiroColors.Muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (info.high24h > 0.0 && info.low24h > 0.0) {
                                    Text(
                                        text = String.format("Range: $%.2f - $%.2f", info.low24h, info.high24h),
                                        color = JiroColors.Muted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Large sparkline chart
                        SparklineChart(
                            dataPoints = info.sparklinePoints,
                            isPositive = isPositive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        )
                    }
                }

                // About the Company
                Column {
                    Text(
                        text = "About ${info.ticker}",
                        color = JiroColors.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = info.description,
                        color = JiroColors.Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                // Basket Weight Allocator
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Target Basket Weight",
                                color = JiroColors.Text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { if (currentWeightBps > 1000) currentWeightBps -= 500 },
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(text = "-", fontSize = 18.sp, color = JiroColors.Text)
                                }
                                Text(
                                    text = "${currentWeightBps / 100}%",
                                    color = JiroColors.Amber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                OutlinedButton(
                                    onClick = { if (currentWeightBps < 5000) currentWeightBps += 500 },
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(text = "+", fontSize = 18.sp, color = JiroColors.Text)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Live stacked weight preview
                        StackedWeightBar(items = basketWeightItems)
                    }
                }

                // Collapsible On-Chain Security Details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLegalDetails = !showLegalDetails }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "On-Chain Security Details",
                        color = JiroColors.Sky,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (showLegalDetails) "▲" else "▼",
                        color = JiroColors.Sky,
                        fontSize = 10.sp
                    )
                }

                AnimatedVisibility(
                    visible = showLegalDetails,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "Token Mint", color = JiroColors.Muted, fontSize = 11.sp)
                        Text(
                            text = stock.mint.toBase58(),
                            color = JiroColors.Sky,
                            fontSize = 10.sp,
                            fontFamily = JiroTypography.MonoFamily
                        )
                        Text(
                            text = "Token Program: Solana Token-2022 (SPL)",
                            color = JiroColors.Muted,
                            fontSize = 11.sp
                        )
                        stock.policy.disclosures.forEach { disclosure ->
                            Text(text = "• $disclosure", color = JiroColors.Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = currentBasket.toMutableList()
                    val idx = updated.indexOfFirst { it.mint == stock.mint }
                    if (idx >= 0) {
                        updated[idx] = updated[idx].copy(targetWeightBps = currentWeightBps)
                    } else {
                        updated.add(
                            BasketHolding(
                                symbol = stock.symbol,
                                mint = stock.mint,
                                targetWeightBps = currentWeightBps,
                                isBuyable = stock.buyable.isBuyable
                            )
                        )
                    }
                    onBasketChanged(updated)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Amber),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (existingHolding != null) "Update Basket Weight (${currentWeightBps / 100}%)" else "Add ${stock.ticker} to Basket",
                    color = JiroColors.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Cancel", color = JiroColors.Muted)
            }
        }
    )
}
