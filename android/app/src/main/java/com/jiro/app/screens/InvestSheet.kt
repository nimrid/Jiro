package com.jiro.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.jiro.app.components.MarketBadge
import com.jiro.app.components.Monogram
import com.jiro.app.components.QuoteCountdown
import com.jiro.app.components.StockLogo
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography
import com.jiro.core.Money
import com.jiro.core.SelectionReason

@Composable
fun InvestSheet(
    ticker: String,
    stockName: String,
    investAmount: Money,
    estimatedUnits: Double,
    priceImpactBps: Int,
    quoteSecondsRemaining: Int,
    isMarketOpen: Boolean,
    selectionReason: SelectionReason?,
    isLoading: Boolean,
    onConfirmInvest: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isReasonExpanded by remember { mutableStateOf(false) }
    val isQuoteExpired = quoteSecondsRemaining <= 0
    val isHighImpact = priceImpactBps > 100

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(1.dp, JiroColors.Line, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        colors = CardDefaults.cardColors(containerColor = JiroColors.SurfaceElevated),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(JiroColors.Line)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sheet title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.invest_title),
                    style = JiroTypography.TitleMedium,
                    color = JiroColors.Text
                )
                QuoteCountdown(secondsRemaining = quoteSecondsRemaining)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stock Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StockLogo(ticker = ticker, size = 56.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = ticker,
                            style = JiroTypography.HeadlineMedium,
                            fontSize = 22.sp,
                            color = JiroColors.Text
                        )
                        MarketBadge(isOpen = isMarketOpen)
                    }
                    Text(
                        text = stockName,
                        style = JiroTypography.BodyMedium,
                        color = JiroColors.Muted,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Order details summary box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JiroColors.Line, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Amount to pour", color = JiroColors.Muted, fontSize = 14.sp)
                        Text(
                            text = investAmount.formatUsdc(),
                            color = JiroColors.Text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Estimated tokens", color = JiroColors.Muted, fontSize = 14.sp)
                        Text(
                            text = String.format("%.4f %s", estimatedUnits, ticker),
                            color = JiroColors.Mint,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.invest_price_impact), color = JiroColors.Muted, fontSize = 14.sp)
                        val impactPercent = priceImpactBps / 100.0
                        Text(
                            text = String.format("%.2f%%", impactPercent),
                            color = if (isHighImpact) JiroColors.Coral else JiroColors.Text,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Market closed warning
            if (!isMarketOpen) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JiroColors.Amber.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = JiroColors.Amber.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.invest_market_closed_warn),
                        color = JiroColors.Amber,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Quote expired warning
            if (isQuoteExpired) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JiroColors.Coral.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = JiroColors.Coral.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Quote expired. Confirming will refresh quote with latest on-chain price.",
                        color = JiroColors.Coral,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "Why this stock?" expander
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp))
                    .clickable { isReasonExpanded = !isReasonExpanded }
                    .semantics { contentDescription = "Why this stock allocation explanation" },
                colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.why_this_stock),
                            color = JiroColors.Sky,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isReasonExpanded) "▲" else "▼",
                            color = JiroColors.Sky,
                            fontSize = 12.sp
                        )
                    }

                    AnimatedVisibility(
                        visible = isReasonExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectionReason != null) {
                                Text(
                                    text = selectionReason.explanation,
                                    color = JiroColors.Text,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Target deficits:",
                                    color = JiroColors.Muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                selectionReason.deficits.forEach { (sym, deficit) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = sym, color = JiroColors.Muted, fontSize = 12.sp)
                                        Text(
                                            text = deficit.formatUsdc(),
                                            color = if (sym == ticker) JiroColors.Mint else JiroColors.Text,
                                            fontWeight = if (sym == ticker) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                if (selectionReason.skippedAssets.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Skipped assets:",
                                        color = JiroColors.Coral,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    selectionReason.skippedAssets.forEach { (sym, rsn) ->
                                        Text(
                                            text = "• $sym: $rsn",
                                            color = JiroColors.Coral,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "$ticker was selected to balance your portfolio towards its target weight.",
                                    color = JiroColors.Text,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Notice
            Text(
                text = stringResource(R.string.tokenized_stock_notice),
                style = JiroTypography.LabelSmall,
                color = JiroColors.Muted,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Confirm Button
            Button(
                onClick = onConfirmInvest,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Mint),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = JiroColors.Ink,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.invest_confirm_button),
                        color = JiroColors.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(text = "Cancel", color = JiroColors.Muted, fontSize = 14.sp)
            }
        }
    }
}
