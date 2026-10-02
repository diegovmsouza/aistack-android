package br.com.amberwrite.aistack.relay

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class PairLink(
    val relay: String,  // ex: wss://aistack.amberwrite.com.br
    val host: String,   // host_id (hex de 64 chars do sha256 da pública Ed25519)
    // Chave X25519 estática do host (base64url). Informativa: o túnel usa chaves efêmeras e a
    // identidade do host é fixada por `host` (sha256 da Ed25519, conferida no hostAuth).
    val pk: String,
    val code: String?   // código de pareamento de uso único (10 min)
) {
    /** Impressão digital curta do host para o usuário conferir com a tela do computador (32 hex, grupos de 4). */
    fun fingerprint(): String = host.lowercase().take(32).chunked(4).joinToString(" ")

    /** Servidor do relay sem esquema nem caminho, para mostrar na confirmação. */
    fun relayHost(): String = try {
        java.net.URI(relay).host ?: relay
    } catch (e: Exception) {
        relay
    }

    companion object {
        fun parse(rawText: String): PairLink? {
            val text = rawText.trim()
            return try {
                if (text.startsWith("aistack://pair") || text.startsWith("http://") || text.startsWith("https://")) {
                    val questionIdx = text.indexOf('?')
                    if (questionIdx < 0) return null
                    val queryString = text.substring(questionIdx + 1)
                    val params = parseQueryParams(queryString)
                    val relay = params["relay"] ?: return null
                    // Só TLS: um relay em ws:// deixaria o túnel (e o pareamento) sem proteção de transporte.
                    if (!relay.startsWith("wss://", ignoreCase = true)) return null
                    val host = params["host"] ?: return null
                    val pk = params["pk"] ?: return null
                    val code = params["code"]
                    PairLink(relay = relay, host = host, pk = pk, code = code)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun parseQueryParams(query: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            val pairs = query.split('&')
            for (pair in pairs) {
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
