package br.com.amberwrite.aistack.feature.chat.thread

import br.com.amberwrite.aistack.core.rpc.asObj
import br.com.amberwrite.aistack.core.rpc.displayText
import br.com.amberwrite.aistack.core.rpc.str
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.ChatState
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.ui.designsystem.components.StepState
import br.com.amberwrite.aistack.ui.designsystem.components.TimelineStep
import br.com.amberwrite.aistack.ui.designsystem.components.ToolKind
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import br.com.amberwrite.aistack.data.model.ToolStatus as ModelToolStatus
import br.com.amberwrite.aistack.ui.designsystem.components.ToolStatus as DsToolStatus

/** Linha renderizável da thread, na ordem cronológica (mais antiga primeiro). */
sealed interface ThreadRow {
    val key: String

    /** Gatilho de paginação no topo ("carregando anteriores…"). */
    data class Older(val loading: Boolean) : ThreadRow {
        override val key: String get() = KEY
        companion object { const val KEY = "older" }
    }

    /** Item da conversa. [caret] marca o texto que está chegando ao vivo. */
    data class Item(val item: ChatItem, val caret: Boolean = false, val retry: Boolean = false) : ThreadRow {
        override val key: String get() = item.key
    }

    /** Pedido de permissão / `AskUserQuestion` inline. */
    data class Pending(val request: PermissionRequest) : ThreadRow {
        override val key: String get() = "P:${request.requestId}"
    }

    /** Pergunta de ferramenta (`ask_question`) aguardando resposta. */
    data class ToolAsk(val tool: ChatItem.Tool, val question: Question) : ThreadRow {
        override val key: String get() = "Q:${tool.key}"
    }

    /** Indicador de trabalho em curso no fim da thread. */
    data class Status(val kind: StatusKind) : ThreadRow {
        override val key: String get() = KEY
        companion object { const val KEY = "status" }
    }
}

/** O que o agente está fazendo agora (o texto final vem dos recursos de string). */
sealed interface StatusKind {
    data class Custom(val text: String) : StatusKind
    data class RunningTool(val name: String) : StatusKind
    data object Thinking : StatusKind
    data object Working : StatusKind
}

/** Um sub-agente (ferramenta `Task`/`Agent`) para a folha «Agentes». */
data class AgentInfo(
    val key: String,
    val toolId: String,
    val title: String,
    val subtitle: String?,
    val status: ModelToolStatus,
    val steps: List<TimelineStep>,
) {
    val running: Boolean get() = status == ModelToolStatus.RUNNING
}

/** Lógica pura da thread: mapeamentos de ferramenta, sub-agentes e montagem das linhas. */
object ThreadModel {

    private const val PREVIEW = 120

    fun toolKindOf(name: String, subagent: Boolean = false): ToolKind = when (name) {
        "Read", "Glob", "LS", "read_file", "list_dir", "view" -> ToolKind.File
        "Edit", "MultiEdit", "Write", "NotebookEdit", "apply_patch", "edit_file", "write_file" -> ToolKind.Edit
        "Bash", "shell", "run_shell_command", "exec_command", "BashOutput", "KillShell" -> ToolKind.Terminal
        "Grep", "search", "grep", "codebase_search" -> ToolKind.Search
        "WebFetch", "WebSearch", "web_search", "web_fetch" -> ToolKind.Web
        "Task", "Agent", "delegate" -> ToolKind.Agent
        "TodoWrite", "update_plan" -> ToolKind.Todo
        else -> if (subagent) ToolKind.Agent else ToolKind.Tool
    }

    /** Resumo de uma linha da entrada (comando, caminho, padrão, URL…). */
    fun toolDetailOf(input: JsonElement?): String? {
        val o = input.asObj() ?: return null
        val raw = o.str("command") ?: o.str("file_path") ?: o.str("path") ?: o.str("notebook_path")
            ?: o.str("pattern") ?: o.str("url") ?: o.str("query") ?: o.str("description")
            ?: o.str("subagent_type") ?: o.str("prompt")
        return raw?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(PREVIEW)
    }

    fun dsStatus(s: ModelToolStatus): DsToolStatus = when (s) {
        ModelToolStatus.RUNNING -> DsToolStatus.Running
        ModelToolStatus.DONE -> DsToolStatus.Success
        ModelToolStatus.ERROR, ModelToolStatus.INTERRUPTED -> DsToolStatus.Error
    }

    /** `B:turn:seq` → (turn, seq). Itens ao vivo (`L:`) não têm bloco persistido. */
    fun parseBlockKey(key: String): Pair<Long, Long>? {
        val parts = key.split(':')
        if (parts.size != 3 || parts[0] != "B") return null
        val turn = parts[1].toLongOrNull() ?: return null
        val seq = parts[2].toLongOrNull() ?: return null
        return turn to seq
    }

    /** Referência do bloco para `getBlock`: o `seq` da ferramenta ou a chave persistida. */
    fun blockRef(item: ChatItem): Pair<Long, Long>? =
        (item as? ChatItem.Tool)?.seq?.let { item.turn to it } ?: parseBlockKey(item.key)

    /** JSON identado para exibição; texto puro passa intacto; `null`/vazio → `null`. */
    fun prettyJson(el: JsonElement?): String? {
        if (el == null || el.isJsonNull) return null
        if (el.isJsonPrimitive) return el.asJsonPrimitive.let { if (it.isString) it.asString else it.toString() }.ifBlank { null }
        return PRETTY.toJson(el)
    }

    /**
     * Código de entrada mais legível por ferramenta: o comando do terminal como `bash`,
     * o conteúdo novo de uma edição na linguagem do arquivo, e o resto como JSON.
     * Devolve (código, linguagem).
     */
    fun inputCode(tool: ChatItem.Tool): Pair<String, String?>? {
        val o = tool.input.asObj()
        when (toolKindOf(tool.name)) {
            ToolKind.Terminal -> o?.str("command")?.let { return it to "bash" }
            ToolKind.Edit -> {
                val path = o?.str("file_path") ?: o?.str("path")
                val body = o?.str("new_string") ?: o?.str("content") ?: o?.str("patch") ?: o?.str("input")
                if (body != null) return body to languageOf(path)
            }
            else -> Unit
        }
        return prettyJson(tool.input)?.let { it to (if (tool.input?.isJsonPrimitive == true) null else "json") }
    }

    /** Saída já em texto (string, ou JSON identado), ou `null` sem saída. */
    fun outputText(tool: ChatItem.Tool): String? {
        val out = tool.output ?: return null
        if (out.isJsonNull) return null
        // Resultado no formato de blocos do Claude: [{type:"text", text:"…"}, …].
        if (out.isJsonArray) {
            val texts = out.asJsonArray.mapNotNull { el -> el.asObj()?.takeIf { it.str("type") == "text" }?.str("text") }
            if (texts.isNotEmpty() && texts.size == out.asJsonArray.size()) return texts.joinToString("\n").ifBlank { null }
        }
        val text = if (out.isJsonPrimitive) out.displayText() else prettyJson(out) ?: out.displayText()
        return text.ifBlank { null }
    }

    /** Linguagem do realce a partir da extensão do arquivo. */
    fun languageOf(path: String?): String? {
        val ext = path?.substringAfterLast('/')?.substringAfterLast('.', "")?.lowercase()?.ifBlank { null } ?: return null
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "md", "markdown", "txt" -> null
            else -> ext
        }
    }

    // ---- sub-agentes ----------------------------------------------------------------------

    /**
     * Converte a atividade de sub-agente (`subagentActivity`) em passos da linha do tempo:
     * `tool` abre um passo em execução; `result` com o mesmo `toolId` o conclui (ou falha);
     * `text` vira um passo concluído. Com a ferramenta-mãe parada, nada fica "rodando".
     */
    fun subagentSteps(tool: ChatItem.Tool): List<TimelineStep> {
        val steps = ArrayList<TimelineStep>()
        val byTool = HashMap<String, Int>()
        tool.subagent.forEachIndexed { i, s ->
            when (s.kind) {
                "tool" -> {
                    val id = s.toolId ?: "s$i"
                    byTool[id] = steps.size
                    steps += TimelineStep(
                        id = id,
                        title = s.name?.ifBlank { null } ?: "Ferramenta",
                        state = StepState.Running,
                        subtitle = s.text?.let(::stepPreview),
                    )
                }
                "result" -> {
                    val idx = s.toolId?.let(byTool::get)
                    val state = if (s.isError) StepState.Failed else StepState.Done
                    if (idx != null) {
                        val old = steps[idx]
                        steps[idx] = old.copy(state = state, meta = if (s.isError) s.text?.let(::firstLine) else old.meta)
                    }
                }
                "text" -> {
                    val text = s.text?.let(::firstLine) ?: return@forEachIndexed
                    steps += TimelineStep(id = "t$i", title = text, state = StepState.Done)
                }
            }
        }
        if (tool.status != ModelToolStatus.RUNNING) {
            val leftover = if (tool.status == ModelToolStatus.DONE) StepState.Done else StepState.Skipped
            for (i in steps.indices) if (steps[i].state == StepState.Running) steps[i] = steps[i].copy(state = leftover)
        }
        return steps
    }

    /** Título de um sub-agente: descrição da tarefa, tipo do agente ou nome da ferramenta. */
    fun agentTitle(tool: ChatItem.Tool): String {
        val o = tool.input.asObj()
        return o?.str("description")?.ifBlank { null } ?: o?.str("subagent_type")?.ifBlank { null } ?: tool.name
    }

    fun collectAgents(items: List<ChatItem>): List<AgentInfo> = items
        .filterIsInstance<ChatItem.Tool>()
        .filter { it.isSubagent }
        .map { t ->
            val o = t.input.asObj()
            AgentInfo(
                key = t.key,
                toolId = t.id,
                title = agentTitle(t),
                subtitle = o?.str("subagent_type")?.takeIf { it != agentTitle(t) } ?: o?.str("prompt")?.let(::firstLine),
                status = t.status,
                steps = subagentSteps(t),
            )
        }

    private fun firstLine(s: String): String? =
        s.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(PREVIEW)

    /** Prévia do `input` do passo: o campo mais útil do JSON, ou a primeira linha. */
    private fun stepPreview(text: String): String? {
        val parsed = runCatching { JsonParser.parseString(text) }.getOrNull()
        return toolDetailOf(parsed) ?: firstLine(text)
    }

    // ---- linhas da thread -----------------------------------------------------------------

    /** Erro que encerrou o último turno (último item relevante), com a conversa parada. */
    fun lastTurnError(state: ChatState): ChatItem.Error? {
        if (state.busy) return null
        return state.items.lastOrNull { it !is ChatItem.Notice } as? ChatItem.Error
    }

    /** Texto da última mensagem do usuário (para "tentar de novo"). */
    fun lastUserText(state: ChatState): String? =
        state.items.lastOrNull { it is ChatItem.User }?.let { (it as ChatItem.User).text }?.takeIf { it.isNotBlank() }

    fun statusOf(state: ChatState): StatusKind? {
        if (!state.busy) return null
        state.status?.takeIf { it.isNotBlank() }?.let { return StatusKind.Custom(it) }
        return when (val last = state.items.lastOrNull()) {
            is ChatItem.Tool -> if (last.status == ModelToolStatus.RUNNING) StatusKind.RunningTool(last.name) else StatusKind.Working
            // O cabeçalho do pensamento ao vivo já brilha; sem streaming, segue trabalhando.
            is ChatItem.Thinking -> if (state.isStreaming) null else StatusKind.Working
            // O texto ao vivo já mostra o cursor; nada a acrescentar.
            is ChatItem.Text -> if (state.isStreaming) null else StatusKind.Working
            else -> StatusKind.Working
        }
    }

    fun buildRows(state: ChatState, toolQuestion: ChatItem.Tool?): List<ThreadRow> {
        val rows = ArrayList<ThreadRow>(state.items.size + state.pending.size + 3)
        if (state.hasMore) rows += ThreadRow.Older(state.loadingOlder)
        val lastIndex = state.items.lastIndex
        val errorKey = lastTurnError(state)?.key
        state.items.forEachIndexed { i, item ->
            val caret = state.isStreaming && i == lastIndex && item is ChatItem.Text
            rows += ThreadRow.Item(item, caret = caret, retry = item.key == errorKey)
            if (toolQuestion != null && item.key == toolQuestion.key) {
                toolQuestion.question?.questions?.firstOrNull()?.let { rows += ThreadRow.ToolAsk(toolQuestion, it) }
            }
        }
        state.pending.forEach { rows += ThreadRow.Pending(it) }
        statusOf(state)?.let { rows += ThreadRow.Status(it) }
        return rows
    }

    private val PRETTY = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
}
