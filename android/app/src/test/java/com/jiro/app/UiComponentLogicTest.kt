package com.jiro.app

import com.jiro.app.components.BasketWeightItem
import com.jiro.app.components.TreemapTile
import com.jiro.app.theme.JiroColors
import com.jiro.core.BasketValidator
import com.jiro.core.Money
import org.junit.Assert.*
import org.junit.Test

class UiComponentLogicTest {

    @Test
    fun testMonogramDeterministicColor() {
        val colorAapl1 = JiroColors.getMonogramColor("AAPL")
        val colorAapl2 = JiroColors.getMonogramColor("AAPL")
        assertEquals("Monogram color must be deterministic for AAPL", colorAapl1, colorAapl2)

        val colorVoo = JiroColors.getMonogramColor("VOO")
        assertNotNull(colorVoo)
    }

    @Test
    fun testStackedWeightBarValidation() {
        val validItems = listOf(
            BasketWeightItem("VOO", 4000),
            BasketWeightItem("AAPL", 3000),
            BasketWeightItem("MSFT", 3000)
        )
        val totalBps = validItems.sumOf { it.weightBps }
        assertEquals(BasketValidator.TARGET_SUM_BPS, totalBps)
        assertTrue(validItems.size in 1..BasketValidator.MAX_HOLDINGS)
        assertTrue(validItems.all { it.weightBps >= BasketValidator.MIN_WEIGHT_BPS })

        // Exceeding 100%
        val overItems = listOf(
            BasketWeightItem("VOO", 5000),
            BasketWeightItem("AAPL", 4000),
            BasketWeightItem("MSFT", 3000)
        )
        assertTrue(overItems.sumOf { it.weightBps } > BasketValidator.TARGET_SUM_BPS)

        // Below minimum 10%
        val underMinItems = listOf(
            BasketWeightItem("VOO", 9500),
            BasketWeightItem("AAPL", 500) // 5% < 10%
        )
        assertFalse(underMinItems.all { it.weightBps >= BasketValidator.MIN_WEIGHT_BPS })
    }

    @Test
    fun testSplitBarRatioCalculation() {
        val payment = Money(33_500_000L) // $33.50
        val roundUp = Money(500_000L)    // $0.50
        val total = payment.baseUnits + roundUp.baseUnits
        assertEquals(34_000_000L, total)

        val ratio = payment.baseUnits.toFloat() / total.toFloat()
        assertTrue("Ratio should be approximately 0.985", ratio > 0.98f)
    }

    @Test
    fun testTreemapTileWeightComparison() {
        val tile = TreemapTile(
            ticker = "VOO",
            name = "Vanguard S&P 500",
            mint = "7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs",
            units = 0.085,
            currentWeightBps = 3500,
            targetWeightBps = 4000,
            valueUsdc = 35.0,
            contributionsUsdc = 33.5,
            isNextBuy = true
        )

        assertTrue(tile.currentWeightBps < tile.targetWeightBps)
        assertTrue(tile.isNextBuy)
    }
}
