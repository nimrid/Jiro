package com.jiro.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class WindowHeightClass {
    COMPACT, // < 700 dp (small phones, 16:9 aspect ratio, high display scaling)
    MEDIUM,  // 700..830 dp (standard modern tall phones)
    EXPANDED // > 830 dp (flagships, phablets, tablets)
}

enum class WindowWidthClass {
    NARROW,   // < 360 dp (small compact phones)
    STANDARD, // 360..599 dp (standard phones)
    EXPANDED  // >= 600 dp (foldables unfolded, tablets, landscape)
}

data class ScreenAdaptiveSpec(
    val heightClass: WindowHeightClass,
    val widthClass: WindowWidthClass,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val horizontalPadding: Dp,
    val jarWidth: Dp,
    val jarHeight: Dp,
    val spacerSmall: Dp,
    val spacerMedium: Dp,
    val spacerLarge: Dp,
    val statusTextSize: TextUnit
)

@Composable
fun rememberAdaptiveSpec(): ScreenAdaptiveSpec {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    val widthClass = when {
        screenWidth < 360 -> WindowWidthClass.NARROW
        screenWidth < 600 -> WindowWidthClass.STANDARD
        else -> WindowWidthClass.EXPANDED
    }

    val heightClass = when {
        screenHeight < 700 -> WindowHeightClass.COMPACT
        screenHeight <= 830 -> WindowHeightClass.MEDIUM
        else -> WindowHeightClass.EXPANDED
    }

    val horizontalPadding = when (widthClass) {
        WindowWidthClass.NARROW -> 14.dp
        WindowWidthClass.STANDARD -> 20.dp
        WindowWidthClass.EXPANDED -> 24.dp
    }

    val (jarWidth, jarHeight) = when (heightClass) {
        WindowHeightClass.COMPACT -> Pair(165.dp, 182.dp)
        WindowHeightClass.MEDIUM -> Pair(195.dp, 215.dp)
        WindowHeightClass.EXPANDED -> Pair(215.dp, 235.dp)
    }

    val (spacerSmall, spacerMedium, spacerLarge) = when (heightClass) {
        WindowHeightClass.COMPACT -> Triple(6.dp, 10.dp, 16.dp)
        WindowHeightClass.MEDIUM -> Triple(8.dp, 14.dp, 22.dp)
        WindowHeightClass.EXPANDED -> Triple(12.dp, 18.dp, 28.dp)
    }

    val statusTextSize = when {
        widthClass == WindowWidthClass.NARROW || heightClass == WindowHeightClass.COMPACT -> 17.sp
        heightClass == WindowHeightClass.MEDIUM -> 19.sp
        else -> 21.sp
    }

    return ScreenAdaptiveSpec(
        heightClass = heightClass,
        widthClass = widthClass,
        screenWidthDp = screenWidth,
        screenHeightDp = screenHeight,
        horizontalPadding = horizontalPadding,
        jarWidth = jarWidth,
        jarHeight = jarHeight,
        spacerSmall = spacerSmall,
        spacerMedium = spacerMedium,
        spacerLarge = spacerLarge,
        statusTextSize = statusTextSize
    )
}

/**
 * Centered responsive container that prevents wide screens (tablets, foldables, landscape)
 * from stretching uncomfortably wide, while filling width on standard phones.
 */
@Composable
fun AdaptiveContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            content = content
        )
    }
}
