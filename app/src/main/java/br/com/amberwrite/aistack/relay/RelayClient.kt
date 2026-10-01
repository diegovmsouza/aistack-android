package br.com.amberwrite.aistack.relay

import android.content.Context
import android.util.Log
import br.com.amberwrite.aistack.crypto.CryptoEngine
import br.com.amberwrite.aistack.crypto.DeviceIdentity
import br.com.amberwrite.aistack.crypto.TunnelSession
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class RelayState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,
    ONLINE,
    ERROR
}

data class RelayEvent(val event: String, val payload: JsonElement?)

class RelayClient(
    private val context: Context,
    val link: PairLink,
    val identity: DeviceIdentity
) {
    private val tag = "RelayClient"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(RelayState.DISCONNECTED)
    val state: StateFlow<RelayState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RelayEvent> = _events.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var webSocket: WebSocket? = null
    private var tunnelSession: TunnelSession? = null
    private val nextRpcId = AtomicLong(1)
    private val pendingRpcs = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Sem timeout de leitura para WS persistente
        .build()

    private var isPairing = link.code != null
    private var isManuallyClosed = false

    // Estado temporário do handshake
    private var handshakeStep = 0
    private var keyAuthRpcId: Long? = null
    private var clientHelloBytes: ByteArray? = null
    /** Privada X25519 efêmera da conexão atual; some quando o handshake deriva as chaves. */
    private var ephemeralPriv: ByteArray? = null
    private var hostHelloBytes: ByteArray? = null

    fun connect() {
        isManuallyClosed = false
        _state.value = RelayState.CONNECTING
        _lastError.value = null

        val relayUrl = link.relay.trimEnd('/')
        val url = "$relayUrl/client?host=${link.host}&device=${identity.id}"
        Log.i(tag, "Conectando ao relay: $url")

        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    fun disconnect() {
        isManuallyClosed = true
        _state.value = RelayState.DISCONNECTED
        webSocket?.close(1000, "Desconexão solicitada pelo usuário")
        webSocket = null
        tunnelSession = null
        handshakeStep = 0
    }

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            Log.i(tag, "WebSocket conectado ao relay. Iniciando handshake E2E.")
            _state.value = RelayState.HANDSHAKING
            handshakeStep = 1

            // 1. Enviar Hello em claro com a chave X25519 persistente do aparelho e uma efêmera nova
            // desta conexão (R-173): roubar a persistente depois não decifra o tráfego gravado.
            val (ephPriv, ephPub) = CryptoEngine.generateEphemeralKeyPair()
            ephemeralPriv = ephPriv
            val hello = RelayProtocol.HelloMessage(
                t = "hello",
                k = identity.publicKeyB64Url(),
                aead = "a",
                e = CryptoEngine.b64uEncode(ephPub)
            )
            val helloJson = RelayProtocol.toJson(hello)
            val bytes = helloJson.toByteArray(Charsets.UTF_8)
            clientHelloBytes = bytes
            ws.send(ByteString.of(*bytes))
        }

        override fun onMessage(ws: WebSocket, bytes: ByteString) {
            val raw = bytes.toByteArray()
            try {
                handleIncomingBinary(ws, raw)
            } catch (e: Exception) {
                Log.e(tag, "Erro processando quadro: ${e.message}", e)
                _lastError.value = e.message
                _state.value = RelayState.ERROR
            }
        }

        override fun onMessage(ws: WebSocket, text: String) {
            // Mensagens JSON de controle do relay (ex: host_offline)
            Log.d(tag, "Mensagem de texto do relay: $text")
            try {
                val json = gson.fromJson(text, JsonObject::class.java)
                val type = json.get("type")?.asString
                if (type == "host_offline") {
                    _lastError.value = "Host desktop está offline"
                    _state.value = RelayState.DISCONNECTED
                }
            } catch (e: Exception) {
                // ignorar
            }
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.i(tag, "WebSocket fechado: $code - $reason")
            _state.value = RelayState.DISCONNECTED
            if (!isManuallyClosed) {
                scheduleReconnect()
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(tag, "Falha na conexão do WebSocket: ${t.message}")
            _lastError.value = t.message ?: "Falha de conexão"
            _state.value = RelayState.ERROR
            if (!isManuallyClosed) {
                scheduleReconnect()
            }
        }
    }

    private fun handleIncomingBinary(ws: WebSocket, raw: ByteArray) {
        when (handshakeStep) {
            1 -> {
                // Resposta do host: Hello em claro com chave efêmera
                hostHelloBytes = raw
                val hostHelloJson = String(raw, Charsets.UTF_8)
                val hostHello = RelayProtocol.fromJson(hostHelloJson, RelayProtocol.HelloMessage::class.java)
                if (hostHello.t != "hello") {
                    throw IllegalStateException("Host não respondeu hello: $hostHelloJson")
                }

                // Derivar chaves X25519 -> HKDF
                val hostPubBytes = CryptoEngine.b64uDecode(hostHello.k)
                val ephPriv = ephemeralPriv ?: throw IllegalStateException("Chave efêmera ausente")
                val sharedSecret = CryptoEngine.deriveSessionSecret(identity.privateKeyBytes, ephPriv, hostPubBytes)
                ephemeralPriv = null
                val (sendKey, recvKey) = CryptoEngine.deriveTunnelKeys(sharedSecret)

                tunnelSession = TunnelSession(sendKey, recvKey)
                handshakeStep = 2
                Log.d(tag, "Handshake passo 1 OK: chaves derivadas. Aguardando hostAuth...")
            }
            2 -> {
                // HostAuth cifrado no túnel
                val session = tunnelSession ?: throw IllegalStateException("Sessão não inicializada")
                val plainJson = session.open(raw)
                val auth = RelayProtocol.fromJson(plainJson, RelayProtocol.HostAuthMessage::class.java)
                if (auth.t != "hostAuth") {
                    throw IllegalStateException("Esperava hostAuth, recebeu: $plainJson")
                }

                // Verificar assinatura do hostAuth
                val hostEd25519Pk = CryptoEngine.b64uDecode(auth.pk)
                val hostSig = CryptoEngine.b64uDecode(auth.sig)

                val prefix = "aistack-host-auth:".toByteArray(Charsets.UTF_8)
                val cHello = clientHelloBytes ?: ByteArray(0)
                val hHello = hostHelloBytes ?: ByteArray(0)

                val transcript = ByteArray(prefix.size + cHello.size + hHello.size)
                System.arraycopy(prefix, 0, transcript, 0, prefix.size)
                System.arraycopy(cHello, 0, transcript, prefix.size, cHello.size)
                System.arraycopy(hHello, 0, transcript, prefix.size + cHello.size, hHello.size)

                val isHostValid = CryptoEngine.verifyHostAuth(link.host, hostEd25519Pk, hostSig, transcript)
                if (!isHostValid) {
                    throw SecurityException("Prova de identidade do Host inválida! Possível MITM.")
                }

                Log.i(tag, "Identidade do Host autenticada com sucesso via Ed25519.")

                // Passo 3: Enviar pedido de pareamento (se tem code) ou keyAuth
                if (isPairing && link.code != null) {
                    handshakeStep = 3
                    val pairMsg = RelayProtocol.PairMessage(
                        code = link.code,
                        device = RelayProtocol.PairDevicePayload(
                            id = identity.id,
                            name = identity.name,
                            pk = identity.publicKeyB64Url()
                        )
                    )
                    sendSealed(ws, session, RelayProtocol.toJson(pairMsg))
                } else {
                    handshakeStep = 4
                    // keyAuth via RPC
                    val rpcId = nextRpcId.getAndIncrement()
                    keyAuthRpcId = rpcId
                    val keyAuthMsg = RelayProtocol.RpcMessage(
                        id = rpcId,
                        method = "keyAuth",
                        params = mapOf("pk" to identity.publicKeyB64Url())
                    )
                    sendSealed(ws, session, RelayProtocol.toJson(keyAuthMsg))
                }
            }
            3 -> {
                // Resposta do Pareamento (PairResult)
                val session = tunnelSession ?: throw IllegalStateException("Sessão não inicializada")
                val plainJson = session.open(raw)
                val pairResult = RelayProtocol.fromJson(plainJson, RelayProtocol.PairResultMessage::class.java)
                if (pairResult.ok) {
                    Log.i(tag, "Aparelho pareado com sucesso no AiStack Host!")
                    isPairing = false
                    _state.value = RelayState.ONLINE
                    handshakeStep = 5
                } else {
                    throw SecurityException("Pareamento recusado: ${pairResult.error}")
                }
            }
            4, 5 -> {
                // Fluxo normal do túnel (ONLINE): mensagens RPC ou Eventos
                val session = tunnelSession ?: return
                val plainJson = session.open(raw)
                handleTunnelMessage(plainJson)
            }
        }
    }

    private fun handleTunnelMessage(json: String) {
        try {
            val root = gson.fromJson(json, JsonObject::class.java)
            val type = root.get("t")?.asString

            when (type) {
                "rpcResult" -> {
                    val id = root.get("id")?.asLong ?: return
                    val deferred = pendingRpcs.remove(id)
                    if (deferred != null) {
                        val error = root.get("error")?.asString
                        if (error != null) {
                            deferred.completeExceptionally(RuntimeException(error))
                        } else {
                            deferred.complete(root.get("result") ?: root)
                        }
                    } else if ((id == 1L || id == keyAuthRpcId) && handshakeStep == 4) {
                        // Resposta do keyAuth inicial
                        Log.i(tag, "keyAuth autenticado com sucesso! Túnel ONLINE.")
                        _state.value = RelayState.ONLINE
                        handshakeStep = 5
                    }
                }
                "event" -> {
                    val eventName = root.get("event")?.asString ?: "unknown"
                    val payload = root.get("payload")
                    scope.launch {
                        _events.emit(RelayEvent(eventName, payload))
                    }
                }
                "close" -> {
                    val reason = root.get("reason")?.asString ?: "fechado"
                    Log.w(tag, "Host encerrou sessão do túnel: $reason")
                    if (reason == "revoked") {
                        _lastError.value = "Aparelho foi revogado pelo Host"
                    }
                    disconnect()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Erro ao interpretar mensagem do túnel: ${e.message}", e)
        }
    }

    private fun sendSealed(ws: WebSocket, session: TunnelSession, jsonPayload: String) {
        val sealed = session.seal(jsonPayload)
        ws.send(ByteString.of(*sealed))
    }

    /**
     * Envia uma chamada RPC pelo túnel cifrado e aguarda o resultado.
     */
    suspend fun call(method: String, params: Any? = null): JsonElement {
        val session = tunnelSession ?: throw IllegalStateException("Túnel não conectado")
        val ws = webSocket ?: throw IllegalStateException("WebSocket não conectado")

        val id = nextRpcId.getAndIncrement()
        val rpc = RelayProtocol.RpcMessage(id = id, method = method, params = params)
        val json = RelayProtocol.toJson(rpc)

        val deferred = CompletableDeferred<JsonElement>()
        pendingRpcs[id] = deferred

        sendSealed(ws, session, json)

        return deferred.await()
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(3000)
            if (!isManuallyClosed && _state.value != RelayState.ONLINE) {
                Log.i(tag, "Tentando reconectar...")
                connect()
            }
        }
    }
}
