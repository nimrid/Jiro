package com.jiro.app

import android.content.Context
import android.content.SharedPreferences
import com.jiro.core.ActivityItem
import com.jiro.core.ActivityType
import com.jiro.core.Money
import org.json.JSONArray
import org.json.JSONObject

class ActivityRepository(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("jiro_activity", Context.MODE_PRIVATE)
    }

    fun getActivitiesForWallet(walletAddress: String): List<ActivityItem> {
        val rawJson = prefs.getString("activity_$walletAddress", null) ?: return emptyList()
        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<ActivityItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val typeStr = obj.optString("type", ActivityType.ROUND_UP_DEPOSIT.name)
                val type = try { ActivityType.valueOf(typeStr) } catch (_: Exception) { ActivityType.ROUND_UP_DEPOSIT }
                val amountRaw = obj.optLong("amountRaw", 0L)
                val sig = obj.optString("signature", "")
                val stockSymbol = if (obj.has("stockSymbol") && !obj.isNull("stockSymbol")) obj.getString("stockSymbol") else null
                val ts = obj.optLong("timestampMs", System.currentTimeMillis())
                val status = obj.optString("status", "Confirmed")

                list.add(
                    ActivityItem(
                        signature = sig,
                        type = type,
                        amount = Money(amountRaw),
                        stockSymbol = stockSymbol,
                        timestampMs = ts,
                        status = status
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveActivityForWallet(walletAddress: String, item: ActivityItem) {
        val currentList = getActivitiesForWallet(walletAddress).toMutableList()
        // Deduplicate by signature
        if (item.signature.isNotBlank()) {
            currentList.removeAll { it.signature == item.signature }
        }
        val updated = listOf(item) + currentList.take(49)
        saveList(walletAddress, updated)
    }

    fun saveActivitiesForWallet(walletAddress: String, items: List<ActivityItem>) {
        val currentList = getActivitiesForWallet(walletAddress).toMutableList()
        val combined = (items + currentList).distinctBy { it.signature }.sortedByDescending { it.timestampMs }.take(50)
        saveList(walletAddress, combined)
    }

    private fun saveList(walletAddress: String, items: List<ActivityItem>) {
        try {
            val arr = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("signature", item.signature)
                    put("type", item.type.name)
                    put("amountRaw", item.amount.baseUnits)
                    if (item.stockSymbol != null) {
                        put("stockSymbol", item.stockSymbol)
                    }
                    put("timestampMs", item.timestampMs)
                    put("status", item.status)
                }
                arr.put(obj)
            }
            prefs.edit().putString("activity_$walletAddress", arr.toString()).apply()
        } catch (_: Exception) {}
    }
}
