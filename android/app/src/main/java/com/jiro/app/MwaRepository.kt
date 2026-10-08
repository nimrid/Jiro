package com.jiro.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class WalletSession(
    val publicKey: PublicKey,
    val authToken: String,
    val accountLabel: String?
)

class MwaRepository(private val context: Context) {

    private val identityUri = Uri.parse("https://jiro.finance")
    private val iconUri = Uri.parse("https://jiro.finance/icon.png")
    private val identityName = "JIRO Round-Up"
    private val cluster = "devnet"

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "jiro_mwa_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("jiro_mwa_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun isWalletInstalled(): Boolean {
        return LocalAssociationIntentCreator.isWalletEndpointAvailable(context.packageManager)
    }

    fun getCachedPublicKey(): PublicKey? {
        val b58 = prefs.getString("cached_pubkey", null) ?: return null
        return try {
            PublicKey(b58)
        } catch (e: Exception) {
            null
        }
    }

    private fun getCachedAuthToken(): String? {
        return prefs.getString("cached_auth_token", null)
    }

    fun saveSession(session: WalletSession) {
        prefs.edit()
            .putString("cached_pubkey", session.publicKey.toBase58())
            .putString("cached_auth_token", session.authToken)
            .putString("cached_label", session.accountLabel)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove("cached_pubkey")
            .remove("cached_auth_token")
            .remove("cached_label")
            .apply()
    }

    /**
     * Connects to a real Solana wallet installed on device via MWA 2.0.
     * Absolutely NO mock wallets.
     */
    suspend fun connectWallet(activity: Activity): Result<WalletSession> = withContext(Dispatchers.IO) {
        if (!isWalletInstalled()) {
            return@withContext Result.failure(
                IllegalStateException("No MWA-compatible Solana wallet found on this device. Please install Phantom, Solflare, or ensure Seed Vault is active.")
            )
        }

        val scenario = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        try {
            val intent = LocalAssociationIntentCreator.createAssociationIntent(null, scenario.port, scenario.session)
            activity.startActivity(intent)

            val client = scenario.start().get()
            val cachedToken = getCachedAuthToken()

            val authResult = try {
                if (cachedToken != null) {
                    client.reauthorize(identityUri, iconUri, identityName, cachedToken).get()
                } else {
                    client.authorize(identityUri, iconUri, identityName, cluster).get()
                }
            } catch (e: Exception) {
                // Reauthorize might fail if token expired; fallback to fresh authorize
                client.authorize(identityUri, iconUri, identityName, cluster).get()
            }

            val pubkey = PublicKey(authResult.publicKey)
            val session = WalletSession(
                publicKey = pubkey,
                authToken = authResult.authToken,
                accountLabel = authResult.accountLabel
            )
            saveSession(session)
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                scenario.close().get()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Signs and sends a transaction through the connected real wallet.
     * Returns the list of Solana transaction signatures (Base58).
     */
    suspend fun signAndSendTransaction(
        activity: Activity,
        transactionPayload: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isWalletInstalled()) {
            return@withContext Result.failure(
                IllegalStateException("No MWA-compatible Solana wallet found.")
            )
        }

        val scenario = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        try {
            val intent = LocalAssociationIntentCreator.createAssociationIntent(null, scenario.port, scenario.session)
            activity.startActivity(intent)

            val client = scenario.start().get()
            val cachedToken = getCachedAuthToken()

            val authResult = try {
                if (cachedToken != null) {
                    client.reauthorize(identityUri, iconUri, identityName, cachedToken).get()
                } else {
                    client.authorize(identityUri, iconUri, identityName, cluster).get()
                }
            } catch (e: Exception) {
                client.authorize(identityUri, iconUri, identityName, cluster).get()
            }
            saveSession(
                WalletSession(
                    publicKey = PublicKey(authResult.publicKey),
                    authToken = authResult.authToken,
                    accountLabel = authResult.accountLabel
                )
            )

            val signAndSendResult = client.signAndSendTransactions(arrayOf(transactionPayload), null).get()
            val signatures = signAndSendResult.signatures
            if (signatures.isEmpty() || signatures[0].isEmpty()) {
                return@withContext Result.failure(IOException("Wallet returned empty transaction signature"))
            }

            val sigBase58 = Base58.encode(signatures[0])
            Result.success(sigBase58)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                scenario.close().get()
            } catch (ignored: Exception) {}
        }
    }
}
