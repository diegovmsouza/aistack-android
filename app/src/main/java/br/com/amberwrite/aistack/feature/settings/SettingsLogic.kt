package br.com.amberwrite.aistack.feature.settings

/** Modos de tema persistidos em `SettingsStore.Settings.theme`. */
enum class ThemeMode(val id: String) {
    System("system"), Light("light"), Dark("dark");

    companion object {
        fun from(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}

/** Atalhos para os canais de notificação do app (ids de `NotificationChannels`). */
enum class ChannelShortcut(val channelId: String) {
    Pending("aistack_pending"),
    Progress("aistack_progress"),
    Done("aistack_done"),
    Connection("aistack_connection")
}

/** Regras puras das configurações (testáveis na JVM). */
object SettingsLogic {

    const val NAME_MAX = 40

    /** Normaliza o nome digitado: sem espaços duplicados nem quebras, até [NAME_MAX] caracteres. */
    fun sanitizeName(raw: String): String =
        raw.replace(Regex("\\s+"), " ").trim().take(NAME_MAX).trim()

    /** O rascunho vale ser salvo: não vazio e diferente do nome atual. */
    fun nameChanged(current: String, draft: String): Boolean {
        val clean = sanitizeName(draft)
        return clean.isNotEmpty() && clean != current.trim()
    }

    /** Host legível da URL do relay (para o resumo), ou a própria URL se não der para extrair. */
    fun relayHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val noScheme = url.substringAfter("://", url)
        return noScheme.substringBefore('/').substringBefore('?').ifBlank { url }
    }

    /** Material You só existe a partir do Android 12 (API 31). */
    fun materialYouSupported(sdkInt: Int): Boolean = sdkInt >= 31
}
