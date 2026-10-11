package com.jiro.app.components

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography
import com.jiro.core.Money

/**
 * Live payment split bar demonstrating:
 * [ Pay $33.50 | +$0.50 to jar ]
 * Features:
 * - Proportional animated split width
 * - Accessible TalkBack semantics
 * - Amber jar portion highlight
 */
@Composable
fun SplitBar(
    paymentAmount: Money,
    roundUpAmount: Money,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false
) {
    val totalUnits = paymentAmount.baseUnits + roundUpAmount.baseUnits
    val targetRatio = if (totalUnits > 0L) {
        (paymentAmount.baseUnits.toFloat() / totalUnits.toFloat()).coerceIn(0.15f, 0.85f)
    } else {
        0.80f
    }

    val animatedRatio by animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = tween(durationMillis = if (reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
        label = "splitBarRatio"
    )

    val paymentText = "Pay ${paymentAmount.formatUsdc()}"
    val roundUpText = "+${roundUpAmount.formatUsdc()} to jar"
    val fullSemantics = "$paymentText with $roundUpText"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = fullSemantics }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp))
        ) {
            // Left: Payment to merchant
            Box(
                modifier = Modifier
                    .weight(animatedRatio)
                    .fillMaxHeight()
                    .background(JiroColors.SurfaceElevated)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = paymentText,
                    color = JiroColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = JiroTypography.BodyFamily,
                    maxLines = 1
                )
            }

            // Divider line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(JiroColors.Line)
            )

            // Right: Round-up to jar
            Box(
                modifier = Modifier
                    .weight(1f - animatedRatio)
                    .fillMaxHeight()
                    .background(JiroColors.Amber.copy(alpha = 0.20f))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = roundUpText,
                    color = JiroColors.Amber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JiroTypography.BodyFamily,
                    maxLines = 1
                )
            }
        }
    }
}
