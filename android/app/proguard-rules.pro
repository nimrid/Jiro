# JIRO ProGuard / R8 Rules

# ──────────────────────────────────────────────────────────────────────────────
# Keep OkHttp + Okio
# ──────────────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.** { *; }
-keepnames interface okhttp3.** { *; }

# ──────────────────────────────────────────────────────────────────────────────
# Solana Mobile Wallet Adapter
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.solana.mobilewalletadapter.** { *; }
-dontwarn com.solana.mobilewalletadapter.**

# ──────────────────────────────────────────────────────────────────────────────
# BouncyCastle / ed25519-dalek crypto
# ──────────────────────────────────────────────────────────────────────────────
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ──────────────────────────────────────────────────────────────────────────────
# Kotlin coroutines + serialization
# ──────────────────────────────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ──────────────────────────────────────────────────────────────────────────────
# Compose
# ──────────────────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ──────────────────────────────────────────────────────────────────────────────
# Coil image loading
# ──────────────────────────────────────────────────────────────────────────────
-dontwarn coil.**
-keep class coil.** { *; }

# ──────────────────────────────────────────────────────────────────────────────
# Core data models — prevent stripping fields used via JSON reflection
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.jiro.core.** { *; }

# ──────────────────────────────────────────────────────────────────────────────
# Remove all android.util.Log calls in release
# ──────────────────────────────────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
