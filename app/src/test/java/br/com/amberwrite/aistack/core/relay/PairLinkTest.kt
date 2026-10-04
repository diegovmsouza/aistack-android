package br.com.amberwrite.aistack.core.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairLinkTest {

    @Test
    fun parse_validDeepLink_returnsPairLink() {
        val raw = "aistack://pair?relay=wss%3A%2F%2Faistack.amberwrite.com.br&host=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855&pk=k_test_pk12345&code=994821"
        val link = PairLink.parse(raw)

        assertNotNull(link)
        assertEquals("wss://aistack.amberwrite.com.br", link?.relay)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", link?.host)
        assertEquals("k_test_pk12345", link?.pk)
        assertEquals("994821", link?.code)
    }

    @Test
    fun parse_validHttpLink_returnsPairLink() {
        val raw = "https://aistack.amberwrite.com.br/pair?relay=wss%3A%2F%2Faistack.amberwrite.com.br&host=host_abc&pk=pk_xyz"
        val link = PairLink.parse(raw)

        assertNotNull(link)
        assertEquals("wss://aistack.amberwrite.com.br", link?.relay)
        assertEquals("host_abc", link?.host)
        assertEquals("pk_xyz", link?.pk)
        assertNull(link?.code)
    }

    @Test
    fun parse_invalidScheme_returnsNull() {
        val raw = "random://invalid?relay=wss%3A%2F%2Ftest&host=1&pk=2"
        assertNull(PairLink.parse(raw))
    }

    @Test
    fun parse_missingHostOrPk_returnsNull() {
        val missingPk = "aistack://pair?relay=wss%3A%2F%2Ftest&host=1"
        assertNull(PairLink.parse(missingPk))

        val missingRelay = "aistack://pair?host=1&pk=2"
        assertNull(PairLink.parse(missingRelay))
    }

    @Test
    fun parse_emptyString_returnsNull() {
        assertNull(PairLink.parse(""))
        assertNull(PairLink.parse("   "))
    }
}
