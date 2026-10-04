package br.com.amberwrite.aistack.core.crypto

import android.content.Context
import android.os.Build
import android.util.Log
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/**
 * Identidade persistente do aparelho: `id` estável (`dev-…`), nome exibido no desktop e o
 * par X25519 estático usado no handshake e no `keyAuth`.
 */
data class DeviceIdentity(
    val id: String,
    val name: String,
    val publicKeyBytes: ByteArray,
    val privateKeyBytes: ByteArray
) {
    fun publicKeyB64Url(): String = CryptoEngine.b64uEncode(publicKeyBytes)

    override fun equals(other: Any?): Boolean =
        other is DeviceIdentity && other.id == id && other.name == name &&
            other.publicKeyBytes.contentEquals(publicKeyBytes)

    override fun hashCode(): Int = id.hashCode() * 31 + publicKeyBytes.contentHashCode()

    override fun toString(): String = "DeviceIdentity(id=$id, name=$name)"

    companion object {
        private const val TAG = "DeviceIdentity"
        private const val PREFS_NAME = "aistack_device_identity"
        private const val KEY_ID = "device_id"
        private const val KEY_NAME = "device_name"
        private const val KEY_PUB = "device_pub"

        /** Chave antiga: privada em base64url em claro (migrada e apagada na primeira leitura). */
        private const val KEY_PRIV_LEGACY = "device_priv"

        /** Chave nova: privada cifrada com AES-GCM do Android Keystore. */
        private const val KEY_PRIV_ENC = "device_priv_ks"

        fun defaultName(): String {
            val model = Build.MODEL?.trim().orEmpty()
            val maker = Build.MANUFACTURER?.trim().orEmpty()
            return when {
                model.isEmpty() -> "Android"
                maker.isNotEmpty() && !model.startsWith(maker, ignoreCase = true) ->
                    maker.replaceFirstChar { it.uppercase() } + " " + model
                else -> model
            }
        }

        /**
         * Carrega a identidade, migrando a privada em claro para o Keystore sem trocar `id` nem
         * chaves (o pareamento continua válido). Se não houver identidade, gera uma nova.
         */
        @Synchronized
        fun getOrCreate(context: Context, cipher: KeystoreCipher = KeystoreCipher()): DeviceIdentity {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_ID, null)
            val pubStr = prefs.getString(KEY_PUB, null)
            val name = prefs.getString(KEY_NAME, null) ?: defaultName()

            if (id != null && pubStr != null) {
                val pub = CryptoEngine.b64uDecode(pubStr)
                val enc = prefs.getString(KEY_PRIV_ENC, null)
                val legacy = prefs.getString(KEY_PRIV_LEGACY, null)
                val priv: ByteArray? = when {
                    enc != null -> runCatching { cipher.decrypt(enc) }
                        .onFailure { Log.e(TAG, "Falha ao decifrar a chave privada: ${it.message}") }
                        .getOrNull()
                        ?: legacy?.let { CryptoEngine.b64uDecode(it) }
                    legacy != null -> CryptoEngine.b64uDecode(legacy)
                    else -> null
                }
                if (priv != null) {
                    if (legacy != null) migrateLegacy(prefs, cipher, priv)
                    return DeviceIdentity(id, name, pub, priv)
                }
                Log.w(TAG, "Identidade sem chave privada legível; gerando uma nova (exige novo pareamento).")
            }
            return generate(prefs, cipher, name)
        }

        /** Renomeia o aparelho localmente (o nome só chega ao desktop num novo pareamento). */
        fun rename(context: Context, newName: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_NAME, newName.trim()).apply()
        }

        private fun migrateLegacy(
            prefs: android.content.SharedPreferences,
            cipher: KeystoreCipher,
            priv: ByteArray
        ) {
            try {
                val sealed = cipher.encrypt(priv)
                // Confere a volta antes de apagar a cópia em claro.
                check(cipher.decrypt(sealed).contentEquals(priv))
                prefs.edit().putString(KEY_PRIV_ENC, sealed).remove(KEY_PRIV_LEGACY).commit()
                Log.i(TAG, "Chave privada migrada para o Android Keystore.")
            } catch (e: Exception) {
                // Sem Keystore utilizável: mantém o formato antigo para não perder o pareamento.
                Log.e(TAG, "Migração para o Keystore falhou: ${e.message}")
            }
        }

        private fun generate(
            prefs: android.content.SharedPreferences,
            cipher: KeystoreCipher,
            name: String
        ): DeviceIdentity {
            val random = SecureRandom()
            val gen = X25519KeyPairGenerator()
            gen.init(X25519KeyGenerationParameters(random))
            val pair = gen.generateKeyPair()
            val privBytes = (pair.private as X25519PrivateKeyParameters).encoded
            val pubBytes = (pair.public as X25519PublicKeyParameters).encoded

            val randomIdBytes = ByteArray(6).also { random.nextBytes(it) }
            val newId = "dev-" + CryptoEngine.b64uEncode(randomIdBytes)

            val editor = prefs.edit()
                .putString(KEY_ID, newId)
                .putString(KEY_NAME, name)
                .putString(KEY_PUB, CryptoEngine.b64uEncode(pubBytes))
            try {
                editor.putString(KEY_PRIV_ENC, cipher.encrypt(privBytes)).remove(KEY_PRIV_LEGACY)
            } catch (e: Exception) {
                Log.e(TAG, "Keystore indisponível; guardando a chave no formato antigo: ${e.message}")
                editor.putString(KEY_PRIV_LEGACY, CryptoEngine.b64uEncode(privBytes))
            }
            editor.commit()
            return DeviceIdentity(newId, name, pubBytes, privBytes)
        }
    }
}
