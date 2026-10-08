package com.jiro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoundUpTest {

    @Test
    fun testRoundUp33_50Yields50Cents() {
        // $33.50 = 33_500_000 base units
        val payment = 33_500_000L
        val roundUp = RoundUp.calculate(payment, roundWholeDollarUp = false)
        // Expected: $0.50 = 500_000 base units
        assertEquals(500_000L, roundUp)
    }

    @Test
    fun testRoundUp33_01Yields99Cents() {
        // $33.01 = 33_010_000 base units
        val payment = 33_010_000L
        val roundUp = RoundUp.calculate(payment, roundWholeDollarUp = false)
        // Expected: $0.99 = 990_000 base units
        assertEquals(990_000L, roundUp)
    }

    @Test
    fun testRoundUpWholeDollarDefaultsToZero() {
        // $33.00 = 33_000_000 base units
        val payment = 33_000_000L
        val roundUp = RoundUp.calculate(payment, roundWholeDollarUp = false)
        assertEquals(0L, roundUp)
    }

    @Test
    fun testRoundUpWholeDollarConfigTrueYieldsOneDollar() {
        // $33.00 = 33_000_000 base units
        val payment = 33_000_000L
        val roundUp = RoundUp.calculate(payment, roundWholeDollarUp = true)
        assertEquals(1_000_000L, roundUp)
    }

    @Test
    fun testRoundUpOneCentYields99Cents() {
        // $0.01 = 10_000 base units
        val payment = 10_000L
        val roundUp = RoundUp.calculate(payment, roundWholeDollarUp = false)
        assertEquals(990_000L, roundUp)
    }

    @Test
    fun testZeroPaymentThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            RoundUp.calculate(0L)
        }
    }

    @Test
    fun testNegativePaymentThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            RoundUp.calculate(-100_000L)
        }
    }

    @Test
    fun testFormatBaseUnits() {
        assertEquals("0.50", RoundUp.formatUsdc(500_000L))
        assertEquals("5.00", RoundUp.formatUsdc(5_000_000L))
        assertEquals("33.01", RoundUp.formatUsdc(33_010_000L))
        assertEquals("0.00", RoundUp.formatUsdc(0L))
    }

    @Test
    fun testParseUsdcToRaw() {
        assertEquals(33_500_000L, RoundUp.parseUsdcToRaw("33.50"))
        assertEquals(5_000_000L, RoundUp.parseUsdcToRaw("5"))
        assertEquals(10_000L, RoundUp.parseUsdcToRaw("0.01"))
        assertEquals(1_000_000L, RoundUp.parseUsdcToRaw("1.00"))
    }
}
