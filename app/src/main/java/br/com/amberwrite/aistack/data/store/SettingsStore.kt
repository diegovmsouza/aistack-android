package br.com.amberwrite.aistack.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferências locais do app (não sensíveis). */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Settings(
        /** Liga o serviço de conexão ao iniciar o aparelho. */
        val startAtBoot: Boolean = false,
        /** Mantém a conexão em segundo plano (serviço em primeiro plano). */
        val keepConnected: Boolean = true,
        /** Notifica quando um turno termina com o app em segundo plano. */
        val notifyDone: Boolean = true,
        /** Tema: "system", "dark" ou "light". */
        val theme: String = "system"
    )

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putBoolean(KEY_BOOT, next.startAtBoot)
            .putBoolean(KEY_KEEP, next.keepConnected)
            .putBoolean(KEY_DONE, next.notifyDone)
            .putString(KEY_THEME, next.theme)
            .apply()
        _settings.value = next
    }

    private val _materialYou = MutableStateFlow(prefs.getBoolean(KEY_MATERIAL_YOU, false))

    /** Cores dinâmicas do Material You (Android 12+). Desligado por padrão. (F4, aditivo) */
    val materialYou: StateFlow<Boolean> = _materialYou.asStateFlow()

    /** Liga/desliga as cores dinâmicas do Material You e persiste. (F4, aditivo) */
    fun setMaterialYou(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MATERIAL_YOU, enabled).apply()
        _materialYou.value = enabled
    }

    /** O usuário dispensou a explicação das notificações; não perguntar de novo. */
    var notificationRationaleDismissed: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_RATIONALE, false)
        set(value) { prefs.edit().putBoolean(KEY_NOTIF_RATIONALE, value).apply() }

    private fun load() = Settings(
        startAtBoot = prefs.getBoolean(KEY_BOOT, false),
        keepConnected = prefs.getBoolean(KEY_KEEP, true),
        notifyDone = prefs.getBoolean(KEY_DONE, true),
        theme = prefs.getString(KEY_THEME, "system") ?: "system"
    )

    companion object {
        private const val PREFS = "aistack_settings"
        private const val KEY_BOOT = "start_at_boot"
        private const val KEY_KEEP = "keep_connected"
        private const val KEY_DONE = "notify_done"
        private const val KEY_THEME = "theme"
        private const val KEY_MATERIAL_YOU = "material_you"
        private const val KEY_NOTIF_RATIONALE = "notif_rationale_dismissed"
    }
}
