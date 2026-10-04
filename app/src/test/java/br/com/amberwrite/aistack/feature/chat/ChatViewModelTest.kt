package br.com.amberwrite.aistack.feature.chat

import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.data.model.Attachment
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.ChatState
import br.com.amberwrite.aistack.data.model.Conversation
import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.data.model.QueuedMessage
import br.com.amberwrite.aistack.data.repo.ActionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeChatGateway(initial: ChatState = ChatState(conversationId = "c1")) : ChatGateway {
    override val conversationId = "c1"
    override val chat = MutableStateFlow(initial)
    override val connection: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Online(hostFrag = true))
    override val conversation: Flow<Conversation?> = flowOf(null)

    val calls = mutableListOf<String>()
    var failWith: Exception? = null
    var permissionResult: ActionResult = ActionResult.Ok
    var expandResult = true
    var gate: CompletableDeferred<Unit>? = null
    var closed = false

    private suspend fun step(name: String) {
        calls += name
        gate?.await()
        failWith?.let { throw it }
    }

    override fun openToolQuestion(state: ChatState): ChatItem.Tool? = null
    override suspend fun refresh() = step("refresh")
    override suspend fun loadOlder() = step("loadOlder")
    override suspend fun expandBlock(turn: Long, seq: Long): Boolean { step("expand:$turn:$seq"); return expandResult }
    override suspend fun send(text: String, attachments: List<Attachment>) = step("send:$text")
    override suspend fun queue(text: String, attachments: List<Attachment>) = step("queue:$text")
    override suspend fun sendNow(text: String, attachments: List<Attachment>) = step("sendNow:$text")
    override suspend fun unqueue(queueId: String) = step("unqueue:$queueId")
    override suspend fun interrupt() = step("interrupt")
    override suspend fun answerPermission(requestId: String, decision: PermissionDecision): ActionResult {
        step("permission:$requestId:${decision::class.simpleName}:${(decision as? PermissionDecision.Allow)?.remember}")
        return permissionResult
    }
    override suspend fun answerQuestion(request: PermissionRequest, selected: Map<Question, List<String>>): ActionResult {
        step("question:${request.requestId}"); return permissionResult
    }
    override suspend fun dismissQuestion(request: PermissionRequest): ActionResult {
        step("dismiss:${request.requestId}"); return permissionResult
    }
    override suspend fun answerToolQuestion(question: Question, optionIndex: Int?, freeText: String?) = step("tool:$optionIndex")
    override suspend fun rename(title: String) = step("rename:$title")
    override suspend fun archive(archived: Boolean) = step("archive:$archived")
    override fun close() { closed = true }
}

private fun request(id: String = "r1") = PermissionRequest(
    conversationId = "c1",
    requestId = id,
    tool = "Bash",
    toolUseId = null,
    input = null,
    inputPreview = "ls",
    truncated = false,
    reason = null,
    since = null,
    suggestions = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `estado combina chat e linhas da thread`() = runTest {
        val gw = FakeChatGateway(
            ChatState(
                conversationId = "c1",
                items = listOf(ChatItem.User("B:1:0", 1, "oi", emptyList()), ChatItem.Text("B:1:1", 1, "olá")),
            ),
        )
        val vm = ChatViewModel(gw)
        observe(vm)
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.online)
        assertEquals(2, s.rows.size)
        assertFalse(s.isEmpty)
    }

    @Test
    fun `permitir marca enviando e limpa ao terminar`() = runTest {
        val gw = FakeChatGateway()
        gw.gate = CompletableDeferred()
        val vm = ChatViewModel(gw)
        observe(vm)
        vm.allow(request(), remember = true)
        assertEquals(ChatViewModel.Decision.Allow, currentLocal(vm).answering["r1"])
        // Segundo toque enquanto envia é ignorado.
        vm.deny(request())
        gw.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("permission:r1:Allow:true"), gw.calls)
        assertTrue(currentLocal(vm).answering.isEmpty())
    }

    @Test
    fun `falha ao responder vira erro do cartao`() = runTest {
        val gw = FakeChatGateway()
        gw.permissionResult = ActionResult.Failed("sem rede")
        val vm = ChatViewModel(gw)
        observe(vm)
        vm.deny(request())
        advanceUntilIdle()
        assertEquals("sem rede", currentLocal(vm).failures["r1"])
    }

    @Test
    fun `enviar ja tira da fila e envia`() = runTest {
        val gw = FakeChatGateway()
        val vm = ChatViewModel(gw)
        observe(vm)
        vm.sendNow(QueuedMessage("q1", "depois", emptyList()))
        advanceUntilIdle()
        assertEquals(listOf("unqueue:q1", "sendNow:depois"), gw.calls)
        assertTrue(currentLocal(vm).queueBusy.isEmpty())
    }

    @Test
    fun `envio falho devolve a mensagem para a fila`() = runTest {
        val gw = object : ChatGateway by FakeChatGateway() {
            val calls = mutableListOf<String>()
            override suspend fun unqueue(queueId: String) { calls += "unqueue" }
            override suspend fun sendNow(text: String, attachments: List<Attachment>) { calls += "sendNow"; error("caiu") }
            override suspend fun queue(text: String, attachments: List<Attachment>) { calls += "queue:$text" }
        }
        val vm = ChatViewModel(gw)
        observe(vm)
        vm.sendNow(QueuedMessage("q1", "depois", emptyList()))
        advanceUntilIdle()
        assertEquals(listOf("unqueue", "sendNow", "queue:depois"), gw.calls)
        assertTrue(currentLocal(vm).actionError != null)
    }

    @Test
    fun `tentar de novo reenvia a ultima mensagem`() = runTest {
        val gw = FakeChatGateway(
            ChatState(
                conversationId = "c1",
                items = listOf(ChatItem.User("B:1:0", 1, "faça", emptyList()), ChatItem.Error("B:1:1", 1, "error", "x")),
            ),
        )
        val vm = ChatViewModel(gw)
        observe(vm)
        vm.retryLastTurn()
        advanceUntilIdle()
        assertEquals(listOf("send:faça"), gw.calls)
    }

    @Test
    fun `expandir item cortado e falha registrada`() = runTest {
        val gw = FakeChatGateway()
        val vm = ChatViewModel(gw)
        observe(vm)
        val item = ChatItem.Text("B:4:2", 4, "…")
        gw.expandResult = false
        vm.expand(item)
        advanceUntilIdle()
        assertEquals(listOf("expand:4:2"), gw.calls)
        assertTrue("B:4:2" in currentLocal(vm).expandFailed)

        gw.expandResult = true
        vm.expand(item)
        advanceUntilIdle()
        assertFalse("B:4:2" in currentLocal(vm).expandFailed)

        // Item ao vivo sem bloco persistido falha sem chamar o host.
        vm.expand(ChatItem.Text("L:4:x", 4, "…"))
        assertTrue("L:4:x" in currentLocal(vm).expandFailed)
        assertEquals(2, gw.calls.size)
    }

    @Test
    fun `renomear e arquivar`() = runTest {
        val gw = FakeChatGateway()
        val vm = ChatViewModel(gw)
        observe(vm)
        vm.rename("  Novo nome ")
        vm.archive()
        advanceUntilIdle()
        assertEquals(listOf("rename:Novo nome", "archive:true"), gw.calls)
        assertEquals("Novo nome", vm.state.value.title)
        assertTrue(vm.state.value.local.archived)
    }

    @Test
    fun `erro de interromper aparece e pode ser dispensado`() = runTest {
        val gw = FakeChatGateway()
        gw.failWith = IllegalStateException("host ocupado")
        val vm = ChatViewModel(gw)
        observe(vm)
        vm.interrupt()
        advanceUntilIdle()
        assertTrue(currentLocal(vm).actionError != null)
        vm.dismissError()
        assertNull(currentLocal(vm).actionError)
    }

    /** Mantém um coletor durante o teste (o `stateIn` só atualiza quando coletado). */
    private fun TestScope.observe(vm: ChatViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
    }

    private fun TestScope.currentLocal(vm: ChatViewModel): ChatViewModel.Local {
        advanceUntilIdle()
        return vm.state.value.local
    }
}
