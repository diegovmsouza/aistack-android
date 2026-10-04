package br.com.amberwrite.aistack.navigation

import android.net.Uri

/** Rotas (strings) do NavHost. Argumentos de caminho/consulta são codificados com [Uri.encode]. */
object Routes {
    const val PAIR = "pair"
    const val SESSIONS = "sessions"
    const val NEW_SESSION = "newSession"
    const val CHAT = "chat/{id}"
    const val FILES = "files/{convId}?path={path}"
    const val FILE_VIEW = "fileView/{convId}?path={path}"
    const val PENDING = "pending"
    const val ACCOUNTS = "accounts"
    const val SETTINGS = "settings"
    const val DEVICES = "devices"
    const val DESIGN_CATALOG = "designCatalog"

    fun chat(id: String) = "chat/${Uri.encode(id)}"

    fun files(convId: String, path: String? = null): String =
        "files/${Uri.encode(convId)}" + (path?.let { "?path=${Uri.encode(it)}" } ?: "")

    fun fileView(convId: String, path: String) = "fileView/${Uri.encode(convId)}?path=${Uri.encode(path)}"
}
