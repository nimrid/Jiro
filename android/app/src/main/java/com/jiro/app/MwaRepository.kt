package com.jiro.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.DefaultTransactionParams
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jiro.core.Base58
import com.jiro.core.PublicKey
import java.io.IOException

data class WalletSession(
    val publicKey: PublicKey,
    val authToken: String,
    val accountLabel: String?
)

class MwaRepository(private val context: Context) {

    private val identityUri = Uri.parse("https://jiro.finance")
    // Official MWA requirement: iconUri MUST be a relative URI, e.g. "favicon.ico"
    private val iconUri = Uri.parse("favicon.ico")
    private val identityName = "JIRO"

    val walletAdapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = identityUri,
            iconUri = iconUri,
            identityName = identityName
        )
    ).apply {
        blockchain = Solana.Devnet
        @Suppress("DEPRECATION")
        rpcCluster = com.solana.mobilewalletadapter.clientlib.RpcCluster.Devnet
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        // Never fall back to unencrypted prefs — fail loudly if the keystore is broken.
        EncryptedSharedPreferences.create(
            context,
            "jiro_mwa_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getCachedPublicKey(): PublicKey? {
        val b58 = prefs.getString("cached_pubkey", null) ?: return null
        return try {
            PublicKey(b58)
        } catch (e: Exception) {
            null
        }
    }

    fun getCachedAccountLabel(): String? {
        return prefs.getString("cached_label", null)
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
        walletAdapter.authToken = session.authToken
    }

    fun clearSession() {
        prefs.edit()
            .remove("cached_pubkey")
            .remove("cached_auth_token")
            .remove("cached_label")
            .apply()
        walletAdapter.authToken = null
    }

    suspend fun disconnectWallet(sender: ActivityResultSender): Result<Unit> = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) android.util.Log.d("MWA_REPO", "Calling walletAdapter.disconnect(sender)...")
        try {
            walletAdapter.disconnect(sender)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.w("MWA_REPO", "walletAdapter.disconnect error", e)
        } finally {
            clearSession()
        }
        Result.success(Unit)
    }

    /**
     * Connects to installed wallet (e.g. Solflare) via official MWA clientlib-ktx.
     */
    private fun parseSession(authResult: MobileWalletAdapterClient.AuthorizationResult): WalletSession {
        val firstAccount = authResult.accounts?.firstOrNull()
        @Suppress("DEPRECATION")
        val rawKey = firstAccount?.publicKey ?: authResult.publicKey
        @Suppress("DEPRECATION")
        val label = firstAccount?.accountLabel ?: authResult.accountLabel
        return WalletSession(
            publicKey = PublicKey(rawKey),
            authToken = authResult.authToken,
            accountLabel = label
        )
    }

    suspend fun connectWallet(sender: ActivityResultSender): Result<WalletSession> = withContext(Dispatchers.IO) {
        val cachedToken = getCachedAuthToken()
        if (cachedToken != null) {
            walletAdapter.authToken = cachedToken
        }

        if (BuildConfig.DEBUG) android.util.Log.d("MWA_REPO", "Calling walletAdapter.connect(sender)...")
        var result = walletAdapter.connect(sender)

        // If connect failed with a cached token, clear the stale token and retry fresh authorization
        if (result is TransactionResult.Failure && cachedToken != null) {
            if (BuildConfig.DEBUG) android.util.Log.w("MWA_REPO", "Connect with cached token failed (${result.message}). Retrying fresh connection...")
            clearSession()
            result = walletAdapter.connect(sender)
        }

        when (result) {
            is TransactionResult.Success -> {
                if (BuildConfig.DEBUG) android.util.Log.d("MWA_REPO", "walletAdapter.connect Success!")
                val session = parseSession(result.authResult)
                saveSession(session)
                Result.success(session)
            }
            is TransactionResult.NoWalletFound -> {
                if (BuildConfig.DEBUG) android.util.Log.e("MWA_REPO", "walletAdapter.connect NoWalletFound: ${result.message}")
                Result.failure(IllegalStateException(result.message))
            }
            is TransactionResult.Failure -> {
                if (BuildConfig.DEBUG) android.util.Log.e("MWA_REPO", "walletAdapter.connect Failure: ${result.message}", result.e)
                Result.failure(result.e ?: IllegalStateException(result.message))
            }
        }
    }

    /**
     * Signs and sends a transaction through the connected wallet.
     * Evaluates transaction builder lambda inside the MWA transact block so the
     * recent blockhash is fetched right when the wallet session is established.
     */
    suspend fun signAndSendTransaction(
        sender: ActivityResultSender,
        onAwaitingApproval: (() -> Unit)? = null,
        buildTransaction: suspend () -> ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        val cachedToken = getCachedAuthToken()
        if (BuildConfig.DEBUG) android.util.Log.i("MWA_REPO", "signAndSendTransaction started.")
        if (cachedToken != null) {
            walletAdapter.authToken = cachedToken
        }

        var result = walletAdapter.transact(sender) { authResult ->
            if (BuildConfig.DEBUG) android.util.Log.i("MWA_REPO", "transact session authorized.")
            val session = parseSession(authResult)
            saveSession(session)

            if (BuildConfig.DEBUG) android.util.Log.i("MWA_REPO", "Executing buildTransaction callback...")
            val payload = buildTransaction()
            if (BuildConfig.DEBUG) android.util.Log.i("MWA_REPO", "buildTransaction payload ready (${payload.size} bytes)")

            onAwaitingApproval?.invoke()

            if (BuildConfig.DEBUG) android.util.Log.i("MWA_REPO", "Dispatching signAndSendTransactions to wallet...")
            val sendResult = signAndSendTransactions(
                arrayOf(payload),
                DefaultTransactionParams
            )
            if (BuildConfig.DEBUG) android.util.Log.i("MWA_REPO", "signAndSendTransactions returned with ${sendResult.signatures.size} signatures")
            val signatures = sendResult.signatures
            if (signatures.isEmpty() || signatures[0].isEmpty()) {
                throw IOException("Wallet returned empty transaction signature")
            }
            val sigB58 = Base58.encode(signatures[0])
            if (BuildConfig.DEBUG) android.util.Log.i("MWA_REPO", "Transaction signature success: $sigB58")
            sigB58
        }

        // If transact failed with cached token, retry once with fresh authorization
        if (result is TransactionResult.Failure && cachedToken != null) {
            if (BuildConfig.DEBUG) android.util.Log.w("MWA_REPO", "transact failed with cached token (${result.message}). Retrying fresh transaction...")
            walletAdapter.authToken = null
            clearSession()
            result = walletAdapter.transact(sender) { authResult ->
                val session = parseSession(authResult)
                saveSession(session)
                val payload = buildTransaction()
                onAwaitingApproval?.invoke()
                val sendResult = signAndSendTransactions(arrayOf(payload), DefaultTransactionParams)
                val signatures = sendResult.signatures
                if (signatures.isEmpty() || signatures[0].isEmpty()) {
                    throw IOException("Wallet returned empty transaction signature")
                }
                Base58.encode(signatures[0])
            }
        }

        when (result) {
            is TransactionResult.Success -> {
                if (BuildConfig.DEBUG) android.util.Log.i("MWA_REPO", "TransactionResult.Success")
                Result.success(result.payload)
            }
            is TransactionResult.NoWalletFound -> {
                if (BuildConfig.DEBUG) android.util.Log.e("MWA_REPO", "TransactionResult.NoWalletFound: ${result.message}")
                Result.failure(IllegalStateException(result.message))
            }
            is TransactionResult.Failure -> {
                if (BuildConfig.DEBUG) android.util.Log.e("MWA_REPO", "TransactionResult.Failure: ${result.message}", result.e)
                Result.failure(result.e ?: IllegalStateException(result.message))
            }
        }
    }

    suspend fun signAndSendTransaction(
        sender: ActivityResultSender,
        transactionPayload: ByteArray
    ): Result<String> = signAndSendTransaction(sender) { transactionPayload }
}
