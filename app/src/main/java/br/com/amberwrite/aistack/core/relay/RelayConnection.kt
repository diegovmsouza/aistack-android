package br.com.amberwrite.aistack.core.relay

import android.util.Log
import br.com.amberwrite.aistack.core.crypto.CryptoEngine
import br.com.amberwrite.aistack.core.crypto.DeviceIdentity
import br.com.amberwrite.aistack.core.crypto.TunnelSession
import br.com.amberwrite.aistack.core.rpc.FrameSender
import br.com.amberwrite.aistack.core.rpc.RpcClient
import br.com.amberwrite.aistack.core.rpc.asStr
import br.com.amberwrite.aistack.core.rpc.bool
import br.com.amberwrite.aistack.core.rpc.long
import br.com.amberwrite.aistack.core.rpc.obj
import br.com.amberwrite.aistack.core.rpc.opt
import br.com.amberwrite.aistack.core.rpc.parseJsonOrNull
import br.com.amberwrite.aistack.core.rpc.str
import br.com.amberwrite.aistack.core.rpc.asObj
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

/**
 * Dona da ligação WebSocket com o relay: handshake E2E (spec 01 §§4–6, contrato v2 §17),
 * estados ([ConnectionState]), reconexão com backoff e entrega dos quadros decifrados ao
 * [RpcClient]. Uma única instância por processo (ver `AppContainer`).
 *
 * Regras de pareamento: o código de uso único sai **uma vez só**. Se a ligação cair depois
 * do envio, a próxima tentativa usa `keyAuth`; se o host aceitar, o pareamento conta como
 * concluído. O link só é entregue a [Listener.onPaired] depois do sucesso, já sem o código.
 */
class RelayConnection(
    private val identityProvider: () -> DeviceIdentity,
    private val rpc: RpcClient,
    private val scope: CoroutineScope,
    private val listener: Listener,
    private val okHttp: OkHttpClient = defaultHttpClient(),
    private val backoff: Backoff = Backoff()
) {
    interface Listener {
        /** Pareamento confirmado pelo host; [link] já vem sem código. */
        fun onPaired(link: PairLink)
        /** O host revogou este aparelho. */
        fun onRevoked()
    }

    private enum class Step { HELLO_SENT, AWAIT_HOST_AUTH, AWAIT_PAIR_RESULT, AWAIT_KEY_AUTH, ONLINE }

    /** Estado de uma tentativa (um WebSocket). */
    private inner class Attempt(val gen: Long, val link: PairLink, val identity: DeviceIdentity) {
        var ws: WebSocket? = null
        var step = Step.HELLO_SENT
        var tunnel: TunnelSession? = null
        var clientHello: ByteArray = ByteArray(0)
        var hostHello: ByteArray = ByteArray(0)
        var hostFrag = false
        var handshakeTimer: Job? = null
        val sendLock = Any()
    }

    private val tag = "RelayConnection"
    private val lock = Any()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var generation = 0L
    private var current: Attempt? = null
    private var link: PairLink? = null
    private var running = false
    private var reconnectJob: Job? = null
    private var attemptCount = 0

    /** Código de pareamento ainda não enviado (zera ao enviar; nunca é reenviado). */
    private var pendingCode: String? = null

    /** O código já saiu, mas o `pairResult` não chegou: o próximo `keyAuth` OK conclui o pareamento. */
    private var pairingInFlight = false

    /** Link atualmente em uso (sem o código). */
    val currentLink: PairLink? get() = synchronized(lock) { link }

    /**
     * Liga (ou religa) com [newLink]. Com `code`, faz o pareamento; sem, `keyAuth`.
     * Chamar de novo com o mesmo link sem código enquanto já liga não faz nada.
     */
    fun start(newLink: PairLink) {
        synchronized(lock) {
            val same = link == newLink.withoutCode() && newLink.code == null
            if (same && running && !_state.value.isTerminal) return
            closeCurrentLocked("trocando de ligação")
            link = newLink.withoutCode()
            pendingCode = newLink.code
            pairingInFlight = false
            running = true
            backoff.reset()
            attemptCount = 0
            connectLocked()
        }
    }

    /** Desliga e para de reconectar. */
    fun stop() {
        synchronized(lock) {
            running = false
            pendingCode = null
            pairingInFlight = false
            closeCurrentLocked("parado")
            _state.value = ConnectionState.Disconnected
        }
        rpc.detach()
    }

    /** Rede voltou (ou o usuário pediu): zera a espera e tenta já, se fizer sentido. */
    fun reconnectNow() {
        synchronized(lock) {
            backoff.reset()
            if (!running || _state.value.isTerminal) return
            val st = _state.value
            if (st is ConnectionState.Online || st is ConnectionState.Handshaking) return
            if (st is ConnectionState.Connecting && current != null) return
            closeCurrentLocked("reconectando")
            connectLocked()
        }
    }

    // ---------------------------------------------------------------- conexão

    private fun connectLocked() {
        reconnectJob?.cancel()
        reconnectJob = null
        val l = link ?: return
        val identity = try {
            identityProvider()
        } catch (e: Exception) {
            _state.value = ConnectionState.Error("Falha ao ler a identidade do aparelho: ${e.message}", null)
            return
        }
        val attempt = Attempt(++generation, l, identity)
        current = attempt
        attemptCount++
        _state.value = ConnectionState.Connecting(attemptCount)
        val url = "${l.relay.trimEnd('/')}/client?host=${l.host}&device=${identity.id}"
        Log.i(tag, "Conectando ao relay (tentativa $attemptCount)")
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            _state.value = ConnectionState.AuthRejected("Endereço do relay inválido: ${l.relay}", pendingCode != null)
            running = false
            return
        }
        attempt.ws = okHttp.newWebSocket(request, SocketListener(attempt))
    }

    private fun closeCurrentLocked(reason: String) {
        reconnectJob?.cancel()
        reconnectJob = null
        current?.let {
            it.handshakeTimer?.cancel()
            it.ws?.close(1000, reason)
        }
        current = null
        generation++
    }

    private fun isCurrent(a: Attempt) = synchronized(lock) { current === a && a.gen == generation }

    /** Fecha a tentativa [a] e agenda nova com backoff (se ainda estiver ligada). */
    private fun dropAndRetry(a: Attempt, message: String, hostOffline: Boolean = false) {
        synchronized(lock) {
            if (current !== a) return
            a.handshakeTimer?.cancel()
            a.ws?.close(1000, null)
            current = null
            generation++
            if (!running) {
                _state.value = ConnectionState.Disconnected
            } else {
                val delayMs = backoff.nextDelayMs()
                _state.value = if (hostOffline) ConnectionState.HostOffline(delayMs)
                else ConnectionState.Error(message, delayMs)
                reconnectJob = scope.launch {
                    delay(delayMs)
                    synchronized(lock) {
                        if (running && current == null && !_state.value.isTerminal) connectLocked()
                    }
                }
            }
        }
        rpc.detach()
    }

    private fun terminal(a: Attempt, newState: ConnectionState) {
        synchronized(lock) {
            if (current !== a) return
            a.handshakeTimer?.cancel()
            a.ws?.close(1000, null)
            current = null
            generation++
            running = false
            pendingCode = null
            pairingInFlight = false
            _state.value = newState
        }
        rpc.detach()
    }

    // ---------------------------------------------------------------- handshake

    private inner class SocketListener(private val a: Attempt) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isCurrent(a)) return
            _state.value = ConnectionState.Handshaking
            val hello = RelayProtocol.HelloMessage(
                k = a.identity.publicKeyB64Url(),
                aead = "a",
                caps = listOf(RelayProtocol.CAP_FRAG)
            )
            val bytes = RelayProtocol.toJson(hello).toByteArray(Charsets.UTF_8)
            a.clientHello = bytes
            a.step = Step.HELLO_SENT
            webSocket.send(bytes.toByteString())
            a.handshakeTimer = scope.launch {
                delay(HANDSHAKE_TIMEOUT_MS)
                if (isCurrent(a) && a.step != Step.ONLINE) {
                    dropAndRetry(a, "O desktop não concluiu o handshake a tempo.")
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!isCurrent(a)) return
            try {
                onBinary(a, bytes.toByteArray())
            } catch (e: SecurityException) {
                Log.w(tag, "Falha de segurança no túnel: ${e.message}")
                dropAndRetry(a, e.message ?: "Falha de segurança no túnel.")
            } catch (e: Exception) {
                Log.w(tag, "Quadro inválido: ${e.message}")
                dropAndRetry(a, "Quadro inválido do desktop: ${e.message}")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent(a)) return
            val type = parseJsonOrNull(text).asObj()?.str("type")
            if (type == "host_offline") dropAndRetry(a, "O desktop está offline.", hostOffline = true)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent(a)) return
            dropAndRetry(a, if (reason.isNotBlank()) "Conexão fechada: $reason" else "Conexão fechada.")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent(a)) return
            val msg = when (response?.code) {
                null -> t.message ?: "Falha de conexão."
                else -> "O relay respondeu ${response.code}."
            }
            dropAndRetry(a, msg)
        }
    }

    private fun onBinary(a: Attempt, raw: ByteArray) {
        when (a.step) {
            Step.HELLO_SENT -> {
                val hello = parseJsonOrNull(String(raw, Charsets.UTF_8)).asObj()
                    ?: throw IllegalStateException("hello do host ilegível")
                if (hello.str("t") != "hello") throw IllegalStateException("o host não respondeu hello")
                val hostPub = CryptoEngine.b64uDecode(hello.str("k") ?: throw IllegalStateException("hello sem chave"))
                a.hostHello = raw
                a.hostFrag = hello.get("caps")?.let { caps ->
                    (caps as? com.google.gson.JsonArray)?.any { it.asStr() == RelayProtocol.CAP_FRAG }
                } ?: false
                val shared = CryptoEngine.computeSharedSecret(a.identity.privateKeyBytes, hostPub)
                val (sendKey, recvKey) = CryptoEngine.deriveTunnelKeys(shared)
                a.tunnel = TunnelSession(sendKey, recvKey)
                a.step = Step.AWAIT_HOST_AUTH
            }
            Step.AWAIT_HOST_AUTH -> {
                val frame = openFrame(a, raw)
                if (frame.str("t") != "hostAuth") throw IllegalStateException("esperava hostAuth")
                val pk = CryptoEngine.b64uDecode(frame.str("pk") ?: "")
                val sig = CryptoEngine.b64uDecode(frame.str("sig") ?: "")
                val prefix = HOST_AUTH_PREFIX.toByteArray(Charsets.UTF_8)
                val transcript = prefix + a.clientHello + a.hostHello
                if (!CryptoEngine.verifyHostAuth(a.link.host, pk, sig, transcript)) {
                    throw SecurityException("A identidade do desktop não confere (possível interceptação).")
                }
                val code = synchronized(lock) { pendingCode.also { pendingCode = null } }
                if (code != null) {
                    synchronized(lock) { pairingInFlight = true }
                    a.step = Step.AWAIT_PAIR_RESULT
                    val msg = RelayProtocol.PairMessage(
                        code = code,
                        device = RelayProtocol.PairDevicePayload(
                            id = a.identity.id,
                            name = a.identity.name,
                            pk = a.identity.publicKeyB64Url()
                        )
                    )
                    sendSealed(a, listOf(RelayProtocol.toJson(msg)))
                } else {
                    a.step = Step.AWAIT_KEY_AUTH
                    val msg = RelayProtocol.RpcMessage(
                        id = KEY_AUTH_ID,
                        method = "keyAuth",
                        params = mapOf("pk" to a.identity.publicKeyB64Url())
                    )
                    sendSealed(a, listOf(RelayProtocol.toJson(msg)))
                }
            }
            Step.AWAIT_PAIR_RESULT -> {
                val frame = openFrame(a, raw)
                when (frame.str("t")) {
                    "pairResult" -> {
                        if (frame.bool("ok") == true) {
                            goOnline(a, paired = true)
                        } else {
                            val err = frame.str("error") ?: "Pareamento recusado pelo desktop."
                            terminal(a, ConnectionState.AuthRejected(err, duringPairing = true))
                        }
                    }
                    "close" -> onClose(a, frame)
                    else -> Log.d(tag, "Quadro inesperado antes do pairResult: ${frame.str("t")}")
                }
            }
            Step.AWAIT_KEY_AUTH -> {
                val frame = openFrame(a, raw)
                when (frame.str("t")) {
                    "rpcResult" -> if (frame.long("id") == KEY_AUTH_ID) onKeyAuthResult(a, frame)
                    "close" -> onClose(a, frame)
                    else -> Log.d(tag, "Quadro inesperado antes do keyAuth: ${frame.str("t")}")
                }
            }
            Step.ONLINE -> {
                val frame = openFrame(a, raw)
                when (frame.str("t")) {
                    "close" -> onClose(a, frame)
                    // Um segundo rpcResult do keyAuth pode chegar; é ignorado.
                    "rpcResult" -> if (frame.long("id") != KEY_AUTH_ID) rpc.onMessage(frame)
                    else -> rpc.onMessage(frame)
                }
            }
        }
    }

    private fun onKeyAuthResult(a: Attempt, frame: JsonObject) {
        val error = frame.str("error")
        val okFlag = frame.obj("result")?.bool("ok")
        val wasPairing = synchronized(lock) { pairingInFlight }
        if (error != null || okFlag == false) {
            val msg = if (wasPairing) {
                "O pareamento não foi concluído. Gere um novo QR no desktop e tente de novo."
            } else {
                error ?: "O desktop recusou a chave deste aparelho."
            }
            terminal(a, ConnectionState.AuthRejected(msg, duringPairing = wasPairing))
            return
        }
        goOnline(a, paired = wasPairing)
    }

    private fun goOnline(a: Attempt, paired: Boolean) {
        synchronized(lock) {
            if (current !== a) return
            a.step = Step.ONLINE
            a.handshakeTimer?.cancel()
            pairingInFlight = false
            backoff.reset()
            attemptCount = 0
        }
        if (paired) listener.onPaired(a.link.withoutCode())
        rpc.attach(FrameSender { frames -> sendSealed(a, frames) }, a.hostFrag)
        _state.value = ConnectionState.Online(a.hostFrag)
        Log.i(tag, "Túnel online (frag=${a.hostFrag})")
    }

    private fun onClose(a: Attempt, frame: JsonObject) {
        val reason = frame.str("reason") ?: ""
        if (reason == "revoked") {
            terminal(a, ConnectionState.Revoked)
            listener.onRevoked()
        } else {
            dropAndRetry(a, "O desktop encerrou a sessão${if (reason.isNotBlank()) ": $reason" else "."}")
        }
    }

    private fun openFrame(a: Attempt, raw: ByteArray): JsonObject {
        val tunnel = a.tunnel ?: throw IllegalStateException("túnel sem chaves")
        val text = tunnel.open(raw)
        return parseJsonOrNull(text).asObj() ?: throw IllegalStateException("quadro JSON inválido")
    }

    private fun sendSealed(a: Attempt, frames: List<String>): Boolean {
        val tunnel = a.tunnel ?: return false
        val ws = a.ws ?: return false
        synchronized(a.sendLock) {
            if (!isCurrent(a)) return false
            for (f in frames) {
                if (!ws.send(tunnel.seal(f).toByteString())) return false
            }
        }
        return true
    }

    companion object {
        const val KEY_AUTH_ID = 1L
        const val HANDSHAKE_TIMEOUT_MS = 20_000L
        private const val HOST_AUTH_PREFIX = "aistack-host-auth:"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}

