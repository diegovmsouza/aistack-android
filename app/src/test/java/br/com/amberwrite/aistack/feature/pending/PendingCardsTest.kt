package br.com.amberwrite.aistack.feature.pending

import br.com.amberwrite.aistack.service.Fx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingCardsTest {

    @Test
    fun `um cartao por conversa com pedidos, mais antigo primeiro`() {
        val items = listOf(
            Fx.conversation("a", permissions = listOf(Fx.permission("a", "a2", since = 500), Fx.permission("a", "a1", since = 300))),
            Fx.conversation("b", permissions = listOf(Fx.permission("b", "b1", since = 100))),
            Fx.conversation("ocupada", busy = true),
        )
        val cards = PendingCards.build(items, emptySet(), emptyMap(), emptyMap())
        assertEquals(listOf("b", "a"), cards.map { it.key })
        assertEquals(listOf("a1", "a2"), cards[1].entries.map { it.request.requestId })
        assertEquals(3, cards.sumOf { it.waitingCount })
    }

    @Test
    fun `envio e erro aparecem no pedido`() {
        val items = listOf(Fx.conversation("a", permissions = listOf(Fx.permission("a", "1"), Fx.permission("a", "2"))))
        val card = PendingCards.build(items, setOf("1"), mapOf("2" to "Sem conexão"), emptyMap()).single()
        assertEquals(EntryStatus.Sending, card.entries.first { it.request.requestId == "1" }.status)
        val failed = card.entries.first { it.request.requestId == "2" }
        assertEquals(EntryStatus.Pending, failed.status)
        assertEquals("Sem conexão", failed.error)
        assertEquals(2, card.waitingCount)
    }

    @Test
    fun `fantasma fica um instante no cartao da conversa e nao conta como espera`() {
        val conv = Fx.conversation("a")
        val answered = Fx.permission("a", "1", since = 50)
        val ghosts = mapOf("1" to PendingCards.Ghost(conv, answered, EntryStatus.Allowed))
        val items = listOf(Fx.conversation("a", permissions = listOf(Fx.permission("a", "2", since = 80))))
        val card = PendingCards.build(items, emptySet(), emptyMap(), ghosts).single()
        assertEquals(listOf("1" to EntryStatus.Allowed, "2" to EntryStatus.Pending), card.entries.map { it.request.requestId to it.status })
        assertEquals(1, card.waitingCount)
    }

    @Test
    fun `fantasma de conversa que saiu da lista vira cartao proprio`() {
        val ghosts = mapOf("x" to PendingCards.Ghost(Fx.conversation("z"), Fx.permission("z", "x"), EntryStatus.Denied))
        val card = PendingCards.build(emptyList(), emptySet(), emptyMap(), ghosts).single()
        assertEquals("z", card.key)
        assertEquals(EntryStatus.Denied, card.entries.single().status)
        assertEquals(0, card.waitingCount)

        // Conversa ainda na lista (ocupada, sem pedidos) mas com fantasma: mantém o cartão.
        val kept = PendingCards.build(listOf(Fx.conversation("z", busy = true)), emptySet(), emptyMap(), ghosts)
        assertEquals(1, kept.size)
    }

    @Test
    fun `fantasma de pedido ainda vivo nao duplica`() {
        val req = Fx.permission("a", "1")
        val ghosts = mapOf("1" to PendingCards.Ghost(Fx.conversation("a"), req, EntryStatus.Allowed))
        val card = PendingCards.build(listOf(Fx.conversation("a", permissions = listOf(req))), emptySet(), emptyMap(), ghosts).single()
        assertEquals(1, card.entries.size)
    }

    @Test
    fun `respostas por pergunta`() {
        val two = Fx.askTwo()
        assertEquals(
            linkedMapOf("Linguagem?" to "Kotlin", "Build?" to "Gradle"),
            PendingCards.answersFor(two, mapOf(1 to "Gradle", 0 to "Kotlin")),
        )
        assertEquals(listOf("Linguagem?", "Build?"), PendingCards.answersFor(two, mapOf(0 to "a", 1 to "b"))!!.keys.toList())
        assertNull(PendingCards.answersFor(two, mapOf(0 to "Kotlin")))
        assertNull(PendingCards.answersFor(two, mapOf(0 to "Kotlin", 1 to "  ")))
        assertNull(PendingCards.answersFor(two, mapOf(0 to "Kotlin", 5 to "x")))
        assertNull(PendingCards.answersFor(Fx.permission(), mapOf(0 to "x")))
        assertTrue(PendingCards.answersFor(Fx.askOne(), mapOf(0 to "Mongo"))!!.containsValue("Mongo"))
    }
}
