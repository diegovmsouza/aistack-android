package br.com.amberwrite.aistack.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkTest {

    @Test
    fun chat() {
        assertEquals(DeepLink.Chat("c1"), DeepLink.parse("aistack://chat/c1"))
        assertEquals(DeepLink.Chat("a b"), DeepLink.parse("aistack://chat/a%20b?x=1"))
        assertNull(DeepLink.parse("aistack://chat/"))
    }

    @Test
    fun pair() {
        val raw = "aistack://pair?relay=wss%3A%2F%2Faistack.amberwrite.com.br&host=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855&pk=k_test_pk12345&code=994821"
        val link = DeepLink.parse(raw)
        assertTrue(link is DeepLink.Pair)
        assertEquals("994821", (link as DeepLink.Pair).link.code)
    }

    @Test
    fun invalido() {
        assertNull(DeepLink.parse(null))
        assertNull(DeepLink.parse(""))
        assertNull(DeepLink.parse("https://exemplo.com"))
    }
}
