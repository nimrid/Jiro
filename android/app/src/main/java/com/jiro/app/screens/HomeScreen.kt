package com.jiro.app.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.R
import com.jiro.app.components.AdaptiveContainer
import com.jiro.app.components.JarView
import com.jiro.app.components.Monogram
import com.jiro.app.components.rememberAdaptiveSpec
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography
import com.jiro.core.Money

data class RecentDropReceipt(
    val merchantName: String,
    val paymentAmount: Money,
    val roundUpAmount: Money,
    val timeAgo: String,
    val signature: String? = null
)

@Composable
fun HomeScreen(
    jarBalance: Long,
    threshold: Long,
    nextStockTicker: String,
    skippedStockMessage: String? = null,
    recentDrops: List<RecentDropReceipt>,
    isDropping: Boolean = false,
    isDemoMode: Boolean = false,
    onPayClick: () -> Unit,
    onInvestClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isReadyToInvest = jarBalance >= threshold
    val currentFormatted = Money(jarBalance).formatUsdc()
    val thresholdFormatted = Money(threshold).formatUsdc()
    val spec = rememberAdaptiveSpec()

    AdaptiveContainer(
        modifier = modifier
            .fillMaxSize()
            .background(JiroColors.Ink)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spec.horizontalPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(spec.spacerSmall))

            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = JiroTypography.HeadlineMedium,
                        color = JiroColors.Amber
                    )
                    if (isDemoMode) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(JiroColors.Sky.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DEMO",
                                color = JiroColors.Sky,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Settings avatar button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(JiroColors.SurfaceElevated)
                        .border(1.dp, JiroColors.Line, CircleShape)
                        .clickable(onClick = onSettingsClick)
                        .semantics { contentDescription = "Open Settings and Safety" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚙",
                        fontSize = 18.sp,
                        color = JiroColors.Text
                    )
                }
            }

            Spacer(modifier = Modifier.height(spec.spacerMedium))

            // Centerpiece: JarView with dynamic device height adaptation
            JarView(
                currentBaseUnits = jarBalance,
                thresholdBaseUnits = threshold,
                isDropping = isDropping,
                modifier = Modifier.size(width = spec.jarWidth, height = spec.jarHeight)
            )

            Spacer(modifier = Modifier.height(spec.spacerMedium))

            // Subtitle status line: "$3.50 of $5.00 -> next: VOO"
            Text(
                text = if (isReadyToInvest) {
                    "$currentFormatted ready to pour -> $nextStockTicker"
                } else {
                    "$currentFormatted of $thresholdFormatted -> next: $nextStockTicker"
                },
                style = JiroTypography.HeadlineMedium,
                fontSize = spec.statusTextSize,
                color = if (isReadyToInvest) JiroColors.Mint else JiroColors.Text,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(spec.spacerSmall))

            // Persistent "Withdraw anytime" chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(JiroColors.SurfaceElevated)
                    .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp))
                    .clickable(onClick = onWithdrawClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { contentDescription = "Withdraw anytime button" }
            ) {
                Text(
                    text = stringResource(R.string.withdraw_anytime),
                    color = JiroColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Skipped banner (e.g. "Skipped TSLA: paused")
            if (!skippedStockMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(spec.spacerSmall))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JiroColors.Coral.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = JiroColors.Coral.copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "⚠", color = JiroColors.Coral, fontSize = 14.sp)
                        Text(
                            text = skippedStockMessage,
                            color = JiroColors.Coral,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spec.spacerMedium))

            // Primary morphing action buttons
            if (isReadyToInvest) {
                Button(
                    onClick = onInvestClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Mint),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Invest $currentFormatted",
                        color = JiroColors.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPayClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JiroColors.Line),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pay_button),
                        color = JiroColors.Text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            } else {
                Button(
                    onClick = onPayClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Amber),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pay_button),
                        color = JiroColors.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(spec.spacerLarge))

        // Last 3 drops as tiny receipts
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.home_last_drops),
                style = JiroTypography.TitleMedium,
                color = JiroColors.Text,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (recentDrops.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.home_empty_jar_desc),
                            style = JiroTypography.BodyMedium,
                            color = JiroColors.Muted
                        )
                    }
                }
            } else {
                recentDrops.take(4).forEach { receipt ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = JiroColors.SurfaceElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(JiroColors.Amber.copy(alpha = 0.15f))
                                        .border(1.dp, JiroColors.Amber.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🪙",
                                        fontSize = 16.sp
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = receipt.merchantName,
                                            color = JiroColors.Text,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (receipt.signature != null) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(JiroColors.Mint.copy(alpha = 0.15f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "Devnet",
                                                    color = JiroColors.Mint,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Paid ${receipt.paymentAmount.formatUsdc()} • ${receipt.timeAgo}",
                                        color = JiroColors.Muted,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "+${receipt.roundUpAmount.formatUsdc()}",
                                    color = JiroColors.Amber,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "to jar",
                                    color = JiroColors.Muted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spec.spacerLarge))
    }
}
}
