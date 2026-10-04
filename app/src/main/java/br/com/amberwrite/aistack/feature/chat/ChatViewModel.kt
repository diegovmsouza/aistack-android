package br.com.amberwrite.aistack.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.ChatState
import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.data.repo.ActionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Uma conversa aberta. Abre a assinatura `full` no [ChatRepo] ao nascer e a devolve a
 * `summary` em [onCleared]. As ações são `suspend` no repositório; aqui só viram estado
 * de interface (rascunho, envio em curso, erro da última ação).
 */
class ChatViewModel(
    private val container: AppContainer,
    val conversationId: String
) : ViewModel() {

    data class Local(
        val draft: String = "",
        val sending: Boolean = false,
        /** Pedidos de permissão com resposta em voo (botões desabilitados). */
        val answering: Set<String> = emptySet(),
        val actionError: String? = null
    )

    data class UiState(
        val chat: ChatState,
        val local: Local = Local(),
        val connection: ConnectionState = ConnectionState.Disconnected,
        val title: String = "",
        /** Pergunta de ferramenta (`ask_question`) aguardando resposta, se houver. */
        val toolQuestion: ChatItem.Tool? = null
    ) {
        val online: Boolean get() = connection is ConnectionState.Online
    }

    private val chatFlow = container.chatRepo.open(conversationId)
    private val local = MutableStateFlow(Local())

    val state: StateFlow<UiState> = combine(chatFlow, local, container.connectionState, container.sessionsRepo.state) { chat, l, conn, sessions ->
        val conv = chat.conversation ?: sessions.conversations.firstOrNull { it.id == conversationId }
        UiState(
            chat = chat,
            local = l,
            connection = conn,
            title = conv?.displayTitle ?: "Conversa",
            toolQuestion = container.chatRepo.openToolQuestion(chat)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(chatFlow.value))

    fun setDraft(text: String) = local.update { it.copy(draft = text) }

    fun dismissError() = local.update { it.copy(actionError = null) }

    /** Envia o rascunho; com a conversa ocupada, põe na fila. */
    fun send() {
        val text = local.value.draft.trim()
        if (text.isEmpty() || local.value.sending) return
        val busy = chatFlow.value.busy
        runAction(clearDraft = true) {
            if (busy) container.chatRepo.queue(conversationId, text) else container.chatRepo.send(conversationId, text)
        }
    }

    /** Interrompe o turno atual e envia o rascunho imediatamente. */
    fun sendNow() {
        val text = local.value.draft.trim()
        if (text.isEmpty() || local.value.sending) return
        runAction(clearDraft = true) { container.chatRepo.sendNow(conversationId, text) }
    }

    fun interrupt() = runAction { container.chatRepo.interrupt(conversationId) }

    fun unqueue(queueId: String) = runAction { container.chatRepo.unqueue(conversationId, queueId) }

    fun loadOlder() {
        viewModelScope.launch { container.chatRepo.loadOlder(conversationId) }
    }

    fun refresh() {
        viewModelScope.launch { container.chatRepo.refresh(conversationId) }
    }

    fun expand(item: ChatItem) {
        val parts = item.key.split(':')
        if (parts.size != 3 || parts[0] != "B") return
        val turn = parts[1].toLongOrNull() ?: return
        val seq = parts[2].toLongOrNull() ?: return
        viewModelScope.launch { runCatching { container.chatRepo.expandBlock(conversationId, turn, seq) } }
    }

    fun allow(request: PermissionRequest, remember: Boolean = false) =
        answer(request) { container.chatRepo.answerPermission(conversationId, request.requestId, PermissionDecision.Allow(remember = remember)) }

    fun deny(request: PermissionRequest) =
        answer(request) { container.chatRepo.answerPermission(conversationId, request.requestId, PermissionDecision.Deny()) }

    fun answerQuestion(request: PermissionRequest, selected: Map<Question, List<String>>) =
        answer(request) { container.chatRepo.answerQuestion(conversationId, request, selected) }

    fun dismissQuestion(request: PermissionRequest) =
        answer(request) { container.chatRepo.dismissQuestion(conversationId, request) }

    fun answerToolQuestion(question: Question, optionIndex: Int?, freeText: String?) =
        runAction { container.chatRepo.answerToolQuestion(conversationId, question, optionIndex, freeText) }

    private fun answer(request: PermissionRequest, block: suspend () -> ActionResult) {
        if (request.requestId in local.value.answering) return
        local.update { it.copy(answering = it.answering + request.requestId, actionError = null) }
        viewModelScope.launch {
            val r = runCatching { block() }.getOrElse { ActionResult.Failed(it.userMessage) }
            local.update {
                it.copy(
                    answering = it.answering - request.requestId,
                    actionError = (r as? ActionResult.Failed)?.message
                )
            }
        }
    }

    private fun runAction(clearDraft: Boolean = false, block: suspend () -> Unit) {
        local.update { it.copy(sending = true, actionError = null) }
        viewModelScope.launch {
            try {
                block()
                local.update { it.copy(sending = false, draft = if (clearDraft) "" else it.draft) }
            } catch (e: Exception) {
                local.update { it.copy(sending = false, actionError = e.userMessage) }
            }
        }
    }

    override fun onCleared() {
        container.chatRepo.close(conversationId)
    }
}
