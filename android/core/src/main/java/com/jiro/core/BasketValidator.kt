package com.jiro.core

sealed class BasketValidationError(message: String) : Exception(message) {
    class TooManyHoldings(message: String) : BasketValidationError(message)
    class EmptyBasket(message: String) : BasketValidationError(message)
    class InvalidSum(message: String) : BasketValidationError(message)
    class BelowMinimumWeight(message: String) : BasketValidationError(message)
    class InvalidStep(message: String) : BasketValidationError(message)
    class DuplicateHolding(message: String) : BasketValidationError(message)
}

data class PresetResolutionResult(
    val holdings: List<BasketHolding>,
    val droppedTickers: Map<String, String>
)

object BasketValidator {
    const val MAX_HOLDINGS = 5
    const val MIN_WEIGHT_BPS = 1000 // 10%
    const val STEP_BPS = 500        // 5%
    const val TARGET_SUM_BPS = 10_000 // 100%

    /**
     * Validates a custom or preset user basket according to safety and allocation constraints.
     */
    fun validate(holdings: List<BasketHolding>) {
        if (holdings.isEmpty()) {
            throw BasketValidationError.EmptyBasket("Basket must contain at least 1 holding")
        }
        if (holdings.size > MAX_HOLDINGS) {
            throw BasketValidationError.TooManyHoldings(
                "Basket exceeds maximum of $MAX_HOLDINGS holdings (got ${holdings.size})"
            )
        }

        val seenMints = mutableSetOf<PublicKey>()
        var sumBps = 0

        for (holding in holdings) {
            if (!seenMints.add(holding.mint)) {
                throw BasketValidationError.DuplicateHolding("Duplicate mint in basket: ${holding.symbol}")
            }

            if (holding.targetWeightBps < MIN_WEIGHT_BPS) {
                throw BasketValidationError.BelowMinimumWeight(
                    "${holding.symbol} weight ${holding.targetWeightBps / 100}% is below minimum ${MIN_WEIGHT_BPS / 100}%"
                )
            }

            if (holding.targetWeightBps % STEP_BPS != 0) {
                throw BasketValidationError.InvalidStep(
                    "${holding.symbol} weight ${holding.targetWeightBps / 100}% is not a multiple of ${STEP_BPS / 100}%"
                )
            }

            sumBps += holding.targetWeightBps
        }

        if (sumBps != TARGET_SUM_BPS) {
            throw BasketValidationError.InvalidSum(
                "Basket weights must sum to $TARGET_SUM_BPS bps (100%), got $sumBps bps"
            )
        }
    }

    /**
     * Resolves preset tickers against a catalog.
     * Takes up to MAX_HOLDINGS, drops missing/ambiguous tickers with reasons, and assigns equal weights.
     */
    fun resolvePreset(
        presetTickers: List<String>,
        catalog: List<CatalogEntry>
    ): PresetResolutionResult {
        val resolved = mutableListOf<BasketHolding>()
        val dropped = mutableMapOf<String, String>()

        for (ticker in presetTickers) {
            if (resolved.size == MAX_HOLDINGS) {
                dropped[ticker] = "Dropped because basket is already full ($MAX_HOLDINGS holdings)"
                continue
            }

            // Find matching catalog entry (check symbol e.g. "AAPL.US" or ticker "AAPL")
            val matches = catalog.filter {
                it.symbol.equals(ticker, ignoreCase = true) ||
                it.ticker.equals(ticker, ignoreCase = true)
            }

            when {
                matches.isEmpty() -> {
                    dropped[ticker] = "Ticker not found in verified catalog"
                }
                matches.size > 1 -> {
                    dropped[ticker] = "Ambiguous ticker matches multiple mints (${matches.size})"
                }
                else -> {
                    val entry = matches[0]
                    resolved.add(
                        BasketHolding(
                            symbol = entry.symbol,
                            mint = entry.mint,
                            targetWeightBps = 0, // will assign below
                            isBuyable = entry.buyable.isBuyable,
                            unbuyableReason = entry.buyable.reasons.firstOrNull()
                        )
                    )
                }
            }
        }

        if (resolved.isEmpty()) {
            return PresetResolutionResult(emptyList(), dropped)
        }

        // Evenly distribute 10,000 bps in multiples of 500 bps
        val count = resolved.size
        val baseWeight = ((TARGET_SUM_BPS / count) / STEP_BPS) * STEP_BPS
        val remainder = TARGET_SUM_BPS - (baseWeight * count)
        val remainderSteps = remainder / STEP_BPS

        val weightedHoldings = resolved.mapIndexed { index, holding ->
            val extra = if (index < remainderSteps) STEP_BPS else 0
            holding.copy(targetWeightBps = baseWeight + extra)
        }

        return PresetResolutionResult(
            holdings = weightedHoldings,
            droppedTickers = dropped
        )
    }
}
