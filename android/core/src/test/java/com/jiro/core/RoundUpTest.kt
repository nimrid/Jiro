package com.jiro.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RoundUpTest {

    @Test
    fun testRequiredCases() {
        // 33.50 -> 0.50
        val r1 = RoundUpCalculator.calculate(Money.parseUsdc("33.50"))
        assertEquals(Money.parseUsdc("0.50"), r1)

        // 33.01 -> 0.99
        val r2 = RoundUpCalculator.calculate(Money.parseUsdc("33.01"))
        assertEquals(Money.parseUsdc("0.99"), r2)

        // 33.00 -> 0 (default)
        val r3 = RoundUpCalculator.calculate(Money.parseUsdc("33.00"))
        assertEquals(Money.ZERO, r3)

        // 33.00 with roundUpExactWhole = true -> 1.00
        val r3Whole = RoundUpCalculator.calculate(
            Money.parseUsdc("33.00"),
            RoundUpCalculator.Config(roundUpExactWhole = true)
        )
        assertEquals(Money.ONE_DOLLAR, r3Whole)

        // 0.01 -> 0.99
        val r4 = RoundUpCalculator.calculate(Money.parseUsdc("0.01"))
        assertEquals(Money.parseUsdc("0.99"), r4)
    }

    @Test
    fun testTwoDollarStepWithBoost() {
        // 33.50 with step 2.00, boost 2x
        // 33.50 % 2.00 = 1.50 -> raw roundUp = 0.50 -> 2x boost = 1.00
        val r = RoundUpCalculator.calculate(
            paymentAmount = Money.parseUsdc("33.50"),
            config = RoundUpCalculator.Config(
                step = Money.fromDollars(2L),
                multiplier = 2
            )
        )
        assertEquals(Money.fromDollars(1L), r)
    }

    @Test
    fun testClampToJarCapacity() {
        // 33.50 -> raw 0.50 -> remaining capacity is 0.30 -> clamped to 0.30
        val r = RoundUpCalculator.calculate(
            paymentAmount = Money.parseUsdc("33.50"),
            jarRemainingCapacity = Money.parseUsdc("0.30")
        )
        assertEquals(Money.parseUsdc("0.30"), r)

        // Remaining capacity is 0 -> clamped to 0
        val rZero = RoundUpCalculator.calculate(
            paymentAmount = Money.parseUsdc("33.50"),
            jarRemainingCapacity = Money.ZERO
        )
        assertEquals(Money.ZERO, rZero)
    }

    @Test
    fun testSkipBelowOneCent() {
        // Payment has remainder 999_999 microUsdc -> raw round-up is 1 microUsdc (0.000001)
        val amount = Money(999_999L)
        val r = RoundUpCalculator.calculate(
            paymentAmount = amount,
            config = RoundUpCalculator.Config(skipBelowOneCent = true)
        )
        assertEquals(Money.ZERO, r)

        // If skipBelowOneCent is false, it returns the 1 microUsdc
        val rNoSkip = RoundUpCalculator.calculate(
            paymentAmount = amount,
            config = RoundUpCalculator.Config(skipBelowOneCent = false)
        )
        assertEquals(Money(1L), rNoSkip)
    }

    @Test
    fun testPropertyInvariants() {
        val random = Random(42)
        val step = Money.fromDollars(1L)
        val config = RoundUpCalculator.Config(step = step, multiplier = 1, roundUpExactWhole = false)

        for (i in 0 until 1000) {
            // Real-world payments are multiples of 1 cent (10_000 microUsdc)
            val cents = random.nextLong(1L, 50_000L)
            val amount = Money.fromCents(cents)
            val roundUp = RoundUpCalculator.calculate(amount, config)

            // Result in [0, step * mult]
            assertTrue("Round-up must be >= 0", roundUp >= Money.ZERO)
            assertTrue("Round-up must be <= step", roundUp <= step)

            // Divisibility: (amount + roundUp) % step == 0 when mult == 1
            if (roundUp > Money.ZERO) {
                val total = amount + roundUp
                val remainder = total.baseUnits % step.baseUnits
                assertEquals(
                    "Sum must be divisible by step ($amount + $roundUp = $total)",
                    0L,
                    remainder
                )
            } else {
                // If roundUp is 0, original amount was already an exact step
                assertEquals(0L, amount.baseUnits % step.baseUnits)
            }
        }
    }

    @Test
    fun testPropertyInvariantsWithBoost() {
        val random = Random(123)
        for (mult in 1..5) {
            val step = Money.fromDollars(2L)
            val config = RoundUpCalculator.Config(step = step, multiplier = mult)

            for (i in 0 until 200) {
                val amountBaseUnits = random.nextLong(10_000L, 100_000_000L)
                val amount = Money(amountBaseUnits)
                val roundUp = RoundUpCalculator.calculate(amount, config)

                assertTrue("Round up >= 0", roundUp >= Money.ZERO)
                assertTrue("Round up <= step * mult", roundUp.baseUnits <= step.baseUnits * mult)
            }
        }
    }
}
