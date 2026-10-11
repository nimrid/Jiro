package com.jiro.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.AppTab
import com.jiro.app.theme.JiroColors

/**
 * Modern fintech navigation dock with custom vector icons,
 * animated selection pills, floating Pay FAB, and system gesture insets.
 */
@Composable
fun JiroBottomBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    onPayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = JiroColors.SurfaceElevated,
        border = BorderStroke(1.dp, JiroColors.Line),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home Tab
            BottomNavItem(
                label = "Home",
                isSelected = currentTab == AppTab.HOME,
                onClick = { onTabSelected(AppTab.HOME) },
                modifier = Modifier.weight(1f),
                icon = { tint -> NavHomeIcon(tint = tint) }
            )

            // Portfolio Tab
            BottomNavItem(
                label = "Portfolio",
                isSelected = currentTab == AppTab.PORTFOLIO,
                onClick = { onTabSelected(AppTab.PORTFOLIO) },
                modifier = Modifier.weight(1f),
                icon = { tint -> NavPortfolioIcon(tint = tint) }
            )

            // Elevated Floating Center "Pay" Button
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                PayFabButton(onClick = onPayClick)
            }

            // Pick Tab
            BottomNavItem(
                label = "Pick",
                isSelected = currentTab == AppTab.PICK,
                onClick = { onTabSelected(AppTab.PICK) },
                modifier = Modifier.weight(1f),
                icon = { tint -> NavPickIcon(tint = tint) }
            )

            // Activity Tab
            BottomNavItem(
                label = "Activity",
                isSelected = currentTab == AppTab.ACTIVITY,
                onClick = { onTabSelected(AppTab.ACTIVITY) },
                modifier = Modifier.weight(1f),
                icon = { tint -> NavActivityIcon(tint = tint) }
            )
        }
    }
}

/**
 * Interactive navigation tab item with animated selection pill,
 * smooth color interpolation, and icon scale micro-animation.
 */
@Composable
fun BottomNavItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (tint: Color) -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (isSelected) JiroColors.Amber else JiroColors.Muted,
        animationSpec = tween(durationMillis = 180),
        label = "tabTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabScale"
    )
    val pillBg by animateColorAsState(
        targetValue = if (isSelected) JiroColors.Amber.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "pillBg"
    )
    val pillBorder by animateColorAsState(
        targetValue = if (isSelected) JiroColors.Amber.copy(alpha = 0.24f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "pillBorder"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(pillBg)
            .border(BorderStroke(1.dp, pillBorder), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = JiroColors.Amber.copy(alpha = 0.2f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            icon(tint)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.2.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Centerpiece Floating Pay Button with radiant Amber gradient,
 * ambient glow, tactile press scale, and Solana Pay send glyph.
 */
@Composable
fun PayFabButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow halo
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFB020).copy(alpha = 0.32f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Main action circle
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFC043),
                            Color(0xFFFF9A00)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    color = Color(0xFFFFE6A0).copy(alpha = 0.55f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = JiroColors.Ink),
                    onClick = onClick
                )
                .semantics {
                    contentDescription = "Open payment screen"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                NavPayIcon(tint = JiroColors.Ink)
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "PAY",
                    color = JiroColors.Ink,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Custom Vector Canvas Icon Drawables
// -------------------------------------------------------------

@Composable
fun NavHomeIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        // Roof peak
        val roof = Path().apply {
            moveTo(w * 0.12f, h * 0.44f)
            lineTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.88f, h * 0.44f)
        }
        drawPath(
            path = roof,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Walls and arched hearth
        val walls = Path().apply {
            moveTo(w * 0.22f, h * 0.40f)
            lineTo(w * 0.22f, h * 0.88f)
            lineTo(w * 0.40f, h * 0.88f)
            lineTo(w * 0.40f, h * 0.62f)
            arcTo(
                rect = Rect(w * 0.40f, h * 0.56f, w * 0.60f, h * 0.68f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(w * 0.60f, h * 0.88f)
            lineTo(w * 0.78f, h * 0.88f)
            lineTo(w * 0.78f, h * 0.40f)
        }
        drawPath(
            path = walls,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun NavPortfolioIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val cornerRadius = CornerRadius(2.dp.toPx())

        // 3 Growth / Portfolio bars (ascending)
        // Bar 1 (Left: 42% height)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.52f),
            size = Size(w * 0.19f, h * 0.36f),
            cornerRadius = cornerRadius
        )

        // Bar 2 (Center: 68% height)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.40f, h * 0.32f),
            size = Size(w * 0.19f, h * 0.56f),
            cornerRadius = cornerRadius
        )

        // Bar 3 (Right: 92% height)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.69f, h * 0.14f),
            size = Size(w * 0.19f, h * 0.74f),
            cornerRadius = cornerRadius
        )
    }
}

@Composable
fun NavPickIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val center = Offset(w * 0.5f, h * 0.5f)
        val radius = w * 0.36f

        // Outer radar/target circle
        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )

        // Center target pip
        drawCircle(
            color = tint,
            radius = w * 0.11f,
            center = center
        )

        // 4 Crosshair ticks on target perimeter
        val tickLen = w * 0.12f
        // Top
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, center.y - radius - tickLen * 0.3f),
            end = Offset(w * 0.5f, center.y - radius + tickLen * 0.7f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Bottom
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, center.y + radius - tickLen * 0.7f),
            end = Offset(w * 0.5f, center.y + radius + tickLen * 0.3f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Left
        drawLine(
            color = tint,
            start = Offset(center.x - radius - tickLen * 0.3f, h * 0.5f),
            end = Offset(center.x - radius + tickLen * 0.7f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Right
        drawLine(
            color = tint,
            start = Offset(center.x + radius - tickLen * 0.7f, h * 0.5f),
            end = Offset(center.x + radius + tickLen * 0.3f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun NavActivityIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        // Transaction pulse / heartbeat line
        val pulse = Path().apply {
            moveTo(w * 0.08f, h * 0.52f)
            lineTo(w * 0.28f, h * 0.52f)
            lineTo(w * 0.44f, h * 0.16f)
            lineTo(w * 0.58f, h * 0.86f)
            lineTo(w * 0.72f, h * 0.52f)
            lineTo(w * 0.92f, h * 0.52f)
        }
        drawPath(
            path = pulse,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun NavPayIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp, 16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()

        // 45-degree send arrow
        drawLine(
            color = tint,
            start = Offset(w * 0.18f, h * 0.82f),
            end = Offset(w * 0.82f, h * 0.18f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Arrow head
        val head = Path().apply {
            moveTo(w * 0.42f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.58f)
        }
        drawPath(
            path = head,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
