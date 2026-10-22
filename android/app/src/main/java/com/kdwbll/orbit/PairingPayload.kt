package com.kdwbll.orbit

import org.json.JSONObject

data class PairingPayload(val hostId: String, val hostPub: ByteArray, val hostName: String, val relay: String?, val token: String) {
    companion object {
        fun parse(link: String): PairingPayload {
            require(link.startsWith("opa://pair?d=")) { "不是 Orbit 配对链接" }
            val json = JSONObject(String(OpaProtocol.unb64url(link.substringAfter("d=").substringBefore('&'))))
            require(json.optInt("v") == 1) { "不支持的配对链接版本" }
            require(json.optLong("exp") > System.currentTimeMillis()) { "配对链接已过期，请在 Mac 上重新运行 opa pair" }
            return PairingPayload(json.getString("hostId"), OpaProtocol.unb64(json.getString("hostPub")), json.getString("hostName"), json.optString("relay").ifBlank { null }, json.getString("pairToken"))
        }
    }
}
