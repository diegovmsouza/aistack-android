package br.com.amberwrite.aistack.data.model

import br.com.amberwrite.aistack.core.rpc.arr
import br.com.amberwrite.aistack.core.rpc.asArr
import br.com.amberwrite.aistack.core.rpc.bool
import br.com.amberwrite.aistack.core.rpc.long
import br.com.amberwrite.aistack.core.rpc.objects
import br.com.amberwrite.aistack.core.rpc.str
import br.com.amberwrite.aistack.core.rpc.strList
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Modelo do catálogo de um provedor (`getCatalog`). */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val description: String?,
    val efforts: List<String>,
    val defaultEffort: String?,
    val isDefault: Boolean
) {
    companion object {
        fun parse(o: JsonObject): ModelInfo? {
            val id = o.str("id") ?: return null
            return ModelInfo(
                id = id,
                displayName = o.str("displayName") ?: id,
                description = o.str("description"),
                efforts = o.strList("efforts"),
                defaultEffort = o.str("defaultEffort"),
                isDefault = o.bool("isDefault") ?: false
            )
        }

        fun parseList(e: JsonElement?): List<ModelInfo> =
            e.asArr()?.objects()?.mapNotNull(::parse) ?: emptyList()
    }
}

/** Estado do relay no desktop (`relayStatus`). */
data class HostRelayStatus(
    /** desligado | conectando | online | erro */
    val state: String,
    val relayUrl: String?,
    val hostId: String?,
    val sessions: Long?,
    val error: String?
) {
    companion object {
        fun parse(o: JsonObject): HostRelayStatus = HostRelayStatus(
            state = o.str("state") ?: "desconhecido",
            relayUrl = o.str("relayUrl"),
            hostId = o.str("hostId"),
            sessions = o.long("sessions"),
            error = o.str("error")
        )
    }
}

/** Informações do AiStack do desktop (`appInfo`). Só o que o celular mostra. */
data class HostAppInfo(val version: String?, val home: String?) {
    companion object {
        fun parse(o: JsonObject): HostAppInfo = HostAppInfo(o.str("version"), o.str("home"))
    }
}

/** Servidor MCP como `listMcp` devolve (só leitura no celular). */
data class McpEntry(
    val name: String,
    val transport: String?,
    val enabled: Boolean,
    val statuses: List<McpServer>
) {
    companion object {
        fun parse(o: JsonObject): McpEntry? {
            val name = o.str("name") ?: return null
            val t = o.get("transport") as? JsonObject
            val transport = when (t?.str("kind")) {
                "stdio" -> t.str("command")
                "http" -> t.str("url")
                else -> null
            }
            val statuses = o.arr("status")?.objects()?.map {
                McpServer(it.str("provider") ?: name, it.str("status") ?: "?", it.str("detail"))
            } ?: emptyList()
            return McpEntry(name, transport, o.bool("enabled") ?: true, statuses)
        }

        fun parseList(e: JsonElement?): List<McpEntry> =
            e.asArr()?.objects()?.mapNotNull(::parse) ?: emptyList()
    }
}

/** Decisão para `answerPermission`. */
sealed interface PermissionDecision {
    fun toParams(): Map<String, Any>

    data class Allow(val remember: Boolean = false, val answers: Map<String, String>? = null) : PermissionDecision {
        override fun toParams(): Map<String, Any> = buildMap {
            put("behavior", "allow")
            if (remember) put("remember", true)
            if (answers != null) put("answers", answers)
        }
    }

    data class Deny(val message: String? = null) : PermissionDecision {
        override fun toParams(): Map<String, Any> = buildMap {
            put("behavior", "deny")
            if (!message.isNullOrBlank()) put("message", message)
        }
    }

    companion object {
        const val DISMISS_QUESTION_MESSAGE = "O usuário dispensou a pergunta."
    }
}
