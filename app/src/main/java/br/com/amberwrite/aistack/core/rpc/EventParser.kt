package br.com.amberwrite.aistack.core.rpc

import br.com.amberwrite.aistack.data.model.AccountStatus
import br.com.amberwrite.aistack.data.model.Attachment
import br.com.amberwrite.aistack.data.model.McpServer
import br.com.amberwrite.aistack.data.model.PairedDevice
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.QueuedMessage
import br.com.amberwrite.aistack.data.model.RateLimitInfo
import br.com.amberwrite.aistack.data.model.TurnUsage
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Converte `{"t":"event","event":…,"payload":…}` em [HostEvent]. Nunca lança exceção. */
object EventParser {

    fun parse(name: String, payload: JsonElement?): HostEvent = try {
        parseOrThrow(name, payload)
    } catch (e: Exception) {
        HostEvent.Unknown(name, payload)
    }

    private fun parseOrThrow(name: String, payload: JsonElement?): HostEvent {
        val p = payload.asObj()
        return when (name) {
            "conv-event" -> parseConv(p) ?: HostEvent.Unknown(name, payload)
            "queue-update" -> {
                val id = p?.str("conversationId") ?: return HostEvent.Unknown(name, payload)
                HostEvent.QueueUpdate(id, QueuedMessage.parseList(p.opt("queue")))
            }
            "conversations-changed" -> HostEvent.ConversationsChanged(p?.str("id"), p?.str("reason"))
            "notice" -> HostEvent.Notice(
                level = p?.str("level") ?: "info",
                message = p?.str("message") ?: "",
                provider = p?.str("provider"),
                slot = p?.str("slot"),
                conversationId = p?.str("conversationId")
            )
            "usage-update" -> HostEvent.UsageUpdate(p?.str("provider"), p?.str("slot"), p?.opt("report"), p?.str("source"))
            "accounts-update" -> HostEvent.AccountsUpdate(
                if (payload is JsonArray) AccountStatus.parseList(payload) else null
            )
            "mcp-update" -> HostEvent.McpUpdate
            "api-accounts-changed" -> HostEvent.ApiAccountsChanged
            "relay-status" -> HostEvent.RelayStatus(p?.str("state"), p?.str("error"))
            "devices-changed" -> HostEvent.DevicesChanged(PairedDevice.parseList(payload))
            "resync" -> HostEvent.Resync(p?.str("reason"), p?.long("missed"))
            "eventTooLarge" -> HostEvent.EventTooLarge(
                event = p?.str("event"),
                conversationId = p?.str("conversationId"),
                turn = p?.long("turn"),
                kind = p?.str("kind"),
                originalBytes = p?.long("originalBytes")
            )
            else -> HostEvent.Unknown(name, payload)
        }
    }

    private fun parseConv(p: JsonObject?): HostEvent.Conv? {
        val convId = p?.str("conversationId") ?: return null
        val ev = p.obj("event") ?: return null
        return HostEvent.Conv(
            conversationId = convId,
            turn = p.long("turn"),
            event = parseEngine(ev, convId),
            truncated = ev.bool("truncated") ?: false
        )
    }

    fun parseEngine(e: JsonObject, conversationId: String): EngineEvent {
        val type = e.str("type")
        return when (type) {
            "ready" -> EngineEvent.Ready(e.str("nativeSessionId"), e.str("model"), e.str("permissionMode"))
            "textDelta" -> EngineEvent.TextDelta(e.long("block") ?: 0, e.str("text") ?: "")
            "thinkingDelta" -> EngineEvent.ThinkingDelta(e.long("block") ?: 0, e.str("text") ?: "")
            "toolStart" -> EngineEvent.ToolStart(
                block = e.long("block") ?: 0,
                id = e.str("id") ?: "",
                name = e.str("name") ?: "?",
                nested = e.bool("nested") ?: false
            )
            "toolInput" -> EngineEvent.ToolInput(e.str("id") ?: "", e.opt("input"))
            "toolResult" -> EngineEvent.ToolResult(e.str("id") ?: "", e.opt("output"), e.bool("isError") ?: false)
            "permissionRequest" -> PermissionRequest.parse(e, conversationId)
                ?.let { EngineEvent.PermissionRequested(it) } ?: EngineEvent.Unknown(type)
            "permissionCancelled" -> e.str("requestId")
                ?.let { EngineEvent.PermissionCancelled(it) } ?: EngineEvent.Unknown(type)
            "rateLimitWait" -> EngineEvent.RateLimitWait(e.long("secondsRemaining") ?: 0)
            "rateLimit" -> EngineEvent.RateLimit(RateLimitInfo.parse(e))
            "status" -> EngineEvent.Status(e.str("text"))
            "turnComplete" -> EngineEvent.TurnComplete(
                usage = TurnUsage.parse(e.obj("usage")),
                costUsd = e.double("costUsd"),
                durationMs = e.long("durationMs")
            )
            "turnError" -> EngineEvent.TurnError(e.str("kind") ?: "other", e.str("message") ?: "Erro no turno.")
            "exited" -> EngineEvent.Exited(e.long("code"), e.str("detail") ?: "")
            "mcpStatus" -> EngineEvent.McpStatus(
                e.arr("servers")?.objects()?.mapNotNull { s ->
                    s.str("name")?.let { McpServer(it, s.str("status") ?: "unknown", s.str("detail")) }
                } ?: emptyList()
            )
            "subagentActivity" -> EngineEvent.SubagentActivity(
                parentToolUseId = e.str("parentToolUseId") ?: "",
                kind = e.str("kind") ?: "text",
                toolId = e.str("toolId"),
                name = e.str("name"),
                text = e.str("text"),
                isError = e.bool("isError") ?: false,
                at = e.epochMillis("at")
            )
            "turnStarted" -> {
                val user = e.obj("user")
                EngineEvent.TurnStarted(
                    slot = e.str("slot"),
                    text = user?.str("text") ?: "",
                    attachments = Attachment.parseList(user?.opt("attachments"))
                )
            }
            "steer" -> EngineEvent.Steer(e.str("text") ?: "", Attachment.parseList(e.opt("attachments")))
            "failover" -> EngineEvent.Failover(e.str("provider"), e.str("from"), e.str("to"), e.str("reason"))
            else -> EngineEvent.Unknown(type)
        }
    }
}
