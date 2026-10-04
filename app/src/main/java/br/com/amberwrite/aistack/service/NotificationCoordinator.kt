package br.com.amberwrite.aistack.service

import br.com.amberwrite.aistack.core.rpc.EngineEvent
import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.data.model.PendingConversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Decide quando mostrar/remover notificações, sem depender do Android (testável com um
 * [Notifier] falso).
 *
 * - Pedidos de permissão e perguntas: uma notificação por pedido, exceto quando o app está
 *   em primeiro plano com aquela conversa aberta; some quando o pedido é resolvido.
 * - Progresso: conversas ocupadas, só com o app em segundo plano.
 * - Concluído: no fim do turno (`turnComplete`/`turnError`), se a conversa não está à vista
 *   e a preferência estiver ligada.
 */
class NotificationCoordinator(
    private val notifier: Notifier,
    private val scope: CoroutineScope,
    private val pending: StateFlow<List<PendingConversation>>,
    private val events: Flow<HostEvent>,
    private val isForeground: StateFlow<Boolean>,
    private val visibleChat: StateFlow<String?>,
    private val notifyDone: () -> Boolean,
    private val titleOf: (String) -> String?
) {
    private val posted = HashSet<Pair<String, String>>()
    private var progressShown = false
    private val jobs = ArrayList<Job>()

    fun start() {
        if (jobs.isNotEmpty()) return
        jobs += scope.launch {
            combine(pending, isForeground, visibleChat) { p, fg, chat -> Triple(p, fg, chat) }
                .collect { (p, fg, chat) -> sync(p, fg, chat) }
        }
        jobs += scope.launch { events.collect { onEvent(it) } }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        clearAll()
    }

    /** Remove tudo que este coordenador postou (despareamento, revogação). */
    fun clearAll() {
        synchronized(posted) {
            posted.forEach { (c, r) -> notifier.cancelPermission(c, r) }
            posted.clear()
        }
        if (progressShown) {
            notifier.showProgress(emptyList())
            progressShown = false
        }
    }

    private fun title(convId: String, fallback: String?): String =
        fallback?.takeIf { it.isNotBlank() } ?: titleOf(convId)?.takeIf { it.isNotBlank() } ?: "Conversa"

    internal fun sync(items: List<PendingConversation>, foreground: Boolean, chat: String?) {
        val wanted = LinkedHashMap<Pair<String, String>, Pair<PendingConversation, Int>>()
        for (conv in items) {
            if (foreground && conv.conversationId == chat) continue
            conv.permissions.forEachIndexed { i, req -> wanted[req.conversationId to req.requestId] = conv to i }
        }
        synchronized(posted) {
            val gone = posted.filter { it !in wanted }
            gone.forEach { (c, r) -> notifier.cancelPermission(c, r); posted.remove(c to r) }
            for ((key, v) in wanted) {
                if (key in posted) continue
                val (conv, idx) = v
                notifier.showPermission(conv.permissions[idx], title(conv.conversationId, conv.title))
                posted += key
            }
        }
        val busy = if (foreground) emptyList() else items.filter { it.busy }
        if (busy.isNotEmpty() || progressShown) {
            notifier.showProgress(busy)
            progressShown = busy.isNotEmpty()
        }
        if (foreground && chat != null) notifier.cancelDone(chat)
    }

    internal fun onEvent(ev: HostEvent) {
        if (ev !is HostEvent.Conv) return
        val convId = ev.conversationId
        val (text, isError) = when (val e = ev.event) {
            is EngineEvent.TurnComplete -> "A IA terminou de responder." to false
            is EngineEvent.TurnError -> {
                if (e.kind == "interrupted") return
                (e.message.ifBlank { "O turno terminou com erro." }) to true
            }
            else -> return
        }
        if (!notifyDone()) return
        if (isForeground.value && visibleChat.value == convId) return
        notifier.showDone(convId, title(convId, null), text, isError)
    }
}
