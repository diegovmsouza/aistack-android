package br.com.amberwrite.aistack.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** O que a tela precisa fazer, além de atualizar a lista, depois de aplicar um evento do host. */
sealed class ChatEffect {
    data object None : ChatEffect()

    /** O turno acabou (concluído, com erro ou com o processo do motor encerrado). */
    data object TurnEnded : ChatEffect()

    data class PermissionAsked(val requestId: String, val tool: String, val input: String) : ChatEffect()

    /** O CLI desistiu do pedido (turno interrompido): a notificação não vale mais. */
    data class PermissionCancelled(val requestId: String) : ChatEffect()
}

/**
 * Aplica à lista de mensagens um evento normalizado do host (`EngineEvent`, ver
 * `src-tauri/src/engines/mod.rs`). Função pura sobre a lista: sem Android, testável na JVM.
 *
 * Eventos que não mudam o chat (Ready, RateLimit, RateLimitWait, Status, McpStatus e os eventos
 * de ciclo `turnStarted` etc.) são ignorados de propósito; o app não os mostra.
 */
object ChatReducer {
    fun apply(messages: MutableList<ChatMessage>, inner: JsonObject, newId: () -> String): ChatEffect {
        when (inner.str("type")) {
            "TextDelta" -> appendStreaming(messages, newId, inner.str("text") ?: "", thinking = false)
            "ThinkingDelta" -> appendStreaming(messages, newId, inner.str("text") ?: "", thinking = true)
            "ToolStart" -> {
                val block = ChatBlock.ToolCall(
                    id = inner.str("id") ?: newId(),
                    toolName = inner.str("name") ?: "ferramenta",
                    input = "",
                    isRunning = true
                )
                addBlock(messages, newId, block)
            }
            "ToolInput" -> updateTool(messages, inner.str("id")) { it.copy(input = inner.get("input").text()) }
            "ToolResult" -> updateTool(messages, inner.str("id")) {
                it.copy(
                    output = inner.get("output").text(),
                    isError = inner.get("isError")?.takeIf { e -> e.isJsonPrimitive }?.asBoolean ?: false,
                    isRunning = false
                )
            }
            "PermissionRequest" -> {
                val reqId = inner.str("requestId") ?: return ChatEffect.None
                val tool = inner.str("tool") ?: "Comando"
                val input = inner.get("input").text()
                addBlock(messages, newId, ChatBlock.Permission(requestId = reqId, toolName = tool, commandOrFile = input))
                return ChatEffect.PermissionAsked(reqId, tool, input)
            }
            "PermissionCancelled" -> {
                val reqId = inner.str("requestId") ?: return ChatEffect.None
                markPermission(messages, reqId, "cancelled")
                return ChatEffect.PermissionCancelled(reqId)
            }
            "TurnComplete" -> {
                settle(messages)
                return ChatEffect.TurnEnded
            }
            "TurnError" -> {
                settle(messages)
                val msg = inner.str("message")?.takeIf { it.isNotBlank() } ?: "O turno terminou com erro."
                messages.add(systemMessage(newId, "Erro: $msg"))
                return ChatEffect.TurnEnded
            }
            "Exited" -> {
                settle(messages)
                val detail = inner.str("detail")?.takeIf { it.isNotBlank() }
                messages.add(systemMessage(newId, "O motor foi encerrado" + (detail?.let { ": $it" } ?: ".")))
                return ChatEffect.TurnEnded
            }
        }
        return ChatEffect.None
    }

    /** Registra a decisão tomada no cartão (o botão some e o resultado aparece). */
    fun markPermission(messages: MutableList<ChatMessage>, requestId: String, decision: String) {
        for (mi in messages.indices.reversed()) {
            val m = messages[mi]
            val bi = m.blocks.indexOfFirst { it is ChatBlock.Permission && it.requestId == requestId }
            if (bi < 0) continue
            val old = m.blocks[bi] as ChatBlock.Permission
            messages[mi] = m.copy(blocks = m.blocks.toMutableList().also { it[bi] = old.copy(isDecided = true, decision = decision) })
            return
        }
    }

    private fun appendStreaming(messages: MutableList<ChatMessage>, newId: () -> String, text: String, thinking: Boolean) {
        val last = messages.lastOrNull()?.takeIf { it.role == "assistant" }
        val tail = last?.blocks?.lastOrNull()
        if (last != null && tail is ChatBlock.Text && !thinking) {
            replaceTail(messages, last, tail.copy(text = tail.text + text, isStreaming = true))
        } else if (last != null && tail is ChatBlock.Thinking && thinking) {
            replaceTail(messages, last, tail.copy(text = tail.text + text, isStreaming = true))
        } else {
            val block: ChatBlock = if (thinking) {
                ChatBlock.Thinking(id = newId(), text = text, isStreaming = true)
            } else {
                ChatBlock.Text(id = newId(), text = text, isStreaming = true)
            }
            addBlock(messages, newId, block)
        }
    }

    private fun replaceTail(messages: MutableList<ChatMessage>, last: ChatMessage, block: ChatBlock) {
        messages[messages.size - 1] = last.copy(blocks = last.blocks.toMutableList().also { it[it.size - 1] = block })
    }

    /** Põe o bloco na mensagem do assistente em curso; abre uma se a última não for dele. */
    private fun addBlock(messages: MutableList<ChatMessage>, newId: () -> String, block: ChatBlock) {
        val last = messages.lastOrNull()
        if (last != null && last.role == "assistant") {
            messages[messages.size - 1] = last.copy(blocks = last.blocks + block)
        } else {
            messages.add(ChatMessage(id = newId(), role = "assistant", blocks = listOf(block)))
        }
    }

    private fun updateTool(messages: MutableList<ChatMessage>, id: String?, change: (ChatBlock.ToolCall) -> ChatBlock.ToolCall) {
        if (id == null) return
        for (mi in messages.indices.reversed()) {
            val m = messages[mi]
            val bi = m.blocks.indexOfFirst { it is ChatBlock.ToolCall && it.id == id }
            if (bi < 0) continue
            val old = m.blocks[bi] as ChatBlock.ToolCall
            messages[mi] = m.copy(blocks = m.blocks.toMutableList().also { it[bi] = change(old) })
            return
        }
    }

    /** Fim de turno: nada continua girando nem em "digitando". */
    private fun settle(messages: MutableList<ChatMessage>) {
        for (mi in messages.indices) {
            val m = messages[mi]
            if (m.blocks.none { it is ChatBlock.Text && it.isStreaming || it is ChatBlock.Thinking && it.isStreaming || it is ChatBlock.ToolCall && it.isRunning }) continue
            messages[mi] = m.copy(
                blocks = m.blocks.map {
                    when (it) {
                        is ChatBlock.Text -> it.copy(isStreaming = false)
                        is ChatBlock.Thinking -> it.copy(isStreaming = false)
                        is ChatBlock.ToolCall -> it.copy(isRunning = false)
                        else -> it
                    }
                }
            )
        }
    }

    private fun systemMessage(newId: () -> String, text: String) =
        ChatMessage(id = newId(), role = "system", blocks = listOf(ChatBlock.Text(id = newId(), text = text)))

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    /** Texto de um valor JSON arbitrário: string vira ela mesma; o resto, o JSON compacto. */
    private fun JsonElement?.text(): String = when {
        this == null || isJsonNull -> ""
        isJsonPrimitive -> asString
        else -> toString()
    }
}
