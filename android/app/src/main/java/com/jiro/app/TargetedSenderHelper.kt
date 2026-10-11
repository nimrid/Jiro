package com.jiro.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.lang.reflect.Field

data class InstalledWallet(
    val name: String,
    val packageName: String
)

object TargetedSenderHelper {
    private const val TAG = "TargetedSender"
    private const val PREF_KEY_WALLET_PKG = "preferred_wallet_pkg"

    /**
     * Private — use [getActiveTargetPackage] / [savePreferredPackage] for safe access.
     * Never exposed publicly to prevent arbitrary code from redirecting MWA intents.
     */
    @Volatile
    private var activeTargetPackage: String? = null

    /** Read-only access for the intent hook. */
    fun getActiveTargetPackage(): String? = activeTargetPackage

    fun getInstalledWallets(context: Context): List<InstalledWallet> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("solana-wallet:/v1/associate/local"))
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val wallets = mutableListOf<InstalledWallet>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue
            val label = when (pkg) {
                "com.solflare.mobile" -> "Solflare"
                "ag.jup.jupiter.android" -> "Jupiter"
                "app.phantom" -> "Phantom"
                else -> try {
                    info.loadLabel(pm).toString()
                } catch (e: Exception) {
                    pkg
                }
            }
            if (wallets.none { it.packageName == pkg }) {
                wallets.add(InstalledWallet(label, pkg))
            }
        }
        return wallets
    }

    /**
     * Returns the encrypted SharedPreferences used for wallet preferences.
     * Reuses the same keystore key as MwaRepository so both share the same AES-256-GCM protection.
     */
    private fun encryptedPrefs(context: Context) = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "jiro_mwa_prefs",         // same store as MwaRepository
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    fun initPreferredPackage(context: Context) {
        val prefs = encryptedPrefs(context)
        val savedPkg = prefs?.getString(PREF_KEY_WALLET_PKG, null)
        val installed = getInstalledWallets(context)

        activeTargetPackage = when {
            savedPkg != null && installed.any { it.packageName == savedPkg } -> savedPkg
            installed.any { it.packageName == "com.solflare.mobile" } -> "com.solflare.mobile"
            installed.isNotEmpty() -> installed.first().packageName
            else -> null
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "Initialized activeTargetPackage: $activeTargetPackage")
    }

    /**
     * Persists the preferred wallet package only after validating it is actually installed
     * as a Solana wallet. This prevents an attacker with filesystem write access from
     * redirecting MWA intents to an arbitrary (malicious) package.
     */
    fun savePreferredPackage(context: Context, packageName: String) {
        val installed = getInstalledWallets(context)
        if (installed.none { it.packageName == packageName }) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Rejected savePreferredPackage: $packageName is not an installed Solana wallet")
            return
        }
        activeTargetPackage = packageName
        encryptedPrefs(context)
            ?.edit()
            ?.putString(PREF_KEY_WALLET_PKG, packageName)
            ?.apply()
        if (BuildConfig.DEBUG) Log.d(TAG, "Saved preferred package: $packageName")
    }

    fun attachTargetPackageHook(sender: ActivityResultSender) {
        try {
            val field: Field = ActivityResultSender::class.java.getDeclaredField("activityResultLauncher")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val originalLauncher = field.get(sender) as ActivityResultLauncher<Intent>

            // Avoid double-wrapping
            if (originalLauncher.javaClass.name.contains("TargetedLauncher")) {
                return
            }

            val targetedLauncher = object : ActivityResultLauncher<Intent>() {
                override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                    val pkg = getActiveTargetPackage()
                    if (BuildConfig.DEBUG) Log.d(TAG, "Launching intent: action=${input.action}, pkg=$pkg")
                    if (!pkg.isNullOrEmpty() &&
                        (input.data?.scheme == "solana-wallet" || input.action == Intent.ACTION_VIEW)
                    ) {
                        input.setPackage(pkg)
                        if (BuildConfig.DEBUG) Log.d(TAG, "Set explicit target package: $pkg")
                    }
                    originalLauncher.launch(input, options)
                }

                override fun unregister() {
                    originalLauncher.unregister()
                }

                override val contract: ActivityResultContract<Intent, *>
                    get() = originalLauncher.contract
            }

            field.set(sender, targetedLauncher)
            if (BuildConfig.DEBUG) Log.d(TAG, "Successfully attached TargetedLauncher hook to ActivityResultSender")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach TargetedLauncher hook", e)
        }
    }
}
