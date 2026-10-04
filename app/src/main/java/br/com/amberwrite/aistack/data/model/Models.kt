package br.com.amberwrite.aistack.data.model

import br.com.amberwrite.aistack.core.rpc.arr
import br.com.amberwrite.aistack.core.rpc.asObj
import br.com.amberwrite.aistack.core.rpc.asStr
import br.com.amberwrite.aistack.core.rpc.bool
import br.com.amberwrite.aistack.core.rpc.double
import br.com.amberwrite.aistack.core.rpc.epochMillis
import br.com.amberwrite.aistack.core.rpc.int
import br.com.amberwrite.aistack.core.rpc.long
import br.com.amberwrite.aistack.core.rpc.obj
import br.com.amberwrite.aistack.core.rpc.objects
import br.com.amberwrite.aistack.core.rpc.opt
import br.com.amberwrite.aistack.core.rpc.str
import br.com.amberwrite.aistack.core.rpc.strList
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/*
 * Tipos de domínio do app, em Kotlin puro (testáveis na JVM). Cada tipo tem um `parse`
 * tolerante: campos ausentes viram valores padrão e campos desconhecidos são ignorados.
 */

data class Attachment(val path: String, val mime: String) {
    fun toParams(): Map<String, String> = mapOf("path" to path, "mime" to mime)

    companion object {
        fun parse(o: JsonObject): Attachment? {
            val path = o.str("path") ?: return null
            return Attachment(path, o.str("mime") ?: "application/octet-stream")
        }

        fun parseList(e: JsonElement?): List<Attachment> =
            e.asArrSafe().mapNotNull { (it as? JsonObject)?.let(::parse) }
    }
}

data class OriginDevice(val id: String, val name: String)

enum class ConversationOrigin { DESKTOP, MOBILE;
    companion object {
        fun fromId(v: String?): ConversationOrigin = if (v == "mobile") MOBILE else DESKTOP
    }
}

data class Conversation(
    val id: String,
    val provider: Provider,
    val providerId: String,
    val nativeSessionId: String?,
    val projectPath: String,
    val title: String,
    val model: String?,
    val effort: String?,
    val permissionMode: String?,
    val activeSlot: String?,
    val extraDirs: List<String>,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val origin: ConversationOrigin,
    val originDevice: OriginDevice?,
    val warning: String?
) {
    val displayTitle: String
        get() = title.ifBlank { projectPath.substringAfterLast('/').ifBlank { "Sem título" } }

    companion object {
        fun parse(o: JsonObject): Conversation? {
            val id = o.str("id") ?: return null
            val providerId = o.str("provider") ?: ""
            val dev = o.obj("originDevice")
            return Conversation(
                id = id,
                provider = Provider.fromId(providerId),
                providerId = providerId,
                nativeSessionId = o.str("nativeSessionId"),
                projectPath = o.str("projectPath") ?: "",
                title = o.str("title") ?: "",
                model = o.str("model"),
                effort = o.str("effort"),
                permissionMode = o.str("permissionMode"),
                activeSlot = o.str("activeSlot"),
                extraDirs = o.strList("extraDirs"),
                archived = o.bool("archived") ?: false,
                createdAt = o.epochMillis("createdAt") ?: 0L,
                updatedAt = o.epochMillis("updatedAt") ?: o.epochMillis("createdAt") ?: 0L,
                origin = ConversationOrigin.fromId(o.str("origin")),
                originDevice = dev?.let { d ->
                    d.str("id")?.let { OriginDevice(it, d.str("name") ?: it) }
                },
                warning = o.str("warning")
            )
        }

        fun parseList(e: JsonElement?): List<Conversation> =
            e.asArrSafe().mapNotNull { (it as? JsonObject)?.let(::parse) }
    }
}

data class QueuedMessage(val id: String, val text: String, val attachments: List<Attachment>) {
    companion object {
        fun parse(o: JsonObject): QueuedMessage? {
            val id = o.str("id") ?: return null
            return QueuedMessage(id, o.str("text") ?: "", Attachment.parseList(o.opt("attachments")))
        }

        fun parseList(e: JsonElement?): List<QueuedMessage> =
            e.asArrSafe().mapNotNull { (it as? JsonObject)?.let(::parse) }
    }
}

/** Bloco persistido do histórico (`getConversation.blocks` / `getBlock`). */
data class Block(
    val turn: Long,
    val seq: Long,
    val kind: String,
    val content: JsonElement?,
    val createdAt: Long?,
    val truncated: Boolean,
    val originalBytes: Long?
) {
    companion object {
        fun parse(o: JsonObject): Block? {
            val turn = o.long("turn") ?: return null
            return Block(
                turn = turn,
                seq = o.long("seq") ?: 0L,
                kind = o.str("kind") ?: "text",
                content = o.opt("content"),
                createdAt = o.epochMillis("createdAt"),
                truncated = o.bool("truncated") ?: false,
                originalBytes = o.long("originalBytes")
            )
        }

        fun parseList(e: JsonElement?): List<Block> =
            e.asArrSafe().mapNotNull { (it as? JsonObject)?.let(::parse) }
                .sortedWith(compareBy({ it.turn }, { it.seq }))
    }
}

data class PageInfo(val hasMore: Boolean, val oldestTurn: Long?)

/** Resposta de `getConversation`. [page] nulo indica host antigo (histórico completo). */
data class ConversationSnapshot(
    val conversation: Conversation?,
    val blocks: List<Block>,
    val busy: Boolean,
    val queue: List<QueuedMessage>,
    val pending: List<PermissionRequest>,
    val page: PageInfo?
) {
    companion object {
        fun parse(o: JsonObject, conversationId: String): ConversationSnapshot = ConversationSnapshot(
            conversation = o.obj("conversation")?.let(Conversation::parse),
            blocks = Block.parseList(o.opt("blocks")),
            busy = o.bool("busy") ?: false,
            queue = QueuedMessage.parseList(o.opt("queue")),
            pending = o.arr("pending")?.objects()
                ?.mapNotNull { PermissionRequest.parse(it, conversationId) } ?: emptyList(),
            page = o.obj("page")?.let { PageInfo(it.bool("hasMore") ?: false, it.long("oldestTurn")) }
        )
    }
}

data class QuestionOption(val label: String, val description: String?)

data class Question(
    val question: String,
    val header: String?,
    val multiSelect: Boolean,
    val options: List<QuestionOption>
) {
    companion object {
        fun parse(o: JsonObject): Question? {
            val q = o.str("question") ?: return null
            val opts = o.arr("options")?.mapNotNull { el ->
                when {
                    el.asObj() != null -> el.asObj()!!.let { oo ->
                        (oo.str("label") ?: oo.str("text") ?: oo.str("title"))
                            ?.let { QuestionOption(it, oo.str("description") ?: oo.str("detail")) }
                    }
                    else -> el.asStr()?.let { QuestionOption(it, null) }
                }
            } ?: emptyList()
            return Question(
                question = q,
                header = o.str("header"),
                multiSelect = o.bool("multiSelect") ?: o.bool("is_multi_select") ?: o.bool("multi_select") ?: false,
                options = opts
            )
        }

        /** Lê `{questions:[…]}` ou a forma simples `{question, options, is_multi_select}`. */
        fun parseAll(input: JsonObject?): List<Question> {
            if (input == null) return emptyList()
            input.arr("questions")?.let { arr -> return arr.objects().mapNotNull(::parse) }
            return listOfNotNull(parse(input))
        }
    }
}

/** Pedido de permissão pendente (evento `permissionRequest`, `listPending` ou `getConversation`). */
data class PermissionRequest(
    val conversationId: String,
    val requestId: String,
    val tool: String,
    val toolUseId: String?,
    val input: JsonElement?,
    val inputPreview: String?,
    val truncated: Boolean,
    val reason: String?,
    val since: Long?,
    val suggestions: JsonElement?
) {
    /** Perguntas estruturadas quando a ferramenta é `AskUserQuestion` (caso (a) da spec). */
    val questions: List<Question>
        get() = if (isAskUserQuestion) Question.parseAll(input.asObj()) else emptyList()

    val isAskUserQuestion: Boolean get() = tool == ASK_USER_QUESTION

    companion object {
        const val ASK_USER_QUESTION = "AskUserQuestion"

        fun parse(o: JsonObject, fallbackConversationId: String?): PermissionRequest? {
            val requestId = o.str("requestId") ?: return null
            val convId = o.str("conversationId") ?: fallbackConversationId ?: return null
            return PermissionRequest(
                conversationId = convId,
                requestId = requestId,
                tool = o.str("tool") ?: "?",
                toolUseId = o.str("toolUseId"),
                input = o.opt("input"),
                inputPreview = o.str("inputPreview"),
                truncated = o.bool("truncated") ?: false,
                reason = o.str("reason"),
                since = o.epochMillis("since"),
                suggestions = o.opt("suggestions")
            )
        }
    }
}

/** Uma conversa com pendências, conforme `listPending`. */
data class PendingConversation(
    val conversationId: String,
    val title: String,
    val projectPath: String,
    val provider: Provider,
    val busy: Boolean,
    val turn: Long?,
    val lastEventAt: Long?,
    val permissions: List<PermissionRequest>
) {
    companion object {
        fun parse(o: JsonObject): PendingConversation? {
            val id = o.str("conversationId") ?: return null
            return PendingConversation(
                conversationId = id,
                title = o.str("title") ?: "",
                projectPath = o.str("projectPath") ?: "",
                provider = Provider.fromId(o.str("provider")),
                busy = o.bool("busy") ?: false,
                turn = o.long("turn"),
                lastEventAt = o.epochMillis("lastEventAt"),
                permissions = o.arr("pendingPermissions")?.objects()
                    ?.mapNotNull { PermissionRequest.parse(it, id) } ?: emptyList()
            )
        }

        fun parseList(e: JsonElement?): List<PendingConversation> =
            e.asArrSafe().mapNotNull { (it as? JsonObject)?.let(::parse) }
    }
}

data class UsageWindow(
    val kind: String,
    val label: String,
    val usedPct: Double?,
    val resetsAt: Long?,
    val group: String?
) {
    companion object {
        fun parse(o: JsonObject): UsageWindow = UsageWindow(
            kind = o.str("kind") ?: "other",
            label = o.str("label") ?: o.str("kind") ?: "",
            usedPct = o.double("usedPct"),
            resetsAt = o.epochMillis("resetsAt"),
            group = o.str("group")
        )
    }
}

data class RateLimitInfo(val windows: List<UsageWindow>, val status: String, val plan: String?) {
    companion object {
        fun parse(o: JsonObject): RateLimitInfo = RateLimitInfo(
            windows = o.arr("windows")?.objects()?.map(UsageWindow::parse) ?: emptyList(),
            status = o.str("status") ?: "unknown",
            plan = o.str("plan")
        )
    }
}

data class AccountStatus(
    val provider: Provider,
    val providerId: String,
    val slot: String,
    val label: String?,
    val email: String?,
    val plan: String?,
    val authState: String?,
    val usage: JsonElement?,
    val usageAt: Long?,
    val installed: Boolean
) {
    /** Janelas de uso, quando `usage` segue o formato de `rateLimit` (lista ou `{windows}`). */
    val usageWindows: List<UsageWindow>
        get() = when (val u = usage) {
            is com.google.gson.JsonArray -> u.objects().map(UsageWindow::parse)
            is JsonObject -> u.arr("windows")?.objects()?.map(UsageWindow::parse) ?: emptyList()
            else -> emptyList()
        }

    companion object {
        fun parse(o: JsonObject): AccountStatus? {
            val providerId = o.str("provider") ?: return null
            return AccountStatus(
                provider = Provider.fromId(providerId),
                providerId = providerId,
                slot = o.str("slot") ?: "a",
                label = o.str("label"),
                email = o.str("email"),
                plan = o.str("plan"),
                authState = o.str("authState"),
                usage = o.opt("usage"),
                usageAt = o.epochMillis("usageAt"),
                installed = o.bool("installed") ?: true
            )
        }

        fun parseList(e: JsonElement?): List<AccountStatus> =
            e.asArrSafe().mapNotNull { (it as? JsonObject)?.let(::parse) }
    }
}

/** Aparelho pareado (`listDevices` / `devices-changed`); tempos em epoch **segundos** no fio. */
data class PairedDevice(
    val id: String,
    val name: String,
    val pk: String?,
    val pairedAtMs: Long?,
    val lastSeenMs: Long?,
    val revoked: Boolean
) {
    companion object {
        private fun secondsToMs(v: Long?): Long? = v?.let { if (it < 100_000_000_000L) it * 1000 else it }

        fun parse(o: JsonObject): PairedDevice? {
            val id = o.str("id") ?: return null
            return PairedDevice(
                id = id,
                name = o.str("name") ?: id,
                pk = o.str("pk"),
                pairedAtMs = secondsToMs(o.long("pairedAt")),
                lastSeenMs = secondsToMs(o.long("lastSeen")),
                revoked = o.bool("revoked") ?: false
            )
        }

        fun parseList(e: JsonElement?): List<PairedDevice> =
            e.asArrSafe().mapNotNull { (it as? JsonObject)?.let(::parse) }
    }
}

enum class EntryKind { FILE, DIR, OTHER }

data class DirEntry(val name: String, val kind: EntryKind, val size: Long?, val mtime: Long?)

data class DirListing(val path: String?, val entries: List<DirEntry>, val truncated: Boolean) {
    companion object {
        fun parse(o: JsonObject): DirListing = DirListing(
            path = o.str("path"),
            entries = o.arr("entries")?.objects()?.mapNotNull { e ->
                val name = e.str("name") ?: return@mapNotNull null
                DirEntry(
                    name = name,
                    kind = when (e.str("kind")) { "dir" -> EntryKind.DIR; "file" -> EntryKind.FILE; else -> EntryKind.OTHER },
                    size = e.long("size"),
                    mtime = e.epochMillis("mtime")
                )
            } ?: emptyList(),
            truncated = o.bool("truncated") ?: false
        )
    }
}

sealed interface FileContent {
    val path: String
    val size: Long?
    val truncated: Boolean

    data class Text(
        override val path: String,
        val text: String,
        override val size: Long?,
        override val truncated: Boolean,
        val language: String?
    ) : FileContent

    data class Binary(
        override val path: String,
        override val size: Long?,
        override val truncated: Boolean,
        val mime: String?,
        val warning: String?
    ) : FileContent

    companion object {
        /** Aceita a forma v2 (`text` / `binary:true`) e a antiga (`content`, `isBinary`). */
        fun parse(o: JsonObject, requestedPath: String): FileContent {
            val path = o.str("path") ?: requestedPath
            val size = o.long("size")
            val truncated = o.bool("truncated") ?: false
            val binary = o.bool("binary") ?: o.bool("isBinary") ?: false
            return if (binary) {
                Binary(path, size, truncated, o.str("mime"), o.str("warning"))
            } else {
                Text(path, o.str("text") ?: o.str("content") ?: "", size, truncated, o.str("language"))
            }
        }
    }
}

data class SlashCommand(
    val name: String,
    val description: String?,
    val provider: String?,
    val source: String?,
    val desktopOnly: Boolean
) {
    companion object {
        /** Comandos que só funcionam no desktop mesmo quando o host não marca. */
        private val LOCAL_DESKTOP_ONLY = setOf("btw", "grill-me")

        fun parseList(e: JsonElement?): List<SlashCommand> =
            e.asArrSafe().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val name = o.str("name") ?: return@mapNotNull null
                val bare = name.removePrefix("/")
                SlashCommand(
                    name = name,
                    description = o.str("description"),
                    provider = o.str("provider"),
                    source = o.str("source"),
                    desktopOnly = (o.bool("desktopOnly") ?: false) || bare in LOCAL_DESKTOP_ONLY
                )
            }
    }
}

data class TurnUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long
) {
    companion object {
        fun parse(o: JsonObject?): TurnUsage? = o?.let {
            TurnUsage(it.long("inputTokens") ?: 0, it.long("outputTokens") ?: 0,
                it.long("cacheReadTokens") ?: 0, it.long("cacheWriteTokens") ?: 0)
        }
    }
}

data class McpServer(val name: String, val status: String, val detail: String?)

data class UploadedFile(val path: String, val mime: String)

private fun JsonElement?.asArrSafe(): List<JsonElement> = (this as? com.google.gson.JsonArray)?.toList() ?: emptyList()

@Suppress("unused")
private fun JsonObject.intOrZero(key: String): Int = int(key) ?: 0
