package com.laptop.gallery

import android.content.Context
import java.security.SecureRandom

/**
 * Holds the current one-time pairing code and the set of approved device tokens.
 * Tokens persist across restarts (SharedPreferences).
 * The OTP is persisted until devices are unpaired or cleared.
 */
class PairStore(context: Context) {

    private val prefs = context.getSharedPreferences("gallery_pair", Context.MODE_PRIVATE)
    private val rnd = SecureRandom()

    val otp: String
        get() = getOrCreateOtp()

    /** Returns the persisted OTP or generates a fresh 6-digit code if none exists. */
    fun getOrCreateOtp(): String {
        val existing = prefs.getString("saved_otp", null)
        if (!existing.isNullOrEmpty()) {
            return existing
        }
        val newCode = "%06d".format(rnd.nextInt(1_000_000))
        prefs.edit().putString("saved_otp", newCode).apply()
        return newCode
    }

    /** Generate a fresh 6-digit code and save it. */
    fun newOtp(): String {
        val newCode = "%06d".format(rnd.nextInt(1_000_000))
        prefs.edit().putString("saved_otp", newCode).apply()
        return newCode
    }

    /** Approved device tokens (persisted). */
    private fun tokens(): MutableSet<String> =
        HashSet(prefs.getStringSet("tokens", emptySet()) ?: emptySet())

    fun isApproved(token: String?): Boolean =
        !token.isNullOrEmpty() && tokens().contains(token)

    fun hasPairedTokens(): Boolean = tokens().isNotEmpty()

    fun unpairToken(token: String?) {
        if (token.isNullOrEmpty()) return
        val set = tokens().apply { remove(token) }
        prefs.edit().putStringSet("tokens", set).apply()
        if (set.isEmpty()) {
            prefs.edit().remove("saved_otp").apply()
        }
    }

    fun clearAll() {
        prefs.edit().remove("tokens").remove("saved_otp").apply()
    }

    /**
     * Validate a client-supplied OTP. On match, mint a new device token, store
     * it in the whitelist, and return it. Returns null on mismatch.
     */
    fun redeem(candidateOtp: String?): String? {
        val currentOtp = getOrCreateOtp()
        if (currentOtp.isEmpty() || candidateOtp != currentOtp) return null
        val token = randomToken()
        val set = tokens().apply { add(token) }
        prefs.edit().putStringSet("tokens", set).apply()
        return token
    }

    private fun randomToken(): String {
        val bytes = ByteArray(24)
        rnd.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
