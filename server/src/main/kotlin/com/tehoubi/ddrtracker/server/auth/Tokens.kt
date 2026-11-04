package com.tehoubi.ddrtracker.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Tokens {
    private val random = SecureRandom()

    /** 256 random bits, URL-safe. */
    fun generate(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())

    /** Letters and digits that can't be mistaken for each other when copied by hand (no 0/O, 1/I/L, U). */
    private const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** A recovery code to write down: 25 characters (about 123 bits) in groups of five. */
    fun recoveryCode(): String =
        (1..25).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.chunked(5).joinToString("-") { it.joinToString("") }

    /** The form a recovery code is hashed in: case, spaces and dashes don't matter. */
    fun normalizeRecoveryCode(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }
}
