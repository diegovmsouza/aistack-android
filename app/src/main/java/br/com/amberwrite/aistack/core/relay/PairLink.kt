package br.com.amberwrite.aistack.core.relay

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Conteúdo do QR / link de pareamento (`aistack://pair?relay=…&host=…&pk=…&code=…`).
 *
 * @property relay URL base do relay (ex.: `wss://aistack.amberwrite.com.br`).
 * @property host host_id: hex de 64 caracteres do sha256 da pública Ed25519 do desktop.
 * @property pk pública X25519 estática do host (base64url).
 * @property code código de pareamento de uso único (10 min); `null` num link já pareado.
 */
data class PairLink(
    val relay: String,
    val host: String,
    val pk: String,
    val code: String?
) {
    /** Mesma ligação sem o código de uso único (o que é persistido após o pareamento). */
    fun withoutCode(): PairLink = copy(code = null)

    fun toUri(): String = buildString {
        append("aistack://pair?relay=").append(enc(relay))
        append("&host=").append(enc(host))
        append("&pk=").append(enc(pk))
        code?.let { append("&code=").append(enc(it)) }
    }

    companion object {
        fun parse(rawText: String): PairLink? {
            val text = rawText.trim()
            return try {
                if (text.startsWith("aistack://pair") || text.startsWith("http://") || text.startsWith("https://")) {
                    val questionIdx = text.indexOf('?')
                    if (questionIdx < 0) return null
                    val queryString = text.substring(questionIdx + 1).substringBefore('#')
                    val params = parseQueryParams(queryString)
                    val relay = params["relay"]?.takeIf { it.isNotBlank() } ?: return null
                    val host = params["host"]?.takeIf { it.isNotBlank() } ?: return null
                    val pk = params["pk"]?.takeIf { it.isNotBlank() } ?: return null
                    val code = params["code"]?.takeIf { it.isNotBlank() }
                    PairLink(relay = relay, host = host, pk = pk, code = code)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun enc(v: String) = URLEncoder.encode(v, StandardCharsets.UTF_8.name())

        private fun parseQueryParams(query: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            for (pair in query.split('&')) {
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                    val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                    map[key] = value
                }
            }
            return map
        }
    }
}
