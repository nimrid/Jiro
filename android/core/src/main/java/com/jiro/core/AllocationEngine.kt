package com.jiro.core

data class BasketHolding(
    val symbol: String,
    val mint: PublicKey,
    val targetWeightBps: Int, // e.g. 2500 = 25.00%
    val isBuyable: Boolean = true,
    val unbuyableReason: String? = null
)

data class HoldingState(
    val mint: PublicKey,
    val rawBalance: Long,
    val priceInMicroUsdc: Long, // micro-USDC per whole token
    val decimals: Int = 6,
    val scaledUiMultiplier: Long = 1L
) {
    /**
     * Calculates the USD value in base units:
     * value = price * rawBalance * multiplier / (10^decimals)
     */
    fun calculateValue(): Long {
        if (rawBalance == 0L || priceInMicroUsdc == 0L) return 0L
        val divisor = Math.pow(10.0, decimals.toDouble()).toLong()
        val num = (rawBalance.toBigInteger() * priceInMicroUsdc.toBigInteger() * scaledUiMultiplier.toBigInteger())
        return (num / divisor.toBigInteger()).toLong()
    }
}

data class SelectionReason(
    val portfolioValue: Money,
    val buyAmount: Money,
    val targetValues: Map<String, Money>,
    val deficits: Map<String, Money>,
    val skippedAssets: Map<String, String>,
    val explanation: String
)

data class AllocationDecision(
    val selectedHolding: BasketHolding,
    val reason: SelectionReason
)

object AllocationEngine {

    const val BPS_TOTAL = 10_000
    val ONE_DOLLAR_DEFICIT = Money.ONE_DOLLAR

    /**
     * Allocates buy amount A to the most underweight buyable stock in the user's basket.
     */
    fun selectStock(
        basket: List<BasketHolding>,
        holdings: Map<PublicKey, HoldingState>,
        buyAmount: Money
    ): AllocationDecision {
        require(basket.isNotEmpty()) { "Basket cannot be empty" }

        // Compute current portfolio value
        var totalPortfolioValueUnits = 0L
        val currentValuesByMint = mutableMapOf<PublicKey, Long>()

        for (holding in basket) {
            val state = holdings[holding.mint]
            val value = state?.calculateValue() ?: 0L
            currentValuesByMint[holding.mint] = value
            totalPortfolioValueUnits += value
        }

        val portfolioValue = Money(totalPortfolioValueUnits)
        val postBuyTotal = portfolioValue + buyAmount

        val deficits = mutableMapOf<String, Money>()
        val targetValues = mutableMapOf<String, Money>()
        val skipped = mutableMapOf<String, String>()

        val buyableHoldings = mutableListOf<BasketHolding>()

        for (holding in basket) {
            if (!holding.isBuyable) {
                skipped[holding.symbol] = holding.unbuyableReason ?: "Stock is currently not buyable"
                continue
            }
            buyableHoldings.add(holding)

            // target_i = (targetWeightBps * (V + A)) / 10000
            val targetValUnits = (postBuyTotal.baseUnits * holding.targetWeightBps.toLong()) / BPS_TOTAL.toLong()
            targetValues[holding.symbol] = Money(targetValUnits)

            val curVal = currentValuesByMint[holding.mint] ?: 0L
            val deficitUnits = maxOf(0L, targetValUnits - curVal)
            deficits[holding.symbol] = Money(deficitUnits)
        }

        if (buyableHoldings.isEmpty()) {
            throw IllegalStateException("No buyable stocks in basket. All assets skipped: $skipped")
        }

        // Selection criteria:
        // 1. If all deficits < $1, pick largest target weight
        val allDeficitsUnderOneDollar = buyableHoldings.all {
            (deficits[it.symbol] ?: Money.ZERO) < ONE_DOLLAR_DEFICIT
        }

        val selected: BasketHolding = if (allDeficitsUnderOneDollar) {
            buyableHoldings.maxWithOrNull(
                compareBy<BasketHolding> { it.targetWeightBps }
                    .thenBy { deficits[it.symbol] ?: Money.ZERO }
            )!!
        } else {
            // Pick largest deficit, tie-break by larger target weight
            buyableHoldings.maxWithOrNull(
                compareBy<BasketHolding> { deficits[it.symbol] ?: Money.ZERO }
                    .thenBy { it.targetWeightBps }
            )!!
        }

        val explanation = if (allDeficitsUnderOneDollar) {
            "${selected.symbol} was selected because all deficits are under $1, and it has the largest target weight (${selected.targetWeightBps / 100}%)."
        } else {
            "${selected.symbol} has the largest deficit (${deficits[selected.symbol]?.formatUsdc() ?: "$0.00"}) to bring it back to its target weight (${selected.targetWeightBps / 100}%)."
        }

        val reason = SelectionReason(
            portfolioValue = portfolioValue,
            buyAmount = buyAmount,
            targetValues = targetValues,
            deficits = deficits,
            skippedAssets = skipped,
            explanation = explanation
        )

        return AllocationDecision(selectedHolding = selected, reason = reason)
    }
}
