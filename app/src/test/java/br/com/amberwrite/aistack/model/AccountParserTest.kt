package br.com.amberwrite.aistack.model

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountParserTest {
    @Test
    fun contaComUsoEContaSemUso() {
        val json = """[
          {"provider":"claude","slot":"a","label":"Pessoal","email":"x@y.z","plan":"Pro","authState":"ok",
           "usage":{"windows":[{"kind":"fiveHour","label":"Sessão (5h)","usedPct":42.5,"resetsAt":1790000000}],"status":"ok","plan":"Pro"},"installed":true},
          {"provider":"codex","slot":"b","authState":"ok","usage":null,"installed":true}
        ]"""
        val out = AccountParser.parse(JsonParser.parseString(json).asJsonArray)
        assertEquals(2, out.size)
        assertEquals(42.5, out[0].windows.single().usedPct, 0.0)
        assertEquals("Pro", out[0].plan)
        // Sem medição: lista vazia, para a gaveta mostrar "—" em vez de 0%.
        assertTrue(out[1].windows.isEmpty())
    }

    @Test
    fun entradaInvalidaNaoDerrubaALista() {
        val out = AccountParser.parse(JsonParser.parseString("""[1,{"provider":"claude"},{"provider":"agy","slot":"3"}]""").asJsonArray)
        assertEquals(listOf("3"), out.map { it.slot })
    }
}
