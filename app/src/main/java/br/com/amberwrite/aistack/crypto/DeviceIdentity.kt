package br.com.amberwrite.aistack.crypto

import android.content.Context
import android.util.Base64
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

data class DeviceIdentity(
    val id: String, // ex: dev-xxxxxx
    val name: String,
    val publicKeyBytes: ByteArray,
    val privateKeyBytes: ByteArray
) {
    fun publicKeyB64Url(): String = Base64.encodeToString(
        publicKeyBytes,
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )

    companion object {
        private const val PREFS_NAME = "aistack_device_identity"
        private const val KEY_ID = "device_id"
        private const val KEY_NAME = "device_name"
        private const val KEY_PUB = "device_pub"
        private const val KEY_PRIV = "device_priv"

        fun getOrCreate(context: Context, defaultName: String = "Samsung Galaxy"): DeviceIdentity {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_ID, null)
            val pubStr = prefs.getString(KEY_PUB, null)
            val privStr = prefs.getString(KEY_PRIV, null)
            val name = prefs.getString(KEY_NAME, defaultName) ?: defaultName

            if (id != null && pubStr != null && privStr != null) {
                val pub = Base64.decode(pubStr, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                val priv = Base64.decode(privStr, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                return DeviceIdentity(id, name, pub, priv)
            }

            // Gerar novo par X25519
            val random = SecureRandom()
            val gen = X25519KeyPairGenerator()
            gen.init(X25519KeyGenerationParameters(random))
            val pair = gen.generateKeyPair()

            val privParams = pair.private as X25519PrivateKeyParameters
            val pubParams = pair.public as X25519PublicKeyParameters

            val privBytes = privParams.encoded
            val pubBytes = pubParams.encoded

            val randomIdBytes = ByteArray(6)
            random.nextBytes(randomIdBytes)
            val newId = "dev-" + Base64.encodeToString(
                randomIdBytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )

            prefs.edit()
                .putString(KEY_ID, newId)
                .putString(KEY_NAME, defaultName)
                .putString(
                    KEY_PUB,
                    Base64.encodeToString(pubBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                )
                .putString(
                    KEY_PRIV,
                    Base64.encodeToString(privBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                )
                .apply()

            return DeviceIdentity(newId, defaultName, pubBytes, privBytes)
        }
    }
}
