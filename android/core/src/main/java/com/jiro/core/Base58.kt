package com.jiro.core

import java.util.Arrays

object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val INDEXES = IntArray(128) { -1 }.apply {
        for (i in ALPHABET.indices) {
            this[ALPHABET[i].code] = i
        }
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""

        val copy = input.clone()
        var zeros = 0
        while (zeros < copy.size && copy[zeros].toInt() == 0) {
            zeros++
        }

        val encoded = CharArray(copy.size * 2)
        var outputStart = encoded.size
        var inputStart = zeros

        while (inputStart < copy.size) {
            val decodedByte = divmod(copy, inputStart, 256, 58)
            if (copy[inputStart].toInt() == 0) {
                inputStart++
            }
            outputStart--
            encoded[outputStart] = ALPHABET[decodedByte]
        }

        while (outputStart < encoded.size && encoded[outputStart] == '1') {
            outputStart++
        }

        while (--zeros >= 0) {
            outputStart--
            encoded[outputStart] = '1'
        }

        return String(encoded, outputStart, encoded.size - outputStart)
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)

        val input58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            if (digit < 0) {
                throw IllegalArgumentException("Illegal character $c at index $i")
            }
            input58[i] = digit.toByte()
        }

        var zeros = 0
        while (zeros < input58.size && input58[zeros].toInt() == 0) {
            zeros++
        }

        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeros

        while (inputStart < input58.size) {
            val decodedByte = divmod(input58, inputStart, 58, 256)
            if (input58[inputStart].toInt() == 0) {
                inputStart++
            }
            outputStart--
            decoded[outputStart] = decodedByte.toByte()
        }

        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) {
            outputStart++
        }

        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.size)
    }

    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Int {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder
    }
}
