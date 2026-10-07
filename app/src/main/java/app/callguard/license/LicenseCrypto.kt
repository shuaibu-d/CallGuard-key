package app.callguard.license

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Shared by the CallGuard app (verifies) and the CallGuard Keys app (signs).
 * An activation code is an ECDSA P-256 signature over the phone's Device ID.
 * The app only contains the PUBLIC key, so it can check codes but never make them.
 */
object LicenseCrypto {
    private const val PREFIX = "CG1:"
    private const val ALGORITHM = "SHA256withECDSA"

    fun normalizeId(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }

    fun isValidId(raw: String): Boolean {
        val id = normalizeId(raw)
        return id.length == 16 && id.all { it in '0'..'9' || it in 'A'..'F' }
    }

    /** A1B2C3D4E5F67890 -> A1B2-C3D4-E5F6-7890 */
    fun formatId(raw: String): String = normalizeId(raw).chunked(4).joinToString("-")

    fun sign(privateKeyBase64: String, deviceId: String): String {
        val keyBytes = Base64.getDecoder().decode(privateKeyBase64.filter { !it.isWhitespace() })
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val signature = Signature.getInstance(ALGORITHM)
        signature.initSign(key)
        signature.update(message(deviceId))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign())
    }

    fun verify(publicKeyBase64: String, deviceId: String, code: String): Boolean {
        return try {
            val keyBytes = Base64.getDecoder().decode(publicKeyBase64.filter { !it.isWhitespace() })
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
            val signature = Signature.getInstance(ALGORITHM)
            signature.initVerify(key)
            signature.update(message(deviceId))
            signature.verify(Base64.getUrlDecoder().decode(code.filter { !it.isWhitespace() }))
        } catch (e: Exception) {
            false
        }
    }

    /** Returns (publicKey, privateKey) as base64. Used by tests. */
    fun generateKeyPair(): Pair<String, String> {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val enc = Base64.getEncoder()
        return enc.encodeToString(pair.public.encoded) to enc.encodeToString(pair.private.encoded)
    }

    private fun message(deviceId: String): ByteArray = (PREFIX + normalizeId(deviceId)).toByteArray(Charsets.UTF_8)
}
