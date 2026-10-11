package com.jiro.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography
import com.jiro.core.BasketValidator

data class BasketWeightItem(
    val ticker: String,
    val weightBps: Int, // e.g. 2500 = 25%
    val color: Color = JiroColors.getMonogramColor(ticker)
)

/**
 * Stacked weight bar for the basket editor with live validation.
 * Constraints:
 * - Sum must equal 100% (10,000 bps)
 * - Max 5 holdings
 * - Min 10% (1,000 bps) per holding
 * - 5% (500 bps) step
 */
@Composable
fun StackedWeightBar(
    items: List<BasketWeightItem>,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false
) {
    val totalBps = items.sumOf { it.weightBps }
    val isValidSum = totalBps == BasketValidator.TARGET_SUM_BPS
    val hasValidCount = items.size in 1..BasketValidator.MAX_HOLDINGS
    val allMeetMin = items.all { it.weightBps >= BasketValidator.MIN_WEIGHT_BPS }

    val statusColor by animateColorAsState(
        targetValue = if (isValidSum && hasValidCount && allMeetMin) JiroColors.Mint else JiroColors.Coral,
        label = "statusColor"
    )

    val percentageTotal = totalBps / 100
    val statusText = when {
        items.isEmpty() -> "Add at least 1 stock"
        items.size > BasketValidator.MAX_HOLDINGS -> "Max ${BasketValidator.MAX_HOLDINGS} stocks allowed"
        !allMeetMin -> "Each stock must be at least 10%"
        totalBps < BasketValidator.TARGET_SUM_BPS -> "$percentageTotal% (${(100 - percentageTotal)}% remaining)"
        totalBps > BasketValidator.TARGET_SUM_BPS -> "$percentageTotal% (${percentageTotal - 100}% over limit)"
        else -> "100% Target Allocated"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Basket weight allocation: $statusText" }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target Allocation",
                color = JiroColors.Muted,
                fontSize = 12.sp,
                fontFamily = JiroTypography.BodyFamily
            )
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = JiroTypography.BodyFamily
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .background(JiroColors.Surface)
        ) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Empty basket",
                        color = JiroColors.Muted,
                        fontSize = 11.sp
                    )
                }
            } else {
                items.forEach { item ->
                    val weightFraction = (item.weightBps.toFloat() / 10_000f).coerceAtLeast(0.01f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = weightFraction,
                        animationSpec = tween(
                            durationMillis = if (reduceMotion) 100 else 300,
                            easing = FastOutSlowInEasing
                        ),
                        label = "segmentWeight_${item.ticker}"
                    )

                    Box(
                        modifier = Modifier
                            .weight(animatedFraction)
                            .fillMaxHeight()
                            .background(item.color)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.weightBps >= 1500) {
                            Text(
                                text = "${item.ticker} ${item.weightBps / 100}%",
                                color = JiroColors.Ink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        } else if (item.weightBps >= 1000) {
                            Text(
                                text = "${item.weightBps / 100}%",
                                color = JiroColors.Ink,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
