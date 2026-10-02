package br.com.amberwrite.aistack.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectBackoffTest {
    @Test
    fun dobraAteOTetoDeSessentaSegundos() {
        val sem = 0.5 // jitter neutro: fator 1,0
        assertEquals(3_000L, RelayClient.reconnectDelayMs(0, sem))
        assertEquals(6_000L, RelayClient.reconnectDelayMs(1, sem))
        assertEquals(12_000L, RelayClient.reconnectDelayMs(2, sem))
        assertEquals(48_000L, RelayClient.reconnectDelayMs(4, sem))
        assertEquals(60_000L, RelayClient.reconnectDelayMs(5, sem))
        assertEquals(60_000L, RelayClient.reconnectDelayMs(50, sem))
    }

    @Test
    fun jitterFicaEntreVinteEOitentaPorCentoParaMaisOuParaMenos() {
        val min = RelayClient.reconnectDelayMs(2, 0.0)
        val max = RelayClient.reconnectDelayMs(2, 1.0)
        assertEquals(9_600L, min)
        assertEquals(14_400L, max)
        assertTrue(RelayClient.reconnectDelayMs(10, 1.0) <= 72_000L)
    }
}
