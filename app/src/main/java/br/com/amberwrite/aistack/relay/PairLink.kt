package br.com.amberwrite.aistack.relay

import android.net.Uri

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
                val uri = Uri.parse(text)
                if (uri.scheme == "aistack" && uri.host == "pair") {
                    val relay = uri.getQueryParameter("relay") ?: return null
                    val host = uri.getQueryParameter("host") ?: return null
                    val pk = uri.getQueryParameter("pk") ?: return null
                    val code = uri.getQueryParameter("code")
                    PairLink(relay, host, pk, code)
                } else if (text.startsWith("http://") || text.startsWith("https://")) {
                    val relay = uri.getQueryParameter("relay") ?: return null
                    val host = uri.getQueryParameter("host") ?: return null
                    val pk = uri.getQueryParameter("pk") ?: return null
                    val code = uri.getQueryParameter("code")
                    PairLink(relay, host, pk, code)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
