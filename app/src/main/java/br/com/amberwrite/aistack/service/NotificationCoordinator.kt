package br.com.amberwrite.aistack.service

import br.com.amberwrite.aistack.core.rpc.EngineEvent
import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.data.model.PendingConversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Decide quando mostrar/atualizar/remover notificações, sem depender do Android (testável
 * com um [Notifier] falso e relógio injetado).
 *
 * - **Pendências** (canal alto): uma notificação por pedido de permissão/pergunta, exceto com
 *   o app em primeiro plano e aquela conversa aberta; some quando o pedido é resolvido. Todas
 *   ficam num grupo com resumo (abre `aistack://pending`). Reposts (ex.: voltou do primeiro
 *   plano) não alertam de novo.
 * - **Pergunta de ferramenta** (`ask_question`/`AskFollowupQuestion`): se o turno terminar com
 *   ela aberta, vira uma notificação de pendência cuja resposta é uma mensagem.
 * - **Live Update** (canal de progresso): um por conversa ocupada, só com o app em segundo
 *   plano; ferramenta atual, sub-agentes e cronômetro, no máximo uma atualização por segundo.
 * - **Concluído** (canal de concluídos): fim de turno (`turnComplete`/`turnError`/`exited`
 *   com erro), se a preferência estiver ligada e a conversa não estiver à vista.
 */
class NotificationCoordinator(
    private val notifier: Notifier,
    private val scope: CoroutineScope,
    private val pending: StateFlow<List<PendingConversation>>,
    private val events: Flow<HostEvent>,
    private val isForeground: StateFlow<Boolean>,
    private val visibleChat: StateFlow<String?>,
    private val notifyDone: () -> Boolean,
    private val titleOf: (String) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val liveIntervalMs: Long = 1_000,
) {
    private val lock = Any()
    private val jobs = ArrayList<Job>()

    private val tracker = LiveTurnTracker(clock)
    private val throttle = LiveThrottle(liveIntervalMs)

    /** Pedidos com notificação postada agora. */
    private val posted = LinkedHashMap<Pair<String, String>, PermissionNotice>()

    /** Pedidos que já alertaram (repostar sem som). Limpo quando o pedido some da lista. */
    private val alerted = HashSet<Pair<String, String>>()

    private val toolQuestions = LinkedHashMap<String, ToolQuestionNotice>()
    private var summaryShown: PendingSummary? = null

    private val liveShown = HashMap<String, LiveNotice>()
    private val liveJobs = HashMap<String, Job>()

    private var items: List<PendingConversation> = emptyList()
    private var foreground = false
    private var chat: String? = null

    fun start() {
        synchronized(lock) {
            if (jobs.isNotEmpty()) return
            jobs += scope.launch {
                combine(pending, isForeground, visibleChat) { p, fg, c -> Triple(p, fg, c) }
                    .collect { (p, fg, c) -> sync(p, fg, c) }
            }
            jobs += scope.launch { events.collect { onEvent(it) } }
        }
    }

    fun stop() {
        synchronized(lock) {
            jobs.forEach { it.cancel() }
            jobs.clear()
        }
        clearAll()
    }

    /** Remove tudo que este coordenador postou (despareamento, revogação). */
    fun clearAll() {
        synchronized(lock) {
            posted.keys.forEach { (c, r) -> notifier.cancelPermission(c, r) }
            posted.clear()
            alerted.clear()
            toolQuestions.keys.forEach { notifier.cancelToolQuestion(it) }
            toolQuestions.clear()
            if (summaryShown != null) notifier.showPendingSummary(null)
            summaryShown = null
            liveJobs.values.forEach { it.cancel() }
            liveJobs.clear()
            liveShown.keys.forEach { notifier.cancelLive(it) }
            liveShown.clear()
            tracker.clear()
            throttle.clear()
            items = emptyList()
        }
    }

    private fun title(convId: String): String =
        items.firstOrNull { it.conversationId == convId }?.title?.takeIf { it.isNotBlank() }
            ?: titleOf(convId)?.takeIf { it.isNotBlank() }
            ?: NotificationMapper.DEFAULT_TITLE

    private fun isVisible(convId: String) = foreground && chat == convId

    internal fun sync(items: List<PendingConversation>, foreground: Boolean, chat: String?) {
        synchronized(lock) { syncLocked(items, foreground, chat) }
    }

    private fun syncLocked(items: List<PendingConversation>, foreground: Boolean, chat: String?) {
        this.items = items
        this.foreground = foreground
        this.chat = chat

        // Pedidos de permissão / AskUserQuestion.
        val wanted = LinkedHashMap<Pair<String, String>, PermissionNotice>()
        val existing = HashSet<Pair<String, String>>()
        for (conv in items) {
            for (req in conv.permissions) {
                val key = req.conversationId to req.requestId
                existing += key
                if (isVisible(conv.conversationId)) continue
                wanted[key] = NotificationMapper.permission(req, title(conv.conversationId), silent = key in alerted)
            }
        }
        posted.keys.filter { it !in wanted }.forEach { key ->
            notifier.cancelPermission(key.first, key.second)
            posted.remove(key)
        }
        for ((key, notice) in wanted) {
            if (key in posted) continue
            notifier.showPermission(notice)
            posted[key] = notice
            alerted += key
        }
        alerted.retainAll(existing)

        // Pergunta de ferramenta: some com a conversa à vista (o chat mostra a pergunta).
        toolQuestions.keys.filter { isVisible(it) }.forEach {
            notifier.cancelToolQuestion(it)
            toolQuestions.remove(it)
        }
        updateSummary()

        // Turnos em andamento.
        val busy = items.filter { it.busy }.map { it.conversationId }.toSet()
        tracker.syncBusy(busy)
        val all = tracker.turnsSnapshot.map { it.conversationId }.toSet() + liveShown.keys
        all.forEach { refreshLive(it, immediate = false) }

        if (chat != null && foreground) notifier.cancelDone(chat)
    }

    internal fun onEvent(ev: HostEvent) {
        synchronized(lock) { onEventLocked(ev) }
    }

    private fun onEventLocked(ev: HostEvent) {
        val change = tracker.onEvent(ev) ?: return
        when (change) {
            is LiveChange.Updated -> {
                val e = (ev as? HostEvent.Conv)?.event
                if (e is EngineEvent.TurnStarted) {
                    // Turno novo: a pergunta de ferramenta antiga foi respondida (ou abandonada).
                    if (toolQuestions.remove(change.conversationId) != null) {
                        notifier.cancelToolQuestion(change.conversationId)
                        updateSummary()
                    }
                }
                refreshLive(change.conversationId, immediate = false)
            }
            is LiveChange.Ended -> {
                val id = change.conversationId
                refreshLive(id, immediate = true)
                if (isVisible(id)) return
                val tq = change.toolQuestion
                if (tq != null) {
                    val notice = NotificationMapper.toolQuestion(id, title(id), tq)
                    toolQuestions[id] = notice
                    notifier.showToolQuestion(notice)
                    updateSummary()
                    return
                }
                if (!notifyDone()) return
                NotificationMapper.done(id, title(id), change.event)?.let { notifier.showDone(it) }
            }
        }
    }

    private fun updateSummary() {
        val summary = NotificationMapper.summary(posted.values, toolQuestions.values)
        if (summary == summaryShown) return
        notifier.showPendingSummary(summary)
        summaryShown = summary
    }

    /**
     * Atualiza o Live Update de [convId] respeitando o limite de frequência. [immediate] ignora
     * o limite (fim de turno: a notificação some na hora).
     */
    private fun refreshLive(convId: String, immediate: Boolean) {
        // Remoção (primeiro plano ou turno acabou) não espera o limite: só atualizações esperam.
        if (immediate || foreground || tracker.turn(convId) == null) {
            liveJobs.remove(convId)?.cancel()
            emitLive(convId)
            return
        }
        if (liveJobs[convId]?.isActive == true) return // a emissão agendada pega o estado mais novo
        val wait = throttle.delayFor(convId, clock())
        if (wait <= 0L) {
            emitLive(convId)
            return
        }
        liveJobs[convId] = scope.launch {
            delay(wait)
            synchronized(lock) {
                liveJobs.remove(convId)
                emitLive(convId)
            }
        }
    }

    private fun emitLive(convId: String) {
        val turn = tracker.turn(convId)
        if (turn == null || foreground) {
            if (liveShown.remove(convId) != null) notifier.cancelLive(convId)
            throttle.reset(convId)
            return
        }
        val waiting = items.any { it.conversationId == convId && it.permissions.isNotEmpty() } || convId in toolQuestions
        val notice = NotificationMapper.live(turn, title(convId), waiting)
        if (liveShown[convId] == notice) return
        notifier.showLive(notice)
        liveShown[convId] = notice
        throttle.mark(convId, clock())
    }
}
