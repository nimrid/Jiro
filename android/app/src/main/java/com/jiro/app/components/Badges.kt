package com.jiro.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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

enum class StockStatus(val label: String, val color: Color) {
    BUYABLE("Buyable", JiroColors.Mint),
    THIN_LIQUIDITY("Thin liquidity", JiroColors.Amber),
    PAUSED("Paused", JiroColors.Coral),
    NEEDS_REVIEW("Needs review", JiroColors.Muted)
}

@Composable
fun StatusChip(
    status: StockStatus,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(status.color.copy(alpha = 0.12f))
            .border(1.dp, status.color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = "Status: ${status.label}" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = status.label,
            color = status.color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JiroTypography.BodyFamily
        )
    }
}

enum class LiquidityTier(val label: String, val color: Color) {
    DEEP("Deep", JiroColors.Mint),
    OK("OK", JiroColors.Sky),
    THIN("Thin", JiroColors.Amber)
}

@Composable
fun LiquidityBadge(
    tier: LiquidityTier,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(JiroColors.Surface)
            .border(1.dp, JiroColors.Line, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .semantics { contentDescription = "Liquidity depth: ${tier.label}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tier.color)
        )
        Text(
            text = tier.label,
            color = JiroColors.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = JiroTypography.BodyFamily
        )
    }
}

@Composable
fun MarketBadge(
    isOpen: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isOpen) JiroColors.Mint else JiroColors.Muted
    val text = if (isOpen) "Market Open" else "Market Closed"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(JiroColors.SurfaceElevated)
            .border(1.dp, JiroColors.Line, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            color = if (isOpen) JiroColors.Text else JiroColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = JiroTypography.BodyFamily
        )
    }
}

@Composable
fun QuoteCountdown(
    secondsRemaining: Int,
    totalSeconds: Int = 30,
    modifier: Modifier = Modifier
) {
    val progress = (secondsRemaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    val color = when {
        secondsRemaining <= 5 -> JiroColors.Coral
        secondsRemaining <= 10 -> JiroColors.Amber
        else -> JiroColors.Sky
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(JiroColors.SurfaceElevated)
            .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = "Quote expires in $secondsRemaining seconds" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(14.dp),
            color = color,
            trackColor = JiroColors.Line,
            strokeWidth = 2.dp
        )
        Text(
            text = "${secondsRemaining}s quote",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JiroTypography.MonoFamily
        )
    }
}
