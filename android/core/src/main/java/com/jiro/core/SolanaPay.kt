package com.jiro.core

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed class SolanaPayError(message: String) : Exception(message) {
    class InvalidScheme(message: String) : SolanaPayError(message)
    class InvalidRecipient(message: String) : SolanaPayError(message)
    class InvalidAmount(message: String) : SolanaPayError(message)
    class InvalidSplToken(message: String) : SolanaPayError(message)
    class InvalidReference(message: String) : SolanaPayError(message)
    class AmbiguousUri(message: String) : SolanaPayError(message)
}

data class SolanaPayRequest(
    val recipient: PublicKey,
    val amount: Money?,
    val splToken: PublicKey?,
    val references: List<PublicKey>,
    val label: String?,
    val message: String?,
    val memo: String?
)

object SolanaPay {

    /**
     * Parses a `solana:` URI adhering to the Solana Pay specification.
     *
     * Example:
     * `solana:9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM?amount=10.5&spl-token=EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v&label=Coffee`
     */
    fun parse(uriString: String): SolanaPayRequest {
        val trimmed = uriString.trim()
        if (!trimmed.startsWith("solana:", ignoreCase = true)) {
            throw SolanaPayError.InvalidScheme("URI must start with 'solana:': $uriString")
        }

        val withoutScheme = trimmed.substring("solana:".length).trim()
        if (withoutScheme.isEmpty()) {
            throw SolanaPayError.InvalidRecipient("Recipient is missing in URI: $uriString")
        }

        // Split path from query
        val queryIndex = withoutScheme.indexOf('?')
        val recipientPart = if (queryIndex >= 0) withoutScheme.substring(0, queryIndex) else withoutScheme
        val queryPart = if (queryIndex >= 0) withoutScheme.substring(queryIndex + 1) else null

        // Decode recipient (strip leading/trailing slashes if present)
        val cleanRecipient = recipientPart.removePrefix("//").trim()
        val recipient = try {
            val pubkey = PublicKey(cleanRecipient)
            pubkey
        } catch (e: Exception) {
            throw SolanaPayError.InvalidRecipient("Invalid recipient public key '$cleanRecipient': ${e.message}")
        }

        var amount: Money? = null
        var splToken: PublicKey? = null
        val references = mutableListOf<PublicKey>()
        var label: String? = null
        var message: String? = null
        var memo: String? = null

        if (!queryPart.isNullOrEmpty()) {
            val pairs = queryPart.split("&")
            for (pair in pairs) {
                if (pair.isEmpty()) continue
                val kv = pair.split("=", limit = 2)
                val key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name())
                val value = if (kv.size == 2) URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()) else ""

                when (key) {
                    "amount" -> {
                        if (amount != null) {
                            throw SolanaPayError.AmbiguousUri("Duplicate 'amount' parameter in URI")
                        }
                        amount = try {
                            Money.parseUsdc(value)
                        } catch (e: Exception) {
                            throw SolanaPayError.InvalidAmount("Invalid amount value '$value': ${e.message}")
                        }
                    }
                    "spl-token" -> {
                        if (splToken != null) {
                            throw SolanaPayError.AmbiguousUri("Duplicate 'spl-token' parameter in URI")
                        }
                        splToken = try {
                            PublicKey(value)
                        } catch (e: Exception) {
                            throw SolanaPayError.InvalidSplToken("Invalid spl-token '$value': ${e.message}")
                        }
                    }
                    "reference" -> {
                        val ref = try {
                            PublicKey(value)
                        } catch (e: Exception) {
                            throw SolanaPayError.InvalidReference("Invalid reference '$value': ${e.message}")
                        }
                        references.add(ref)
                    }
                    "label" -> label = value
                    "message" -> message = value
                    "memo" -> memo = value
                }
            }
        }

        return SolanaPayRequest(
            recipient = recipient,
            amount = amount,
            splToken = splToken,
            references = references,
            label = label,
            message = message,
            memo = memo
        )
    }
}
