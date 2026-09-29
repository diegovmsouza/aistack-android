package br.com.amberwrite.aistack.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject

enum class Provider(val id: String, val displayName: String, val colorHex: Long) {
    CLAUDE("claude", "Claude", 0xFFD97757),
    CODEX("codex", "OpenAI / Codex", 0xFFE5E7EB),
    AGY("agy", "Gemini", 0xFF38BDF8),
    KIMI("kimi", "Kimi", 0xFF027AFF),
    DEEPSEEK("deepseek", "DeepSeek", 0xFF4D6BFE),
    GLM("glm", "GLM", 0xFF1F54FE),
    QWEN("qwen", "Qwen", 0xFF615CED);

    companion object {
        fun fromId(id: String?): Provider {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: CLAUDE
        }
    }
}

data class ConversationItem(
    val id: String,
    val provider: Provider,
    val title: String,
    val projectPath: String?,
    val model: String?,
    val effort: String?,
    val permissionMode: String,
    val updatedAt: Long
)

sealed class ChatBlock {
    data class Text(
        val id: String,
        val text: String,
        val isStreaming: Boolean = false
    ) : ChatBlock()

    data class Thinking(
        val id: String,
        val text: String,
        val elapsedSeconds: Int = 0,
        val isStreaming: Boolean = false
    ) : ChatBlock()

    data class ToolCall(
        val id: String,
        val toolName: String,
        val input: String,
        val output: String? = null,
        val isError: Boolean = false,
        val isRunning: Boolean = false,
        val diffAdditions: List<String> = emptyList(),
        val diffDeletions: List<String> = emptyList()
    ) : ChatBlock()

    data class Permission(
        val requestId: String,
        val toolName: String,
        val commandOrFile: String,
        val isDecided: Boolean = false,
        val decision: String? = null
    ) : ChatBlock()
}

data class ChatMessage(
    val id: String,
    val role: String, // "user" | "assistant" | "system"
    val blocks: List<ChatBlock>,
    val timestamp: Long = System.currentTimeMillis()
)

data class UsageWindow(
    val kind: String, // "five_hour" | "weekly"
    val label: String,
    val usedPct: Double,
    val resetsAt: Long?
)

data class AccountStatus(
    val provider: Provider,
    val slot: String, // "a" | "b"
    val email: String?,
    val label: String?,
    val plan: String?,
    val windows: List<UsageWindow>
)
