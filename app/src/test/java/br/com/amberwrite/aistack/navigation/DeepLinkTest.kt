package br.com.amberwrite.aistack.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkTest {

    @Test
    fun pending() {
        assertEquals(DeepLink.Pending, DeepLink.parse("aistack://pending"))
        assertEquals(DeepLink.Pending, DeepLink.parse("aistack://pending/"))
    }

    @Test
    fun chat() {
        assertEquals(DeepLink.Chat("c1"), DeepLink.parse("aistack://chat/c1"))
        assertEquals(DeepLink.Chat("a-b"), DeepLink.parse("aistack://chat/a%2Db?x=1"))
        assertEquals(
            DeepLink.Chat("6f1c2a9e-0d1b-4c8e-9a51-2f0e7b3c4d5a"),
            DeepLink.parse("aistack://chat/6f1c2a9e-0d1b-4c8e-9a51-2f0e7b3c4d5a/")
        )
        assertNull(DeepLink.parse("aistack://chat/"))
    }

    @Test
    fun chatComIdInvalidoEhIgnorado() {
        assertNull(DeepLink.parse("aistack://chat/a%20b"))
        assertNull(DeepLink.parse("aistack://chat/..%2F..%2Fsettings"))
        assertNull(DeepLink.parse("aistack://chat/a%2Fb"))
        assertNull(DeepLink.parse("aistack://chat/" + "x".repeat(200)))
        assertNull(DeepLink.parse("aistack://chat/%E0%A4%A"))
    }

    @Test
    fun validacaoDeId() {
        assertTrue(DeepLink.isValidConversationId("conv_01:abc.def-9"))
        assertFalse(DeepLink.isValidConversationId(null))
        assertFalse(DeepLink.isValidConversationId(""))
        assertFalse(DeepLink.isValidConversationId(".."))
        assertFalse(DeepLink.isValidConversationId("a\nb"))
        assertFalse(DeepLink.isValidConversationId("ação"))
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
