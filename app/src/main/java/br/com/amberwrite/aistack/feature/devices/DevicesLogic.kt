package br.com.amberwrite.aistack.feature.devices

import br.com.amberwrite.aistack.data.model.PairedDevice

/** Ícone sugerido pelo nome do aparelho. */
enum class DeviceKind { Phone, Laptop, Desktop }

data class DeviceView(
    val id: String,
    val name: String,
    val isThis: Boolean,
    val kind: DeviceKind,
    val pairedAtMs: Long?,
    val lastSeenMs: Long?,
    val revoked: Boolean,
    /** Visto há pouco (ou é este aparelho, que está conectado agora). */
    val activeNow: Boolean
)

/** Regras puras da tela de aparelhos (testáveis na JVM). */
object DevicesLogic {

    /** Até quanto tempo depois do último acesso consideramos "ativo agora". */
    const val ACTIVE_WINDOW_MS = 2 * 60_000L

    /** Palavra que o usuário digita para confirmar o desparear. */
    const val CONFIRM_WORD = "desparear"

    fun kind(name: String): DeviceKind {
        val n = name.lowercase()
        return when {
            listOf("laptop", "notebook", "macbook", "thinkpad").any { it in n } -> DeviceKind.Laptop
            listOf("desktop", "pc", "imac", "workstation", "linux", "windows").any { Regex("\\b$it\\b").containsMatchIn(n) } -> DeviceKind.Desktop
            else -> DeviceKind.Phone
        }
    }

    fun isActive(lastSeenMs: Long?, now: Long): Boolean =
        lastSeenMs != null && now - lastSeenMs in 0..ACTIVE_WINDOW_MS

    /**
     * Este aparelho primeiro; depois os ativos por último acesso (mais recente antes, sem data no fim);
     * os revogados vão para o fim.
     */
    fun order(devices: List<PairedDevice>, selfId: String?, now: Long, connected: Boolean = true): List<DeviceView> =
        devices
            .map { d ->
                val isThis = selfId != null && d.id == selfId
                DeviceView(
                    id = d.id,
                    name = d.name,
                    isThis = isThis,
                    kind = kind(d.name),
                    pairedAtMs = d.pairedAtMs,
                    lastSeenMs = d.lastSeenMs,
                    revoked = d.revoked,
                    activeNow = !d.revoked && ((isThis && connected) || isActive(d.lastSeenMs, now))
                )
            }
            .sortedWith(
                compareBy<DeviceView> { if (it.isThis) 0 else if (it.revoked) 2 else 1 }
                    .thenByDescending { it.lastSeenMs ?: Long.MIN_VALUE }
                    .thenBy { it.name.lowercase() }
            )

    /** O texto digitado confirma o desparear (sem diferenciar maiúsculas nem espaços nas pontas). */
    fun confirmMatches(input: String): Boolean = input.trim().equals(CONFIRM_WORD, ignoreCase = true)
}
