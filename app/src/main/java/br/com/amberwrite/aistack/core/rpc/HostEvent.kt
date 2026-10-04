package br.com.amberwrite.aistack.core.rpc

import br.com.amberwrite.aistack.data.model.AccountStatus
import br.com.amberwrite.aistack.data.model.Attachment
import br.com.amberwrite.aistack.data.model.McpServer
import br.com.amberwrite.aistack.data.model.PairedDevice
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.QueuedMessage
import br.com.amberwrite.aistack.data.model.RateLimitInfo
import br.com.amberwrite.aistack.data.model.TurnUsage
import com.google.gson.JsonElement

/** Eventos vindos do host (`{"t":"event"}`), já interpretados (contrato v2 §16.2). */
sealed interface HostEvent {

    /** Local: túnel autenticado (novo ou reconectado). Os repositórios recarregam tudo. */
    data class Connected(val hostFrag: Boolean) : HostEvent

    /** Local: túnel caiu. */
    data object Disconnected : HostEvent

    data class Conv(
        val conversationId: String,
        val turn: Long?,
        val event: EngineEvent,
        val truncated: Boolean
    ) : HostEvent

    data class QueueUpdate(val conversationId: String, val queue: List<QueuedMessage>) : HostEvent

    data class ConversationsChanged(val id: String?, val reason: String?) : HostEvent

    data class Notice(
        val level: String,
        val message: String,
        val provider: String?,
        val slot: String?,
        val conversationId: String?
    ) : HostEvent

    data class UsageUpdate(val provider: String?, val slot: String?, val report: JsonElement?, val source: String?) : HostEvent

    /** [accounts] nulo quando o payload é `{}` (o app deve chamar `listAccounts`). */
    data class AccountsUpdate(val accounts: List<AccountStatus>?) : HostEvent

    data object McpUpdate : HostEvent
    data object ApiAccountsChanged : HostEvent

    data class RelayStatus(val state: String?, val error: String?) : HostEvent

    data class DevicesChanged(val devices: List<PairedDevice>) : HostEvent

    /** O host perdeu eventos (`Lagged`): recarregar listas e a conversa aberta. */
    data class Resync(val reason: String?, val missed: Long?) : HostEvent

    data class EventTooLarge(
        val event: String?,
        val conversationId: String?,
        val turn: Long?,
        val kind: String?,
        val originalBytes: Long?
    ) : HostEvent

    data class Unknown(val name: String, val payload: JsonElement?) : HostEvent
}

/** `EngineEvent` dentro de `conv-event` (contrato v2 §16.3). Tipos desconhecidos viram [Unknown]. */
sealed interface EngineEvent {
    data class Ready(val nativeSessionId: String?, val model: String?, val permissionMode: String?) : EngineEvent
    data class TextDelta(val block: Long, val text: String) : EngineEvent
    data class ThinkingDelta(val block: Long, val text: String) : EngineEvent
    data class ToolStart(val block: Long, val id: String, val name: String, val nested: Boolean) : EngineEvent
    data class ToolInput(val id: String, val input: JsonElement?) : EngineEvent
    data class ToolResult(val id: String, val output: JsonElement?, val isError: Boolean) : EngineEvent
    data class PermissionRequested(val request: PermissionRequest) : EngineEvent
    data class PermissionCancelled(val requestId: String) : EngineEvent
    data class RateLimitWait(val secondsRemaining: Long) : EngineEvent
    data class RateLimit(val info: RateLimitInfo) : EngineEvent
    data class Status(val text: String?) : EngineEvent
    data class TurnComplete(val usage: TurnUsage?, val costUsd: Double?, val durationMs: Long?) : EngineEvent
    data class TurnError(val kind: String, val message: String) : EngineEvent
    data class Exited(val code: Long?, val detail: String) : EngineEvent
    data class McpStatus(val servers: List<McpServer>) : EngineEvent
    data class SubagentActivity(
        val parentToolUseId: String,
        val kind: String,
        val toolId: String?,
        val name: String?,
        val text: String?,
        val isError: Boolean,
        val at: Long?
    ) : EngineEvent
    data class TurnStarted(val slot: String?, val text: String, val attachments: List<Attachment>) : EngineEvent
    data class Steer(val text: String, val attachments: List<Attachment>) : EngineEvent
    data class Failover(val provider: String?, val from: String?, val to: String?, val reason: String?) : EngineEvent
    data class Unknown(val type: String?) : EngineEvent

    companion object {
        private val SUBAGENT_NAMES = setOf("task", "delegate")

        /** Heurística de sub-agente: `nested`, nome com agent/subagent, ou `Task`/`delegate`. */
        fun isSubagentTool(name: String?, nested: Boolean): Boolean {
            if (nested) return true
            val n = name?.lowercase() ?: return false
            return "agent" in n || n in SUBAGENT_NAMES
        }
    }
}

/** Fim de turno (para limpar `isStreaming`). */
val EngineEvent.endsTurn: Boolean
    get() = this is EngineEvent.TurnComplete || this is EngineEvent.TurnError || this is EngineEvent.Exited
