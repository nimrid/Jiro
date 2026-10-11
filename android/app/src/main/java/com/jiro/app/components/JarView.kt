package com.jiro.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jiro.app.theme.JiroColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Canvas jar filling toward the investment threshold.
 * Features:
 * - Smooth easing of liquid level (500ms)
 * - Animated sine wave on the surface of the amber liquid
 * - Coin drop animation falling into the jar
 * - Ready-to-invest pulse when threshold is reached
 * - Accessibility semantic description
 */
@Composable
fun JarView(
    currentBaseUnits: Long,
    thresholdBaseUnits: Long = 5_000_000L, // $5.00 default invest threshold
    modifier: Modifier = Modifier.size(width = 200.dp, height = 240.dp),
    isDropping: Boolean = false,
    reduceMotion: Boolean = false
) {
    val fillRatio = if (thresholdBaseUnits > 0) {
        (currentBaseUnits.toFloat() / thresholdBaseUnits.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val isFull = currentBaseUnits >= thresholdBaseUnits

    // Smooth level easing
    val animatedFillRatio by animateFloatAsState(
        targetValue = fillRatio,
        animationSpec = tween(durationMillis = if (reduceMotion) 150 else 500, easing = FastOutSlowInEasing),
        label = "jarFillRatio"
    )

    // Wave animation phase
    val infiniteTransition = rememberInfiniteTransition(label = "jarWaveTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Drop animation transition
    val dropProgress by animateFloatAsState(
        targetValue = if (isDropping) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutLinearInEasing),
        label = "dropAnimation"
    )

    // Ready pulse glow
    val readyPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "readyPulse"
    )

    val currentDollars = String.format("%.2f", currentBaseUnits / 1_000_000.0)
    val targetDollars = String.format("%.2f", thresholdBaseUnits / 1_000_000.0)

    Box(
        modifier = modifier.semantics {
            contentDescription = "Coin jar containing \$$currentDollars of \$$targetDollars waiting to invest"
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val jarLeft = width * 0.15f
            val jarRight = width * 0.85f
            val jarWidth = jarRight - jarLeft
            val jarTop = height * 0.22f
            val jarBottom = height * 0.92f
            val jarHeight = jarBottom - jarTop

            val rimLeft = width * 0.28f
            val rimRight = width * 0.72f
            val rimTop = height * 0.14f
            val rimBottom = jarTop

            val liquidTopTarget = jarBottom - (jarHeight * animatedFillRatio)

            // 1. Draw Liquid Fill & Wave Path
            if (animatedFillRatio > 0.02f) {
                val liquidPath = Path().apply {
                    moveTo(jarLeft, jarBottom)
                    lineTo(jarRight, jarBottom)

                    val waveAmplitude = if (reduceMotion) 0f else (height * 0.015f)
                    val steps = 30
                    val stepWidth = jarWidth / steps

                    lineTo(jarRight, liquidTopTarget)
                    for (i in steps downTo 0) {
                        val x = jarLeft + (i * stepWidth)
                        val angle = (x / jarWidth * 2 * PI).toFloat() + wavePhase
                        val waveY = liquidTopTarget + (sin(angle) * waveAmplitude)
                        lineTo(x, waveY)
                    }
                    close()
                }

                val liquidColor = if (isFull) {
                    JiroColors.Amber.copy(alpha = readyPulse)
                } else {
                    JiroColors.Amber
                }

                val liquidBrush = Brush.verticalGradient(
                    colors = listOf(
                        liquidColor.copy(alpha = 0.95f),
                        Color(0xFFE59828)
                    ),
                    startY = liquidTopTarget,
                    endY = jarBottom
                )

                drawPath(path = liquidPath, brush = liquidBrush)
            }

            // 2. Draw Falling Coin Drop (if triggered)
            if (isDropping && dropProgress > 0f && dropProgress < 1f && !reduceMotion) {
                val dropY = rimTop + (liquidTopTarget - rimTop) * dropProgress
                val coinRadius = width * 0.045f
                val coinCenter = Offset(width * 0.5f, dropY)

                drawCircle(
                    color = JiroColors.Amber,
                    radius = coinRadius,
                    center = coinCenter
                )
                drawCircle(
                    color = JiroColors.Ink,
                    radius = coinRadius,
                    center = coinCenter,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 3. Draw Jar Outline (Glass Effect)
            // Neck / Rim
            drawRoundRect(
                color = JiroColors.Line,
                topLeft = Offset(rimLeft, rimTop),
                size = Size(rimRight - rimLeft, rimBottom - rimTop),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Main Body
            drawRoundRect(
                color = if (isFull) JiroColors.Amber.copy(alpha = 0.7f) else JiroColors.Line,
                topLeft = Offset(jarLeft, jarTop),
                size = Size(jarWidth, jarHeight),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                style = Stroke(width = if (isFull) 2.5.dp.toPx() else 2.dp.toPx())
            )

            // Glass Reflection Accent Line (Subtle premium aesthetic)
            val reflectionPath = Path().apply {
                moveTo(jarLeft + 8.dp.toPx(), jarTop + 24.dp.toPx())
                lineTo(jarLeft + 8.dp.toPx(), jarBottom - 24.dp.toPx())
            }
            drawPath(
                path = reflectionPath,
                color = Color.White.copy(alpha = 0.12f),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
