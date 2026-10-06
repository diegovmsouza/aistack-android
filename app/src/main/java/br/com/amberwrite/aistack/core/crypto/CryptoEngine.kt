package br.com.amberwrite.aistack.core.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    /** Base64url sem preenchimento (sem quebras de linha), igual ao `android.util.Base64` anterior. */
    fun b64uEncode(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /**
     * Decodifica base64url (aceita também preenchimento e o alfabeto padrão). Espaços e
     * quebras de linha são ignorados, como no `Base64.DEFAULT` do Android; qualquer outro
     * caractere inválido lança [IllegalArgumentException].
     */
    fun b64uDecode(s: String): ByteArray {
        val clean = s.filterNot { it.isWhitespace() }.trimEnd('=').replace('-', '+').replace('_', '/')
        val pad = when (clean.length % 4) {
            2 -> "=="
            3 -> "="
            else -> ""
        }
        return java.util.Base64.getDecoder().decode(clean + pad)
    }

    fun sha256Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calcula o segredo compartilhado X25519 (32 bytes).
     */
    /** Par X25519 novo (privada, pública), para a chave efêmera de cada conexão. */
    fun generateEphemeral(): Pair<ByteArray, ByteArray> {
        val gen = org.bouncycastle.crypto.generators.X25519KeyPairGenerator()
        gen.init(org.bouncycastle.crypto.params.X25519KeyGenerationParameters(java.security.SecureRandom()))
        val pair = gen.generateKeyPair()
        return (pair.private as X25519PrivateKeyParameters).encoded to (pair.public as X25519PublicKeyParameters).encoded
    }

    fun computeSharedSecret(myPrivBytes: ByteArray, peerPubBytes: ByteArray): ByteArray {
        val priv = X25519PrivateKeyParameters(myPrivBytes, 0)
        val pub = X25519PublicKeyParameters(peerPubBytes, 0)
        val agreement = X25519Agreement()
        agreement.init(priv)
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(pub, shared, 0)
        return shared
    }

    /**
     * Deriva chaves AES-256 (32 bytes) para o cliente via HKDF-SHA256:
     * - "aistack-tunnel-v1 c2h" -> sendKey (cliente envia para host)
     * - "aistack-tunnel-v1 h2c" -> recvKey (cliente recebe do host)
     */
    fun deriveTunnelKeys(sharedSecret: ByteArray): Pair<ByteArray, ByteArray> {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        // HKDF-Extract com salt vazio
        hkdf.init(HKDFParameters(sharedSecret, ByteArray(0), null))

        fun expand(info: String): ByteArray {
            val gen = HKDFBytesGenerator(SHA256Digest())
            gen.init(HKDFParameters(sharedSecret, ByteArray(0), info.toByteArray(Charsets.UTF_8)))
            val out = ByteArray(32)
            gen.generateBytes(out, 0, 32)
            return out
        }

        val h2c = expand("aistack-tunnel-v1 h2c")
        val c2h = expand("aistack-tunnel-v1 c2h")
        return Pair(c2h, h2c) // (sendKey, recvKey para o aparelho)
    }

    /**
     * Valida a prova de identidade do host (hostAuth):
     * 1. sha256(hostPk) == hostId
     * 2. Assinatura Ed25519 válida sobre transcript: "aistack-host-auth:" + clientHello + hostHello
     */
    fun verifyHostAuth(
        expectedHostId: String,
        hostEd25519Pk: ByteArray,
        signature: ByteArray,
        transcript: ByteArray
    ): Boolean {
        // Valida se o hash da chave bate com o host_id esperado do QR
        val computedHostId = sha256Hex(hostEd25519Pk)
        if (!computedHostId.equals(expectedHostId, ignoreCase = true)) {
            return false
        }

        return try {
            val pubParams = Ed25519PublicKeyParameters(hostEd25519Pk, 0)
            val signer = Ed25519Signer()
            signer.init(false, pubParams)
            signer.update(transcript, 0, transcript.size)
            signer.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Sessão cifrada do túnel E2E com perfil "a" (AES-256-GCM, contador u64 BE como AAD e anti-replay).
 */
class TunnelSession(
    private val sendKeyBytes: ByteArray,
    private val recvKeyBytes: ByteArray
) {
    private var sendCtr: Long = 0
    private var recvMax: Long = -1
    private val replayWindow = 256
    private val maxSkip = 4096
    private val recvSeen = BooleanArray(replayWindow)

    private val sendKeySpec = SecretKeySpec(sendKeyBytes, "AES")
    private val recvKeySpec = SecretKeySpec(recvKeyBytes, "AES")

    private fun ctrBytes(ctr: Long): ByteArray {
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(ctr).array()
    }

    private fun iv(ctr: Long): ByteArray {
        val iv = ByteArray(12)
        val cb = ctrBytes(ctr)
        System.arraycopy(cb, 0, iv, 0, 8)
        // 4 bytes finais são 0
        return iv
    }

    @Synchronized
    fun seal(payloadJson: String): ByteArray {
        val ctr = sendCtr++
        val plainBytes = payloadJson.toByteArray(Charsets.UTF_8)
        val aad = ctrBytes(ctr)
        val ivBytes = iv(ctr)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.ENCRYPT_MODE, sendKeySpec, spec)
        cipher.updateAAD(aad)
        val ct = cipher.doFinal(plainBytes)

        // Envelope: 8 bytes counter + 12 bytes IV + ct (com tag de 16B embutida)
        val out = ByteArray(8 + 12 + ct.size)
        System.arraycopy(aad, 0, out, 0, 8)
        System.arraycopy(ivBytes, 0, out, 8, 12)
        System.arraycopy(ct, 0, out, 20, ct.size)
        return out
    }

    @Synchronized
    fun open(frame: ByteArray): String {
        if (frame.size < 8 + 12 + 16) {
            throw IllegalArgumentException("Quadro do túnel curto demais")
        }

        val ctr = ByteBuffer.wrap(frame, 0, 8).order(ByteOrder.BIG_ENDIAN).long

        // Validação anti-replay antes de tentar decifrar
        if (recvMax >= 0) {
            if (ctr > recvMax) {
                if (ctr - recvMax > maxSkip) {
                    throw SecurityException("Salto de contador excedido")
                }
            } else {
                val back = (recvMax - ctr).toInt()
                if (back >= replayWindow) {
                    throw SecurityException("Mensagem antiga demais fora da janela")
                }
                if (recvSeen[back]) {
                    throw SecurityException("Replay detectado")
                }
            }
        }

        val ivBytes = ByteArray(12)
        System.arraycopy(frame, 8, ivBytes, 0, 12)
        val ctSize = frame.size - 20
        val ctBytes = ByteArray(ctSize)
        System.arraycopy(frame, 20, ctBytes, 0, ctSize)

        val aad = ctrBytes(ctr)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, recvKeySpec, spec)
        cipher.updateAAD(aad)
        val plainBytes = cipher.doFinal(ctBytes)

        // Marca como visto somente após autenticação do tag com sucesso
        if (recvMax < 0 || ctr > recvMax) {
            val shift = if (recvMax < 0) 0 else (ctr - recvMax).toInt().coerceAtMost(replayWindow)
            val newSeen = BooleanArray(replayWindow)
            newSeen[0] = true
            for (i in 0 until replayWindow) {
                if (recvSeen[i] && (i + shift) < replayWindow) {
                    newSeen[i + shift] = true
                }
            }
            System.arraycopy(newSeen, 0, recvSeen, 0, replayWindow)
            recvMax = ctr
        } else {
            val back = (recvMax - ctr).toInt()
            recvSeen[back] = true
        }

        return String(plainBytes, Charsets.UTF_8)
    }
}
