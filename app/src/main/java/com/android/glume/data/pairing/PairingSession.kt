package com.android.glume.data.pairing

import java.security.SecureRandom

/**
 * One pairing attempt: a secret token embedded in the QR link, a short code shown on both the TV
 * and the phone page so the user can check they match, and an expiry. The link works for a
 * single successful sign-in. Pure Kotlin so it is unit-testable.
 */
class PairingSession(
    val token: String,
    val code: String,
    private val createdAt: Long,
    private val ttlMs: Long = TTL_MS,
) {
    var used = false
        private set

    fun isValid(token: String, now: Long): Boolean = !used && token == this.token && now - createdAt < ttlMs

    fun markUsed() { used = true }

    fun remainingMs(now: Long) = (ttlMs - (now - createdAt)).coerceAtLeast(0)

    companion object {
        const val TTL_MS = 10 * 60_000L
        private val rng = SecureRandom()
        // No 0/O, 1/I/L, so the code reads cleanly on a TV.
        private const val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        fun create(now: Long, ttlMs: Long = TTL_MS): PairingSession {
            val token = ByteArray(18).also(rng::nextBytes).joinToString("") { "%02x".format(it) }
            val code = (1..6).map { CODE_CHARS[rng.nextInt(CODE_CHARS.length)] }.joinToString("").let { "${it.take(3)}-${it.drop(3)}" }
            return PairingSession(token, code, now, ttlMs)
        }
    }
}
