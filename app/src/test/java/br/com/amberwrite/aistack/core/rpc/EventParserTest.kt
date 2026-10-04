package br.com.amberwrite.aistack.core.rpc

import br.com.amberwrite.aistack.data.model.TurnUsage
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exemplos tirados das specs 01 e 05 (contrato v2). */
class EventParserTest {

    /** Interpreta um quadro `{"t":"event",…}` completo. */
    private fun frame(json: String): HostEvent {
        val o = JsonParser.parseString(json).asJsonObject
        return EventParser.parse(o["event"].asString, o["payload"])
    }

    private fun conv(engineJson: String, turn: Int = 4): HostEvent.Conv {
        val ev = frame("""{"t":"event","event":"conv-event","payload":{"conversationId":"c1","turn":$turn,"event":$engineJson}}""")
        assertTrue(ev is HostEvent.Conv)
        return ev as HostEvent.Conv
    }

    @Test
    fun textDelta() {
        val ev = conv("""{"type":"textDelta","block":0,"text":"Olá"}""", turn = 3)
        assertEquals("c1", ev.conversationId)
        assertEquals(3L, ev.turn)
        assertEquals(EngineEvent.TextDelta(0, "Olá"), ev.event)
    }

    @Test
    fun turnStarted() {
        val e = conv("""{"type":"turnStarted","slot":"a","user":{"text":"faça X","attachments":[]}}""").event
        assertTrue(e is EngineEvent.TurnStarted)
        e as EngineEvent.TurnStarted
        assertEquals("a", e.slot)
        assertEquals("faça X", e.text)
        assertTrue(e.attachments.isEmpty())
    }

    @Test
    fun toolStart() {
        val e = conv("""{"type":"toolStart","block":1,"id":"toolu_01","name":"Bash","nested":false}""").event
        assertEquals(EngineEvent.ToolStart(1, "toolu_01", "Bash", false), e)
    }

    @Test
    fun toolResultTruncado() {
        val ev = conv("""{"type":"toolResult","id":"toolu_01","output":"…primeiros 16 KiB…","isError":false,"truncated":true,"originalBytes":203117}""")
        assertTrue(ev.truncated)
        val e = ev.event as EngineEvent.ToolResult
        assertEquals("toolu_01", e.id)
        assertEquals(false, e.isError)
    }

    @Test
    fun subagentActivityComIsErrorNulo() {
        val e = conv(
            """{"type":"subagentActivity","parentToolUseId":"toolu_01ABC","kind":"tool","toolId":"toolu_02DEF","name":"Grep","text":"{\"pattern\":\"MAX_DATA\"}","isError":null,"at":1759496401234}"""
        ).event as EngineEvent.SubagentActivity
        assertEquals("toolu_01ABC", e.parentToolUseId)
        assertEquals("tool", e.kind)
        assertEquals("toolu_02DEF", e.toolId)
        assertEquals("Grep", e.name)
        assertEquals(false, e.isError)
        assertEquals(1759496401234L, e.at)
    }

    @Test
    fun permissionRequestAskUserQuestion() {
        val e = conv(
            """{"type":"permissionRequest","requestId":"req_12","tool":"AskUserQuestion","toolUseId":"toolu_7",
               "input":{"questions":[{"question":"Qual banco usar?","header":"Banco","multiSelect":false,
               "options":[{"label":"SQLite","description":"local"},{"label":"Postgres","description":"servidor"}]}]},
               "suggestions":[],"reason":null}"""
        ).event as EngineEvent.PermissionRequested
        val r = e.request
        assertEquals("c1", r.conversationId)
        assertEquals("req_12", r.requestId)
        assertTrue(r.isAskUserQuestion)
        assertEquals(1, r.questions.size)
        assertEquals("Qual banco usar?", r.questions[0].question)
        assertEquals(listOf("SQLite", "Postgres"), r.questions[0].options.map { it.label })
        assertNull(r.reason)
    }

    @Test
    fun turnComplete() {
        val e = conv(
            """{"type":"turnComplete","usage":{"inputTokens":1200,"outputTokens":340,"cacheReadTokens":0,"cacheWriteTokens":0},"costUsd":0.0123,"durationMs":5400}"""
        ).event as EngineEvent.TurnComplete
        assertEquals(TurnUsage(1200, 340, 0, 0), e.usage)
        assertEquals(0.0123, e.costUsd!!, 1e-9)
        assertEquals(5400L, e.durationMs)
    }

    @Test
    fun turnCompleteComCustoNulo() {
        val e = conv("""{"type":"turnComplete","usage":null,"costUsd":null,"durationMs":null}""").event as EngineEvent.TurnComplete
        assertNull(e.costUsd)
        assertNull(e.durationMs)
    }

    @Test
    fun eventTooLarge() {
        val ev = frame("""{"t":"event","event":"eventTooLarge","payload":{"event":"conv-event","conversationId":"c1","turn":4,"kind":"toolResult","originalBytes":812345}}""")
        assertEquals(HostEvent.EventTooLarge("conv-event", "c1", 4, "toolResult", 812345), ev)
    }

    @Test
    fun tiposDesconhecidosNaoLancam() {
        assertTrue(frame("""{"t":"event","event":"algo-novo","payload":{}}""") is HostEvent.Unknown)
        assertTrue(conv("""{"type":"futuro"}""").event is EngineEvent.Unknown)
        // Payload malformado vira Unknown em vez de exceção.
        assertTrue(frame("""{"t":"event","event":"conv-event","payload":[1,2]}""") is HostEvent.Unknown)
    }
}
