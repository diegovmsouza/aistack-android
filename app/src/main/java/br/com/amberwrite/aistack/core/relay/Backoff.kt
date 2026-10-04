package br.com.amberwrite.aistack.core.relay

import kotlin.random.Random

/**
 * Espera exponencial com jitter para reconexão: 1 s, 2 s, 4 s… até [maxMs] (60 s), cada uma
 * sorteada entre 50% e 100% do teto da tentativa ("equal jitter") para evitar que vários
 * aparelhos reconectem em sincronia. [reset] volta ao início (sucesso ou rede de volta).
 */
class Backoff(
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 60_000,
    private val random: Random = Random.Default
) {
    var attempt: Int = 0
        private set

    /** Teto (sem jitter) da próxima espera. */
    fun ceilingMs(forAttempt: Int = attempt): Long {
        val shift = forAttempt.coerceIn(0, 30)
        val raw = baseMs shl shift
        return if (raw <= 0 || raw > maxMs) maxMs else raw
    }

    /** Devolve a próxima espera em ms e avança o contador. Sempre em [baseMs, maxMs]. */
    fun nextDelayMs(): Long {
        val ceiling = ceilingMs()
        attempt++
        val half = ceiling / 2
        val delay = half + (if (ceiling - half > 0) random.nextLong(ceiling - half + 1) else 0L)
        return delay.coerceIn(baseMs, maxMs)
    }

    fun reset() {
        attempt = 0
    }
}
