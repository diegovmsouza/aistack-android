package br.com.amberwrite.aistack.service

import br.com.amberwrite.aistack.core.rpc.EngineEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationMapperTest {

    @Test
    fun `permissao comum tem Permitir, Negar e Responder com o preview como texto`() {
        val n = NotificationMapper.permission(Fx.permission(reason = "  fora do projeto "), "Refatorar")
        assertEquals(listOf(NoticeAction.Allow, NoticeAction.Deny, NoticeAction.Reply), n.actions)
        assertEquals("rm -rf build", n.headline)
        assertEquals("fora do projeto", n.reason)
        assertFalse(n.isQuestion)
        assertEquals(0, n.questionCount)
        assertTrue(n.choices.isEmpty())
        assertFalse(n.silent)
        assertEquals(1_000L, n.since)
    }

    @Test
    fun `titulo vazio vira o padrao e preview em branco nao vira texto`() {
        val n = NotificationMapper.permission(Fx.permission(preview = "   "), "")
        assertEquals(NotificationMapper.DEFAULT_TITLE, n.conversationTitle)
        assertNull(n.headline)
    }

    @Test
    fun `pergunta tem Responder e Dispensar e opcoes como respostas rapidas`() {
        val n = NotificationMapper.permission(Fx.askOne(), "Conv")
        assertTrue(n.isQuestion)
        assertEquals(listOf(NoticeAction.Reply, NoticeAction.Dismiss), n.actions)
        assertEquals("Qual banco?", n.headline)
        assertEquals(listOf("Postgres", "Mongo", "SQLite"), n.choices)
        assertEquals(1, n.questionCount)
    }

    @Test
    fun `multipla escolha e varias perguntas nao tem respostas rapidas`() {
        assertTrue(NotificationMapper.permission(Fx.askOne(multi = true), "C").choices.isEmpty())
        val two = NotificationMapper.permission(Fx.askTwo(), "C")
        assertTrue(two.choices.isEmpty())
        assertEquals(2, two.questionCount)
        assertEquals("Linguagem?", two.headline)
    }

    @Test
    fun `enviando nao tem acoes e repostagem com erro e silenciosa`() {
        val sending = NotificationMapper.permission(Fx.permission(), "C", sending = true)
        assertTrue(sending.actions.isEmpty())
        assertTrue(sending.silent)
        val failed = NotificationMapper.permission(Fx.permission(), "C", error = "Sem conexão")
        assertEquals("Sem conexão", failed.error)
        assertTrue(failed.silent)
        assertEquals(3, failed.actions.size)
    }

    @Test
    fun `fim de turno vira concluido ou erro`() {
        val ok = NotificationMapper.done("c1", "T", Fx.complete(5_000))
        assertNotNull(ok)
        assertFalse(ok!!.isError)
        assertEquals(5_000L, ok.durationMs)

        val err = NotificationMapper.done("c1", "T", EngineEvent.TurnError("api", " estourou "))
        assertTrue(err!!.isError)
        assertEquals("estourou", err.message)

        assertNull(NotificationMapper.done("c1", "T", EngineEvent.TurnError("interrupted", "parado")))
        assertNull(NotificationMapper.done("c1", "T", EngineEvent.Exited(0, "")))
        assertNull(NotificationMapper.done("c1", "T", EngineEvent.Exited(null, "")))
        val crash = NotificationMapper.done("c1", "T", EngineEvent.Exited(137, ""))
        assertTrue(crash!!.isError)
        assertNull(crash.message)
        assertNull(NotificationMapper.done("c1", "T", EngineEvent.Status("x")))
    }

    @Test
    fun `resumo agrupa por conversa`() {
        val a1 = NotificationMapper.permission(Fx.permission(conv = "a", id = "1"), "A")
        val a2 = NotificationMapper.permission(Fx.permission(conv = "a", id = "2"), "A")
        val b1 = NotificationMapper.permission(Fx.permission(conv = "b", id = "3"), "B")
        val tq = NotificationMapper.toolQuestion(
            "b", "B",
            br.com.amberwrite.aistack.data.model.ToolQuestion.from("ask_question", Fx.json(Fx.QUESTION_INPUT))!!,
        )
        val s = NotificationMapper.summary(listOf(a1, a2, b1), listOf(tq))!!
        assertEquals(4, s.total)
        assertEquals(listOf(PendingSummaryLine("a", "A", 2), PendingSummaryLine("b", "B", 2)), s.lines)
        assertNull(NotificationMapper.summary(emptyList(), emptyList()))
    }

    @Test
    fun `pergunta de ferramenta tem opcoes e titulo padrao`() {
        val q = br.com.amberwrite.aistack.data.model.ToolQuestion.from("ask_question", Fx.json(Fx.QUESTION_INPUT))!!
        val n = NotificationMapper.toolQuestion("c", " ", q)
        assertEquals(NotificationMapper.DEFAULT_TITLE, n.conversationTitle)
        assertEquals(listOf("Azul", "Vermelho"), n.choices)
        assertEquals("Qual cor?", n.headline)
        assertEquals(1, n.questionCount)
    }

    @Test
    fun `nome curto de ferramenta`() {
        assertEquals("read_file", NotificationMapper.shortTool("mcp__fs__read_file"))
        assertEquals("Bash", NotificationMapper.shortTool(" Bash "))
        assertNull(NotificationMapper.shortTool("  "))
        assertNull(NotificationMapper.shortTool(null))
        val long = NotificationMapper.shortTool("a".repeat(40), max = 10)!!
        assertEquals(10, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun `duracao legivel`() {
        assertEquals("0 s", NotificationMapper.formatDuration(-5))
        assertEquals("45 s", NotificationMapper.formatDuration(45_000))
        assertEquals("3 min 05 s", NotificationMapper.formatDuration(185_000))
        assertEquals("1 h 02 min", NotificationMapper.formatDuration(3_720_000))
    }

    @Test
    fun `live copia o estado do turno`() {
        val turn = LiveTurn("c1", 500, tool = "Grep", activeSubagents = setOf("s1", "s2"), rateLimitSeconds = 30, detailed = true)
        val n = NotificationMapper.live(turn, "", waiting = true)
        assertEquals(LiveNotice("c1", NotificationMapper.DEFAULT_TITLE, 500, "Grep", 2, true, 30, true), n)
    }
}
