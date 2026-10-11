package com.jiro.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import com.jiro.app.R

// Typography hierarchy matching DESIGN.md
// Space Grotesk for Headings, Inter with Tabular Numerals for body, JetBrains Mono for addresses/hash
object JiroTypography {
    val HeadingsFamily = FontFamily(
        Font(R.font.space_grotesk, FontWeight.Normal)
    )
    val BodyFamily = FontFamily(
        Font(R.font.inter, FontWeight.Normal)
    )
    val MonoFamily = FontFamily(
        Font(R.font.jetbrains_mono, FontWeight.Normal)
    )

    val DisplayLarge = TextStyle(
        fontFamily = HeadingsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum",
        color = JiroColors.Text
    )

    val HeadlineMedium = TextStyle(
        fontFamily = HeadingsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = JiroColors.Text
    )

    val TitleMedium = TextStyle(
        fontFamily = HeadingsFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = JiroColors.Text
    )

    val BodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum",
        color = JiroColors.Text
    )

    val BodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
        color = JiroColors.Muted
    )

    val LabelSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = JiroColors.Muted
    )

    val MonospaceMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = JiroColors.Muted
    )
}

val AppTypography = Typography(
    displayLarge = JiroTypography.DisplayLarge,
    headlineMedium = JiroTypography.HeadlineMedium,
    titleMedium = JiroTypography.TitleMedium,
    bodyLarge = JiroTypography.BodyLarge,
    bodyMedium = JiroTypography.BodyMedium,
    labelSmall = JiroTypography.LabelSmall
)
