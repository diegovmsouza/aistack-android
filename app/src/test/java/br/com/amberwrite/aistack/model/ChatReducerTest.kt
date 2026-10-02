package br.com.amberwrite.aistack.model

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {
    private var n = 0
    private fun id() = "id${n++}"
    private fun ev(json: String) = JsonParser.parseString(json).asJsonObject
    private fun run(messages: MutableList<ChatMessage>, json: String) = ChatReducer.apply(messages, ev(json), ::id)

    @Test
    fun toolResultEncerraOCartaoQueGirava() {
        val m = mutableListOf<ChatMessage>()
        run(m, """{"type":"ToolStart","block":0,"id":"t1","name":"Bash","nested":false}""")
        run(m, """{"type":"ToolInput","id":"t1","input":{"command":"ls"}}""")
        var tool = m.last().blocks.last() as ChatBlock.ToolCall
        assertTrue(tool.isRunning)
        assertEquals("""{"command":"ls"}""", tool.input)

        run(m, """{"type":"ToolResult","id":"t1","output":"a.txt","isError":true}""")
        tool = m.last().blocks.last() as ChatBlock.ToolCall
        assertFalse(tool.isRunning)
        assertTrue(tool.isError)
        assertEquals("a.txt", tool.output)
    }

    @Test
    fun pedidoDePermissaoUsaORequestIdDoHost() {
        val m = mutableListOf<ChatMessage>()
        val fx = run(m, """{"type":"PermissionRequest","requestId":"r9","tool":"Bash","input":{"command":"rm x"}}""")
        assertEquals(ChatEffect.PermissionAsked("r9", "Bash", """{"command":"rm x"}"""), fx)
        assertEquals("r9", (m.last().blocks.last() as ChatBlock.Permission).requestId)
    }

    @Test
    fun permissaoCancelada_marcaOCartaoEAvisaATela() {
        val m = mutableListOf<ChatMessage>()
        run(m, """{"type":"PermissionRequest","requestId":"r9","tool":"Bash","input":{}}""")
        val fx = run(m, """{"type":"PermissionCancelled","requestId":"r9"}""")
        assertEquals(ChatEffect.PermissionCancelled("r9"), fx)
        val p = m.last().blocks.last() as ChatBlock.Permission
        assertTrue(p.isDecided)
        assertEquals("cancelled", p.decision)
    }

    @Test
    fun turnErrorEExitedEncerramOTurnoEAvisam() {
        val m = mutableListOf<ChatMessage>()
        run(m, """{"type":"TextDelta","block":0,"text":"oi"}""")
        assertEquals(ChatEffect.TurnEnded, run(m, """{"type":"TurnError","kind":"rateLimited","message":"limite"}"""))
        assertFalse((m.first().blocks.first() as ChatBlock.Text).isStreaming)
        assertEquals("system", m.last().role)
        assertTrue((m.last().blocks.first() as ChatBlock.Text).text.contains("limite"))

        assertEquals(ChatEffect.TurnEnded, run(m, """{"type":"Exited","code":1,"detail":"caiu"}"""))
        assertTrue((m.last().blocks.first() as ChatBlock.Text).text.contains("caiu"))
    }

    @Test
    fun pensamentoSemMensagemDoAssistenteAbreUma() {
        val m = mutableListOf<ChatMessage>()
        run(m, """{"type":"ThinkingDelta","block":0,"text":"hm"}""")
        assertTrue(m.single().blocks.single() is ChatBlock.Thinking)
    }

    @Test
    fun eventosSemEfeitoNoChatSaoIgnorados() {
        val m = mutableListOf<ChatMessage>()
        assertEquals(ChatEffect.None, run(m, """{"type":"RateLimitWait","secondsRemaining":3}"""))
        assertEquals(ChatEffect.None, run(m, """{"type":"turnStarted"}"""))
        assertTrue(m.isEmpty())
    }

    @Test
    fun markPermissionRegistraADecisao() {
        val m = mutableListOf<ChatMessage>()
        run(m, """{"type":"PermissionRequest","requestId":"r1","tool":"Bash","input":{}}""")
        ChatReducer.markPermission(m, "r1", "allow")
        assertEquals("allow", (m.last().blocks.last() as ChatBlock.Permission).decision)
    }
}
