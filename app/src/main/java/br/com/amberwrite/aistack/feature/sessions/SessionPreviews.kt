package br.com.amberwrite.aistack.feature.sessions

import br.com.amberwrite.aistack.core.rpc.EngineEvent
import br.com.amberwrite.aistack.core.rpc.HostEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Prévia da última mensagem de cada conversa, montada a partir dos eventos ao vivo
 * (`listConversations` não traz a última mensagem). Guarda o texto do usuário ao iniciar um
 * turno e acumula o começo da resposta do agente, limitado a [PREVIEW_CAP] caracteres para
 * não reemitir o mapa a cada delta de uma resposta longa.
 */
class SessionPreviewTracker {

    private val _previews = MutableStateFlow<Map<String, String>>(emptyMap())
    val previews: StateFlow<Map<String, String>> = _previews.asStateFlow()

    /** Resposta em curso por conversa (só o começo). */
    private val replies = HashMap<String, StringBuilder>()

    fun onEvent(ev: HostEvent) {
        if (ev !is HostEvent.Conv) return
        val id = ev.conversationId
        when (val e = ev.event) {
            is EngineEvent.TurnStarted -> synchronized(replies) {
                replies.remove(id)
                val text = e.text.trim()
                if (text.isNotEmpty()) put(id, "$USER_PREFIX$text")
            }
            is EngineEvent.TextDelta -> synchronized(replies) {
                val sb = replies.getOrPut(id) { StringBuilder() }
                if (sb.length >= PREVIEW_CAP) return
                sb.append(e.text.take(PREVIEW_CAP - sb.length))
                val text = sb.toString().trim()
                if (text.isNotEmpty()) put(id, text)
            }
            else -> Unit
        }
    }

    private fun put(id: String, text: String) = _previews.update { it + (id to text) }

    companion object {
        const val PREVIEW_CAP = 200
        const val USER_PREFIX = "Você: "
    }
}

/**
 * Instância do processo, ligada uma única vez aos eventos do host. Fica fora do ViewModel
 * para que a prévia sobreviva a sair e voltar da lista.
 */
object SessionPreviews {
    private val tracker = SessionPreviewTracker()
    @Volatile private var attached = false

    val previews: StateFlow<Map<String, String>> get() = tracker.previews

    fun attach(events: Flow<HostEvent>, scope: CoroutineScope) {
        if (attached) return
        synchronized(this) {
            if (attached) return
            attached = true
        }
        scope.launch { events.collect { tracker.onEvent(it) } }
    }
}
