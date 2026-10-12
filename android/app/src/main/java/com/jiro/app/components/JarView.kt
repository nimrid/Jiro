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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jiro.app.theme.JiroColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium glass savings jar with:
 * - Strict inner-body path clipping (eliminates any liquid spilling outside curved corners/bottom)
 * - Multi-layer animated sine waves with volumetric golden-amber gradient
 * - Realistic stacked 3D embossed gold coins submerged at the jar base
 * - Laser-etched glass measurement graduations (25%, 50%, 75%, GOAL)
 * - Specular glass highlights, thick glass base refraction, and threaded collar rim
 * - Animated dropping coin and surface splash ripples when savings drop occurs
 * - Glowing pulse when target threshold is reached
 */
@Composable
fun JarView(
    currentBaseUnits: Long,
    thresholdBaseUnits: Long = 5_000_000L, // $5.00 default invest threshold
    modifier: Modifier = Modifier.size(width = 210.dp, height = 230.dp),
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
        animationSpec = tween(durationMillis = if (reduceMotion) 150 else 600, easing = FastOutSlowInEasing),
        label = "jarFillRatio"
    )

    // Wave animation phases (primary and secondary wave)
    val infiniteTransition = rememberInfiniteTransition(label = "jarWaveTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (-2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondaryPhase"
    )

    // Bubble rising animation
    val bubblePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubblePhase"
    )

    // Drop animation transition
    val dropProgress by animateFloatAsState(
        targetValue = if (isDropping) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutLinearInEasing),
        label = "dropAnimation"
    )

    // Ready pulse glow
    val readyPulse by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
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

            // Jar architecture coordinates
            val jarLeft = width * 0.13f
            val jarRight = width * 0.87f
            val jarWidth = jarRight - jarLeft
            val jarTop = height * 0.22f
            val jarBottom = height * 0.88f
            val jarHeight = jarBottom - jarTop
            val cornerRadius = 22.dp.toPx()

            // Neck / Collar coordinates
            val neckLeft = width * 0.28f
            val neckRight = width * 0.72f
            val neckWidth = neckRight - neckLeft
            val neckTop = height * 0.12f
            val neckBottom = jarTop

            // Rim / Lip coordinates
            val rimLeft = width * 0.24f
            val rimRight = width * 0.76f
            val rimTop = height * 0.06f
            val rimBottom = neckTop

            // Strict inner path for liquid and coin clipping (prevents ANY spill)
            val innerJarPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = jarLeft + 2.dp.toPx(),
                        top = jarTop + 2.dp.toPx(),
                        right = jarRight - 2.dp.toPx(),
                        bottom = jarBottom - 2.dp.toPx(),
                        cornerRadius = CornerRadius(cornerRadius - 2.dp.toPx(), cornerRadius - 2.dp.toPx())
                    )
                )
            }

            // Outer jar silhouette path (including neck and shoulders)
            val jarOutlinePath = Path().apply {
                // Top lip of neck
                moveTo(neckLeft, neckBottom)
                lineTo(neckLeft, neckTop)
                lineTo(neckRight, neckTop)
                lineTo(neckRight, neckBottom)
                // Right shoulder curve
                cubicTo(
                    neckRight + (jarRight - neckRight) * 0.3f, neckBottom,
                    jarRight, jarTop - (jarHeight * 0.05f),
                    jarRight, jarTop + cornerRadius
                )
                // Right side down to bottom-right corner
                lineTo(jarRight, jarBottom - cornerRadius)
                // Bottom-right corner
                cubicTo(
                    jarRight, jarBottom,
                    jarRight - cornerRadius * 0.5f, jarBottom,
                    jarRight - cornerRadius, jarBottom
                )
                // Bottom edge
                lineTo(jarLeft + cornerRadius, jarBottom)
                // Bottom-left corner
                cubicTo(
                    jarLeft + cornerRadius * 0.5f, jarBottom,
                    jarLeft, jarBottom,
                    jarLeft, jarBottom - cornerRadius
                )
                // Left side up to shoulder
                lineTo(jarLeft, jarTop + cornerRadius)
                // Left shoulder curve
                cubicTo(
                    jarLeft, jarTop - (jarHeight * 0.05f),
                    neckLeft - (neckLeft - jarLeft) * 0.3f, neckBottom,
                    neckLeft, neckBottom
                )
                close()
            }

            // 1. Frosty ambient glass background
            drawPath(
                path = innerJarPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x12FFFFFF),
                        Color(0x06FFFFFF),
                        Color(0x10FFFFFF)
                    ),
                    startY = jarTop,
                    endY = jarBottom
                )
            )

            // 2. Liquid & Coins (STRICTLY CLIPPED INSIDE JAR)
            clipPath(innerJarPath) {
                val liquidTopTarget = jarBottom - (jarHeight * animatedFillRatio)

                if (animatedFillRatio > 0.015f) {
                    val waveAmp = if (reduceMotion) 0f else (height * 0.012f)
                    val steps = 36
                    val stepWidth = jarWidth / steps

                    // A. Secondary / Background Wave (Darker amber, depth)
                    val backWavePath = Path().apply {
                        moveTo(jarLeft - 10f, jarBottom + 10f)
                        lineTo(jarRight + 10f, jarBottom + 10f)
                        lineTo(jarRight + 10f, liquidTopTarget + 2.dp.toPx())
                        for (i in steps downTo 0) {
                            val x = jarLeft + (i * stepWidth)
                            val angle = (x / jarWidth * 2.2 * PI).toFloat() + secondaryPhase
                            val waveY = liquidTopTarget + 2.dp.toPx() + (sin(angle) * (waveAmp * 0.7f))
                            lineTo(x, waveY)
                        }
                        close()
                    }
                    drawPath(
                        path = backWavePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFB45309).copy(alpha = 0.55f),
                                Color(0xFF78350F).copy(alpha = 0.75f)
                            ),
                            startY = liquidTopTarget,
                            endY = jarBottom
                        )
                    )

                    // B. Primary Liquid Fill & Foreground Wave
                    val liquidPath = Path().apply {
                        moveTo(jarLeft - 10f, jarBottom + 10f)
                        lineTo(jarRight + 10f, jarBottom + 10f)
                        lineTo(jarRight + 10f, liquidTopTarget)
                        for (i in steps downTo 0) {
                            val x = jarLeft + (i * stepWidth)
                            val angle = (x / jarWidth * 2 * PI).toFloat() + wavePhase
                            val waveY = liquidTopTarget + (sin(angle) * waveAmp)
                            lineTo(x, waveY)
                        }
                        close()
                    }

                    val topGlowColor = if (isFull) {
                        Color(0xFFFDE047).copy(alpha = readyPulse)
                    } else {
                        Color(0xFFFBBF24)
                    }

                    drawPath(
                        path = liquidPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                topGlowColor,
                                Color(0xFFF59E0B),
                                Color(0xFFD97706),
                                Color(0xFFB45309)
                            ),
                            startY = liquidTopTarget,
                            endY = jarBottom
                        )
                    )

                    // C. Glowing Surface Meniscus Line
                    val meniscusPath = Path().apply {
                        for (i in 0..steps) {
                            val x = jarLeft + (i * stepWidth)
                            val angle = (x / jarWidth * 2 * PI).toFloat() + wavePhase
                            val waveY = liquidTopTarget + (sin(angle) * waveAmp)
                            if (i == 0) moveTo(x, waveY) else lineTo(x, waveY)
                        }
                    }
                    drawPath(
                        path = meniscusPath,
                        color = Color(0xFFFEF08A).copy(alpha = if (isFull) readyPulse else 0.85f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // D. Floating Effervescent Micro-Bubbles
                    if (!reduceMotion && animatedFillRatio > 0.1f) {
                        val bubbleYs = listOf(
                            jarBottom - (jarHeight * animatedFillRatio * ((bubblePhase + 0.2f) % 1f)),
                            jarBottom - (jarHeight * animatedFillRatio * ((bubblePhase + 0.6f) % 1f)),
                            jarBottom - (jarHeight * animatedFillRatio * ((bubblePhase + 0.85f) % 1f))
                        )
                        val bubbleXs = listOf(
                            jarLeft + jarWidth * 0.32f + (sin(wavePhase) * 4.dp.toPx()),
                            jarLeft + jarWidth * 0.68f + (cos(wavePhase) * 5.dp.toPx()),
                            jarLeft + jarWidth * 0.48f + (sin(secondaryPhase) * 3.dp.toPx())
                        )
                        for (b in 0..2) {
                            if (bubbleYs[b] > liquidTopTarget + 4.dp.toPx() && bubbleYs[b] < jarBottom - 4.dp.toPx()) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.40f),
                                    radius = (1.5f + b * 0.5f).dp.toPx(),
                                    center = Offset(bubbleXs[b], bubbleYs[b])
                                )
                            }
                        }
                    }
                }

                // 3. Realistic Stacked Gold Coins resting at the bottom
                if (currentBaseUnits > 0L) {
                    val coinRadiusX = width * 0.085f
                    val coinRadiusY = height * 0.022f

                    // Draw coin stack based on units (e.g. 1 coin for small balance, up to 6 coins)
                    val coinCount = ((currentBaseUnits.toDouble() / thresholdBaseUnits.toDouble()) * 6.0)
                        .toInt()
                        .coerceIn(1, 6)

                    val baseCoinY = jarBottom - 10.dp.toPx()
                    val coinOffsets = listOf(
                        Offset(jarLeft + jarWidth * 0.38f, baseCoinY),
                        Offset(jarLeft + jarWidth * 0.62f, baseCoinY + 1.dp.toPx()),
                        Offset(jarLeft + jarWidth * 0.50f, baseCoinY - 6.dp.toPx()),
                        Offset(jarLeft + jarWidth * 0.32f, baseCoinY - 10.dp.toPx()),
                        Offset(jarLeft + jarWidth * 0.68f, baseCoinY - 11.dp.toPx()),
                        Offset(jarLeft + jarWidth * 0.48f, baseCoinY - 16.dp.toPx())
                    )

                    for (c in 0 until coinCount) {
                        val center = coinOffsets[c]
                        // Shadow under coin
                        drawOval(
                            color = Color(0x66000000),
                            topLeft = Offset(center.x - coinRadiusX, center.y - coinRadiusY + 2.dp.toPx()),
                            size = Size(coinRadiusX * 2, coinRadiusY * 2)
                        )
                        // Coin edge (3D thickness)
                        drawOval(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFFBBF24), Color(0xFFB45309)),
                                startY = center.y - coinRadiusY,
                                endY = center.y + coinRadiusY
                            ),
                            topLeft = Offset(center.x - coinRadiusX, center.y - coinRadiusY),
                            size = Size(coinRadiusX * 2, coinRadiusY * 2)
                        )
                        // Shiny coin face
                        drawOval(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B)),
                                center = Offset(center.x - coinRadiusX * 0.2f, center.y - coinRadiusY * 0.2f),
                                radius = coinRadiusX
                            ),
                            topLeft = Offset(center.x - coinRadiusX + 1.5.dp.toPx(), center.y - coinRadiusY + 1.dp.toPx()),
                            size = Size((coinRadiusX - 1.5.dp.toPx()) * 2, (coinRadiusY - 1.dp.toPx()) * 2)
                        )
                    }
                }
            } // End of clipPath

            // 4. Etched Measurement Tick Marks along the left glass wall
            val tickX = jarLeft + 3.dp.toPx()
            val ticks = listOf(0.25f, 0.50f, 0.75f, 1.0f)
            ticks.forEach { t ->
                val tickY = jarBottom - (jarHeight * t)
                val tickLen = if (t == 1.0f) 14.dp.toPx() else 8.dp.toPx()
                val tickColor = if (t == 1.0f && isFull) JiroColors.Amber else Color.White.copy(alpha = 0.22f)

                drawLine(
                    color = tickColor,
                    start = Offset(tickX, tickY),
                    end = Offset(tickX + tickLen, tickY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 5. Heavy Glass Base (Refraction line at bottom)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(jarLeft + 8.dp.toPx(), jarBottom - 6.dp.toPx()),
                size = Size(jarWidth - 16.dp.toPx(), 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // 6. Jar Body Outline (Smooth glass styling)
            val glassOutlineColor = if (isFull) {
                JiroColors.Amber.copy(alpha = readyPulse)
            } else {
                Color.White.copy(alpha = 0.25f)
            }
            drawRoundRect(
                color = glassOutlineColor,
                topLeft = Offset(jarLeft, jarTop),
                size = Size(jarWidth, jarHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = if (isFull) 2.5.dp.toPx() else 2.dp.toPx())
            )

            // 7. Glass Neck & Screw-Collar Rim
            // Neck Outline
            drawRect(
                color = glassOutlineColor,
                topLeft = Offset(neckLeft, neckTop),
                size = Size(neckWidth, neckBottom - neckTop),
                style = Stroke(width = 1.8.dp.toPx())
            )

            // Thread ridge on neck
            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = Offset(neckLeft + 4.dp.toPx(), neckTop + (neckBottom - neckTop) * 0.5f),
                end = Offset(neckRight - 4.dp.toPx(), neckTop + (neckBottom - neckTop) * 0.5f),
                strokeWidth = 1.5.dp.toPx()
            )

            // Glass/Metal Collar Lip
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFE2E8F0).copy(alpha = 0.35f),
                        Color(0xFFFFFFFF).copy(alpha = 0.65f),
                        Color(0xFFCBD5E1).copy(alpha = 0.40f)
                    ),
                    startX = rimLeft,
                    endX = rimRight
                ),
                topLeft = Offset(rimLeft, rimTop),
                size = Size(rimRight - rimLeft, rimBottom - rimTop),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // 8. Glass Specular Highlight Refraction Line (Left & Right sheen)
            val specularPath = Path().apply {
                moveTo(jarLeft + 8.dp.toPx(), jarTop + cornerRadius * 0.8f)
                lineTo(jarLeft + 8.dp.toPx(), jarBottom - cornerRadius * 0.8f)
            }
            drawPath(
                path = specularPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.20f)
                    ),
                    startY = jarTop,
                    endY = jarBottom
                ),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Right subtle glass bounce light
            val rightSheenPath = Path().apply {
                moveTo(jarRight - 6.dp.toPx(), jarTop + cornerRadius)
                lineTo(jarRight - 6.dp.toPx(), jarBottom - cornerRadius)
            }
            drawPath(
                path = rightSheenPath,
                color = Color.White.copy(alpha = 0.08f),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            )

            // 9. Falling Animated Coin Drop with splash ripples
            if (isDropping && dropProgress > 0f && dropProgress < 1f && !reduceMotion) {
                val liquidTopTarget = jarBottom - (jarHeight * animatedFillRatio)
                val dropY = rimTop + (liquidTopTarget - rimTop) * dropProgress
                val coinW = width * 0.07f
                // 3D tumble perspective
                val coinH = coinW * (0.4f + 0.6f * kotlin.math.abs(cos(dropProgress * 4 * PI).toFloat()))

                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFFB45309)),
                        startY = dropY - coinH,
                        endY = dropY + coinH
                    ),
                    topLeft = Offset(width * 0.5f - coinW, dropY - coinH),
                    size = Size(coinW * 2, coinH * 2)
                )
                drawOval(
                    color = Color(0xFF78350F),
                    topLeft = Offset(width * 0.5f - coinW, dropY - coinH),
                    size = Size(coinW * 2, coinH * 2),
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // Splash ripples when coin nears the liquid
                if (dropProgress > 0.75f) {
                    val rippleAlpha = (1f - dropProgress) * 4f
                    val rippleRadius = (dropProgress - 0.75f) * width * 0.35f
                    drawOval(
                        color = Color(0xFFFEF08A).copy(alpha = rippleAlpha.coerceIn(0f, 0.8f)),
                        topLeft = Offset(width * 0.5f - rippleRadius, liquidTopTarget - (rippleRadius * 0.35f)),
                        size = Size(rippleRadius * 2, rippleRadius * 0.7f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
    }
}
