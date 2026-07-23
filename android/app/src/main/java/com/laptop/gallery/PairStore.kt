package com.laptop.gallery

import android.content.Context
import java.security.SecureRandom

/**
 * Holds the current one-time pairing code and the set of approved device tokens.
 * Tokens persist across restarts (SharedPreferences); the OTP lives only for the
 * current sharing session and is regenerated each time sharing starts.
 */
class PairStore(context: Context) {

    private val prefs = context.getSharedPreferences("gallery_pair", Context.MODE_PRIVATE)
    private val rnd = SecureRandom()

    @Volatile var otp: String = ""
        private set

    /** Generate a fresh 6-digit code for this sharing session. */
    fun newOtp(): String {
        otp = "%06d".format(rnd.nextInt(1_000_000))
        return otp
    }

    /** Approved device tokens (persisted). */
    private fun tokens(): MutableSet<String> =
        HashSet(prefs.getStringSet("tokens", emptySet()) ?: emptySet())

    fun isApproved(token: String?): Boolean =
        !token.isNullOrEmpty() && tokens().contains(token)

    /**
     * Validate a client-supplied OTP. On match, mint a new device token, store
     * it in the whitelist, and return it. Returns null on mismatch.
     */
    fun redeem(candidateOtp: String?): String? {
        if (otp.isEmpty() || candidateOtp != otp) return null
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
