package br.com.amberwrite.aistack.service

import android.app.Notification
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.rpc.EngineEvent
import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.data.model.PendingConversation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Notifier] falso: registra as chamadas em ordem. */
private class FakeNotifier : Notifier {
    val calls = ArrayList<Any>()
    val permissions get() = calls.filterIsInstance<PermissionNotice>()
    val lives get() = calls.filterIsInstance<LiveNotice>()
    val dones get() = calls.filterIsInstance<DoneNotice>()
    val toolQuestions get() = calls.filterIsInstance<ToolQuestionNotice>()
    var summary: PendingSummary? = null

    data class Cancel(val kind: String, val conv: String, val req: String? = null)

    fun cancels(kind: String) = calls.filterIsInstance<Cancel>().filter { it.kind == kind }

    override fun showPermission(notice: PermissionNotice) { calls += notice }
    override fun cancelPermission(conversationId: String, requestId: String) { calls += Cancel("perm", conversationId, requestId) }
    override fun showToolQuestion(notice: ToolQuestionNotice) { calls += notice }
    override fun cancelToolQuestion(conversationId: String) { calls += Cancel("tq", conversationId) }
    override fun showPendingSummary(summary: PendingSummary?) { this.summary = summary; calls += "summary" }
    override fun showLive(notice: LiveNotice) { calls += notice }
    override fun cancelLive(conversationId: String) { calls += Cancel("live", conversationId) }
    override fun showDone(notice: DoneNotice) { calls += notice }
    override fun cancelDone(conversationId: String) { calls += Cancel("done", conversationId) }
    override fun connectionNotification(state: ConnectionState): Notification = throw UnsupportedOperationException()
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCoordinatorTest {

    private class Rig(scope: TestScope, notifyDone: Boolean = true) {
        val notifier = FakeNotifier()
        val pending = MutableStateFlow<List<PendingConversation>>(emptyList())
        val events = MutableSharedFlow<HostEvent>(extraBufferCapacity = 64)
        val foreground = MutableStateFlow(false)
        val chat = MutableStateFlow<String?>(null)
        var done = notifyDone
        val coordinator = NotificationCoordinator(
            notifier = notifier,
            scope = scope.backgroundScope,
            pending = pending,
            events = events,
            isForeground = foreground,
            visibleChat = chat,
            notifyDone = { done },
            titleOf = { if (it == "c1") "Refatorar login" else null },
            clock = { scope.testScheduler.currentTime },
            liveIntervalMs = 1_000,
        )

        fun event(conv: String, e: EngineEvent) = coordinator.onEvent(Fx.conv(conv, e))
    }

    @Test
    fun `pedido em segundo plano notifica uma vez, com resumo`() = runTest {
        val r = Rig(this)
        val items = listOf(Fx.conversation(permissions = listOf(Fx.permission())))
        r.coordinator.sync(items, foreground = false, chat = null)
        assertEquals(1, r.notifier.permissions.size)
        assertFalse(r.notifier.permissions.single().silent)
        assertEquals(1, r.notifier.summary!!.total)

        // Mesma lista de novo: nada muda.
        r.coordinator.sync(items, foreground = false, chat = null)
        assertEquals(1, r.notifier.permissions.size)
    }

    @Test
    fun `conversa a vista some e volta sem alertar de novo`() = runTest {
        val r = Rig(this)
        val items = listOf(Fx.conversation(permissions = listOf(Fx.permission())))
        r.coordinator.sync(items, foreground = false, chat = null)
        r.coordinator.sync(items, foreground = true, chat = "c1")
        assertEquals(listOf(FakeNotifier.Cancel("perm", "c1", "r1")), r.notifier.cancels("perm"))
        assertNull(r.notifier.summary)

        // Em primeiro plano, mas em outra conversa: notifica (silenciosa, já alertou).
        r.coordinator.sync(items, foreground = true, chat = "outra")
        assertEquals(2, r.notifier.permissions.size)
        assertTrue(r.notifier.permissions.last().silent)
    }

    @Test
    fun `pedido resolvido cancela e limpa o resumo`() = runTest {
        val r = Rig(this)
        r.coordinator.sync(listOf(Fx.conversation(permissions = listOf(Fx.permission()))), false, null)
        r.coordinator.sync(listOf(Fx.conversation()), false, null)
        assertEquals(1, r.notifier.cancels("perm").size)
        assertNull(r.notifier.summary)

        // Um pedido novo com o mesmo id volta a alertar (o "já alertou" foi esquecido).
        r.coordinator.sync(listOf(Fx.conversation(permissions = listOf(Fx.permission()))), false, null)
        assertFalse(r.notifier.permissions.last().silent)
    }

    @Test
    fun `fim de turno posta concluido com titulo e duracao`() = runTest {
        val r = Rig(this)
        r.event("c1", Fx.started())
        r.event("c1", Fx.complete(3_000))
        val d = r.notifier.dones.single()
        assertEquals("Refatorar login", d.title)
        assertEquals(3_000L, d.durationMs)
        assertFalse(d.isError)
    }

    @Test
    fun `concluido nao aparece para a conversa aberta nem com a preferencia desligada`() = runTest {
        val r = Rig(this)
        r.coordinator.sync(emptyList(), foreground = true, chat = "c1")
        r.event("c1", Fx.started())
        r.event("c1", Fx.complete())
        assertTrue(r.notifier.dones.isEmpty())

        // Outra conversa terminando com o app aberto: notifica.
        r.event("c2", Fx.started())
        r.event("c2", EngineEvent.TurnError("api", "falhou"))
        assertEquals("c2", r.notifier.dones.single().conversationId)
        assertTrue(r.notifier.dones.single().isError)

        r.done = false
        r.event("c3", Fx.started())
        r.event("c3", Fx.complete())
        assertEquals(1, r.notifier.dones.size)
    }

    @Test
    fun `interrupcao pelo usuario nao notifica`() = runTest {
        val r = Rig(this)
        r.event("c1", Fx.started())
        r.event("c1", EngineEvent.TurnError("interrupted", ""))
        assertTrue(r.notifier.dones.isEmpty())
    }

    @Test
    fun `abrir a conversa remove o concluido`() = runTest {
        val r = Rig(this)
        r.coordinator.sync(emptyList(), foreground = true, chat = "c1")
        assertEquals(listOf(FakeNotifier.Cancel("done", "c1")), r.notifier.cancels("done"))
    }

    @Test
    fun `live update no maximo uma vez por segundo e some no fim`() = runTest {
        val r = Rig(this)
        r.event("c1", Fx.started())
        assertEquals(1, r.notifier.lives.size)
        assertNull(r.notifier.lives.last().tool)

        advanceTimeBy(100)
        r.event("c1", Fx.toolStart("t1", "Bash"))
        r.event("c1", Fx.toolResult("t1"))
        r.event("c1", Fx.toolStart("t2", "Grep"))
        assertEquals(1, r.notifier.lives.size) // agendado, ainda não emitido

        advanceTimeBy(950)
        runCurrent()
        assertEquals(2, r.notifier.lives.size)
        val live = r.notifier.lives.last()
        assertEquals("Grep", live.tool) // pega o estado mais novo
        assertTrue(live.detailed)
        assertEquals(0L, live.startedAt)

        r.event("c1", Fx.toolStart("s1", "Task"))
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(1, r.notifier.lives.last().subagents)

        r.event("c1", Fx.complete())
        assertEquals(listOf(FakeNotifier.Cancel("live", "c1")), r.notifier.cancels("live"))
    }

    @Test
    fun `sem live update com o app em primeiro plano`() = runTest {
        val r = Rig(this)
        r.event("c1", Fx.started())
        assertEquals(1, r.notifier.lives.size)
        r.coordinator.sync(listOf(Fx.conversation(busy = true)), foreground = true, chat = null)
        assertEquals(1, r.notifier.cancels("live").size)
        r.event("c1", Fx.toolStart("t1", "Bash"))
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, r.notifier.lives.size)
    }

    @Test
    fun `conversa ocupada na lista ganha live e marca espera por permissao`() = runTest {
        val r = Rig(this)
        r.coordinator.sync(listOf(Fx.conversation(busy = true, permissions = listOf(Fx.permission()))), false, null)
        val live = r.notifier.lives.single()
        assertTrue(live.waiting)
        assertFalse(live.detailed)

        // Deixou de estar ocupada: o live some.
        advanceTimeBy(2_000)
        r.coordinator.sync(listOf(Fx.conversation(busy = false)), false, null)
        assertEquals(1, r.notifier.cancels("live").size)
    }

    @Test
    fun `turno que para numa pergunta de ferramenta vira pendencia, nao concluido`() = runTest {
        val r = Rig(this)
        r.event("c1", Fx.started())
        r.event("c1", Fx.toolStart("q", "ask_question"))
        r.event("c1", EngineEvent.ToolInput("q", Fx.json(Fx.QUESTION_INPUT)))
        r.event("c1", Fx.complete())
        val tq = r.notifier.toolQuestions.single()
        assertEquals("Qual cor?", tq.headline)
        assertEquals(listOf("Azul", "Vermelho"), tq.choices)
        assertTrue(r.notifier.dones.isEmpty())
        assertEquals(1, r.notifier.summary!!.total)

        // Novo turno (respondida em outro lugar): a pergunta some.
        r.event("c1", Fx.started())
        assertEquals(listOf(FakeNotifier.Cancel("tq", "c1")), r.notifier.cancels("tq"))
        assertNull(r.notifier.summary)
    }

    @Test
    fun `start coleta os fluxos e clearAll remove tudo`() = runTest {
        val r = Rig(this)
        r.coordinator.start()
        runCurrent()
        r.pending.value = listOf(Fx.conversation(busy = true, permissions = listOf(Fx.permission())))
        runCurrent()
        assertEquals(1, r.notifier.permissions.size)
        r.events.emit(Fx.conv("c1", Fx.toolStart("t", "Bash")))
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals("Bash", r.notifier.lives.last().tool)

        r.coordinator.stop()
        assertEquals(1, r.notifier.cancels("perm").size)
        assertEquals(1, r.notifier.cancels("live").size)
        assertNull(r.notifier.summary)
    }
}
