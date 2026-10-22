package com.kdwbll.orbit

import android.util.Base64
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.math.ec.rfc8032.Ed25519
import org.bouncycastle.math.ec.rfc7748.X25519
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Byte-exact counterpart of docs/protocol.md sections 1–2. */
object OpaProtocol {
    private const val KEY_SIZE = 32
    private const val TAG_SIZE = 16
    private val aad = "opa-v1".toByteArray()

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun unb64(value: String): ByteArray = Base64.decode(value, Base64.DEFAULT)
    fun b64url(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    fun unb64url(value: String): ByteArray = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun id(pub: ByteArray): String = sha256(pub).joinToString("") { "%02x".format(it) }.take(32)

    data class Identity(val seed: ByteArray, val pub: ByteArray) {
        val id: String get() = OpaProtocol.id(pub)
        fun sign(message: ByteArray): ByteArray = ByteArray(64).also { Ed25519.sign(seed, 0, message, 0, message.size, it, 0) }
    }

    fun newIdentity(): Identity {
        val seed = ByteArray(KEY_SIZE)
        java.security.SecureRandom().nextBytes(seed)
        val pub = ByteArray(KEY_SIZE)
        Ed25519.generatePublicKey(seed, 0, pub, 0)
        return Identity(seed, pub)
    }

    fun verify(pub: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
        pub.size == KEY_SIZE && signature.size == 64 && Ed25519.verify(signature, 0, pub, 0, message, 0, message.size)

    data class Ephemeral(val private: ByteArray, val public: ByteArray)
    fun ephemeral(): Ephemeral {
        val private = ByteArray(KEY_SIZE).also { java.security.SecureRandom().nextBytes(it) }
        val public = ByteArray(KEY_SIZE).also { X25519.generatePublicKey(private, 0, it, 0) }
        return Ephemeral(private, public)
    }

    fun derive(eph: Ephemeral, peer: ByteArray, deviceEph: ByteArray, hostEph: ByteArray): SessionKeys {
        require(peer.size == KEY_SIZE && deviceEph.size == KEY_SIZE && hostEph.size == KEY_SIZE)
        val shared = ByteArray(KEY_SIZE)
        check(X25519.calculateAgreement(eph.private, 0, peer, 0, shared, 0)) { "Invalid X25519 shared secret" }
        val salt = deviceEph + hostEph
        val d2h = hkdf(shared, salt, "opa-v1-d2h".toByteArray())
        val h2d = hkdf(shared, salt, "opa-v1-h2d".toByteArray())
        val sasNumber = ByteBuffer.wrap(sha256("opa-sas".toByteArray() + deviceEph + hostEph), 0, 4)
            .order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
        return SessionKeys(d2h, h2d, "%06d".format(sasNumber % 1_000_000))
    }

    private fun hkdf(secret: ByteArray, salt: ByteArray, info: ByteArray): ByteArray = ByteArray(KEY_SIZE).also {
        HKDFBytesGenerator(SHA256Digest()).apply { init(HKDFParameters(secret, salt, info)); generateBytes(it, 0, KEY_SIZE) }
    }

    data class SessionKeys(val d2h: ByteArray, val h2d: ByteArray, val sas: String)

    class Channel(keys: SessionKeys) {
        private val sendKey = keys.d2h
        private val receiveKey = keys.h2d
        val sas = keys.sas
        private var sendN = 0L
        private var lastN = -1L
        fun seal(plain: ByteArray): Pair<Long, String> {
            val n = sendN++
            return n to b64(encrypt(sendKey, n, plain))
        }
        fun open(n: Long, cipherText: String): ByteArray {
            require(n > lastN) { "Detected replay frame" }
            val plain = decrypt(receiveKey, n, unb64(cipherText))
            lastN = n
            return plain
        }
    }

    private fun nonce(n: Long): ByteArray = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).putInt(0).putLong(n).array()
    private fun encrypt(key: ByteArray, n: Long, plain: ByteArray): ByteArray = Cipher.getInstance("ChaCha20-Poly1305")
        .apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce(n))); updateAAD(aad) }.doFinal(plain)
    private fun decrypt(key: ByteArray, n: Long, ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= TAG_SIZE)
        return Cipher.getInstance("ChaCha20-Poly1305").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce(n))); updateAAD(aad)
        }.doFinal(ciphertext)
    }

    fun helloMessage(hostId: String, ephPubB64: String, timestamp: Long) = "opa-hello-v1|$hostId|$ephPubB64|$timestamp".toByteArray()
    fun welcomeMessage(deviceId: String, deviceEphB64: String, hostEphB64: String) = "opa-welcome-v1|$deviceId|$deviceEphB64|$hostEphB64".toByteArray()
    fun relayMessage(hostId: String, deviceId: String, timestamp: Long) = "opa-relay-device-v1|$hostId|$deviceId|$timestamp".toByteArray()
}
