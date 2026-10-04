package br.com.amberwrite.aistack.data.repo

import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.core.rpc.RpcCaller
import br.com.amberwrite.aistack.core.rpc.RpcException
import br.com.amberwrite.aistack.core.rpc.asObj
import br.com.amberwrite.aistack.core.rpc.endsTurn
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.Attachment
import br.com.amberwrite.aistack.data.model.Block
import br.com.amberwrite.aistack.data.model.BlockMapper
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.ChatState
import br.com.amberwrite.aistack.data.model.ConversationSnapshot
import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.data.model.ToolQuestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Conversas abertas na tela. Cada uma tem um [ChatState] próprio, montado pela página de
 * `getConversation` e pelos `conv-event` ao vivo (filtrados por `conversationId`).
 *
 * Abrir assina a conversa em `full`; fechar (última referência) volta a `summary`.
 */
class ChatRepo(
    private val rpc: RpcCaller,
    private val scope: CoroutineScope,
    private val pendingRepo: PendingRepo
) {
    private class Session(id: String) {
        val state = MutableStateFlow(ChatState(conversationId = id, loading = true))
        var refs = 0
        var refreshJob: Job? = null
    }

    private val sessions = ConcurrentHashMap<String, Session>()

    /** Turnos por página; cai pela metade quando a resposta não cabe no túnel. */
    @Volatile
    private var limitTurns: Int? = null

    private fun currentLimit(): Int = limitTurns ?: if (rpc.hostFrag) LIMIT_FRAG else LIMIT_NO_FRAG

    /** Abre (ou reaproveita) a conversa e devolve o seu estado observável. */
    fun open(id: String): StateFlow<ChatState> {
        val s = synchronized(sessions) {
            sessions.getOrPut(id) { Session(id) }.also { it.refs++ }
        }
        if (s.refs == 1) scope.launch { attach(id) }
        return s.state.asStateFlow()
    }

    /** Solta uma referência; a última devolve a conversa ao nível `summary`. */
    fun close(id: String) {
        val removed = synchronized(sessions) {
            val s = sessions[id] ?: return
            s.refs--
            if (s.refs <= 0) {
                sessions.remove(id)
                s.refreshJob?.cancel()
                true
            } else false
        }
        if (removed && rpc.connected.value) scope.launch {
            runCatching { subscribe(listOf(id), "summary") }
        }
    }

    fun state(id: String): StateFlow<ChatState>? = sessions[id]?.state?.asStateFlow()

    /** Ids abertos agora (para não notificar o que o usuário já está vendo). */
    val openIds: Set<String> get() = sessions.keys.toSet()

    /** Nova sessão do túnel: assina de novo e recarrega todas as conversas abertas. */
    suspend fun reattachAll() {
        limitTurns = null
        for (id in sessions.keys.toList()) attach(id)
    }

    private suspend fun attach(id: String) {
        if (!rpc.connected.value) {
            sessions[id]?.state?.update { it.copy(loading = false) }
            return
        }
        runCatching { subscribe(listOf(id), "full") }
        refresh(id)
    }

    private suspend fun subscribe(ids: List<String>, level: String) {
        try {
            rpc.call("subscribe", params("conversations" to ids, "level" to level))
        } catch (e: RpcException) {
            if (e.kind != RpcException.Kind.UNKNOWN_METHOD) throw e
        }
    }

    /** Recarrega a página mais recente (preservando o que já foi carregado de "ver mais"). */
    suspend fun refresh(id: String) {
        val s = sessions[id] ?: return
        s.state.update { it.copy(loading = it.items.isEmpty(), error = null) }
        try {
            val snap = fetchPage(id, beforeTurn = null)
            s.state.update { ChatReducer.applyLatest(it, snap) }
        } catch (e: Exception) {
            s.state.update { it.copy(loading = false, error = e.userMessage) }
        }
    }

    /** Busca turnos anteriores ao mais antigo carregado. */
    suspend fun loadOlder(id: String) {
        val s = sessions[id] ?: return
        val st = s.state.value
        val oldest = st.oldestTurn ?: return
        if (!st.hasMore || st.loadingOlder) return
        s.state.update { it.copy(loadingOlder = true) }
        try {
            val snap = fetchPage(id, beforeTurn = oldest)
            s.state.update { ChatReducer.applyOlder(it, snap) }
        } catch (e: Exception) {
            s.state.update { it.copy(loadingOlder = false, error = e.userMessage) }
        }
    }

    private suspend fun fetchPage(id: String, beforeTurn: Long?): ConversationSnapshot {
        while (true) {
            val limit = currentLimit()
            try {
                val res = rpc.call(
                    "getConversation",
                    params("id" to id, "beforeTurn" to beforeTurn, "limitTurns" to limit)
                )
                val o = res.asObj() ?: throw RpcException(RpcException.Kind.REMOTE, "Resposta inválida do desktop.")
                return ConversationSnapshot.parse(o, id)
            } catch (e: RpcException) {
                if (e.kind == RpcException.Kind.TOO_LARGE && limit > 1) {
                    limitTurns = limit / 2
                    continue
                }
                throw e
            }
        }
    }

    /** Busca o bloco inteiro de um item cortado (`truncated`) e o substitui no chat. */
    suspend fun expandBlock(conversationId: String, turn: Long, seq: Long): Boolean {
        val s = sessions[conversationId] ?: return false
        val res = rpc.call("getBlock", params("conversationId" to conversationId, "turn" to turn, "seq" to seq))
        val block = res.asObj()?.let(Block::parse) ?: return false
        val item = BlockMapper.toItem(block) ?: return false
        s.state.update { st -> st.copy(items = st.items.map { if (it.key == item.key) item else it }) }
        return true
    }

    // ---- eventos -------------------------------------------------------------------------

    fun onEvent(ev: HostEvent) {
        val id = when (ev) {
            is HostEvent.Conv -> ev.conversationId
            is HostEvent.QueueUpdate -> ev.conversationId
            is HostEvent.EventTooLarge -> ev.conversationId ?: return
            is HostEvent.Notice -> ev.conversationId ?: return
            else -> return
        }
        val s = sessions[id] ?: return
        s.state.update { ChatReducer.apply(it, ev) }
        val st = s.state.value
        val ended = ev is HostEvent.Conv && ev.event.endsTurn
        if (ended || (st.needsReload && !st.busy)) scheduleRefresh(id, s)
    }

    private fun scheduleRefresh(id: String, s: Session) {
        s.refreshJob?.cancel()
        s.refreshJob = scope.launch {
            delay(REFRESH_DELAY_MS)
            refresh(id)
        }
    }

    // ---- ações ---------------------------------------------------------------------------

    /** Envia uma mensagem (inicia turno). Com a conversa ocupada, prefira [queue]. */
    suspend fun send(id: String, text: String, attachments: List<Attachment> = emptyList()) {
        rpc.call("sendMessage", messageParams(id, text, attachments))
        sessions[id]?.state?.update { it.copy(busy = true) }
    }

    /** Põe a mensagem na fila do host (entra quando o turno atual acabar). */
    suspend fun queue(id: String, text: String, attachments: List<Attachment> = emptyList()) {
        rpc.call("queueMessage", messageParams(id, text, attachments))
    }

    /** Interrompe o turno e envia já. */
    suspend fun sendNow(id: String, text: String, attachments: List<Attachment> = emptyList()) {
        rpc.call("sendNow", messageParams(id, text, attachments))
    }

    suspend fun unqueue(id: String, queueId: String) {
        rpc.call("unqueueMessage", params("id" to id, "queueId" to queueId))
        sessions[id]?.state?.update { st -> st.copy(queue = st.queue.filterNot { it.id == queueId }) }
    }

    suspend fun interrupt(id: String) {
        rpc.call("interrupt", params("id" to id))
    }

    /** Permitir/negar um pedido de permissão (delegado ao [PendingRepo]). */
    suspend fun answerPermission(id: String, requestId: String, decision: PermissionDecision): ActionResult {
        val r = pendingRepo.answer(id, requestId, decision)
        if (r !is ActionResult.Failed) {
            sessions[id]?.state?.update { st -> st.copy(pending = st.pending.filterNot { it.requestId == requestId }) }
        }
        return r
    }

    /**
     * Responde `AskUserQuestion` (caso (a)): `answerPermission` com `answers`
     * `{pergunta: rótulo}`; em múltipla escolha os rótulos são unidos por ", ".
     * [selected] mapeia cada pergunta aos rótulos escolhidos (ou ao texto livre).
     */
    suspend fun answerQuestion(id: String, request: PermissionRequest, selected: Map<Question, List<String>>): ActionResult {
        val answers = selected.entries.associate { (q, labels) -> q.question to labels.joinToString(", ") }
        return answerPermission(id, request.requestId, PermissionDecision.Allow(answers = answers))
    }

    /** Dispensa a pergunta de `AskUserQuestion`. */
    suspend fun dismissQuestion(id: String, request: PermissionRequest): ActionResult =
        answerPermission(id, request.requestId, PermissionDecision.Deny(PermissionDecision.DISMISS_QUESTION_MESSAGE))

    /**
     * Responde pergunta de ferramenta (caso (b), `ask_question`/`AskFollowupQuestion`):
     * vira uma mensagem comum. Só faz sentido com a conversa parada.
     */
    suspend fun answerToolQuestion(id: String, question: Question, optionIndex: Int?, freeText: String? = null) {
        val text = ToolQuestion.answerText(question, optionIndex, freeText)
        require(text.isNotBlank()) { "Resposta vazia." }
        send(id, text)
    }

    private fun messageParams(id: String, text: String, attachments: List<Attachment>) =
        params("id" to id, "text" to text, "attachments" to attachments.map { it.toParams() })

    /** Ferramenta com pergunta pendente de resposta (último item, conversa parada). */
    fun openToolQuestion(state: ChatState): ChatItem.Tool? {
        if (state.busy) return null
        val last = state.items.lastOrNull { it !is ChatItem.Notice } as? ChatItem.Tool ?: return null
        return last.takeIf { it.question != null }
    }

    companion object {
        const val LIMIT_FRAG = 20
        const val LIMIT_NO_FRAG = 5
        private const val REFRESH_DELAY_MS = 250L
    }
}
