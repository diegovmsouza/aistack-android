package br.com.amberwrite.aistack.feature.pending

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.data.model.PendingConversation
import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.repo.ActionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Situação de um pedido na caixa. */
enum class EntryStatus { Pending, Sending, Allowed, Denied }

/** Um pedido (permissão ou pergunta) dentro do cartão da conversa. */
data class PendingEntry(
    val request: PermissionRequest,
    val status: EntryStatus = EntryStatus.Pending,
    val error: String? = null,
)

/** Um cartão por conversa. */
data class PendingCard(
    val conversation: PendingConversation,
    val entries: List<PendingEntry>,
) {
    val key: String get() = conversation.conversationId
    val waitingCount: Int get() = entries.count { it.status == EntryStatus.Pending || it.status == EntryStatus.Sending }
}

/**
 * Lógica pura da caixa: junta a lista do repositório com os pedidos recém-respondidos
 * ("fantasmas", mostrados por um instante como Permitido/Negado antes de saírem com animação)
 * e com o estado local de envio/erro de cada pedido.
 */
object PendingCards {

    data class Ghost(val conversation: PendingConversation, val request: PermissionRequest, val status: EntryStatus)

    fun build(
        items: List<PendingConversation>,
        sending: Set<String>,
        errors: Map<String, String>,
        ghosts: Map<String, Ghost>,
    ): List<PendingCard> {
        val cards = items
            .filter { it.permissions.isNotEmpty() || ghosts.values.any { g -> g.conversation.conversationId == it.conversationId } }
            .map { conv ->
                val live = conv.permissions.map { req ->
                    PendingEntry(
                        request = req,
                        status = if (req.requestId in sending) EntryStatus.Sending else EntryStatus.Pending,
                        error = errors[req.requestId],
                    )
                }
                val liveIds = conv.permissions.mapTo(HashSet()) { it.requestId }
                val gone = ghosts.values
                    .filter { it.conversation.conversationId == conv.conversationId && it.request.requestId !in liveIds }
                    .map { PendingEntry(it.request, it.status) }
                PendingCard(conv, (live + gone).sortedBy { it.request.since ?: Long.MAX_VALUE })
            }
        val present = cards.mapTo(HashSet()) { it.key }
        val orphans = ghosts.values
            .filter { it.conversation.conversationId !in present }
            .groupBy { it.conversation.conversationId }
            .map { (_, gs) -> PendingCard(gs.first().conversation, gs.map { PendingEntry(it.request, it.status) }) }
        // Quem espera há mais tempo primeiro.
        return (cards + orphans).sortedBy { card -> card.entries.minOfOrNull { it.request.since ?: Long.MAX_VALUE } ?: Long.MAX_VALUE }
    }

    /** `answers` do `AskUserQuestion`: {pergunta: resposta}, na ordem das perguntas. */
    fun answersFor(request: PermissionRequest, byIndex: Map<Int, String>): Map<String, String>? {
        val qs = request.questions
        if (qs.isEmpty() || byIndex.size < qs.size) return null
        val out = LinkedHashMap<String, String>()
        qs.forEachIndexed { i, q -> out[q.question] = byIndex[i]?.takeIf { it.isNotBlank() } ?: return null }
        return out
    }
}

/** Caixa de entrada de pedidos de todas as conversas (`listPending` + eventos ao vivo). */
class PendingViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val cards: List<PendingCard> = emptyList(),
        val supported: Boolean = true,
        /** Primeira carga ainda sem resposta. */
        val loading: Boolean = true,
        /** Falha da última carga (sem conexão etc.). */
        val loadError: Boolean = false,
        /** Falha de uma resposta (também aparece no próprio pedido). */
        val error: String? = null,
        val connection: ConnectionState = ConnectionState.Disconnected,
        val rationaleDismissed: Boolean = true,
    ) {
        val waitingCount: Int get() = cards.sumOf { it.waitingCount }
    }

    private data class Local(
        val loading: Boolean = true,
        val loadError: Boolean = false,
        val sending: Set<String> = emptySet(),
        val errors: Map<String, String> = emptyMap(),
        val ghosts: Map<String, PendingCards.Ghost> = emptyMap(),
        val lastError: String? = null,
        val rationaleDismissed: Boolean = true,
    )

    private val local = MutableStateFlow(Local())
    private var reloadJob: Job? = null
    private val prefs by lazy { container.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    val state: StateFlow<UiState> = combine(
        container.pendingRepo.items,
        container.pendingRepo.supported,
        local,
        container.connectionState,
    ) { items, supported, l, conn ->
        UiState(
            cards = PendingCards.build(items, l.sending, l.errors, l.ghosts),
            supported = supported,
            loading = l.loading,
            loadError = l.loadError,
            error = l.lastError,
            connection = conn,
            rationaleDismissed = l.rationaleDismissed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        reload()
        viewModelScope.launch {
            val dismissed = withContext(Dispatchers.IO) { prefs.getBoolean(KEY_RATIONALE_DISMISSED, false) }
            local.update { it.copy(rationaleDismissed = dismissed) }
        }
        // Voltou a conexão: recarrega (os eventos perdidos não chegam de novo).
        viewModelScope.launch {
            container.rpc.connected.drop(1).filter { it }.collect { reload() }
        }
    }

    fun reload() {
        reloadJob?.cancel()
        local.update { it.copy(loading = true) }
        reloadJob = viewModelScope.launch {
            val ok = try {
                if (container.rpc.connected.value) {
                    container.pendingRepo.reload()
                    true
                } else false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            local.update { it.copy(loading = false, loadError = !ok) }
        }
    }

    fun allow(req: PermissionRequest, remember: Boolean = false) =
        answer(req, PermissionDecision.Allow(remember = remember), EntryStatus.Allowed)

    /** Nega; com [message], "nega e explica" (o modelo lê o texto). */
    fun deny(req: PermissionRequest, message: String? = null) =
        answer(req, PermissionDecision.Deny(message?.trim()?.takeIf { it.isNotEmpty() }), EntryStatus.Denied)

    /** Respostas do `AskUserQuestion`, por índice da pergunta. */
    fun answerQuestions(req: PermissionRequest, byIndex: Map<Int, String>) {
        val answers = PendingCards.answersFor(req, byIndex) ?: return
        answer(req, PermissionDecision.Allow(answers = answers), EntryStatus.Allowed)
    }

    /** Dispensa uma pergunta (o CLI recebe a recusa com o texto padrão). */
    fun dismiss(req: PermissionRequest) =
        answer(req, PermissionDecision.Deny(PermissionDecision.DISMISS_QUESTION_MESSAGE), EntryStatus.Denied)

    fun clearError() = local.update { it.copy(lastError = null) }

    fun dismissRationale() {
        local.update { it.copy(rationaleDismissed = true) }
        viewModelScope.launch(Dispatchers.IO) { prefs.edit().putBoolean(KEY_RATIONALE_DISMISSED, true).apply() }
    }

    private fun answer(req: PermissionRequest, decision: PermissionDecision, resolved: EntryStatus) {
        val id = req.requestId
        if (id in local.value.sending) return
        val conv = container.pendingRepo.items.value.firstOrNull { it.conversationId == req.conversationId }
        local.update { it.copy(sending = it.sending + id, errors = it.errors - id, lastError = null) }
        viewModelScope.launch {
            val r = container.pendingRepo.answer(req.conversationId, id, decision)
            when (r) {
                ActionResult.Ok, ActionResult.AlreadyResolved -> {
                    // A conversa pode sumir da lista do repo na hora: guarda um instante como "fantasma".
                    val ghost = conv?.let { PendingCards.Ghost(it.copy(permissions = emptyList()), req, resolved) }
                    local.update {
                        it.copy(
                            sending = it.sending - id,
                            ghosts = if (ghost != null && r == ActionResult.Ok) it.ghosts + (id to ghost) else it.ghosts,
                        )
                    }
                    if (ghost != null && r == ActionResult.Ok) {
                        delay(GHOST_MS)
                        local.update { it.copy(ghosts = it.ghosts - id) }
                    }
                }
                is ActionResult.Failed -> local.update {
                    it.copy(sending = it.sending - id, errors = it.errors + (id to r.message), lastError = r.message)
                }
            }
        }
    }

    companion object {
        private const val PREFS = "pending_ui"
        private const val KEY_RATIONALE_DISMISSED = "notif_rationale_dismissed"
        private const val GHOST_MS = 900L
    }
}
