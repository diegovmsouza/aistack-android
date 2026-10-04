package br.com.amberwrite.aistack.service

import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.ToolQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyRoutingTest {

    private fun answers(route: ReplyRoute): Map<String, String>? =
        ((route as ReplyRoute.Answer).decision as PermissionDecision.Allow).answers

    @Test
    fun `permissao comum vira negar com mensagem`() {
        assertEquals(
            ReplyRoute.Answer(PermissionDecision.Deny("use o build cache")),
            ReplyRouting.forPermission(Fx.permission(), "  use o build cache "),
        )
    }

    @Test
    fun `texto vazio e rejeitado`() {
        assertEquals(ReplyRoute.Rejected(ReplyRoute.Reason.Empty), ReplyRouting.forPermission(Fx.permission(), "   "))
        val q = ToolQuestion.from("ask_question", Fx.json(Fx.QUESTION_INPUT))!!
        assertEquals(ReplyRoute.Rejected(ReplyRoute.Reason.Empty), ReplyRouting.forToolQuestion(q, "", busy = false))
    }

    @Test
    fun `pergunta aceita numero, rotulo ou texto livre`() {
        val req = Fx.askOne()
        assertEquals(mapOf("Qual banco?" to "Mongo"), answers(ReplyRouting.forPermission(req, "2")))
        assertEquals(mapOf("Qual banco?" to "Mongo"), answers(ReplyRouting.forPermission(req, "2. Mongo")))
        assertEquals(mapOf("Qual banco?" to "SQLite"), answers(ReplyRouting.forPermission(req, "sqlite")))
        assertEquals(mapOf("Qual banco?" to "DuckDB"), answers(ReplyRouting.forPermission(req, "DuckDB")))
    }

    @Test
    fun `multipla escolha separa por virgula`() {
        val req = Fx.askOne(multi = true)
        assertEquals(mapOf("Qual banco?" to "Postgres, SQLite"), answers(ReplyRouting.forPermission(req, "1, 3")))
        // Item desconhecido: tudo vira texto livre.
        assertEquals(mapOf("Qual banco?" to "1, Oracle"), answers(ReplyRouting.forPermission(req, "1, Oracle")))
    }

    @Test
    fun `varias perguntas, uma resposta por linha ou ponto e virgula`() {
        val req = Fx.askTwo()
        val expected = mapOf("Linguagem?" to "Kotlin", "Build?" to "Maven")
        assertEquals(expected, answers(ReplyRouting.forPermission(req, "1\n2")))
        assertEquals(expected, answers(ReplyRouting.forPermission(req, "Kotlin; Maven")))
        // Quantidade não bate: fallback do contrato é negar com a resposta.
        assertEquals(
            ReplyRoute.Answer(PermissionDecision.Deny("só Kotlin")),
            ReplyRouting.forPermission(req, "só Kotlin"),
        )
    }

    @Test
    fun `pergunta de ferramenta vira mensagem no formato do desktop`() {
        val q = ToolQuestion.from("ask_question", Fx.json(Fx.QUESTION_INPUT))!!
        assertEquals(ReplyRoute.Message("1. Azul: frio"), ReplyRouting.forToolQuestion(q, "1", busy = false))
        // Rótulo exato também vira "{n}. {rótulo}".
        assertEquals(ReplyRoute.Message("2. Vermelho"), ReplyRouting.forToolQuestion(q, "vermelho", busy = false))
        assertEquals(ReplyRoute.Message("verde"), ReplyRouting.forToolQuestion(q, "verde", busy = false))
        assertEquals(ReplyRoute.Rejected(ReplyRoute.Reason.Busy), ReplyRouting.forToolQuestion(q, "1", busy = true))
    }

    @Test
    fun `escolha rapida de pergunta de ferramenta`() {
        val q = ToolQuestion.from("ask_question", Fx.json(Fx.QUESTION_INPUT))!!.questions.single()
        assertEquals(ReplyRoute.Message("2. Vermelho"), ReplyRouting.toolQuestionChoice(q, 1))
        assertEquals(ReplyRoute.Rejected(ReplyRoute.Reason.Empty), ReplyRouting.toolQuestionChoice(q, 7))
    }

    @Test
    fun `indice de opcao`() {
        val q = Fx.askOne().questions.single()
        assertEquals(0, ReplyRouting.optionIndex(q, "1)"))
        assertEquals(1, ReplyRouting.optionIndex(q, "2 - Mongo"))
        assertEquals(2, ReplyRouting.optionIndex(q, "SQLITE"))
        assertNull(ReplyRouting.optionIndex(q, "9"))
        assertNull(ReplyRouting.optionIndex(q, "2. Outra coisa"))
        assertNull(ReplyRouting.optionIndex(q, ""))
    }

    @Test
    fun `split por linhas ou ponto e virgula`() {
        assertEquals(listOf("a b"), ReplyRouting.split("a b", 1))
        assertEquals(listOf("a", "b"), ReplyRouting.split("a\n\nb", 2))
        assertEquals(listOf("a", "b"), ReplyRouting.split("a; b", 2))
        assertNull(ReplyRouting.split("a", 2))
    }

    @Test
    fun `json da pergunta de ferramenta vai e volta`() {
        val q = ToolQuestion.from("ask_question", Fx.json(Fx.QUESTION_INPUT))!!
        assertEquals(q, ToolQuestionJson.decode(ToolQuestionJson.encode(q)))
        assertNull(ToolQuestionJson.decode(null))
        assertNull(ToolQuestionJson.decode("{não é json"))
        assertNull(ToolQuestionJson.decode("""{"questions":[]}"""))
    }

    @Test
    fun `plano de assinatura nunca mexe nas conversas abertas`() {
        val plan = SubscriptionPlan.of(current = setOf("a", "b"), wanted = setOf("b", "c", "d"), open = setOf("d", "a"))
        assertEquals(setOf("c"), plan.toFull)
        assertTrue(plan.toSummary.isEmpty())
        val down = SubscriptionPlan.of(current = setOf("a", "b"), wanted = emptySet(), open = emptySet())
        assertEquals(setOf("a", "b"), down.toSummary)
        assertTrue(SubscriptionPlan.of(setOf("a"), setOf("a"), emptySet()).isEmpty)
    }
}
