package br.com.amberwrite.aistack.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.ChatState
import br.com.amberwrite.aistack.data.model.Conversation
import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.QueuedMessage
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.data.repo.ActionResult
import br.com.amberwrite.aistack.feature.chat.thread.AgentInfo
import br.com.amberwrite.aistack.feature.chat.thread.ThreadModel
import br.com.amberwrite.aistack.feature.chat.thread.ThreadRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Uma conversa aberta. Abre a assinatura `full` ao nascer e a devolve a `summary` em
 * [onCleared] (trocar de conversa NÃO interrompe nada no host). O rascunho e o envio são
 * do compositor (F3); aqui ficam a thread, a fila, as permissões, as perguntas e o menu.
 */
class ChatViewModel(private val gateway: ChatGateway) : ViewModel() {

    constructor(container: AppContainer, conversationId: String) : this(ContainerChatGateway(container, conversationId))

    val conversationId: String get() = gateway.conversationId

    /** Decisão otimista mostrada enquanto a resposta viaja. */
    enum class Decision { Allow, Deny, Answer }

    data class Local(
        /** Pedidos com resposta em voo → decisão mostrada. */
        val answering: Map<String, Decision> = emptyMap(),
        /** Falha da última resposta por pedido (o cartão volta a ficar pendente). */
        val failures: Map<String, String> = emptyMap(),
        /** Chaves de blocos sendo carregados por inteiro / que falharam. */
        val expanding: Set<String> = emptySet(),
        val expandFailed: Set<String> = emptySet(),
        val toolAnswering: Boolean = false,
        val toolAnswerError: String? = null,
        val interrupting: Boolean = false,
        /** Itens da fila com ação em voo (enviar já / remover). */
        val queueBusy: Set<String> = emptySet(),
        val retrying: Boolean = false,
        val renaming: Boolean = false,
        val archiving: Boolean = false,
        /** Arquivada com sucesso: a tela volta. */
        val archived: Boolean = false,
        val renamedTitle: String? = null,
        val actionError: String? = null,
    )

    data class UiState(
        val chat: ChatState,
        val local: Local = Local(),
        val connection: ConnectionState = ConnectionState.Disconnected,
        val conversation: Conversation? = null,
        val title: String = "",
        /** Pergunta de ferramenta (`ask_question`) aguardando resposta, se houver. */
        val toolQuestion: ChatItem.Tool? = null,
        val rows: List<ThreadRow> = emptyList(),
        val agents: List<AgentInfo> = emptyList(),
        /** Erro que encerrou o último turno (mostra "tentar de novo"). */
        val lastTurnError: ChatItem.Error? = null,
    ) {
        val online: Boolean get() = connection is ConnectionState.Online
        val initialLoading: Boolean get() = chat.items.isEmpty() && chat.loading
        val isEmpty: Boolean get() = chat.items.isEmpty() && chat.pending.isEmpty() && !chat.loading && chat.error == null && !chat.busy
        val loadFailed: Boolean get() = chat.items.isEmpty() && !chat.loading && chat.error != null
        val canRetryTurn: Boolean get() = lastTurnError != null && ThreadModel.lastUserText(chat) != null
    }

    private val local = MutableStateFlow(Local())

    val state: StateFlow<UiState> = combine(
        gateway.chat,
        local,
        gateway.connection,
        gateway.conversation.onStart { emit(null) },
    ) { chat, l, conn, listed ->
        val conv = listed ?: chat.conversation
        val toolQuestion = gateway.openToolQuestion(chat)
        UiState(
            chat = chat,
            local = l,
            connection = conn,
            conversation = conv,
            title = l.renamedTitle ?: conv?.displayTitle.orEmpty(),
            toolQuestion = toolQuestion,
            rows = ThreadModel.buildRows(chat, toolQuestion),
            agents = ThreadModel.collectAgents(chat.items),
            lastTurnError = ThreadModel.lastTurnError(chat),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial())

    private fun initial(): UiState {
        val chat = gateway.chat.value
        return UiState(chat = chat, connection = gateway.connection.value, conversation = chat.conversation, title = chat.conversation?.displayTitle.orEmpty())
    }

    fun dismissError() = local.update { it.copy(actionError = null, toolAnswerError = null) }

    // ---- thread ---------------------------------------------------------------------------

    fun loadOlder() {
        val chat = gateway.chat.value
        if (!chat.hasMore || chat.loadingOlder) return
        viewModelScope.launch { safely { gateway.loadOlder() } }
    }

    fun refresh() {
        viewModelScope.launch { safely { gateway.refresh() } }
    }

    /** Busca o bloco inteiro de um item cortado (`truncated`). */
    fun expand(item: ChatItem) {
        val key = item.key
        if (key in local.value.expanding) return
        val ref = ThreadModel.blockRef(item) ?: run {
            local.update { it.copy(expandFailed = it.expandFailed + key) }
            return
        }
        local.update { it.copy(expanding = it.expanding + key, expandFailed = it.expandFailed - key) }
        viewModelScope.launch {
            val ok = try {
                gateway.expandBlock(ref.first, ref.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            local.update {
                it.copy(
                    expanding = it.expanding - key,
                    expandFailed = if (ok) it.expandFailed - key else it.expandFailed + key,
                )
            }
        }
    }

    // ---- turno e fila ---------------------------------------------------------------------

    fun interrupt() {
        if (local.value.interrupting) return
        local.update { it.copy(interrupting = true, actionError = null) }
        viewModelScope.launch {
            val err = attempt { gateway.interrupt() }
            local.update { it.copy(interrupting = false, actionError = err ?: it.actionError) }
        }
    }

    fun unqueue(queueId: String) {
        if (queueId in local.value.queueBusy) return
        local.update { it.copy(queueBusy = it.queueBusy + queueId, actionError = null) }
        viewModelScope.launch {
            val err = attempt { gateway.unqueue(queueId) }
            local.update { it.copy(queueBusy = it.queueBusy - queueId, actionError = err ?: it.actionError) }
        }
    }

    /**
     * Tira a mensagem da fila e a envia já (interrompendo o turno). Se o envio falhar
     * depois de sair da fila, ela volta para a fila para não se perder.
     */
    fun sendNow(message: QueuedMessage) {
        if (message.id in local.value.queueBusy) return
        local.update { it.copy(queueBusy = it.queueBusy + message.id, actionError = null) }
        viewModelScope.launch {
            var unqueued = false
            val err = attempt {
                gateway.unqueue(message.id)
                unqueued = true
                gateway.sendNow(message.text, message.attachments)
            }
            if (err != null && unqueued) attempt { gateway.queue(message.text, message.attachments) }
            local.update { it.copy(queueBusy = it.queueBusy - message.id, actionError = err ?: it.actionError) }
        }
    }

    /** Reenvia a última mensagem do usuário após um erro de turno. */
    fun retryLastTurn() {
        val chat = gateway.chat.value
        val text = ThreadModel.lastUserText(chat) ?: return
        val attachments = (chat.items.lastOrNull { it is ChatItem.User } as? ChatItem.User)?.attachments.orEmpty()
        if (local.value.retrying) return
        local.update { it.copy(retrying = true, actionError = null) }
        viewModelScope.launch {
            val err = attempt { if (chat.busy) gateway.queue(text, attachments) else gateway.send(text, attachments) }
            local.update { it.copy(retrying = false, actionError = err ?: it.actionError) }
        }
    }

    // ---- permissões e perguntas -----------------------------------------------------------

    fun allow(request: PermissionRequest, remember: Boolean = false) =
        answer(request, Decision.Allow) { gateway.answerPermission(request.requestId, PermissionDecision.Allow(remember = remember)) }

    fun deny(request: PermissionRequest) =
        answer(request, Decision.Deny) { gateway.answerPermission(request.requestId, PermissionDecision.Deny()) }

    /** `AskUserQuestion`: todas as respostas de uma vez (múltipla escolha une com ", "). */
    fun answerQuestion(request: PermissionRequest, selected: Map<Question, List<String>>) =
        answer(request, Decision.Answer) { gateway.answerQuestion(request, selected) }

    fun dismissQuestion(request: PermissionRequest) =
        answer(request, Decision.Deny) { gateway.dismissQuestion(request) }

    /** Pergunta de ferramenta (`ask_question`): vira mensagem comum. [optionIndex] é 0-based. */
    fun answerToolQuestion(question: Question, optionIndex: Int?, freeText: String?) {
        if (local.value.toolAnswering) return
        local.update { it.copy(toolAnswering = true, toolAnswerError = null) }
        viewModelScope.launch {
            val err = attempt { gateway.answerToolQuestion(question, optionIndex, freeText) }
            local.update { it.copy(toolAnswering = false, toolAnswerError = err) }
        }
    }

    private fun answer(request: PermissionRequest, decision: Decision, block: suspend () -> ActionResult) {
        if (request.requestId in local.value.answering) return
        local.update {
            it.copy(answering = it.answering + (request.requestId to decision), failures = it.failures - request.requestId)
        }
        viewModelScope.launch {
            val r = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ActionResult.Failed(e.userMessage)
            }
            local.update {
                it.copy(
                    answering = it.answering - request.requestId,
                    failures = if (r is ActionResult.Failed) it.failures + (request.requestId to r.message) else it.failures,
                )
            }
        }
    }

    // ---- menu -----------------------------------------------------------------------------

    fun rename(title: String) {
        val t = title.trim()
        if (t.isEmpty() || local.value.renaming) return
        local.update { it.copy(renaming = true, actionError = null) }
        viewModelScope.launch {
            val err = attempt { gateway.rename(t) }
            local.update { it.copy(renaming = false, renamedTitle = if (err == null) t else it.renamedTitle, actionError = err ?: it.actionError) }
        }
    }

    fun archive() {
        if (local.value.archiving) return
        local.update { it.copy(archiving = true, actionError = null) }
        viewModelScope.launch {
            val err = attempt { gateway.archive(true) }
            local.update { it.copy(archiving = false, archived = err == null, actionError = err ?: it.actionError) }
        }
    }

    // ---- infraestrutura -------------------------------------------------------------------

    /** Roda [block]; devolve a mensagem de erro (ou `null`). Cancelamento propaga. */
    private suspend fun attempt(block: suspend () -> Unit): String? = try {
        block(); null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.userMessage
    }

    private suspend fun safely(block: suspend () -> Unit) {
        attempt(block)?.let { msg -> local.update { it.copy(actionError = msg) } }
    }

    override fun onCleared() {
        gateway.close()
    }
}
