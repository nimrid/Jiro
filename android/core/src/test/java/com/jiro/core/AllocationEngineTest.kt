package com.jiro.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AllocationEngineTest {

    private val mintA = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")
    private val mintB = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
    private val mintC = PublicKey("2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a")

    @Test
    fun testEmptyPortfolioSelectsLargestTargetWeight() {
        // Basket with 3 assets: 50%, 30%, 20%
        val basket = listOf(
            BasketHolding("VOO", mintA, 5000),
            BasketHolding("AAPL", mintB, 3000),
            BasketHolding("NVDA", mintC, 2000)
        )
        val holdings = emptyMap<PublicKey, HoldingState>()
        val buyAmount = Money.parseUsdc("5.00")

        val decision = AllocationEngine.selectStock(basket, holdings, buyAmount)
        // With 0 holdings, VOO target is $2.50, AAPL is $1.50, NVDA is $1.00 -> VOO has largest deficit
        assertEquals("VOO", decision.selectedHolding.symbol)
        assertEquals(Money.parseUsdc("2.50"), decision.reason.deficits["VOO"])
    }

    @Test
    fun testSelectsUnderweightStock() {
        // Basket: VOO (50%), AAPL (50%)
        // Current: VOO worth $100, AAPL worth $50 -> AAPL is underweight!
        val basket = listOf(
            BasketHolding("VOO", mintA, 5000),
            BasketHolding("AAPL", mintB, 5000)
        )
        val holdings = mapOf(
            mintA to HoldingState(mintA, rawBalance = 1_000_000L, priceInMicroUsdc = 100_000_000L), // $100
            mintB to HoldingState(mintB, rawBalance = 500_000L, priceInMicroUsdc = 100_000_000L)   // $50
        )
        val buyAmount = Money.parseUsdc("5.00") // Total V+A = 155. Target each = 77.50.
        // VOO current $100 > target $77.50 -> deficit = 0
        // AAPL current $50 < target $77.50 -> deficit = $27.50

        val decision = AllocationEngine.selectStock(basket, holdings, buyAmount)
        assertEquals("AAPL", decision.selectedHolding.symbol)
        assertEquals(Money.parseUsdc("27.50"), decision.reason.deficits["AAPL"])
        assertEquals(Money.ZERO, decision.reason.deficits["VOO"])
    }

    @Test
    fun testTieBreakingByLargerTargetWeight() {
        // Both have equal deficits, but A has 60% weight, B has 40% weight
        val basket = listOf(
            BasketHolding("STOCK_A", mintA, 6000),
            BasketHolding("STOCK_B", mintB, 4000)
        )
        // Set holdings so deficits are both $5.00
        val holdings = mapOf(
            mintA to HoldingState(mintA, rawBalance = 1_000_000L, priceInMicroUsdc = 55_000_000L),
            mintB to HoldingState(mintB, rawBalance = 1_000_000L, priceInMicroUsdc = 35_000_000L)
        )
        // V = 55 + 35 = 90. Buy = 10. V + A = 100.
        // Target A: 60% of 100 = 60. Current 55 -> Deficit = 5.
        // Target B: 40% of 100 = 40. Current 35 -> Deficit = 5.
        // Tie! Stock A has 60% vs Stock B 40% -> picks Stock A.
        val decision = AllocationEngine.selectStock(basket, holdings, Money.parseUsdc("10.00"))
        assertEquals("STOCK_A", decision.selectedHolding.symbol)
    }

    @Test
    fun testSkipsUnbuyableHoldings() {
        val basket = listOf(
            BasketHolding("UNBUYABLE", mintA, 7000, isBuyable = false, unbuyableReason = "Market closed"),
            BasketHolding("BUYABLE", mintB, 3000, isBuyable = true)
        )
        val decision = AllocationEngine.selectStock(basket, emptyMap(), Money.parseUsdc("5.00"))
        assertEquals("BUYABLE", decision.selectedHolding.symbol)
        assertTrue(decision.reason.skippedAssets.containsKey("UNBUYABLE"))
    }

    @Test(expected = IllegalStateException::class)
    fun testAllIneligibleThrows() {
        val basket = listOf(
            BasketHolding("A", mintA, 5000, isBuyable = false),
            BasketHolding("B", mintB, 5000, isBuyable = false)
        )
        AllocationEngine.selectStock(basket, emptyMap(), Money.parseUsdc("5.00"))
    }

    @Test
    fun testScaledUiMultiplier() {
        // Holding with scaled UI multiplier of 2x
        val state = HoldingState(
            mint = mintA,
            rawBalance = 1_000_000L, // 1 whole unit
            priceInMicroUsdc = 50_000_000L, // $50
            decimals = 6,
            scaledUiMultiplier = 2L
        )
        // 50 * 1 * 2 = $100
        assertEquals(100_000_000L, state.calculateValue())
    }

    @Test
    fun testOneAssetBasket() {
        val basket = listOf(BasketHolding("SOLO", mintA, 10000))
        val decision = AllocationEngine.selectStock(basket, emptyMap(), Money.parseUsdc("5.00"))
        assertEquals("SOLO", decision.selectedHolding.symbol)
    }

    @Test
    fun testPropertyRandomizedInvariants() {
        val random = Random(999)
        val basket = listOf(
            BasketHolding("A", mintA, 4000),
            BasketHolding("B", mintB, 3500),
            BasketHolding("C", mintC, 2500)
        )

        for (i in 0 until 500) {
            val holdings = mapOf(
                mintA to HoldingState(mintA, random.nextLong(0, 10_000_000L), 100_000_000L),
                mintB to HoldingState(mintB, random.nextLong(0, 10_000_000L), 150_000_000L),
                mintC to HoldingState(mintC, random.nextLong(0, 10_000_000L), 200_000_000L)
            )
            val buyAmount = Money.fromDollars(random.nextLong(1, 25))

            val decision = AllocationEngine.selectStock(basket, holdings, buyAmount)
            assertNotNull(decision.selectedHolding)
            assertTrue(basket.contains(decision.selectedHolding))
            assertTrue(decision.selectedHolding.isBuyable)
        }
    }
}
