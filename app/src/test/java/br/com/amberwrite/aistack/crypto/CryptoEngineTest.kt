package br.com.amberwrite.aistack.crypto

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class CryptoEngineTest {

    @Test
    fun testX25519DiffieHellmanAgreement() {
        val random = SecureRandom()
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(random))

        val alice = gen.generateKeyPair()
        val bob = gen.generateKeyPair()

        val alicePriv = (alice.private as X25519PrivateKeyParameters).encoded
        val alicePub = (alice.public as X25519PublicKeyParameters).encoded

        val bobPriv = (bob.private as X25519PrivateKeyParameters).encoded
        val bobPub = (bob.public as X25519PublicKeyParameters).encoded

        val sharedAlice = CryptoEngine.computeSharedSecret(alicePriv, bobPub)
        val sharedBob = CryptoEngine.computeSharedSecret(bobPriv, alicePub)

        assertEquals(32, sharedAlice.size)
        assertEquals(32, sharedBob.size)
        assertTrue(sharedAlice.contentEquals(sharedBob))
    }

    @Test
    fun testTunnelSessionSealAndOpen() {
        val random = SecureRandom()
        val key1 = ByteArray(32).also { random.nextBytes(it) }
        val key2 = ByteArray(32).also { random.nextBytes(it) }

        // Host envia com key1, recebe com key2.
        // Client envia com key2, recebe com key1.
        val clientSession = TunnelSession(sendKeyBytes = key2, recvKeyBytes = key1)
        val hostSession = TunnelSession(sendKeyBytes = key1, recvKeyBytes = key2)

        val clientMsg = """{"t":"rpc","id":1,"method":"listConversations"}"""
        val sealedByClient = clientSession.seal(clientMsg)

        val openedByHost = hostSession.open(sealedByClient)
        assertEquals(clientMsg, openedByHost)

        val hostReply = """{"t":"rpcResult","id":1,"result":{"ok":true}}"""
        val sealedByHost = hostSession.seal(hostReply)

        val openedByClient = clientSession.open(sealedByHost)
        assertEquals(hostReply, openedByClient)
    }

    @Test
    fun testAntiReplayRejection() {
        val key = ByteArray(32)
        val clientSession = TunnelSession(sendKeyBytes = key, recvKeyBytes = key)
        val hostSession = TunnelSession(sendKeyBytes = key, recvKeyBytes = key)

        val frame = clientSession.seal("test")
        hostSession.open(frame)

        // Reenviar o mesmo quadro deve falhar com SecurityException
        var rejected = false
        try {
            hostSession.open(frame)
        } catch (e: SecurityException) {
            rejected = true
        }
        assertTrue("Replay de mensagem deve ser recusado", rejected)
    }

    @Test
    fun testEd25519HostAuthVerification() {
        val random = SecureRandom()
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(random))
        val pair = gen.generateKeyPair()

        val hostPub = (pair.public as Ed25519PublicKeyParameters).encoded
        val hostPriv = pair.private as Ed25519PrivateKeyParameters

        val hostId = CryptoEngine.sha256Hex(hostPub)

        val transcript = "aistack-host-auth:helloClienthelloHost".toByteArray(Charsets.UTF_8)
        val signer = Ed25519Signer()
        signer.init(true, hostPriv)
        signer.update(transcript, 0, transcript.size)
        val sig = signer.generateSignature()

        // Validação positiva
        val valid = CryptoEngine.verifyHostAuth(hostId, hostPub, sig, transcript)
        assertTrue(valid)

        // Validação negativa com hostId incorreto
        val invalidHost = CryptoEngine.verifyHostAuth("wrong_host_id", hostPub, sig, transcript)
        assertFalse(invalidHost)
    }

    @Test
    fun testVetorFixoDoSigiloFuturoConfereComONucleo() {
        // Vetor do relay/PROTOCOL.md (§Sigilo futuro), gerado pelo núcleo em Rust e conferido em Node.
        val staticPriv = ByteArray(32) { 0x22 }
        val ephPriv = ByteArray(32) { 0x33 }
        val hostPub = java.util.Base64.getUrlDecoder().decode("e06Qm75__kTEZaIgA31gjuNYl9Me-XLwf3SJLLD3PxM")
        val ikm = CryptoEngine.deriveSessionSecret(staticPriv, ephPriv, hostPub)
        assertEquals(64, ikm.size)
        val (sendKey, recvKey) = CryptoEngine.deriveTunnelKeys(ikm)
        val session = TunnelSession(sendKey, recvKey)
        val frame = "00000000000000000000000000000000000000009c3317863c39500429ee10856cd706990a19262c96f94ab32d264be226b3f0e1154afbc83ec5f850f7c478"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals("""{"t":"close","reason":"ok"}""", session.open(frame))
    }

    @Test
    fun testParEfemeroTemTamanhoEhNovoACadaChamada() {
        val (priv1, pub1) = CryptoEngine.generateEphemeralKeyPair()
        val (_, pub2) = CryptoEngine.generateEphemeralKeyPair()
        assertEquals(32, priv1.size)
        assertEquals(32, pub1.size)
        assertFalse(pub1.contentEquals(pub2))
    }
}
