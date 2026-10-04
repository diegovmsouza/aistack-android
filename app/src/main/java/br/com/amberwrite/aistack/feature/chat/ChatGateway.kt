package br.com.amberwrite.aistack.feature.chat

import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.data.model.Attachment
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.ChatState
import br.com.amberwrite.aistack.data.model.Conversation
import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.data.repo.ActionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Fronteira estreita entre o [ChatViewModel] e o núcleo, presa a UMA conversa. Existe para
 * que o ViewModel seja testável na JVM com um falso, sem `AppContainer` nem Android.
 */
interface ChatGateway {
    val conversationId: String
    val chat: StateFlow<ChatState>
    val connection: StateFlow<ConnectionState>

    /** Registro da conversa na lista de sessões (título atualizado após renomear). */
    val conversation: Flow<Conversation?>

    fun openToolQuestion(state: ChatState): ChatItem.Tool?

    suspend fun refresh()
    suspend fun loadOlder()
    suspend fun expandBlock(turn: Long, seq: Long): Boolean

    suspend fun send(text: String, attachments: List<Attachment> = emptyList())
    suspend fun queue(text: String, attachments: List<Attachment> = emptyList())
    suspend fun sendNow(text: String, attachments: List<Attachment> = emptyList())
    suspend fun unqueue(queueId: String)
    suspend fun interrupt()

    suspend fun answerPermission(requestId: String, decision: PermissionDecision): ActionResult
    suspend fun answerQuestion(request: PermissionRequest, selected: Map<Question, List<String>>): ActionResult
    suspend fun dismissQuestion(request: PermissionRequest): ActionResult
    suspend fun answerToolQuestion(question: Question, optionIndex: Int?, freeText: String?)

    suspend fun rename(title: String)
    suspend fun archive(archived: Boolean)

    /** Devolve a assinatura da conversa a `summary`. NÃO interrompe nada no host. */
    fun close()
}

/** Implementação real sobre o [AppContainer] (`chatRepo` + `sessionsRepo`). */
class ContainerChatGateway(
    private val container: AppContainer,
    override val conversationId: String,
) : ChatGateway {
    private val repo = container.chatRepo

    override val chat: StateFlow<ChatState> = repo.open(conversationId)
    override val connection: StateFlow<ConnectionState> get() = container.connectionState
    override val conversation: Flow<Conversation?> = container.sessionsRepo.state
        .map { s -> s.conversations.firstOrNull { it.id == conversationId } }
        .distinctUntilChanged()

    override fun openToolQuestion(state: ChatState): ChatItem.Tool? = repo.openToolQuestion(state)

    override suspend fun refresh() = repo.refresh(conversationId)
    override suspend fun loadOlder() = repo.loadOlder(conversationId)
    override suspend fun expandBlock(turn: Long, seq: Long): Boolean = repo.expandBlock(conversationId, turn, seq)

    override suspend fun send(text: String, attachments: List<Attachment>) = repo.send(conversationId, text, attachments)
    override suspend fun queue(text: String, attachments: List<Attachment>) = repo.queue(conversationId, text, attachments)
    override suspend fun sendNow(text: String, attachments: List<Attachment>) = repo.sendNow(conversationId, text, attachments)
    override suspend fun unqueue(queueId: String) = repo.unqueue(conversationId, queueId)
    override suspend fun interrupt() = repo.interrupt(conversationId)

    override suspend fun answerPermission(requestId: String, decision: PermissionDecision): ActionResult =
        repo.answerPermission(conversationId, requestId, decision)

    override suspend fun answerQuestion(request: PermissionRequest, selected: Map<Question, List<String>>): ActionResult =
        repo.answerQuestion(conversationId, request, selected)

    override suspend fun dismissQuestion(request: PermissionRequest): ActionResult =
        repo.dismissQuestion(conversationId, request)

    override suspend fun answerToolQuestion(question: Question, optionIndex: Int?, freeText: String?) =
        repo.answerToolQuestion(conversationId, question, optionIndex, freeText)

    override suspend fun rename(title: String) = container.sessionsRepo.rename(conversationId, title)
    override suspend fun archive(archived: Boolean) = container.sessionsRepo.archive(conversationId, archived)

    override fun close() = repo.close(conversationId)
}
