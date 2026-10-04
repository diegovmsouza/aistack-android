package br.com.amberwrite.aistack.data.repo

import br.com.amberwrite.aistack.core.rpc.EventParser
import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.ChatState
import br.com.amberwrite.aistack.data.model.ToolStatus
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {

    private fun ev(engineJson: String, conv: String = "c1", turn: Long = 4): HostEvent =
        EventParser.parse(
            "conv-event",
            JsonParser.parseString("""{"conversationId":"$conv","turn":$turn,"event":$engineJson}""")
        )

    private fun ChatState.apply(vararg events: HostEvent): ChatState =
        events.fold(this) { s, e -> ChatReducer.apply(s, e) }

    private val turn = listOf(
        ev("""{"type":"turnStarted","slot":"a","user":{"text":"faça X","attachments":[]}}"""),
        ev("""{"type":"textDelta","block":0,"text":"Olá, "}"""),
        ev("""{"type":"textDelta","block":0,"text":"mundo"}"""),
        ev("""{"type":"toolStart","block":1,"id":"toolu_01","name":"Task","nested":false}"""),
        ev("""{"type":"toolInput","id":"toolu_01","input":{"prompt":"procure"}}"""),
        ev("""{"type":"subagentActivity","parentToolUseId":"toolu_01","kind":"tool","toolId":"toolu_02","name":"Grep","text":"{}","isError":null,"at":1759496401234}"""),
        ev("""{"type":"toolResult","id":"toolu_01","output":"achei","isError":false}"""),
        ev("""{"type":"permissionRequest","requestId":"req_1","tool":"Bash","input":{"command":"ls"}}""")
    )

    @Test
    fun sequenciaDeUmTurno() {
        val s = ChatState("c1").apply(*turn.toTypedArray())
        assertTrue(s.busy)
        assertTrue(s.isStreaming)
        assertEquals(4L, s.currentTurn)
        assertEquals(3, s.items.size)

        val user = s.items[0] as ChatItem.User
        assertEquals("faça X", user.text)
        assertEquals("Olá, mundo", (s.items[1] as ChatItem.Text).text)

        val tool = s.items[2] as ChatItem.Tool
        assertEquals("toolu_01", tool.id)
        assertEquals(ToolStatus.DONE, tool.status)
        assertEquals("procure", tool.input!!.asJsonObject["prompt"].asString)
        assertEquals(1, tool.subagent.size)
        assertEquals("Grep", tool.subagent[0].name)

        assertEquals(listOf("req_1"), s.pending.map { it.requestId })

        val done = s.apply(
            ev("""{"type":"turnComplete","usage":{"inputTokens":10,"outputTokens":5,"cacheReadTokens":0,"cacheWriteTokens":0},"costUsd":0.01,"durationMs":100}""")
        )
        assertFalse(done.busy)
        assertFalse(done.isStreaming)
        assertTrue(done.pending.isEmpty())
        assertNull(done.status)
        assertEquals(10L, done.lastUsage!!.inputTokens)
        assertEquals(0.01, done.lastCostUsd!!, 1e-9)
    }

    @Test
    fun fimDeTurnoInterrompeFerramentasEmCurso() {
        val s = ChatState("c1").apply(
            ev("""{"type":"turnStarted","slot":"a","user":{"text":"x","attachments":[]}}"""),
            ev("""{"type":"toolStart","block":0,"id":"t1","name":"Bash","nested":false}"""),
            ev("""{"type":"turnError","kind":"other","message":"falhou"}""")
        )
        assertFalse(s.busy)
        assertFalse(s.isStreaming)
        assertEquals(ToolStatus.INTERRUPTED, (s.items[1] as ChatItem.Tool).status)
        assertEquals("falhou", (s.items.last() as ChatItem.Error).message)
    }

    @Test
    fun interrupcaoNaoGeraItemDeErro() {
        val s = ChatState("c1").apply(
            ev("""{"type":"turnStarted","slot":"a","user":{"text":"x","attachments":[]}}"""),
            ev("""{"type":"turnError","kind":"interrupted","message":"Interrompido"}""")
        )
        assertFalse(s.busy)
        assertTrue(s.items.none { it is ChatItem.Error })
    }

    @Test
    fun eventosDeOutraConversaSaoIgnorados() {
        val start = ChatState("c1")
        val s = start.apply(ev("""{"type":"textDelta","block":0,"text":"oi"}""", conv = "c2"))
        assertSame(start, s)
    }

    @Test
    fun turnStartedRepetidoNaoDuplicaUsuario() {
        val started = ev("""{"type":"turnStarted","slot":"a","user":{"text":"x","attachments":[]}}""")
        val s = ChatState("c1").apply(started, started)
        assertEquals(1, s.items.count { it is ChatItem.User })
    }

    @Test
    fun permissaoCanceladaSaiDasPendencias() {
        val s = ChatState("c1").apply(
            ev("""{"type":"permissionRequest","requestId":"req_1","tool":"Bash","input":{}}"""),
            ev("""{"type":"permissionRequest","requestId":"req_1","tool":"Bash","input":{}}"""),
            ev("""{"type":"permissionCancelled","requestId":"req_1"}""")
        )
        assertTrue(s.pending.isEmpty())
    }

    @Test
    fun eventoTruncadoPedeRecarga() {
        val s = ChatState("c1").apply(
            ev("""{"type":"toolResult","id":"x","output":"…","isError":false,"truncated":true}""")
        )
        assertTrue(s.needsReload)
    }
}
