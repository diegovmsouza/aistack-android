package br.com.amberwrite.aistack.core.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackoffTest {

    @Test
    fun tetoDobraAteOMaximo() {
        val b = Backoff()
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), (0..7).map { b.ceilingMs(it) })
        assertEquals(60_000L, b.ceilingMs(64))
    }

    @Test
    fun esperaFicaEntreMetadeETetoEAvanca() {
        val b = Backoff(random = Random(42))
        repeat(40) { i ->
            val ceiling = b.ceilingMs()
            val d = b.nextDelayMs()
            assertTrue("tentativa $i: $d", d in maxOf(1_000L, ceiling / 2)..ceiling)
            assertEquals(i + 1, b.attempt)
        }
    }

    @Test
    fun resetVoltaAoInicio() {
        val b = Backoff(random = Random(1))
        repeat(5) { b.nextDelayMs() }
        b.reset()
        assertEquals(0, b.attempt)
        assertTrue(b.nextDelayMs() in 1_000L..1_000L)
    }
}
