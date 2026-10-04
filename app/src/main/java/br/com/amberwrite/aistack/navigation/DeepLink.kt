package br.com.amberwrite.aistack.navigation

import br.com.amberwrite.aistack.core.relay.PairLink
import java.net.URLDecoder

/**
 * Destinos que o app aceita por link externo. Interpretado a partir do texto do URI (sem
 * `android.net.Uri`) para ser testável na JVM.
 *
 * - `aistack://pair?…` (e os links http(s) que [PairLink.parse] aceita) → [Pair];
 * - `aistack://chat/{id}` → [Chat] (usado pelas notificações).
 */
sealed interface DeepLink {
    data class Pair(val link: PairLink) : DeepLink
    data class Chat(val conversationId: String) : DeepLink

    companion object {
        private const val CHAT_PREFIX = "aistack://chat/"

        fun parse(raw: String?): DeepLink? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            if (text.startsWith(CHAT_PREFIX, ignoreCase = true)) {
                val id = text.substring(CHAT_PREFIX.length).substringBefore('?').substringBefore('#').trim('/')
                if (id.isEmpty()) return null
                return Chat(runCatching { URLDecoder.decode(id, "UTF-8") }.getOrDefault(id))
            }
            return PairLink.parse(text)?.let(::Pair)
        }
    }
}
