package br.com.amberwrite.aistack.data.repo

import br.com.amberwrite.aistack.core.rpc.EngineEvent
import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.data.model.BlockMapper
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.ChatState
import br.com.amberwrite.aistack.data.model.ConversationSnapshot
import br.com.amberwrite.aistack.data.model.SubagentStep
import br.com.amberwrite.aistack.data.model.ToolStatus

/**
 * Funções puras que aplicam respostas e eventos ao [ChatState]. Ficam separadas do
 * [ChatRepo] para serem testadas na JVM sem corrotinas nem rede.
 */
object ChatReducer {

    /**
     * Aplica a página mais recente de `getConversation`. Itens de turnos mais antigos que a
     * página (carregados por "ver mais") são mantidos; o resto é trocado pelos blocos
     * persistidos. Com `busy`, os itens ao vivo do turno corrente (ainda não gravado) ficam.
     */
    fun applyLatest(state: ChatState, snap: ConversationSnapshot): ChatState {
        val fresh = BlockMapper.toItems(snap.blocks)
        val pageOldest = snap.page?.oldestTurn ?: snap.blocks.minOfOrNull { it.turn }
        val maxPersisted = snap.blocks.maxOfOrNull { it.turn }
        val keptOlder = if (pageOldest == null || snap.page == null) emptyList()
        else state.items.filter { it.key.startsWith("B:") && it.turn < pageOldest }
        val live = if (snap.busy) state.items.filter {
            it.key.startsWith("L:") && (maxPersisted == null || it.turn > maxPersisted)
        } else emptyList()
        val hasOlderLoaded = keptOlder.isNotEmpty()
        return state.copy(
            conversation = snap.conversation ?: state.conversation,
            items = keptOlder + fresh + live,
            busy = snap.busy,
            isStreaming = snap.busy && state.isStreaming,
            queue = snap.queue,
            pending = snap.pending,
            hasMore = if (hasOlderLoaded) state.hasMore else (snap.page?.hasMore ?: false),
            oldestTurn = if (hasOlderLoaded) state.oldestTurn else (snap.page?.oldestTurn ?: pageOldest),
            loading = false,
            error = null,
            warning = snap.conversation?.warning ?: state.warning,
            needsReload = false
        )
    }

    /** Acrescenta uma página mais antiga (`beforeTurn`) no topo. */
    fun applyOlder(state: ChatState, snap: ConversationSnapshot): ChatState {
        val older = BlockMapper.toItems(snap.blocks)
        val existing = state.items.map { it.key }.toHashSet()
        return state.copy(
            items = older.filter { it.key !in existing } + state.items,
            hasMore = snap.page?.hasMore ?: false,
            oldestTurn = snap.page?.oldestTurn ?: state.oldestTurn,
            loadingOlder = false
        )
    }

    /** Aplica um evento do host. Eventos de outras conversas são ignorados. */
    fun apply(state: ChatState, ev: HostEvent): ChatState = when (ev) {
        is HostEvent.Conv ->
            if (ev.conversationId != state.conversationId) state
            else applyEngine(state, ev.turn, ev.event).let { if (ev.truncated) it.copy(needsReload = true) else it }
        is HostEvent.QueueUpdate ->
            if (ev.conversationId != state.conversationId) state else state.copy(queue = ev.queue)
        is HostEvent.EventTooLarge ->
            if (ev.conversationId == state.conversationId) state.copy(needsReload = true) else state
        is HostEvent.Notice ->
            if (ev.conversationId == state.conversationId && ev.level != "info") state.copy(warning = ev.message) else state
        else -> state
    }

    fun applyEngine(state: ChatState, turnOrNull: Long?, e: EngineEvent): ChatState {
        val turn = turnOrNull ?: state.currentTurn ?: -1L
        return when (e) {
            is EngineEvent.TurnStarted -> {
                val key = "L:$turn:user"
                val items = if (state.items.any { it.key == key }) state.items
                else state.items + ChatItem.User(key, turn, e.text, e.attachments)
                state.copy(items = items, busy = true, isStreaming = true, currentTurn = turn, status = null)
            }
            is EngineEvent.TextDelta -> appendText(state, turn, e.block, e.text, thinking = false)
            is EngineEvent.ThinkingDelta -> appendText(state, turn, e.block, e.text, thinking = true)
            is EngineEvent.ToolStart -> {
                val key = "L:$turn:b${e.block}"
                val existing = state.items.indexOfFirst { it is ChatItem.Tool && it.id == e.id }
                if (existing >= 0) state.copy(busy = true, isStreaming = true, currentTurn = turn)
                else state.copy(
                    items = state.items + ChatItem.Tool(key, turn, e.id, e.name, null, null, ToolStatus.RUNNING, e.nested),
                    busy = true, isStreaming = true, currentTurn = turn
                )
            }
            is EngineEvent.ToolInput -> updateTool(state, e.id) { it.copy(input = e.input) }
            is EngineEvent.ToolResult -> updateTool(state, e.id) {
                it.copy(output = e.output, status = if (e.isError) ToolStatus.ERROR else ToolStatus.DONE)
            }
            is EngineEvent.SubagentActivity -> updateTool(state, e.parentToolUseId) {
                it.copy(subagent = it.subagent + SubagentStep(e.kind, e.toolId, e.name, e.text, e.isError, e.at))
            }
            is EngineEvent.PermissionRequested -> {
                val r = e.request
                if (state.pending.any { it.requestId == r.requestId }) state
                else state.copy(pending = state.pending + r)
            }
            is EngineEvent.PermissionCancelled ->
                state.copy(pending = state.pending.filterNot { it.requestId == e.requestId })
            is EngineEvent.Steer -> state.copy(
                items = state.items + ChatItem.Steer("L:$turn:steer${state.items.size}", turn, e.text, e.attachments)
            )
            is EngineEvent.Status -> state.copy(status = e.text?.let { if (it == "compacted") "Contexto compactado" else it })
            is EngineEvent.RateLimitWait -> state.copy(status = "Aguardando limite de uso (${e.secondsRemaining} s)")
            is EngineEvent.RateLimit -> state.copy(rateLimit = e.info)
            is EngineEvent.Failover -> state.copy(
                items = state.items + ChatItem.Notice(
                    "L:$turn:failover${state.items.size}", turn,
                    "Troca de conta: ${e.from ?: "?"} → ${e.to ?: "?"}" + (e.reason?.let { " ($it)" } ?: "")
                )
            )
            is EngineEvent.Ready -> state
            is EngineEvent.TurnComplete -> endTurn(state).copy(lastUsage = e.usage, lastCostUsd = e.costUsd)
            is EngineEvent.TurnError -> endTurn(state).let {
                if (e.kind == "interrupted") it
                else it.copy(items = it.items + ChatItem.Error("L:$turn:error", turn, e.kind, e.message))
            }
            is EngineEvent.Exited -> endTurn(state).let {
                if (e.code == null || e.code == 0L) it
                else it.copy(items = it.items + ChatItem.Error("L:$turn:exit", turn, "exited", e.detail.ifBlank { "O processo terminou (código ${e.code})." }))
            }
            is EngineEvent.McpStatus, is EngineEvent.Unknown -> state
        }
    }

    private fun endTurn(state: ChatState): ChatState = state.copy(
        busy = false,
        isStreaming = false,
        status = null,
        // O host limpa as pendências no fim do turno.
        pending = emptyList(),
        items = state.items.map {
            if (it is ChatItem.Tool && it.status == ToolStatus.RUNNING) it.copy(status = ToolStatus.INTERRUPTED) else it
        }
    )

    private fun appendText(state: ChatState, turn: Long, block: Long, text: String, thinking: Boolean): ChatState {
        val key = "L:$turn:b$block${if (thinking) "t" else ""}"
        val idx = state.items.indexOfFirst { it.key == key }
        val items = if (idx >= 0) {
            state.items.toMutableList().also { list ->
                list[idx] = when (val cur = list[idx]) {
                    is ChatItem.Text -> cur.copy(text = cur.text + text)
                    is ChatItem.Thinking -> cur.copy(text = cur.text + text)
                    else -> cur
                }
            }
        } else {
            state.items + if (thinking) ChatItem.Thinking(key, turn, text) else ChatItem.Text(key, turn, text)
        }
        return state.copy(items = items, busy = true, isStreaming = true, currentTurn = turn)
    }

    private fun updateTool(state: ChatState, id: String, f: (ChatItem.Tool) -> ChatItem.Tool): ChatState {
        val idx = state.items.indexOfLast { it is ChatItem.Tool && it.id == id }
        if (idx < 0) return state
        val list = state.items.toMutableList()
        list[idx] = f(list[idx] as ChatItem.Tool)
        return state.copy(items = list)
    }
}
