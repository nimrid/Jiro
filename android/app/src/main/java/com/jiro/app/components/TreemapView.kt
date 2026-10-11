package com.jiro.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.theme.JiroColors

data class TreemapTile(
    val ticker: String,
    val name: String,
    val mint: String,
    val units: Double,
    val currentWeightBps: Int, // Basis points (10000 = 100%)
    val targetWeightBps: Int,
    val valueUsdc: Double,
    val contributionsUsdc: Double,
    val isNextBuy: Boolean = false
)

/**
 * Treemap portfolio visualization:
 * - Tile sizes proportional to holding weight
 * - Dashed target outlines comparing current vs target weights
 * - Next-buy tile pulses with Amber glow
 * - Tap to inspect details
 */
@Composable
fun TreemapView(
    holdings: List<TreemapTile>,
    onTileClick: (TreemapTile) -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(260.dp)
) {
    if (holdings.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(JiroColors.Surface)
                .border(1.dp, JiroColors.Line, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No assets in portfolio yet",
                color = JiroColors.Muted,
                fontSize = 14.sp
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "nextBuyPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Compute proportional bounding rectangles for the treemap tiles
    var tileBounds by remember { mutableStateOf<List<Pair<TreemapTile, Rect>>>(emptyList()) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(JiroColors.Surface)
            .border(1.dp, JiroColors.Line, RoundedCornerShape(16.dp))
            .pointerInput(holdings) {
                detectTapGestures { offset ->
                    val clicked = tileBounds.firstOrNull { it.second.contains(offset) }
                    if (clicked != null) {
                        onTileClick(clicked.first)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWeight = holdings.sumOf { it.currentWeightBps.coerceAtLeast(500) }
            val w = size.width
            val h = size.height

            val bounds = mutableListOf<Pair<TreemapTile, Rect>>()
            val padding = 6.dp.toPx()

            // Simplified layout split (2 columns or balanced rows)
            var currentY = padding
            var currentX = padding

            val sorted = holdings.sortedByDescending { it.currentWeightBps }
            val primary = sorted.firstOrNull()
            val secondary = sorted.drop(1)

            if (primary != null && secondary.isNotEmpty()) {
                val primaryWeightFraction = primary.currentWeightBps.toFloat() / totalWeight.toFloat()
                val leftWidth = (w * primaryWeightFraction.coerceIn(0.4f, 0.65f)) - (padding * 1.5f)
                val primaryRect = Rect(currentX, currentY, currentX + leftWidth, h - padding)
                bounds.add(Pair(primary, primaryRect))

                val rightX = currentX + leftWidth + padding
                val rightWidth = w - rightX - padding
                val remainingHeight = h - (padding * 2)

                val secondaryTotalWeight = secondary.sumOf { it.currentWeightBps.coerceAtLeast(500) }
                var secY = padding

                for (item in secondary) {
                    val fraction = item.currentWeightBps.toFloat() / secondaryTotalWeight.toFloat()
                    val itemHeight = (remainingHeight * fraction) - (padding * 0.5f)
                    val itemRect = Rect(rightX, secY, rightX + rightWidth, (secY + itemHeight).coerceAtMost(h - padding))
                    bounds.add(Pair(item, itemRect))
                    secY += itemHeight + padding
                }
            } else if (primary != null) {
                // Single item fills entire area
                bounds.add(Pair(primary, Rect(padding, padding, w - padding, h - padding)))
            }

            tileBounds = bounds

            // Draw Tiles
            for ((tile, rect) in bounds) {
                val isNext = tile.isNextBuy
                val tileColor = JiroColors.getMonogramColor(tile.ticker)

                // 1. Tile Background Fill
                drawRoundRect(
                    color = tileColor.copy(alpha = if (isNext) 0.22f else 0.12f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                )

                // 2. Tile Border
                if (isNext) {
                    // Pulsing Amber next-buy border
                    drawRoundRect(
                        color = JiroColors.Amber.copy(alpha = pulseAlpha),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                } else {
                    drawRoundRect(
                        color = JiroColors.Line,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 3. Dashed Target Outline comparison
                val targetEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.20f),
                    topLeft = Offset(rect.left + 4f, rect.top + 4f),
                    size = Size(rect.width - 8f, rect.height - 8f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = targetEffect)
                )
            }
        }

        // Overlay text labels on tiles
        for ((tile, rect) in tileBounds) {
            val isNext = tile.isNextBuy
            Box(
                modifier = Modifier
                    .offset(
                        x = (rect.left / LocalDensityWrapper.density).dp,
                        y = (rect.top / LocalDensityWrapper.density).dp
                    )
                    .size(
                        width = (rect.width / LocalDensityWrapper.density).dp,
                        height = (rect.height / LocalDensityWrapper.density).dp
                    )
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.TopStart)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tile.ticker,
                            color = JiroColors.Text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (isNext) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "NEXT",
                                color = JiroColors.Amber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "${tile.currentWeightBps / 100}%",
                        color = JiroColors.Muted,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = String.format("$%.2f", tile.valueUsdc),
                    color = JiroColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

private object LocalDensityWrapper {
    var density: Float = 2.5f
}
