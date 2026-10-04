package br.com.amberwrite.aistack.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogicTest {

    @Test
    fun `tema desconhecido cai no sistema`() {
        assertEquals(ThemeMode.Light, ThemeMode.from("light"))
        assertEquals(ThemeMode.System, ThemeMode.from("roxo"))
        assertEquals(ThemeMode.System, ThemeMode.from(null))
    }

    @Test
    fun `nome é normalizado e limitado`() {
        assertEquals("Meu Pixel", SettingsLogic.sanitizeName("  Meu \n  Pixel "))
        assertEquals(SettingsLogic.NAME_MAX, SettingsLogic.sanitizeName("x".repeat(100)).length)
        assertFalse(SettingsLogic.nameChanged("Pixel", "  Pixel "))
        assertFalse(SettingsLogic.nameChanged("Pixel", "   "))
        assertTrue(SettingsLogic.nameChanged("Pixel", "Pixel 2"))
    }

    @Test
    fun `host do relay`() {
        assertEquals("relay.exemplo.com:8443", SettingsLogic.relayHost("wss://relay.exemplo.com:8443/ws?x=1"))
        assertNull(SettingsLogic.relayHost(" "))
    }

    @Test
    fun `Material You a partir do Android 12`() {
        assertFalse(SettingsLogic.materialYouSupported(30))
        assertTrue(SettingsLogic.materialYouSupported(31))
    }
}
