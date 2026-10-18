package com.example.expensify.feature.auth.domain

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The app lock's MPIN (Settings › Security): four digits the user chooses,
 * kept on this phone only as a salted PBKDF2-HMAC-SHA256 verifier, never as
 * the digits. Pure, so the verifier round-trips in a JVM test. A four-digit
 * code has ten thousand values, so what protects it is the app's private
 * storage and [LockRules]' wait after wrong tries, not the hash; the hash
 * only keeps the digits out of the preferences file.
 */
object MpinRules {
    const val LENGTH = 4

    /** PBKDF2 rounds for a new verifier; stored with it, so it can grow later without breaking old ones. */
    const val ITERATIONS = 10_000

    private const val SALT_BYTES = 16
    private const val VERSION = "v1"
    private const val HMAC = "HmacSHA256"

    /** Whether [pin] is a complete MPIN: exactly [LENGTH] ASCII digits. */
    fun isComplete(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    /** Whether [pin] may still grow into a complete MPIN: digits only, not over the length. */
    fun isPartial(pin: String): Boolean = pin.length <= LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    /** The stored form of [pin]: `v1:<iterations>:<salt hex>:<hash hex>`. */
    fun verifier(pin: String, salt: ByteArray = newSalt(), iterations: Int = ITERATIONS): String {
        require(isComplete(pin)) { "not a complete MPIN" }
        require(iterations >= 1)
        val hash = pbkdf2(pin.toByteArray(Charsets.UTF_8), salt, iterations)
        return listOf(VERSION, iterations.toString(), salt.toHex(), hash.toHex()).joinToString(":")
    }

    /** Whether [pin] is the MPIN [verifier] was made from. A malformed verifier matches nothing. */
    fun matches(pin: String, verifier: String): Boolean {
        if (!isComplete(pin)) return false
        val parts = verifier.split(":")
        if (parts.size != 4 || parts[0] != VERSION) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it >= 1 } ?: return false
        val salt = parts[2].hexToBytesOrNull() ?: return false
        val expected = parts[3].hexToBytesOrNull() ?: return false
        val actual = pbkdf2(pin.toByteArray(Charsets.UTF_8), salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    /**
     * PBKDF2 with HMAC-SHA256 (RFC 8018 §5.2), one 32-byte block. Written out
     * because `PBKDF2WithHmacSHA256` only reaches Android at API 26 and the
     * app runs from 24; `HmacSHA256` itself is on every version.
     */
    internal fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance(HMAC).apply { init(SecretKeySpec(password, HMAC)) }
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val out = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte()
        }
        return out
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0 || isEmpty()) return null
        val out = ByteArray(length / 2)
        for (i in out.indices) {
            val hi = Character.digit(this[2 * i], 16)
            val lo = Character.digit(this[2 * i + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
