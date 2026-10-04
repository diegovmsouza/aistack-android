package br.com.amberwrite.aistack.navigation

import br.com.amberwrite.aistack.core.relay.PairLink
import java.net.URLDecoder

/**
 * Destinos que o app aceita por link externo. Interpretado a partir do texto do URI (sem
 * `android.net.Uri`) para ser testável na JVM.
 *
 * - `aistack://pair?…` (e os links http(s) que [PairLink.parse] aceita) → [Pair];
 * - `aistack://chat/{id}` → [Chat] (usado pelas notificações);
 * - `aistack://pending` → [Pending] (notificação agregada de pendências).
 *
 * Segurança: a activity é `exported` (o link de pareamento chega do navegador ou do app de
 * câmera), então qualquer app do aparelho pode disparar estes links. Por isso:
 * - o id de conversa é validado ([isValidConversationId]) antes de virar rota;
 * - pareamento nunca é automático quando já existe um desktop pareado (pede confirmação);
 * - links de chat/pendências só navegam quando o aparelho já está pareado.
 */
sealed interface DeepLink {
    data class Pair(val link: PairLink) : DeepLink
    data class Chat(val conversationId: String) : DeepLink
    data object Pending : DeepLink

    companion object {
        private const val CHAT_PREFIX = "aistack://chat/"
        private const val PENDING = "aistack://pending"
        private val CONVERSATION_ID = Regex("[A-Za-z0-9._:-]{1,128}")

        /** Ids de conversa do desktop são UUIDs/slugs: nada de barras, espaços ou controle. */
        fun isValidConversationId(id: String?): Boolean =
            id != null && CONVERSATION_ID.matches(id) && id != "." && id != ".."

        fun parse(raw: String?): DeepLink? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            if (text.substringBefore('?').trimEnd('/').equals(PENDING, ignoreCase = true)) return Pending
            if (text.startsWith(CHAT_PREFIX, ignoreCase = true)) {
                val id = text.substring(CHAT_PREFIX.length).substringBefore('?').substringBefore('#').trim('/')
                if (id.isEmpty()) return null
                val decoded = runCatching { URLDecoder.decode(id, "UTF-8") }.getOrNull() ?: return null
                return if (isValidConversationId(decoded)) Chat(decoded) else null
            }
            return PairLink.parse(text)?.let(::Pair)
        }
    }
}
