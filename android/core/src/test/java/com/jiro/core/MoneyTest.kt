package com.jiro.core

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {

    @Test
    fun testBaseUnitsAndDollars() {
        val m = Money(33_500_000L)
        assertEquals(33L, m.dollars)
        assertEquals(50L, m.cents)
        assertEquals(33_500_000L, m.microUsdc)
        assertEquals("$33.50", m.formatUsdc(includeSymbol = true))
        assertEquals("33.50", m.formatUsdc(includeSymbol = false))
    }

    @Test
    fun testParsingValidStrings() {
        assertEquals(Money(33_500_000L), Money.parseUsdc("33.50"))
        assertEquals(Money(33_500_000L), Money.parseUsdc("$33.50"))
        assertEquals(Money(33_010_000L), Money.parseUsdc("33.01"))
        assertEquals(Money(33_000_000L), Money.parseUsdc("33.00"))
        assertEquals(Money(33_000_000L), Money.parseUsdc("33"))
        assertEquals(Money(10_000L), Money.parseUsdc("0.01"))
        assertEquals(Money(1L), Money.parseUsdc("0.000001"))
        assertEquals(Money(0L), Money.parseUsdc("0"))
        assertEquals(Money(0L), Money.parseUsdc("0.00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectNegative() {
        Money.parseUsdc("-1.00")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectScientificNotation() {
        Money.parseUsdc("1e6")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectTooManyDecimals() {
        Money.parseUsdc("0.1234567")
    }

    @Test
    fun testArithmeticOperations() {
        val a = Money(10_000_000L) // $10.00
        val b = Money(5_500_000L)  // $5.50

        assertEquals(Money(15_500_000L), a + b)
        assertEquals(Money(4_500_000L), a - b)
        assertEquals(Money(20_000_000L), a * 2L)
        assertEquals(Money(5_000_000L), a / 2L)
        assertEquals(Money(1_000_000L), Money(7_000_000L) % 3_000_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSubtractionUnderflow() {
        val a = Money(5_000_000L)
        val b = Money(10_000_000L)
        val _res = a - b
    }
}
