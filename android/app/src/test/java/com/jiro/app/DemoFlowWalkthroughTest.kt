package com.jiro.app

import com.jiro.app.components.TreemapTile
import com.jiro.app.screens.PRESET_STYLES
import com.jiro.core.*
import org.junit.Assert.*
import org.junit.Test

class DemoFlowWalkthroughTest {

    @Test
    fun testOnboardingPresetStyles() {
        assertEquals(3, PRESET_STYLES.size)
        val tech = PRESET_STYLES.find { it.id == "tech" }
        assertNotNull(tech)
        assertTrue(tech!!.tickers.contains("AAPL"))
        assertTrue(tech.tickers.contains("MSFT"))
        assertTrue(tech.tickers.contains("NVDA"))

        val index = PRESET_STYLES.find { it.id == "index" }
        assertNotNull(index)
        assertTrue(index!!.tickers.contains("VOO"))
    }

    @Test
    fun testOfflineDemoFlowWalkthrough() {
        // Step 1: Initial state
        var userUsdcBalance = 42_500_000L // $42.50
        var jarBalance = 3_500_000L       // $3.50
        val threshold = 5_000_000L        // $5.00

        assertFalse("Jar not full yet", jarBalance >= threshold)

        // Step 2: User makes a payment of $4.35 -> $0.65 round-up
        val paymentRaw = RoundUp.parseUsdcToRaw("4.35") // 4_350_000
        val roundUpRaw = RoundUp.calculate(paymentRaw, false) // 650_000
        assertEquals(4_350_000L, paymentRaw)
        assertEquals(650_000L, roundUpRaw)

        // Deduct from wallet and add to jar
        userUsdcBalance -= (paymentRaw + roundUpRaw)
        jarBalance += roundUpRaw

        assertEquals(37_500_000L, userUsdcBalance) // $37.50
        assertEquals(4_150_000L, jarBalance)       // $4.15

        // Step 3: Another payment of $8.15 -> $0.85 round-up
        val payment2 = RoundUp.parseUsdcToRaw("8.15")
        val roundUp2 = RoundUp.calculate(payment2, false)
        assertEquals(850_000L, roundUp2)

        userUsdcBalance -= (payment2 + roundUp2)
        jarBalance += roundUp2

        assertEquals(5_000_000L, jarBalance) // $5.00!
        assertTrue("Jar reached threshold and is ready to pour", jarBalance >= threshold)

        // Step 4: Allocation engine selects most underweight asset
        val vooMint = PublicKey("Vv7EU4aoHNY6wFsFRUyXhGm2syKXBA2T6v2MZUZZo7E")
        val aaplMint = PublicKey("AAPLEDt8RpzPgXyhvFzkMBofvFSQw9gpeMCoUdPdLnB8")
        val msftMint = PublicKey("MSFThX899B1DddH5jkyMpdArWXzP85VXLYDrbGGcRJM")

        val basket = listOf(
            BasketHolding("VOO.US", vooMint, 4000), // 40% target
            BasketHolding("AAPL.US", aaplMint, 3000), // 30% target
            BasketHolding("MSFT.US", msftMint, 3000)  // 30% target
        )

        val holdings = mapOf(
            vooMint to HoldingState(vooMint, rawBalance = 80_000L, priceInMicroUsdc = 400_000_000L), // $32 value
            aaplMint to HoldingState(aaplMint, rawBalance = 200_000L, priceInMicroUsdc = 180_000_000L), // $36 value
            msftMint to HoldingState(msftMint, rawBalance = 100_000L, priceInMicroUsdc = 350_000_000L) // $35 value
        )

        val decision = AllocationEngine.selectStock(
            basket = basket,
            holdings = holdings,
            buyAmount = Money(jarBalance)
        )

        // VOO target is 40% of ($103 + $5 = $108) = $43.20. Current is $32. Deficit = $11.20 (largest)
        assertEquals("VOO.US", decision.selectedHolding.symbol)
        assertNotNull(decision.reason.explanation)
        assertTrue(decision.reason.explanation.contains("VOO"))

        // Step 5: Execute pour / investment
        val mockSwap = MockSwapProvider()
        // Jar is emptied
        val pouredAmount = jarBalance
        jarBalance = 0L

        assertEquals(0L, jarBalance)
        assertEquals(5_000_000L, pouredAmount)
    }

    @Test
    fun testSkippedStockHandling() {
        val vooMint = PublicKey("Vv7EU4aoHNY6wFsFRUyXhGm2syKXBA2T6v2MZUZZo7E")
        val tslaMint = PublicKey("TSLAqBbv4CNCnzWFeB7LmydAyEiNMJtve7DYKLpdK4S")

        val basket = listOf(
            BasketHolding("TSLA.US", tslaMint, 5000, isBuyable = false, unbuyableReason = "Paused by issuer"),
            BasketHolding("VOO.US", vooMint, 5000, isBuyable = true)
        )

        val holdings = mapOf(
            tslaMint to HoldingState(tslaMint, rawBalance = 0L, priceInMicroUsdc = 200_000_000L),
            vooMint to HoldingState(vooMint, rawBalance = 100_000L, priceInMicroUsdc = 400_000_000L)
        )

        val decision = AllocationEngine.selectStock(
            basket = basket,
            holdings = holdings,
            buyAmount = Money(5_000_000L)
        )

        // TSLA was skipped because isBuyable = false, VOO must be selected
        assertEquals("VOO.US", decision.selectedHolding.symbol)
        assertTrue(decision.reason.skippedAssets.containsKey("TSLA.US"))
        assertEquals("Paused by issuer", decision.reason.skippedAssets["TSLA.US"])
    }
}
