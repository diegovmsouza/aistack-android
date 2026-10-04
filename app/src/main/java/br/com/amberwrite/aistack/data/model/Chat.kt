package br.com.amberwrite.aistack.data.model

import br.com.amberwrite.aistack.core.rpc.asObj
import br.com.amberwrite.aistack.core.rpc.asStr
import br.com.amberwrite.aistack.core.rpc.bool
import br.com.amberwrite.aistack.core.rpc.objects
import br.com.amberwrite.aistack.core.rpc.arr
import br.com.amberwrite.aistack.core.rpc.str
import com.google.gson.JsonElement

/** Estado de uma ferramenta no chat (espelha `status` do bloco `tool`). */
enum class ToolStatus { RUNNING, DONE, ERROR, INTERRUPTED;
    companion object {
        fun fromId(v: String?): ToolStatus = when (v) {
            "running" -> RUNNING
            "error" -> ERROR
            "interrupted" -> INTERRUPTED
            else -> DONE
        }
    }
}

/** Um passo de sub-agente mostrado dentro do card da ferramenta-mãe (evento `subagentActivity`). */
data class SubagentStep(
    val kind: String,
    val toolId: String?,
    val name: String?,
    val text: String?,
    val isError: Boolean,
    val at: Long?
)

/**
 * Item renderizável do chat. A [key] é estável: blocos persistidos usam `B:turn:seq`;
 * itens ao vivo usam `L:turn:…`. Ao recarregar a página, os ao vivo são trocados pelos persistidos.
 */
sealed interface ChatItem {
    val key: String
    val turn: Long

    data class User(override val key: String, override val turn: Long, val text: String, val attachments: List<Attachment>) : ChatItem
    data class Text(override val key: String, override val turn: Long, val text: String) : ChatItem
    data class Thinking(override val key: String, override val turn: Long, val text: String) : ChatItem
    data class Tool(
        override val key: String,
        override val turn: Long,
        val id: String,
        val name: String,
        val input: JsonElement?,
        val output: JsonElement?,
        val status: ToolStatus,
        val nested: Boolean = false,
        val truncated: Boolean = false,
        /** `seq` do bloco persistido (para `getBlock`), quando houver. */
        val seq: Long? = null,
        val subagent: List<SubagentStep> = emptyList()
    ) : ChatItem {
        /** Pergunta de `ask_question`/`AskFollowupQuestion` (caso (b) da spec), se for uma. */
        val question: ToolQuestion? get() = ToolQuestion.from(name, input)
        val isSubagent: Boolean get() = br.com.amberwrite.aistack.core.rpc.EngineEvent.isSubagentTool(name, nested)
    }
    data class Steer(override val key: String, override val turn: Long, val text: String, val attachments: List<Attachment>) : ChatItem
    data class Error(override val key: String, override val turn: Long, val kind: String, val message: String) : ChatItem
    data class Notice(override val key: String, override val turn: Long, val text: String) : ChatItem
}

/**
 * Pergunta feita por ferramenta (`ask_question`/`AskFollowupQuestion`). A resposta é uma
 * mensagem comum (`sendMessage`), formatada por [answerText].
 */
data class ToolQuestion(val questions: List<Question>) {
    companion object {
        val TOOL_NAMES = setOf("ask_question", "AskFollowupQuestion")

        fun from(name: String, input: JsonElement?): ToolQuestion? {
            if (name !in TOOL_NAMES) return null
            val qs = Question.parseAll(input.asObj()).filter { it.question.isNotBlank() && it.options.size >= 2 }
            return if (qs.isEmpty()) null else ToolQuestion(qs)
        }

        /**
         * Texto da resposta no formato do desktop: `"{n}. {label}{: detalhe}"`.
         * [index] é 0-based; [freeText] tem prioridade quando não vazio.
         */
        fun answerText(question: Question, index: Int?, freeText: String? = null): String {
            if (!freeText.isNullOrBlank()) return freeText.trim()
            val i = index ?: return ""
            val opt = question.options.getOrNull(i) ?: return ""
            val detail = opt.description?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            return "${i + 1}. ${opt.label}$detail"
        }
    }
}

/** Estado completo de uma conversa aberta. */
data class ChatState(
    val conversationId: String,
    val conversation: Conversation? = null,
    val items: List<ChatItem> = emptyList(),
    /** O host tem um turno ativo. */
    val busy: Boolean = false,
    /** Chegam deltas ao vivo; zera em `turnComplete`, `turnError` e `exited`. */
    val isStreaming: Boolean = false,
    val currentTurn: Long? = null,
    val queue: List<QueuedMessage> = emptyList(),
    val pending: List<PermissionRequest> = emptyList(),
    val hasMore: Boolean = false,
    val oldestTurn: Long? = null,
    /** Texto de status do motor (`status`, espera de limite, failover…). */
    val status: String? = null,
    val rateLimit: RateLimitInfo? = null,
    val lastUsage: TurnUsage? = null,
    val lastCostUsd: Double? = null,
    val loading: Boolean = false,
    val loadingOlder: Boolean = false,
    val error: String? = null,
    /** Aviso do host (ex.: bypass rebaixado para ask). */
    val warning: String? = null,
    /** Algum evento ao vivo foi cortado (`truncated`) ou perdido: recarregar no fim do turno. */
    val needsReload: Boolean = false
)

/** Converte blocos persistidos em itens do chat. `kind` desconhecido vira texto genérico. */
object BlockMapper {
    fun toItems(blocks: List<Block>): List<ChatItem> = blocks.mapNotNull(::toItem)

    fun toItem(b: Block): ChatItem? {
        val key = "B:${b.turn}:${b.seq}"
        val c = b.content
        val o = c.asObj()
        return when (b.kind) {
            "user" -> ChatItem.User(key, b.turn, o?.str("text") ?: c.asStr() ?: "", Attachment.parseList(o?.get("attachments")))
            "text" -> ChatItem.Text(key, b.turn, o?.str("text") ?: c.asStr() ?: "")
            "thinking" -> ChatItem.Thinking(key, b.turn, o?.str("text") ?: c.asStr() ?: "")
            "steer" -> ChatItem.Steer(key, b.turn, o?.str("text") ?: c.asStr() ?: "", Attachment.parseList(o?.get("attachments")))
            "tool" -> ChatItem.Tool(
                key = key,
                turn = b.turn,
                id = o?.str("id") ?: key,
                name = o?.str("name") ?: "?",
                input = o?.get("input"),
                output = o?.get("output"),
                status = ToolStatus.fromId(o?.str("status")),
                nested = o?.bool("nested") ?: false,
                truncated = b.truncated,
                seq = b.seq,
                subagent = o?.arr("subagent")?.objects()?.map {
                    SubagentStep(it.str("kind") ?: "", it.str("toolId"), it.str("name"), it.str("text"), it.bool("isError") ?: false, null)
                } ?: emptyList()
            )
            "error" -> ChatItem.Error(key, b.turn, o?.str("kind") ?: "other", o?.str("message") ?: c.asStr() ?: "Erro")
            "compacted", "compact_boundary" -> ChatItem.Notice(key, b.turn, "Contexto compactado")
            else -> {
                val text = o?.str("text") ?: o?.str("message") ?: c.asStr() ?: c?.toString() ?: ""
                if (text.isBlank()) null else ChatItem.Notice(key, b.turn, text)
            }
        }
    }
}
