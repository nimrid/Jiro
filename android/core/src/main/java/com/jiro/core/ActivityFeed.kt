package com.jiro.core

enum class ActivityType {
    ROUND_UP_DEPOSIT,
    WITHDRAWAL,
    STOCK_INVESTMENT,
    VAULT_CLOSED
}

data class ActivityItem(
    val signature: String,
    val type: ActivityType,
    val amount: Money,
    val stockSymbol: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val status: String = "Confirmed"
)

object ActivityParser {

    /**
     * Parses Solana transaction log messages to extract vault events.
     */
    fun parseLogEvents(
        signature: String,
        logs: List<String>,
        timestampMs: Long = System.currentTimeMillis()
    ): List<ActivityItem> {
        val items = mutableListOf<ActivityItem>()

        for (log in logs) {
            when {
                log.contains("Instruction: Deposit") || log.contains("Program log: Event: Deposited") -> {
                    // Extract deposited event
                    items.add(
                        ActivityItem(
                            signature = signature,
                            type = ActivityType.ROUND_UP_DEPOSIT,
                            amount = Money.ZERO, // updated if amount parsed
                            timestampMs = timestampMs
                        )
                    )
                }
                log.contains("Instruction: Withdraw") || log.contains("Program log: Event: Withdrawn") -> {
                    items.add(
                        ActivityItem(
                            signature = signature,
                            type = ActivityType.WITHDRAWAL,
                            amount = Money.ZERO,
                            timestampMs = timestampMs
                        )
                    )
                }
                log.contains("Instruction: MockSwap") || log.contains("Instruction: Swap") -> {
                    items.add(
                        ActivityItem(
                            signature = signature,
                            type = ActivityType.STOCK_INVESTMENT,
                            amount = Money.ZERO,
                            timestampMs = timestampMs
                        )
                    )
                }
            }
        }

        return items
    }
}

interface ActivityRepository {
    suspend fun getActivityFeed(): List<ActivityItem>
    suspend fun recordActivity(item: ActivityItem)
}

class InMemoryActivityRepository : ActivityRepository {
    private val activities = mutableListOf<ActivityItem>()

    override suspend fun getActivityFeed(): List<ActivityItem> = activities.toList()

    override suspend fun recordActivity(item: ActivityItem) {
        activities.add(0, item)
    }
}
