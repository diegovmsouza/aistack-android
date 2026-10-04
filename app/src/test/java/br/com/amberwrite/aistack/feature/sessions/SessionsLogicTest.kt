package br.com.amberwrite.aistack.feature.sessions

import br.com.amberwrite.aistack.core.relay.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SessionsLogicTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun gruposPorData() {
        val now = at(10, 9)
        assertEquals(DateGroup.Today, dateGroupOf(at(10, 0), now, zone))
        assertEquals(DateGroup.Today, dateGroupOf(at(11, 0), now, zone))
        assertEquals(DateGroup.Yesterday, dateGroupOf(at(9, 23), now, zone))
        assertEquals(DateGroup.Last7Days, dateGroupOf(at(4, 12), now, zone))
        assertEquals(DateGroup.Older, dateGroupOf(at(3, 12), now, zone))
    }

    @Test
    fun nomeDoProjeto() {
        assertEquals("app", projectNameOf("/home/d/app/"))
        assertEquals("proj", projectNameOf("C:\\dev\\proj"))
        assertEquals("/", projectNameOf("/"))
    }

    @Test
    fun buscaIgnoraAcentoECaixa() {
        assertEquals("acao rapida", normalizeForSearch("  Ação Rápida "))
    }

    @Test
    fun previaEmUmaLinha() {
        assertEquals("Olá mundo código", cleanPreview("**Olá**\n\n  mundo `código`"))
        val longa = cleanPreview("x".repeat(PREVIEW_MAX + 10))
        assertEquals(PREVIEW_MAX + 1, longa.length)
        assertTrue(longa.endsWith("…"))
    }

    @Test
    fun conteudoDaLista() {
        val online = ConnectionState.Online(true)
        val offline = ConnectionState.HostOffline(null)
        assertEquals(ListContent.List, listContentOf(3, 2, true, "erro", offline))
        assertEquals(ListContent.NoResults, listContentOf(3, 0, true, null, online))
        assertEquals(ListContent.Error, listContentOf(0, 0, false, "erro", online))
        assertEquals(ListContent.Offline, listContentOf(0, 0, false, null, offline))
        assertEquals(ListContent.Loading, listContentOf(0, 0, false, null, online))
        assertEquals(ListContent.Empty, listContentOf(0, 0, true, null, online))
    }
}
