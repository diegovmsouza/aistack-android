package br.com.amberwrite.aistack.core.rpc

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdatedAtTest {

    private fun ms(json: String): Long? = JsonParser.parseString(json).asJsonObject.epochMillis("updatedAt")

    @Test
    fun numeroEmMilissegundos() {
        assertEquals(1759496400000L, ms("""{"updatedAt":1759496400000}"""))
    }

    @Test
    fun numeroEmTexto() {
        assertEquals(1759496400000L, ms("""{"updatedAt":"1759496400000"}"""))
    }

    @Test
    fun rfc3339Utc() {
        assertEquals(1759496400000L, ms("""{"updatedAt":"2025-10-03T13:00:00Z"}"""))
    }

    @Test
    fun rfc3339ComFracaoEFuso() {
        assertEquals(1759496400123L, ms("""{"updatedAt":"2025-10-03T10:00:00.123-03:00"}"""))
    }

    @Test
    fun ausenteNuloOuInvalido() {
        assertNull(ms("""{}"""))
        assertNull(ms("""{"updatedAt":null}"""))
        assertNull(ms("""{"updatedAt":"ontem"}"""))
        assertNull(ms("""{"updatedAt":""}"""))
    }
}
