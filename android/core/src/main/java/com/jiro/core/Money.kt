package com.jiro.core

/**
 * Represents a USDC monetary amount in integer base units (6 decimals).
 * 1 USDC = 1,000,000 base units (micro-USDC).
 * Floating-point numbers are strictly forbidden.
 */
data class Money(val baseUnits: Long) : Comparable<Money> {
    init {
        require(baseUnits >= 0) { "Money baseUnits cannot be negative: $baseUnits" }
    }

    val dollars: Long get() = baseUnits / BASE_UNITS_PER_DOLLAR
    val cents: Long get() = (baseUnits % BASE_UNITS_PER_DOLLAR) / BASE_UNITS_PER_CENT
    val microUsdc: Long get() = baseUnits

    operator fun plus(other: Money): Money {
        val result = Math.addExact(baseUnits, other.baseUnits)
        return Money(result)
    }

    operator fun minus(other: Money): Money {
        require(baseUnits >= other.baseUnits) {
            "Subtraction would result in negative money: $baseUnits - ${other.baseUnits}"
        }
        return Money(baseUnits - other.baseUnits)
    }

    operator fun times(scalar: Long): Money {
        require(scalar >= 0) { "Scalar multiplier must be non-negative: $scalar" }
        return Money(Math.multiplyExact(baseUnits, scalar))
    }

    operator fun div(divisor: Long): Money {
        require(divisor > 0) { "Divisor must be strictly positive: $divisor" }
        return Money(baseUnits / divisor)
    }

    operator fun rem(divisor: Long): Money {
        require(divisor > 0) { "Divisor must be strictly positive: $divisor" }
        return Money(baseUnits % divisor)
    }

    override fun compareTo(other: Money): Int = baseUnits.compareTo(other.baseUnits)

    /**
     * Formats the amount as standard currency, e.g. "$33.50" or "33.50".
     */
    fun formatUsdc(includeSymbol: Boolean = true): String {
        val whole = baseUnits / BASE_UNITS_PER_DOLLAR
        val fraction = baseUnits % BASE_UNITS_PER_DOLLAR
        // Format to 2 decimal places if remainder is multiple of 10_000, else up to 6 decimals
        val fractionStr = if (fraction % BASE_UNITS_PER_CENT == 0L) {
            String.format("%02d", fraction / BASE_UNITS_PER_CENT)
        } else {
            String.format("%06d", fraction).trimEnd('0').let {
                if (it.length < 2) it.padEnd(2, '0') else it
            }
        }
        val symbol = if (includeSymbol) "$" else ""
        return "$symbol$whole.$fractionStr"
    }

    override fun toString(): String = formatUsdc(includeSymbol = true)

    companion object {
        const val DECIMALS = 6
        const val BASE_UNITS_PER_DOLLAR = 1_000_000L
        const val BASE_UNITS_PER_CENT = 10_000L

        val ZERO = Money(0L)
        val ONE_CENT = Money(BASE_UNITS_PER_CENT)
        val ONE_DOLLAR = Money(BASE_UNITS_PER_DOLLAR)
        val HARD_CAP = Money(100_000_000L) // 100 USDC hard cap

        fun fromDollars(dollars: Long): Money {
            require(dollars >= 0) { "Dollars cannot be negative: $dollars" }
            return Money(Math.multiplyExact(dollars, BASE_UNITS_PER_DOLLAR))
        }

        fun fromCents(cents: Long): Money {
            require(cents >= 0) { "Cents cannot be negative: $cents" }
            return Money(Math.multiplyExact(cents, BASE_UNITS_PER_CENT))
        }

        /**
         * Parses a USDC string representation, e.g. "33.50", "$33.50", "0.01", "100".
         * Rejects floats, exponential notation, negative amounts, or > 6 decimal places.
         */
        fun parseUsdc(input: String): Money {
            val trimmed = input.trim().removePrefix("$").trim()
            require(trimmed.isNotEmpty()) { "Amount string cannot be empty" }
            require(!trimmed.startsWith("-")) { "Negative amounts are not allowed: $input" }
            require(!trimmed.contains("e", ignoreCase = true)) { "Scientific notation is not allowed: $input" }

            val parts = trimmed.split(".")
            require(parts.size <= 2) { "Invalid monetary format: $input" }

            val wholePart = parts[0].ifEmpty { "0" }
            val whole = wholePart.toLongOrNull() ?: throw IllegalArgumentException("Invalid whole number: $wholePart")

            val fraction = if (parts.size == 2) {
                val fracStr = parts[1]
                require(fracStr.length <= DECIMALS) { "More than 6 decimal places: $fracStr" }
                val padded = fracStr.padEnd(DECIMALS, '0')
                padded.toLongOrNull() ?: throw IllegalArgumentException("Invalid fraction: $fracStr")
            } else {
                0L
            }

            val totalBaseUnits = Math.addExact(Math.multiplyExact(whole, BASE_UNITS_PER_DOLLAR), fraction)
            return Money(totalBaseUnits)
        }
    }
}
