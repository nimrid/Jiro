package com.jiro.app

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Central factory for OkHttpClient instances with certificate pinning.
 *
 * ## How to update pins
 *
 * Run the following command against each host and copy the `sha256/` lines:
 * ```
 * openssl s_client -connect api.jup.ag:443 -showcerts 2>/dev/null |
 *   openssl x509 -pubkey -noout |
 *   openssl pkey -pubin -outform DER |
 *   openssl dgst -sha256 -binary |
 *   base64
 * ```
 * Or use OkHttp's `CertificatePinner.pin(cert)` on a fresh connection.
 *
 * ## Why two pins per host
 * Always include both the leaf certificate pin AND the intermediate CA pin so that
 * a certificate rotation on the server doesn't break connectivity. If the leaf rotates,
 * the intermediate still matches; if the intermediate rotates, the leaf still matches.
 *
 * Set `BuildConfig.ENABLE_CERT_PINNING` to false only in debug builds (see build.gradle.kts).
 */
object NetworkSecurity {

    // ──────────────────────────────────────────────────────────────────────────
    // Jupiter Swap API  (https://api.jup.ag)
    // TODO: populate by running the openssl command above against api.jup.ag
    // ──────────────────────────────────────────────────────────────────────────
    private val JUPITER_PINS = arrayOf(
        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // leaf — REPLACE ME
        "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="  // intermediate CA — REPLACE ME
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Keeper service  (https://keeper.jiro.finance)
    // TODO: populate once the Keeper is deployed behind TLS
    // ──────────────────────────────────────────────────────────────────────────
    private val KEEPER_PINS = arrayOf(
        "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=", // leaf — REPLACE ME
        "sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD="  // intermediate CA — REPLACE ME
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Solana Devnet RPC — Alchemy (https://solana-mainnet.g.alchemy.com)
    // Pinning is skipped here since the RPC endpoint is user-configurable.
    // Consider pinning if you lock down to a single provider in production.
    // ──────────────────────────────────────────────────────────────────────────

    private fun basePinnedClient(
        host: String,
        vararg pins: String,
        connectTimeoutSec: Long = 15,
        readTimeoutSec: Long = 15
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)

        // Apply certificate pinning in release; skip in debug to allow Charles / Proxyman.
        if (BuildConfig.ENABLE_CERT_PINNING) {
            val pinner = CertificatePinner.Builder()
                .apply { pins.forEach { add(host, it) } }
                .build()
            builder.certificatePinner(pinner)
        }

        return builder.build()
    }

    /** Pre-built client for Jupiter Swap API (api.jup.ag). */
    fun jupiterClient(): OkHttpClient =
        basePinnedClient("api.jup.ag", *JUPITER_PINS)

    /** Pre-built client for the Keeper service. */
    fun keeperClient(): OkHttpClient =
        basePinnedClient("keeper.jiro.finance", *KEEPER_PINS)

    /** Generic unpinned client for the Solana RPC (host may be user-configured). */
    fun rpcClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
