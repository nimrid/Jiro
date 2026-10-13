package com.jiro.app

import android.content.Context
import android.content.SharedPreferences
import com.jiro.app.components.TreemapTile
import org.json.JSONArray
import org.json.JSONObject

class PortfolioRepository(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("jiro_portfolio", Context.MODE_PRIVATE)
    }

    fun getHoldingsForWallet(walletAddress: String): List<TreemapTile>? {
        val rawJson = prefs.getString("holdings_$walletAddress", null) ?: return null
        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<TreemapTile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TreemapTile(
                        ticker = obj.getString("ticker"),
                        name = obj.getString("name"),
                        mint = obj.getString("mint"),
                        units = obj.getDouble("units"),
                        currentWeightBps = obj.getInt("currentWeightBps"),
                        targetWeightBps = obj.getInt("targetWeightBps"),
                        valueUsdc = obj.getDouble("valueUsdc"),
                        contributionsUsdc = obj.getDouble("contributionsUsdc"),
                        isNextBuy = obj.optBoolean("isNextBuy", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            null
        }
    }

    fun getTotalValueUsdc(walletAddress: String): Long? {
        val v = prefs.getLong("total_value_$walletAddress", -1L)
        return if (v >= 0) v else null
    }

    fun getTotalContributionsUsdc(walletAddress: String): Long? {
        val v = prefs.getLong("total_contrib_$walletAddress", -1L)
        return if (v >= 0) v else null
    }

    fun savePortfolioForWallet(
        walletAddress: String,
        holdings: List<TreemapTile>,
        totalValueUsdc: Long,
        totalContributionsUsdc: Long
    ) {
        try {
            val arr = JSONArray()
            holdings.forEach { h ->
                val obj = JSONObject().apply {
                    put("ticker", h.ticker)
                    put("name", h.name)
                    put("mint", h.mint)
                    put("units", h.units)
                    put("currentWeightBps", h.currentWeightBps)
                    put("targetWeightBps", h.targetWeightBps)
                    put("valueUsdc", h.valueUsdc)
                    put("contributionsUsdc", h.contributionsUsdc)
                    put("isNextBuy", h.isNextBuy)
                }
                arr.put(obj)
            }
            prefs.edit()
                .putString("holdings_$walletAddress", arr.toString())
                .putLong("total_value_$walletAddress", totalValueUsdc)
                .putLong("total_contrib_$walletAddress", totalContributionsUsdc)
                .apply()
        } catch (_: Exception) {}
    }
}
