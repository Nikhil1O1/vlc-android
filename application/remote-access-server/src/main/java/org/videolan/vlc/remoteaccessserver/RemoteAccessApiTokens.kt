package org.videolan.vlc.remoteaccessserver

import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.tools.putSingle
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Long-lived access tokens for non-browser clients (for example the MCP bridge running on a computer).
 * Only a SHA-256 hash of each token is stored: the token itself is shown once when it is created.
 */
object RemoteAccessApiTokens {
    private const val KEY = "remote_access_api_tokens"

    data class Info(val id: String, val name: String, val createdAt: Long)

    /** Creates a token and returns it. This is the only time the clear token is available. */
    fun create(settings: SharedPreferences, name: String): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = "vlc_" + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val array = read(settings)
        array.put(
            JSONObject()
                .put("id", Base64.encodeToString(bytes.copyOf(6), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
                .put("name", name)
                .put("hash", hash(token))
                .put("createdAt", System.currentTimeMillis())
        )
        settings.putSingle(KEY, array.toString())
        return token
    }

    fun validate(settings: SharedPreferences, token: String): Boolean {
        val candidate = hash(token).toByteArray()
        val array = read(settings)
        var valid = false
        for (i in 0 until array.length()) {
            // compare every entry in constant time, no early exit
            if (MessageDigest.isEqual(array.getJSONObject(i).optString("hash").toByteArray(), candidate)) valid = true
        }
        return valid
    }

    fun list(settings: SharedPreferences): List<Info> {
        val array = read(settings)
        return (0 until array.length()).map {
            val o = array.getJSONObject(it)
            Info(o.optString("id"), o.optString("name"), o.optLong("createdAt"))
        }
    }

    fun revoke(settings: SharedPreferences, id: String) {
        val array = read(settings)
        val kept = JSONArray()
        for (i in 0 until array.length()) if (array.getJSONObject(i).optString("id") != id) kept.put(array.getJSONObject(i))
        settings.putSingle(KEY, kept.toString())
    }

    fun revokeAll(settings: SharedPreferences) = settings.putSingle(KEY, "[]")

    private fun read(settings: SharedPreferences): JSONArray = try {
        JSONArray(settings.getString(KEY, "[]") ?: "[]")
    } catch (e: Exception) {
        JSONArray()
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
