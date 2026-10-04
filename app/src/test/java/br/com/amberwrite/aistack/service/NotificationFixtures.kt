package br.com.amberwrite.aistack.service

import br.com.amberwrite.aistack.core.rpc.EngineEvent
import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.data.model.PendingConversation
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Provider
import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** Construtores curtos para os testes de notificação e da caixa de pendências. */
internal object Fx {

    fun json(s: String): JsonElement = JsonParser.parseString(s)

    fun permission(
        conv: String = "c1",
        id: String = "r1",
        tool: String = "Bash",
        preview: String? = "rm -rf build",
        reason: String? = null,
        since: Long? = 1_000L,
        input: JsonElement? = null,
        suggestions: JsonElement? = null,
    ) = PermissionRequest(
        conversationId = conv,
        requestId = id,
        tool = tool,
        toolUseId = "tu-$id",
        input = input,
        inputPreview = preview,
        truncated = false,
        reason = reason,
        since = since,
        suggestions = suggestions,
    )

    /** `AskUserQuestion` com uma pergunta e as opções A/B/C. */
    fun askOne(conv: String = "c1", id: String = "q1", multi: Boolean = false, since: Long? = 1_000L) = permission(
        conv = conv,
        id = id,
        tool = PermissionRequest.ASK_USER_QUESTION,
        preview = null,
        since = since,
        input = json(
            """{"questions":[{"question":"Qual banco?","header":"DB","multiSelect":$multi,
              "options":[{"label":"Postgres","description":"relacional"},{"label":"Mongo"},{"label":"SQLite"}]}]}"""
        ),
    )

    /** `AskUserQuestion` com duas perguntas. */
    fun askTwo(conv: String = "c1", id: String = "q2") = permission(
        conv = conv,
        id = id,
        tool = PermissionRequest.ASK_USER_QUESTION,
        preview = null,
        input = json(
            """{"questions":[
              {"question":"Linguagem?","options":[{"label":"Kotlin"},{"label":"Java"}]},
              {"question":"Build?","options":[{"label":"Gradle"},{"label":"Maven"}]}]}"""
        ),
    )

    fun conversation(
        id: String = "c1",
        title: String = "Refatorar login",
        busy: Boolean = false,
        permissions: List<PermissionRequest> = emptyList(),
    ) = PendingConversation(
        conversationId = id,
        title = title,
        projectPath = "/home/u/proj",
        provider = Provider.CLAUDE,
        busy = busy,
        turn = 1,
        lastEventAt = null,
        permissions = permissions,
    )

    fun conv(id: String, event: EngineEvent) = HostEvent.Conv(id, 1, event, false)

    fun started() = EngineEvent.TurnStarted(null, "oi", emptyList())
    fun complete(durationMs: Long? = 12_000) = EngineEvent.TurnComplete(null, null, durationMs)
    fun toolStart(id: String, name: String, nested: Boolean = false) = EngineEvent.ToolStart(0, id, name, nested)
    fun toolResult(id: String) = EngineEvent.ToolResult(id, null, false)

    const val QUESTION_INPUT =
        """{"questions":[{"question":"Qual cor?","options":[{"label":"Azul","description":"frio"},{"label":"Vermelho"}]}]}"""
}
