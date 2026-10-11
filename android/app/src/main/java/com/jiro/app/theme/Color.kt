package com.jiro.app.theme

import androidx.compose.ui.graphics.Color

object JiroColors {
    // Design tokens from docs/DESIGN.md
    val Ink = Color(0xFF0E1013)       // Background
    val Surface = Color(0xFF161A1F)   // Cards & sheets
    val SurfaceElevated = Color(0xFF1F242C) // Modals & floating elements
    val Line = Color(0xFF232932)      // Borders & dividers
    val Text = Color(0xFFF2EFE8)      // Primary readable text
    val Muted = Color(0xFF8A93A0)     // Subtitles, secondary captions
    val Amber = Color(0xFFFFB84D)     // Waiting / pending liquid / jar fill
    val Mint = Color(0xFF5CE0B0)      // Owned / positive / confirmed
    val Coral = Color(0xFFFF6B5B)     // Error / declined / blocked
    val Sky = Color(0xFF7FB5FF)       // Verified badges / explorer links

    // Ticker monogram deterministic color palette
    val MonogramPalettes = listOf(
        Color(0xFF7FB5FF), // Sky
        Color(0xFF5CE0B0), // Mint
        Color(0xFFFFB84D), // Amber
        Color(0xFFBA8FFF), // Lavender
        Color(0xFFFF8E72), // Salmon
        Color(0xFF65D5FF), // Cyan
        Color(0xFFFFD166)  // Gold
    )

    fun getMonogramColor(ticker: String): Color {
        val hash = ticker.fold(0) { acc, c -> acc * 31 + c.code }
        val index = kotlin.math.abs(hash) % MonogramPalettes.size
        return MonogramPalettes[index]
    }
}
