package com.kdwbll.orbit

import android.content.Context

class DeviceIdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences("orbit.identity", Context.MODE_PRIVATE)
    fun get(): OpaProtocol.Identity {
        val encoded = prefs.getString("seed", null)
        val seed = if (encoded == null) OpaProtocol.newIdentity().seed.also { prefs.edit().putString("seed", OpaProtocol.b64(it)).apply() }
        else OpaProtocol.unb64(encoded)
        val pub = ByteArray(32).also { org.bouncycastle.math.ec.rfc8032.Ed25519.generatePublicKey(seed, 0, it, 0) }
        return OpaProtocol.Identity(seed, pub)
    }
}
