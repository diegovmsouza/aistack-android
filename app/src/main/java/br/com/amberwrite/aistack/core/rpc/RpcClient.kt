package br.com.amberwrite.aistack.core.rpc

import br.com.amberwrite.aistack.core.relay.RelayProtocol
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Contrato mínimo que os repositórios usam para falar com o host. [RpcClient] é a
 * implementação real; os testes usam uma falsa.
 */
interface RpcCaller {
    /** Eventos do host e os locais ([HostEvent.Connected]/[HostEvent.Disconnected]). */
    val events: SharedFlow<HostEvent>

    /** Verdadeiro com o túnel autenticado. */
    val connected: StateFlow<Boolean>

    /** O host ecoou `frag` nesta sessão do túnel. */
    val hostFrag: Boolean

    /**
     * Faz uma chamada e suspende até o `rpcResult`. Devolve `result` (ou [JsonNull]).
     * @throws RpcException em erro do host, tempo esgotado, túnel fechado ou falha de fragmentação.
     */
    suspend fun call(method: String, params: Any? = null, timeoutMs: Long = RpcClient.DEFAULT_TIMEOUT_MS): JsonElement
}

/** Envia o JSON de um quadro pelo túnel (selando-o). Devolve falso se o túnel caiu. */
fun interface FrameSender {
    fun send(frames: List<String>): Boolean
}

/**
 * Cliente RPC sobre o túnel E2E: ids a partir de 2, prazo por chamada, respostas fora de
 * ordem, remontagem de `rpcPart` e envio fragmentado (só com o eco de `frag`).
 *
 * A [RelayConnection][br.com.amberwrite.aistack.core.relay.RelayConnection] chama [attach]
 * quando o túnel fica online, [onMessage] para cada quadro decifrado e [detach] na queda.
 */
class RpcClient(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickMs: Long = 500L
) : RpcCaller {

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
        /** Prazo mínimo de uma chamada cujo pedido ou resposta é fragmentado. */
        const val FRAGMENTED_MIN_TIMEOUT_MS = 60_000L
        /** O id 1 é reservado ao `keyAuth`. */
        const val FIRST_ID = 2L
    }

    private class Pending(
        val method: String,
        val deferred: CompletableDeferred<JsonElement>,
        @Volatile var deadline: Long
    )

    private val lock = Any()
    private val pending = HashMap<Long, Pending>()
    private val assembler = RpcPartAssembler(clock)
    private var sender: FrameSender? = null
    private var nextId = FIRST_ID
    private var tickJob: Job? = null

    @Volatile
    override var hostFrag: Boolean = false
        private set

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _events = MutableSharedFlow<HostEvent>(extraBufferCapacity = 1024)
    override val events: SharedFlow<HostEvent> = _events.asSharedFlow()

    /** Túnel autenticado: nova sessão (ids e remontagens recomeçam). */
    fun attach(sender: FrameSender, hostFrag: Boolean) {
        synchronized(lock) {
            failAllLocked("Sessão do túnel reiniciada.")
            this.sender = sender
            this.hostFrag = hostFrag
            nextId = FIRST_ID
            assembler.reset()
        }
        _connected.value = true
        startTicker()
        emit(HostEvent.Connected(hostFrag))
    }

    /** Túnel caiu: todas as chamadas pendentes falham com [RpcException.Kind.DISCONNECTED]. */
    fun detach() {
        val wasAttached: Boolean
        synchronized(lock) {
            wasAttached = sender != null
            sender = null
            hostFrag = false
            assembler.reset()
            failAllLocked(null)
        }
        tickJob?.cancel()
        tickJob = null
        _connected.value = false
        if (wasAttached) emit(HostEvent.Disconnected)
    }

    override suspend fun call(method: String, params: Any?, timeoutMs: Long): JsonElement {
        val deferred = CompletableDeferred<JsonElement>()
        val id: Long
        synchronized(lock) {
            val s = sender ?: throw RpcException.disconnected(method)
            id = nextId++
            val json = RelayProtocol.toJson(RelayProtocol.RpcMessage(id = id, method = method, params = params))
            val size = json.toByteArray(Charsets.UTF_8).size
            val frames: List<String>
            var timeout = timeoutMs
            when {
                RpcFragmenter.shouldFragment(size, hostFrag) -> {
                    if (size > RpcFrag.MAX_TOTAL) {
                        throw RpcException(RpcException.Kind.TOO_LARGE, "Mensagem grande demais para o túnel (máx. 8 MiB).", method)
                    }
                    frames = RpcFragmenter.split(id, json)
                    timeout = maxOf(timeout, FRAGMENTED_MIN_TIMEOUT_MS)
                }
                size > RpcFrag.MAX_UNFRAGMENTED -> throw RpcException(
                    RpcException.Kind.TOO_LARGE,
                    "Mensagem grande demais: o AiStack do desktop não aceita mensagens fragmentadas.",
                    method
                )
                else -> frames = listOf(json)
            }
            pending[id] = Pending(method, deferred, clock() + timeout)
            if (!s.send(frames)) {
                pending.remove(id)
                throw RpcException.disconnected(method)
            }
        }
        try {
            return deferred.await()
        } finally {
            synchronized(lock) { pending.remove(id) }
        }
    }

    /** Processa um quadro já decifrado (exceto handshake e `close`, tratados pela conexão). */
    fun onMessage(frame: JsonObject) {
        when (frame.str("t")) {
            "rpcResult" -> frame.long("id")?.let { complete(it, frame) }
            "rpcPart" -> onPart(frame)
            "event" -> {
                val name = frame.str("event") ?: return
                emit(EventParser.parse(name, frame.opt("payload")))
            }
        }
    }

    private fun onPart(frame: JsonObject) {
        val id = frame.long("id") ?: return
        val result = synchronized(lock) {
            val r = assembler.accept(
                id = id,
                seq = frame.int("seq") ?: -1,
                last = frame.bool("last") ?: false,
                data = frame.str("data") ?: "",
                fragNegotiated = hostFrag
            )
            if (r is RpcPartAssembler.Result.Incomplete) {
                // Resposta grande chegando: o prazo passa a ser o da remontagem.
                val started = assembler.startedAt(id)
                val p = pending[id]
                if (started != null && p != null) {
                    p.deadline = maxOf(p.deadline, started + RpcFrag.TIMEOUT_MS + tickMs * 2)
                }
            }
            r
        }
        when (result) {
            is RpcPartAssembler.Result.Complete -> complete(id, result.frame)
            is RpcPartAssembler.Result.Failed -> failPart(result)
            else -> Unit
        }
    }

    private fun failPart(f: RpcPartAssembler.Result.Failed) {
        val p = synchronized(lock) { pending.remove(f.id) } ?: return
        val kind = if (f.timeout) RpcException.Kind.TIMEOUT else RpcException.Kind.FRAGMENT
        p.deferred.completeExceptionally(RpcException(kind, f.message, p.method))
    }

    private fun complete(id: Long, frame: JsonObject) {
        val p = synchronized(lock) { pending.remove(id) } ?: return
        val error = frame.str("error")
        if (error != null) {
            p.deferred.completeExceptionally(RpcException.fromRemote(p.method, error))
        } else {
            p.deferred.complete(frame.get("result") ?: JsonNull.INSTANCE)
        }
    }

    /** Verifica prazos de chamadas e remontagens. Público para os testes (relógio falso). */
    fun tick() {
        val now = clock()
        val expiredParts: List<RpcPartAssembler.Result.Failed>
        val expiredCalls = ArrayList<Pair<Long, Pending>>()
        synchronized(lock) {
            expiredParts = assembler.sweepExpired()
            for ((id, p) in pending) if (now > p.deadline) expiredCalls += id to p
            expiredCalls.forEach { pending.remove(it.first) }
        }
        expiredParts.forEach(::failPart)
        for ((id, p) in expiredCalls) {
            p.deferred.completeExceptionally(
                RpcException(RpcException.Kind.TIMEOUT, "Tempo esgotado esperando ${p.method} (id $id).", p.method)
            )
        }
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(tickMs)
                tick()
            }
        }
    }

    private fun failAllLocked(message: String?) {
        val all = pending.values.toList()
        pending.clear()
        all.forEach {
            it.deferred.completeExceptionally(
                if (message == null) RpcException.disconnected(it.method)
                else RpcException(RpcException.Kind.DISCONNECTED, message, it.method)
            )
        }
    }

    private fun emit(event: HostEvent) {
        if (!_events.tryEmit(event)) {
            // Buffer cheio (consumidor lento): entrega com espera e pede recarga depois.
            scope.launch {
                _events.emit(event)
                _events.emit(HostEvent.Resync("local-overflow", null))
            }
        }
    }
}
