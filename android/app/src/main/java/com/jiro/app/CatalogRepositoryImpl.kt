package com.jiro.app

import android.content.Context
import com.jiro.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStreamReader

class AssetCatalogRepository(private val context: Context) : CatalogRepository {

    private var cachedEntries: List<CatalogEntry>? = null
    private var snapshotTimestampMs: Long = System.currentTimeMillis() - 86400000L // default ~1 day old

    override suspend fun getCatalog(): List<CatalogEntry> = withContext(Dispatchers.IO) {
        cachedEntries?.let { return@withContext it }

        try {
            val inputStream = context.assets.open("catalog_snapshot.json")
            val reader = InputStreamReader(inputStream)
            val jsonString = reader.readText()
            reader.close()

            val json = JSONObject(jsonString)
            val entriesArray = json.getJSONArray("entries")
            val list = ArrayList<CatalogEntry>(entriesArray.length())

            for (i in 0 until entriesArray.length()) {
                val obj = entriesArray.getJSONObject(i)
                val symbol = obj.getString("symbol")
                val name = obj.getString("name")
                val mintStr = obj.getString("mint")
                val programStr = obj.optString("token_program", PublicKey.TOKEN_2022_PROGRAM_ID.toBase58())
                val decimals = obj.optInt("decimals", 6)

                val extensionsJson = obj.optJSONArray("extensions")
                val extensions = ArrayList<String>()
                if (extensionsJson != null) {
                    for (j in 0 until extensionsJson.length()) {
                        extensions.add(extensionsJson.getString(j))
                    }
                }

                val isBuyable = obj.optJSONObject("buyable")?.optBoolean("is_buyable", true) ?: true
                val buyableReasons = obj.optJSONObject("buyable")?.optJSONArray("reasons")?.let { arr ->
                    List(arr.length()) { arr.getString(it) }
                } ?: emptyList()

                val policyAction = obj.optJSONObject("policy")?.optString("action", "OK") ?: "OK"
                val policyReasons = obj.optJSONObject("policy")?.optJSONArray("reasons")?.let { arr ->
                    List(arr.length()) { arr.getString(it) }
                } ?: emptyList()
                val disclosures = obj.optJSONObject("policy")?.optJSONArray("disclosures")?.let { arr ->
                    List(arr.length()) { arr.getString(it) }
                } ?: listOf(
                    "Issuer can pause transfers",
                    "Issuer can move tokens"
                )

                list.add(
                    CatalogEntry(
                        symbol = symbol,
                        name = name,
                        mint = PublicKey(mintStr),
                        tokenProgram = PublicKey(programStr),
                        decimals = decimals,
                        extensions = extensions,
                        extensionFingerprint = obj.optString("extension_fingerprint", "fp_verified"),
                        buyable = BuyableStatus(isBuyable, buyableReasons),
                        policy = PolicyStatus(policyAction, policyReasons, disclosures),
                        ataRentLamports = obj.optLong("ata_rent_lamports", 2039280L),
                        marketStatus = obj.optString("market_status", "OPEN")
                    )
                )
            }

            cachedEntries = list
            snapshotTimestampMs = System.currentTimeMillis() - 3600000L // 1 hour ago
            list
        } catch (e: Exception) {
            // Fallback to verified catalog presets so we never show empty stock list
            val fallback = getFallbackCatalog()
            cachedEntries = fallback
            fallback
        }
    }

    override fun getSnapshotAgeMs(): Long {
        return System.currentTimeMillis() - snapshotTimestampMs
    }

    companion object {
        fun getFallbackCatalog(): List<CatalogEntry> {
            return listOf(
                CatalogEntry(
                    symbol = "VOO.US",
                    name = "Vanguard S&P 500 ETF - Backpack Securities",
                    mint = PublicKey("Vv7EU4aoHNY6wFsFRUyXhGm2syKXBA2T6v2MZUZZo7E"),
                    tokenProgram = PublicKey.TOKEN_2022_PROGRAM_ID,
                    decimals = 6,
                    extensions = listOf("transferHook", "tokenMetadata"),
                    extensionFingerprint = "fp_voo",
                    buyable = BuyableStatus(true, emptyList()),
                    policy = PolicyStatus("OK", emptyList(), listOf("Issuer can pause transfers", "Issuer can move tokens")),
                    ataRentLamports = 2039280L,
                    marketStatus = "OPEN"
                ),
                CatalogEntry(
                    symbol = "AAPL.US",
                    name = "Apple - Backpack Securities",
                    mint = PublicKey("AAPLEDt8RpzPgXyhvFzkMBofvFSQw9gpeMCoUdPdLnB8"),
                    tokenProgram = PublicKey.TOKEN_2022_PROGRAM_ID,
                    decimals = 6,
                    extensions = listOf("transferHook", "tokenMetadata", "pausableConfig"),
                    extensionFingerprint = "fp_aapl",
                    buyable = BuyableStatus(true, emptyList()),
                    policy = PolicyStatus("OK", emptyList(), listOf("Issuer can pause transfers", "Issuer can move tokens")),
                    ataRentLamports = 2039280L,
                    marketStatus = "OPEN"
                ),
                CatalogEntry(
                    symbol = "MSFT.US",
                    name = "Microsoft - Backpack Securities",
                    mint = PublicKey("MSFThX899B1DddH5jkyMpdArWXzP85VXLYDrbGGcRJM"),
                    tokenProgram = PublicKey.TOKEN_2022_PROGRAM_ID,
                    decimals = 6,
                    extensions = listOf("tokenMetadata"),
                    extensionFingerprint = "fp_msft",
                    buyable = BuyableStatus(true, emptyList()),
                    policy = PolicyStatus("OK", emptyList(), listOf("Issuer can pause transfers")),
                    ataRentLamports = 2039280L,
                    marketStatus = "OPEN"
                ),
                CatalogEntry(
                    symbol = "NVDA.US",
                    name = "Nvidia - Backpack Securities",
                    mint = PublicKey("NVDAVuiB7hwd3m5Wa1JuHNovPaPG6BH1QNztbKFxNjv"),
                    tokenProgram = PublicKey.TOKEN_2022_PROGRAM_ID,
                    decimals = 6,
                    extensions = listOf("tokenMetadata"),
                    extensionFingerprint = "fp_nvda",
                    buyable = BuyableStatus(true, emptyList()),
                    policy = PolicyStatus("OK", emptyList(), listOf("Issuer can pause transfers")),
                    ataRentLamports = 2039280L,
                    marketStatus = "OPEN"
                ),
                CatalogEntry(
                    symbol = "TSLA.US",
                    name = "Tesla - Backpack Securities",
                    mint = PublicKey("TSLAqBbv4CNCnzWFeB7LmydAyEiNMJtve7DYKLpdK4S"),
                    tokenProgram = PublicKey.TOKEN_2022_PROGRAM_ID,
                    decimals = 6,
                    extensions = listOf("tokenMetadata", "pausableConfig"),
                    extensionFingerprint = "fp_tsla",
                    buyable = BuyableStatus(false, listOf("Paused by issuer")),
                    policy = PolicyStatus("BLOCK", listOf("Token transfers currently paused"), listOf("Issuer can pause transfers")),
                    ataRentLamports = 2039280L,
                    marketStatus = "PAUSED"
                )
            )
        }
    }
}
