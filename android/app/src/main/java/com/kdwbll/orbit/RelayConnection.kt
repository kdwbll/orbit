package com.kdwbll.orbit

import android.content.Context
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.UUID

/** Relay client. It authenticates the peer before any app message is decrypted. */
class RelayConnection(context: Context) {
    sealed interface State { data object Idle : State; data object Connecting : State; data class AwaitingMac(val sas: String) : State; data object Connected : State; data class Failed(val reason: String) : State }
    var onState: (State) -> Unit = {}
    var onEvent: (String, JSONObject) -> Unit = { _, _ -> }
    private val identity = DeviceIdentityStore(context).get()
    private val pairedHostStore = PairedHostStore(context)
    private val client = OkHttpClient()
    private var ws: WebSocket? = null
    private var payload: PairingPayload? = null
    private var ephemeral: OpaProtocol.Ephemeral? = null
    private var channel: OpaProtocol.Channel? = null
    private var pairing = false

    fun resume(): Boolean {
        val saved = pairedHostStore.get() ?: return false
        connect(saved.hostId, saved.hostPub, saved.hostName, saved.relay, null)
        return true
    }

    fun pair(p: PairingPayload) {
        val relay = requireNotNull(p.relay) { "此配对链接没有公网 Relay 地址" }
        connect(p.hostId, p.hostPub, p.hostName, relay, p.token)
    }

    private fun connect(hostId: String, hostPub: ByteArray, hostName: String, relayUrl: String, pairToken: String?) {
        close(); pairing = pairToken != null
        payload = PairingPayload(hostId, hostPub, hostName, relayUrl, pairToken ?: "")
        ephemeral = OpaProtocol.ephemeral(); onState(State.Connecting)
        val now = System.currentTimeMillis(); val signature = OpaProtocol.b64url(identity.sign(OpaProtocol.relayMessage(hostId, identity.id, now)))
        val relay = relayUrl.removeSuffix("/")
        val pairingQuery = if (pairToken != null) "&pair=1" else ""
        val url = "$relay/v1/device/$hostId?deviceId=${identity.id}&pub=${OpaProtocol.b64url(identity.pub)}&ts=$now&sig=$signature$pairingQuery"
        ws = client.newWebSocket(Request.Builder().url(url).build(), Listener())
    }

    fun sendChat(text: String) = request("chat.send", JSONObject().put("clientId", UUID.randomUUID().toString()).put("text", text))
    fun request(method: String, data: JSONObject) {
        val body = JSONObject().put("t", "req").put("id", UUID.randomUUID().toString()).put("m", method).put("d", data)
        sendEncrypted(body)
    }
    fun close() { ws?.close(1000, "closed"); ws = null; channel = null; onState(State.Idle) }

    private fun sendEncrypted(value: JSONObject) {
        val current = channel ?: return
        val (n, cipher) = current.seal(value.toString().toByteArray())
        ws?.send(JSONObject().put("t", "x").put("n", n).put("c", cipher).toString())
    }
    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val p = payload ?: return; val eph = ephemeral ?: return; val ts = System.currentTimeMillis(); val ephB64 = OpaProtocol.b64(eph.public)
            val hello = JSONObject().put("t", "hello").put("v", 1).put("deviceId", identity.id).put("devicePub", OpaProtocol.b64(identity.pub))
                .put("deviceName", "Orbit Android").put("ephPub", ephB64).put("ts", ts).put("pairToken", if (pairing) p.token else JSONObject.NULL)
                .put("sig", OpaProtocol.b64(identity.sign(OpaProtocol.helloMessage(p.hostId, ephB64, ts))))
            webSocket.send(hello.toString())
        }
        override fun onMessage(webSocket: WebSocket, text: String) { try { receive(JSONObject(text)) } catch (e: Exception) { onState(State.Failed(e.message ?: "收到无效消息")); webSocket.close(1002, "bad frame") } }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) { onState(State.Failed("Relay 发送了不支持的二进制帧")) }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { onState(State.Failed(t.message ?: "网络连接失败")) }
    }
    private fun receive(frame: JSONObject) {
        when (frame.optString("t")) {
            "reject" -> onState(State.Failed(frame.optString("reason", "Mac 拒绝连接")))
            "welcome" -> welcome(frame)
            "x" -> {
                val plain = channel?.open(frame.getLong("n"), frame.getString("c")) ?: return
                val message = JSONObject(String(plain)); onEvent(message.optString("t"), message)
                if (message.optString("t") == "pair.result" && message.optJSONObject("d")?.optBoolean("ok") == true) {
                    payload?.let { pairedHostStore.save(it) }
                    onState(State.Connected); request("sync", JSONObject().put("lastSeq", 0))
                }
            }
            "host_offline" -> onState(State.Failed("家里的 Mac 当前离线"))
        }
    }
    private fun welcome(frame: JSONObject) {
        val p = payload ?: return; val eph = ephemeral ?: return
        val hostEphB64 = frame.getString("ephPub"); val hostEph = OpaProtocol.unb64(hostEphB64)
        require(OpaProtocol.verify(p.hostPub, OpaProtocol.welcomeMessage(identity.id, OpaProtocol.b64(eph.public), hostEphB64), OpaProtocol.unb64(frame.getString("sig")))) { "Mac 身份签名校验失败" }
        channel = OpaProtocol.Channel(OpaProtocol.derive(eph, hostEph, eph.public, hostEph))
        onState(State.AwaitingMac(requireNotNull(channel).sas))
        if (frame.optBoolean("paired", false)) { onState(State.Connected); request("sync", JSONObject().put("lastSeq", 0)) }
    }
}
