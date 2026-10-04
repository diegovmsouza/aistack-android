package br.com.amberwrite.aistack.service

import br.com.amberwrite.aistack.core.rpc.EngineEvent
import br.com.amberwrite.aistack.core.rpc.HostEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTurnTrackerTest {
    private var now = 10_000L
    private val tracker = LiveTurnTracker { now }

    private fun on(e: EngineEvent, id: String = "c1") = tracker.onEvent(Fx.conv(id, e))

    @Test
    fun `inicio de turno cria o turno com o relogio`() {
        assertEquals(LiveChange.Updated("c1"), on(Fx.started()))
        val t = tracker.turn("c1")!!
        assertEquals(10_000L, t.startedAt)
        assertNull(t.tool)
        assertEquals(false, t.detailed)
    }

    @Test
    fun `ferramenta principal entra e sai`() {
        on(Fx.started())
        on(Fx.toolStart("t1", "Bash"))
        assertEquals("Bash", tracker.turn("c1")!!.tool)
        assertTrue(tracker.turn("c1")!!.detailed)
        on(Fx.toolResult("t1"))
        assertNull(tracker.turn("c1")!!.tool)
    }

    @Test
    fun `sub-agentes sao contados e saem no resultado`() {
        on(Fx.started())
        on(Fx.toolStart("s1", "Task"))
        on(Fx.toolStart("s2", "Task"))
        on(Fx.toolStart("n1", "Grep", nested = true))
        var t = tracker.turn("c1")!!
        assertEquals(setOf("s1", "s2"), t.activeSubagents)
        assertEquals(2, t.subagentsTotal)
        assertEquals("Grep", t.tool)
        on(Fx.toolResult("s1"))
        t = tracker.turn("c1")!!
        assertEquals(setOf("s2"), t.activeSubagents)
        assertEquals(2, t.subagentsTotal)
    }

    @Test
    fun `atividade de sub-agente desconhecido conta e resultado remove`() {
        on(Fx.started())
        on(EngineEvent.SubagentActivity("p1", "tool", "x", "Read", null, false, null))
        assertEquals(setOf("p1"), tracker.turn("c1")!!.activeSubagents)
        assertEquals("Read", tracker.turn("c1")!!.tool)
        on(EngineEvent.SubagentActivity("p1", "result", null, null, null, false, null))
        assertTrue(tracker.turn("c1")!!.activeSubagents.isEmpty())
    }

    @Test
    fun `evento de turno ja em curso cria o turno e status sozinho nao`() {
        assertNull(on(EngineEvent.Status("pensando")))
        assertNull(tracker.turn("c1"))
        assertNotNull(on(Fx.toolStart("t1", "Edit")))
        assertEquals("Edit", tracker.turn("c1")!!.tool)
    }

    @Test
    fun `evento sem mudanca nao notifica e desconexao e ignorada`() {
        on(Fx.started())
        assertNull(on(EngineEvent.Unknown("x")))
        assertNull(tracker.onEvent(HostEvent.Disconnected))
    }

    @Test
    fun `espera de limite e limpa por texto`() {
        on(Fx.started())
        on(EngineEvent.RateLimitWait(30))
        assertEquals(30L, tracker.turn("c1")!!.rateLimitSeconds)
        on(EngineEvent.TextDelta(0, "ok"))
        assertNull(tracker.turn("c1")!!.rateLimitSeconds)
    }

    @Test
    fun `fim de turno remove e devolve a pergunta de ferramenta aberta`() {
        on(Fx.started())
        on(Fx.toolStart("q", "ask_question"))
        on(EngineEvent.ToolInput("q", Fx.json(Fx.QUESTION_INPUT)))
        val ended = on(Fx.complete()) as LiveChange.Ended
        assertEquals("c1", ended.conversationId)
        assertEquals("Qual cor?", ended.toolQuestion!!.questions.single().question)
        assertNull(tracker.turn("c1"))
    }

    @Test
    fun `texto depois da pergunta ou erro de turno descartam a pergunta`() {
        on(Fx.started())
        on(Fx.toolStart("q", "ask_question"))
        on(EngineEvent.ToolInput("q", Fx.json(Fx.QUESTION_INPUT)))
        on(EngineEvent.TextDelta(1, "Escolhi por você"))
        assertNull((on(Fx.complete()) as LiveChange.Ended).toolQuestion)

        on(Fx.started())
        on(Fx.toolStart("q2", "AskFollowupQuestion"))
        on(EngineEvent.ToolInput("q2", Fx.json(Fx.QUESTION_INPUT)))
        assertNull((on(EngineEvent.TurnError("api", "x")) as LiveChange.Ended).toolQuestion)
    }

    @Test
    fun `syncBusy cria, remove e respeita fim recente`() {
        assertEquals(setOf("a", "b"), tracker.syncBusy(setOf("a", "b")))
        assertEquals(setOf("a", "b"), tracker.turnsSnapshot.map { it.conversationId }.toSet())
        assertEquals(setOf("b"), tracker.syncBusy(setOf("a")))

        on(Fx.started(), "x")
        on(Fx.complete(), "x")
        now += 1_000
        // Lista atrasada ainda diz "ocupada": o fim de turno recente vence.
        assertTrue("x" !in tracker.syncBusy(setOf("a", "x")))
        now += 10_000
        assertTrue("x" in tracker.syncBusy(setOf("a", "x")))
    }

    @Test
    fun `throttle limita a uma atualizacao por intervalo`() {
        val th = LiveThrottle(1_000)
        assertEquals(0L, th.delayFor("c", 0))
        th.mark("c", 1_000)
        assertEquals(500L, th.delayFor("c", 1_500))
        assertEquals(0L, th.delayFor("c", 2_100))
        assertEquals(0L, th.delayFor("outra", 1_500))
        th.reset("c")
        assertEquals(0L, th.delayFor("c", 1_200))
    }
}
