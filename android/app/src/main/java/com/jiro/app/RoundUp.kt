package com.jiro.app

/**
 * Pure integer base-unit calculations for USDC (6 decimals).
 * $1.00 = 1_000_000 base units.
 * Never uses floating-point arithmetic for financial amounts.
 */
object RoundUp {
    const val ONE_DOLLAR_BASE_UNITS: Long = 1_000_000L

    /**
     * Calculates the round-up amount in base units (USDC 6 decimals).
     *
     * @param paymentBaseUnits Amount in 6-decimal integer units (e.g. 33_500_000 for $33.50).
     * @param roundWholeDollarUp If true, an exact whole dollar rounds up by $1.00; otherwise $0.00.
     * @return The round-up amount in base units.
     */
    fun calculate(paymentBaseUnits: Long, roundWholeDollarUp: Boolean = false): Long {
        require(paymentBaseUnits > 0L) { "Payment amount must be greater than zero" }

        val remainder = paymentBaseUnits % ONE_DOLLAR_BASE_UNITS
        return if (remainder == 0L) {
            if (roundWholeDollarUp) ONE_DOLLAR_BASE_UNITS else 0L
        } else {
            ONE_DOLLAR_BASE_UNITS - remainder
        }
    }

    /**
     * Formats integer base units (6 decimals) into a dollar string, e.g. "33.50".
     */
    fun formatUsdc(baseUnits: Long): String {
        require(baseUnits >= 0L) { "Amount cannot be negative" }
        val whole = baseUnits / ONE_DOLLAR_BASE_UNITS
        val fractional = baseUnits % ONE_DOLLAR_BASE_UNITS
        // USDC has 6 decimals, but standard display shows 2 decimal cents
        val cents = fractional / 10_000L
        return String.format(java.util.Locale.US, "%d.%02d", whole, cents)
    }

    /**
     * Parses a dollar string like "33.50" into integer base units (33_500_000L).
     * Uses strict string parsing without floats.
     */
    fun parseUsdcToRaw(input: String): Long {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "Amount cannot be empty" }

        val parts = trimmed.split(".")
        require(parts.size <= 2) { "Invalid amount format: $input" }

        val wholePart = parts[0].toLongOrNull() ?: throw IllegalArgumentException("Invalid whole part: ${parts[0]}")
        require(wholePart >= 0) { "Amount cannot be negative" }

        val fractionalPart = if (parts.size == 2) {
            val fracStr = parts[1]
            require(fracStr.length <= 6) { "Too many decimal places (max 6): $fracStr" }
            val padded = fracStr.padEnd(6, '0')
            padded.toLongOrNull() ?: throw IllegalArgumentException("Invalid fractional part: $fracStr")
        } else {
            0L
        }

        return (wholePart * ONE_DOLLAR_BASE_UNITS) + fractionalPart
    }
}
