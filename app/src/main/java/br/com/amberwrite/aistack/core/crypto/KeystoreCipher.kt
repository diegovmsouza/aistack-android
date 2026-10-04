package br.com.amberwrite.aistack.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra pequenos segredos locais (ex.: a chave privada X25519 do aparelho) com uma chave
 * AES-256-GCM que nunca sai do Android Keystore.
 *
 * Formato do texto cifrado: `v1.<iv base64url>.<ct+tag base64url>`.
 */
class KeystoreCipher(private val alias: String = DEFAULT_ALIAS) {

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(plain)
        return "v1." + CryptoEngine.b64uEncode(cipher.iv) + "." + CryptoEngine.b64uEncode(ct)
    }

    fun decrypt(sealed: String): ByteArray {
        val parts = sealed.split('.')
        require(parts.size == 3 && parts[0] == "v1") { "Formato de segredo cifrado desconhecido" }
        val iv = CryptoEngine.b64uDecode(parts[1])
        val ct = CryptoEngine.b64uDecode(parts[2])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    companion object {
        const val DEFAULT_ALIAS = "aistack_identity_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
