package br.com.amberwrite.aistack.relay

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class PairLink(
    val relay: String,  // ex: wss://aistack.amberwrite.com.br
    val host: String,   // host_id (hex de 64 chars do sha256 da pública Ed25519)
    val pk: String,     // chave pública X25519 estática do host (base64url)
    val code: String?   // código de pareamento de uso único (10 min)
) {
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
