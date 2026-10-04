package br.com.amberwrite.aistack.feature.chat.thread

import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.ChatState
import br.com.amberwrite.aistack.data.model.SubagentStep
import br.com.amberwrite.aistack.data.model.ToolStatus
import br.com.amberwrite.aistack.ui.designsystem.components.StepState
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadModelTest {

    private fun user(turn: Long, text: String) = ChatItem.User("B:$turn:0", turn, text, emptyList())
    private fun text(turn: Long, t: String, key: String = "B:$turn:1") = ChatItem.Text(key, turn, t)
    private fun tool(
        name: String,
        status: ToolStatus,
        input: String = "{}",
        sub: List<SubagentStep> = emptyList(),
        key: String = "B:1:2",
    ) = ChatItem.Tool(key, 1, "t1", name, JsonParser.parseString(input), null, status, seq = 2, subagent = sub)

    @Test
    fun `chave de bloco persistido`() {
        assertEquals(3L to 7L, ThreadModel.parseBlockKey("B:3:7"))
        assertNull(ThreadModel.parseBlockKey("L:3:x"))
        assertNull(ThreadModel.parseBlockKey("B:3"))
    }

    @Test
    fun `referencia usa o seq da ferramenta`() {
        assertEquals(1L to 2L, ThreadModel.blockRef(tool("Read", ToolStatus.DONE, key = "L:1:abc")))
        assertEquals(5L to 9L, ThreadModel.blockRef(text(5, "x", key = "B:5:9")))
        assertNull(ThreadModel.blockRef(text(5, "x", key = "L:5:live")))
    }

    @Test
    fun `linhas incluem anteriores cursor pendencias e status`() {
        val state = ChatState(
            conversationId = "c",
            items = listOf(user(1, "oi"), text(1, "resp")),
            busy = true,
            isStreaming = true,
            hasMore = true,
        )
        val rows = ThreadModel.buildRows(state, null)
        assertTrue(rows.first() is ThreadRow.Older)
        val last = rows.filterIsInstance<ThreadRow.Item>().last()
        assertTrue(last.caret)
        // Texto ao vivo já tem cursor: nenhuma linha de status extra.
        assertTrue(rows.none { it is ThreadRow.Status })
    }

    @Test
    fun `ferramenta rodando mostra status com o nome`() {
        val state = ChatState(conversationId = "c", items = listOf(user(1, "oi"), tool("Bash", ToolStatus.RUNNING)), busy = true)
        assertEquals(StatusKind.RunningTool("Bash"), ThreadModel.statusOf(state))
        assertNull(ThreadModel.statusOf(state.copy(busy = false)))
        assertEquals(StatusKind.Custom("Aguardando limite"), ThreadModel.statusOf(state.copy(status = "Aguardando limite")))
    }

    @Test
    fun `pensamento ao vivo nao duplica o status`() {
        val state = ChatState(
            conversationId = "c",
            items = listOf(user(1, "oi"), ChatItem.Thinking("L:1:t", 1, "hmm")),
            busy = true,
            isStreaming = true,
        )
        assertNull(ThreadModel.statusOf(state))
        assertEquals(StatusKind.Working, ThreadModel.statusOf(state.copy(isStreaming = false)))
    }

    @Test
    fun `erro final do turno permite tentar de novo`() {
        val err = ChatItem.Error("B:1:3", 1, "error", "falhou")
        val state = ChatState(conversationId = "c", items = listOf(user(1, "faça"), err))
        assertEquals(err, ThreadModel.lastTurnError(state))
        assertEquals("faça", ThreadModel.lastUserText(state))
        val row = ThreadModel.buildRows(state, null).filterIsInstance<ThreadRow.Item>().last()
        assertTrue(row.retry)
        assertNull(ThreadModel.lastTurnError(state.copy(busy = true)))
    }

    @Test
    fun `passos do sub-agente seguem tool e result`() {
        val steps = listOf(
            SubagentStep("tool", "a", "Read", "{\"file_path\":\"/x/y.kt\"}", false, null),
            SubagentStep("tool", "b", "Bash", "{\"command\":\"ls\"}", false, null),
            SubagentStep("result", "a", null, "ok", false, null),
            SubagentStep("result", "b", null, "boom", true, null),
            SubagentStep("text", null, null, "Pronto.\nmais", false, null),
        )
        val t = tool("Task", ToolStatus.RUNNING, "{\"description\":\"Revisar\",\"subagent_type\":\"reviewer\"}", steps)
        val out = ThreadModel.subagentSteps(t)
        assertEquals(listOf(StepState.Done, StepState.Failed, StepState.Done), out.map { it.state })
        assertEquals("Pronto.", out.last().title)

        val agents = ThreadModel.collectAgents(listOf(t))
        assertEquals(1, agents.size)
        assertEquals("Revisar", agents[0].title)
        assertEquals("reviewer", agents[0].subtitle)
        assertTrue(agents[0].running)
    }

    @Test
    fun `passo pendurado fecha quando a ferramenta-mae termina`() {
        val steps = listOf(SubagentStep("tool", "a", "Read", null, false, null))
        assertEquals(StepState.Running, ThreadModel.subagentSteps(tool("Task", ToolStatus.RUNNING, sub = steps)).single().state)
        assertEquals(StepState.Done, ThreadModel.subagentSteps(tool("Task", ToolStatus.DONE, sub = steps)).single().state)
        assertEquals(StepState.Skipped, ThreadModel.subagentSteps(tool("Task", ToolStatus.INTERRUPTED, sub = steps)).single().state)
    }

    @Test
    fun `pergunta de ferramenta entra logo apos o item`() {
        val q = tool(
            "ask_question",
            ToolStatus.RUNNING,
            "{\"question\":\"Qual?\",\"options\":[{\"label\":\"A\"},{\"label\":\"B\"}]}",
        )
        val state = ChatState(conversationId = "c", items = listOf(user(1, "oi"), q), busy = true)
        val rows = ThreadModel.buildRows(state, q)
        val idx = rows.indexOfFirst { it is ThreadRow.ToolAsk }
        if (q.question != null) {
            assertTrue(idx > 0)
            assertEquals(q.key, (rows[idx - 1] as ThreadRow.Item).item.key)
        }
    }
}
