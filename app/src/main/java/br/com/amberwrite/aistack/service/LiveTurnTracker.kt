package br.com.amberwrite.aistack.service

import br.com.amberwrite.aistack.core.rpc.EngineEvent
import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.core.rpc.endsTurn
import br.com.amberwrite.aistack.data.model.ToolQuestion

/** Estado de um turno em andamento, base do Live Update. */
data class LiveTurn(
    val conversationId: String,
    val startedAt: Long,
    /** Ferramenta atual (principal ou do sub-agente mais recente). */
    val tool: String? = null,
    /** Id da ferramenta principal em execução (para limpar no `toolResult`). */
    val toolId: String? = null,
    /** `toolUseId` dos sub-agentes ainda ativos. */
    val activeSubagents: Set<String> = emptySet(),
    /** Sub-agentes iniciados neste turno. */
    val subagentsTotal: Int = 0,
    val status: String? = null,
    val rateLimitSeconds: Long? = null,
    /** Chegou algum evento de ferramenta (nível `full`). */
    val detailed: Boolean = false,
)

/** O que mudou com um evento. */
sealed interface LiveChange {
    data class Updated(val conversationId: String) : LiveChange

    /**
     * O turno acabou. [toolQuestion] é a pergunta de ferramenta que ficou aberta no fim do
     * turno (o turno parou esperando a resposta do usuário), se houver.
     */
    data class Ended(val conversationId: String, val event: EngineEvent, val toolQuestion: ToolQuestion?) : LiveChange
}

/**
 * Acompanha os turnos em andamento a partir dos eventos do host. Puro (sem Android, relógio
 * injetado). Não é thread-safe: o [NotificationCoordinator] o usa sob o seu lock.
 *
 * Detalhes de ferramenta (`toolStart`, `toolInput`, `subagentActivity`) só chegam com a
 * conversa assinada em `full`; em `summary` o turno tem só início/fim e o cronômetro.
 */
class LiveTurnTracker(private val clock: () -> Long) {
    private val turns = LinkedHashMap<String, LiveTurn>()

    /** Ferramentas de pergunta em execução: toolId → (conversa, nome). */
    private val questionTools = HashMap<String, Pair<String, String>>()

    /** Última pergunta de ferramenta aberta por conversa (some se outra ferramenta começar). */
    private val openQuestions = HashMap<String, ToolQuestion>()

    /** Fim de turno recente por conversa, para ignorar uma lista `busy` atrasada. */
    private val endedAt = HashMap<String, Long>()

    val turnsSnapshot: List<LiveTurn> get() = turns.values.toList()

    fun turn(conversationId: String): LiveTurn? = turns[conversationId]

    fun onEvent(ev: HostEvent): LiveChange? {
        if (ev is HostEvent.Disconnected) return null
        if (ev !is HostEvent.Conv) return null
        val id = ev.conversationId
        val e = ev.event
        if (e is EngineEvent.TurnStarted) {
            endedAt.remove(id)
            openQuestions.remove(id)
            turns[id] = LiveTurn(id, clock())
            return LiveChange.Updated(id)
        }
        if (e.endsTurn) {
            val question = if (e is EngineEvent.TurnComplete) openQuestions.remove(id) else null
            openQuestions.remove(id)
            questionTools.entries.removeAll { it.value.first == id }
            turns.remove(id)
            endedAt[id] = clock()
            return LiveChange.Ended(id, e, question)
        }
        val cur = turns[id] ?: run {
            // Evento de um turno que começou antes de o app escutar.
            if (!isTurnActivity(e)) return null
            LiveTurn(id, clock()).also { turns[id] = it; endedAt.remove(id) }
        }
        val next = when (e) {
            is EngineEvent.ToolStart -> {
                if (e.name in ToolQuestion.TOOL_NAMES) questionTools[e.id] = id to e.name
                else if (!e.nested) openQuestions.remove(id)
                if (EngineEvent.isSubagentTool(e.name, e.nested) && !e.nested) {
                    cur.copy(
                        activeSubagents = cur.activeSubagents + e.id,
                        subagentsTotal = cur.subagentsTotal + if (e.id in cur.activeSubagents) 0 else 1,
                        tool = e.name,
                        detailed = true,
                        rateLimitSeconds = null,
                    )
                } else if (e.nested) {
                    cur.copy(tool = e.name, detailed = true, rateLimitSeconds = null)
                } else {
                    cur.copy(tool = e.name, toolId = e.id, detailed = true, rateLimitSeconds = null)
                }
            }
            is EngineEvent.ToolInput -> {
                questionTools[e.id]?.let { (conv, name) ->
                    ToolQuestion.from(name, e.input)?.let { openQuestions[conv] = it }
                }
                cur.copy(detailed = true)
            }
            is EngineEvent.ToolResult -> {
                questionTools.remove(e.id)
                when {
                    e.id in cur.activeSubagents -> {
                        val left = cur.activeSubagents - e.id
                        cur.copy(activeSubagents = left, tool = if (cur.toolId == null && left.isEmpty()) null else cur.tool, detailed = true)
                    }
                    e.id == cur.toolId -> cur.copy(tool = null, toolId = null, detailed = true)
                    else -> cur.copy(detailed = true)
                }
            }
            is EngineEvent.SubagentActivity -> when (e.kind) {
                "result" -> {
                    val left = cur.activeSubagents - e.parentToolUseId
                    cur.copy(activeSubagents = left, detailed = true)
                }
                else -> {
                    val known = e.parentToolUseId in cur.activeSubagents
                    cur.copy(
                        activeSubagents = cur.activeSubagents + e.parentToolUseId,
                        subagentsTotal = cur.subagentsTotal + if (known) 0 else 1,
                        tool = if (e.kind == "tool" && !e.name.isNullOrBlank()) e.name else cur.tool,
                        detailed = true,
                    )
                }
            }
            is EngineEvent.Status -> cur.copy(status = e.text?.trim()?.takeIf { it.isNotEmpty() })
            is EngineEvent.RateLimitWait -> cur.copy(rateLimitSeconds = e.secondsRemaining.takeIf { it > 0 })
            is EngineEvent.TextDelta -> {
                // Texto depois da pergunta: ela não é mais o último item (não ficou "aberta").
                openQuestions.remove(id)
                cur.copy(rateLimitSeconds = null)
            }
            else -> cur
        }
        if (next == cur) return null
        turns[id] = next
        return LiveChange.Updated(id)
    }

    /**
     * Alinha com a lista de pendências (`busy` vem de `listPending` e dos eventos). Conversas
     * ocupadas sem turno conhecido ganham um (cronômetro a partir de agora); as que deixaram de
     * estar ocupadas saem. Um fim de turno nos últimos [graceMs] vence uma lista atrasada.
     * Devolve as conversas cujo estado mudou.
     */
    fun syncBusy(busy: Set<String>, graceMs: Long = 5_000): Set<String> {
        val now = clock()
        val changed = HashSet<String>()
        for (id in busy) {
            if (id in turns) continue
            val ended = endedAt[id]
            if (ended != null && now - ended < graceMs) continue
            turns[id] = LiveTurn(id, now)
            changed += id
        }
        val gone = turns.keys.filter { it !in busy }
        for (id in gone) {
            turns.remove(id)
            openQuestions.remove(id)
            changed += id
        }
        endedAt.entries.removeAll { now - it.value >= graceMs }
        return changed
    }

    fun clear() {
        turns.clear()
        questionTools.clear()
        openQuestions.clear()
        endedAt.clear()
    }

    private fun isTurnActivity(e: EngineEvent): Boolean = when (e) {
        is EngineEvent.ToolStart, is EngineEvent.ToolInput, is EngineEvent.ToolResult,
        is EngineEvent.SubagentActivity, is EngineEvent.TextDelta, is EngineEvent.ThinkingDelta,
        is EngineEvent.PermissionRequested, is EngineEvent.RateLimitWait -> true
        else -> false
    }
}

/** Limite de frequência por chave (no máximo uma atualização a cada [minIntervalMs]). */
class LiveThrottle(private val minIntervalMs: Long = 1_000) {
    private val last = HashMap<String, Long>()

    /** Quanto esperar antes de emitir [key] agora (0 = pode emitir já). */
    fun delayFor(key: String, now: Long): Long {
        val prev = last[key] ?: return 0
        return (prev + minIntervalMs - now).coerceAtLeast(0)
    }

    fun mark(key: String, now: Long) {
        last[key] = now
    }

    fun reset(key: String) {
        last.remove(key)
    }

    fun clear() = last.clear()
}
