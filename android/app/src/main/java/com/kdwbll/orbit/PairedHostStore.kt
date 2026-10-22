package com.kdwbll.orbit

import android.content.Context

/** Persistent public connection information. The one-time pairing token is deliberately never stored. */
data class PairedHost(val hostId: String, val hostPub: ByteArray, val hostName: String, val relay: String)

class PairedHostStore(context: Context) {
    private val prefs = context.getSharedPreferences("orbit.host", Context.MODE_PRIVATE)

    fun get(): PairedHost? {
        val id = prefs.getString("id", null) ?: return null
        val pub64 = prefs.getString("pub", null) ?: return null
        val relay = prefs.getString("relay", null) ?: return null
        return try {
            PairedHost(id, OpaProtocol.unb64(pub64), prefs.getString("name", "Mac") ?: "Mac", relay)
        } catch (_: IllegalArgumentException) { null }
    }

    fun save(payload: PairingPayload) {
        val relay = payload.relay ?: return
        prefs.edit()
            .putString("id", payload.hostId)
            .putString("pub", OpaProtocol.b64(payload.hostPub))
            .putString("name", payload.hostName)
            .putString("relay", relay)
            .apply()
    }

    fun clear() { prefs.edit().clear().apply() }
}
