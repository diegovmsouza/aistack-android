package br.com.amberwrite.aistack.feature.devices

import br.com.amberwrite.aistack.data.model.PairedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesLogicTest {

    private fun dev(id: String, name: String = id, lastSeen: Long? = null, revoked: Boolean = false) =
        PairedDevice(id, name, null, null, lastSeen, revoked)

    @Test
    fun `tipo pelo nome`() {
        assertEquals(DeviceKind.Laptop, DevicesLogic.kind("MacBook do Diego"))
        assertEquals(DeviceKind.Desktop, DevicesLogic.kind("PC da sala"))
        assertEquals(DeviceKind.Phone, DevicesLogic.kind("Pixel 10 Pro XL"))
        // "pc" só como palavra inteira
        assertEquals(DeviceKind.Phone, DevicesLogic.kind("Epcot"))
    }

    @Test
    fun `este aparelho primeiro, revogados no fim e ativos por último acesso`() {
        val now = 10_000_000L
        val list = DevicesLogic.order(
            listOf(
                dev("old", lastSeen = now - 3_600_000),
                dev("gone", lastSeen = now, revoked = true),
                dev("me", lastSeen = null),
                dev("fresh", lastSeen = now - 30_000),
                dev("never")
            ),
            selfId = "me",
            now = now,
            connected = true
        )
        assertEquals(listOf("me", "fresh", "old", "never", "gone"), list.map { it.id })
        assertTrue(list[0].isThis)
        assertTrue(list[0].activeNow)
        assertTrue(list[1].activeNow)
        assertFalse(list[2].activeNow)
        assertFalse(list[4].activeNow)
    }

    @Test
    fun `este aparelho desconectado não aparece como ativo`() {
        val list = DevicesLogic.order(listOf(dev("me")), selfId = "me", now = 0L, connected = false)
        assertFalse(list.single().activeNow)
    }

    @Test
    fun `janela de atividade`() {
        assertTrue(DevicesLogic.isActive(1000L, 1000L + DevicesLogic.ACTIVE_WINDOW_MS))
        assertFalse(DevicesLogic.isActive(1000L, 1001L + DevicesLogic.ACTIVE_WINDOW_MS))
        assertFalse(DevicesLogic.isActive(null, 0L))
    }

    @Test
    fun `confirmação exige a palavra`() {
        assertTrue(DevicesLogic.confirmMatches("  Desparear "))
        assertFalse(DevicesLogic.confirmMatches("despar"))
    }
}
