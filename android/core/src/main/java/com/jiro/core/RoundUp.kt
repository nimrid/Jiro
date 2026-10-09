package com.jiro.core

/**
 * Calculation rules for spare-change round-up investing on Solana.
 */
object RoundUpCalculator {

    /**
     * Configuration parameters for round-up calculation.
     */
    data class Config(
        val step: Money = Money.ONE_DOLLAR,
        val multiplier: Int = 1,
        val roundUpExactWhole: Boolean = false,
        val skipBelowOneCent: Boolean = true
    ) {
        init {
            require(step > Money.ZERO) { "Round-up step must be strictly positive: $step" }
            require(multiplier >= 1) { "Multiplier must be at least 1: $multiplier" }
        }
    }

    /**
     * Calculates the round-up amount given a payment amount and optional jar remaining capacity.
     *
     * @param paymentAmount The payment amount in USDC base units.
     * @param config The round-up configuration parameters.
     * @param jarRemainingCapacity Optional remaining room in the user's on-chain vault jar.
     * @return The calculated round-up amount as [Money].
     */
    fun calculate(
        paymentAmount: Money,
        config: Config = Config(),
        jarRemainingCapacity: Money? = null
    ): Money {
        val stepUnits = config.step.baseUnits
        val remainder = paymentAmount.baseUnits % stepUnits

        val rawRoundUpUnits = when {
            remainder == 0L -> {
                if (config.roundUpExactWhole) stepUnits else 0L
            }
            else -> stepUnits - remainder
        }

        // Skip if less than 1 cent (0.01 USDC = 10,000 base units)
        if (config.skipBelowOneCent && rawRoundUpUnits in 1 until Money.BASE_UNITS_PER_CENT) {
            return Money.ZERO
        }

        // Apply multiplier / boost
        val boostedUnits = Math.multiplyExact(rawRoundUpUnits, config.multiplier.toLong())

        // Clamp to jar capacity if specified
        val finalUnits = if (jarRemainingCapacity != null) {
            minOf(boostedUnits, jarRemainingCapacity.baseUnits)
        } else {
            boostedUnits
        }

        return Money(finalUnits)
    }
}
