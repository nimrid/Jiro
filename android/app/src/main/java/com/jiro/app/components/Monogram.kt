package com.jiro.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography

/**
 * Deterministic colored monogram avatar for a stock ticker.
 * Adheres to DESIGN.md rule: "No stock logos (ticker monograms only)".
 */
@Composable
fun Monogram(
    ticker: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    backgroundColor: Color = JiroColors.getMonogramColor(ticker),
    textColor: Color = JiroColors.Ink
) {
    val cleanTicker = ticker.removeSuffix(".US").uppercase().take(4)
    val fontSize = when {
        cleanTicker.length <= 2 -> (size.value * 0.42f).sp
        cleanTicker.length == 3 -> (size.value * 0.36f).sp
        else -> (size.value * 0.30f).sp
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .semantics {
                contentDescription = "$cleanTicker ticker monogram"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cleanTicker,
            color = textColor,
            fontFamily = JiroTypography.HeadingsFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            maxLines = 1
        )
    }
}
