package com.jiro.core

import org.junit.Assert.*
import org.junit.Test

class BasketValidatorTest {

    private val mint1 = PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")
    private val mint2 = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
    private val mint3 = PublicKey("2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a")

    @Test
    fun testValidBasket() {
        val basket = listOf(
            BasketHolding("A", mint1, 5000), // 50%
            BasketHolding("B", mint2, 3000), // 30%
            BasketHolding("C", mint3, 2000)  // 20%
        )
        BasketValidator.validate(basket) // should not throw
    }

    @Test(expected = BasketValidationError.EmptyBasket::class)
    fun testEmptyBasketFails() {
        BasketValidator.validate(emptyList())
    }

    @Test(expected = BasketValidationError.TooManyHoldings::class)
    fun testTooManyHoldingsFails() {
        val basket = (1..6).map {
            BasketHolding("S$it", PublicKey("1111111111111111111111111111111$it"), 1500)
        }
        BasketValidator.validate(basket)
    }

    @Test(expected = BasketValidationError.InvalidSum::class)
    fun testInvalidSumFails() {
        val basket = listOf(
            BasketHolding("A", mint1, 5000),
            BasketHolding("B", mint2, 4000) // sums to 9000 != 10000
        )
        BasketValidator.validate(basket)
    }

    @Test(expected = BasketValidationError.BelowMinimumWeight::class)
    fun testBelowMinimumWeightFails() {
        val basket = listOf(
            BasketHolding("A", mint1, 9500),
            BasketHolding("B", mint2, 500) // 5% < 10% minimum
        )
        BasketValidator.validate(basket)
    }

    @Test(expected = BasketValidationError.InvalidStep::class)
    fun testInvalidStepFails() {
        val basket = listOf(
            BasketHolding("A", mint1, 5300), // not a multiple of 500 bps (5%)
            BasketHolding("B", mint2, 4700)
        )
        BasketValidator.validate(basket)
    }

    @Test
    fun testPresetResolution() {
        val catalog = listOf(
            CatalogEntry(
                symbol = "AAPL.US",
                name = "Apple",
                mint = mint1,
                tokenProgram = PublicKey.TOKEN_2022_PROGRAM_ID,
                decimals = 6,
                extensions = emptyList(),
                extensionFingerprint = "fp1",
                buyable = BuyableStatus(true, emptyList()),
                policy = PolicyStatus("WARN", emptyList(), emptyList()),
                ataRentLamports = 2157600L,
                marketStatus = "open"
            ),
            CatalogEntry(
                symbol = "MSFT.US",
                name = "Microsoft",
                mint = mint2,
                tokenProgram = PublicKey.TOKEN_2022_PROGRAM_ID,
                decimals = 6,
                extensions = emptyList(),
                extensionFingerprint = "fp2",
                buyable = BuyableStatus(true, emptyList()),
                policy = PolicyStatus("WARN", emptyList(), emptyList()),
                ataRentLamports = 2157600L,
                marketStatus = "open"
            )
        )

        val presetTickers = listOf("AAPL", "MSFT", "UNKNOWN_TICKER")
        val result = BasketValidator.resolvePreset(presetTickers, catalog)

        assertEquals(2, result.holdings.size)
        assertEquals(1, result.droppedTickers.size)
        assertTrue(result.droppedTickers.containsKey("UNKNOWN_TICKER"))

        // Check assigned weights sum to 10,000 bps
        val sum = result.holdings.sumOf { it.targetWeightBps }
        assertEquals(10_000, sum)
        assertEquals(5000, result.holdings[0].targetWeightBps)
        assertEquals(5000, result.holdings[1].targetWeightBps)
    }
}
