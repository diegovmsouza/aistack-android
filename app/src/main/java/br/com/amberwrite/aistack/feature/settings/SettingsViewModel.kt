package br.com.amberwrite.aistack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.relay.PairLink
import br.com.amberwrite.aistack.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Preferências locais do app, nome do aparelho e desparear. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val settings: SettingsStore.Settings = SettingsStore.Settings(),
        val deviceName: String = "",
        val deviceNameDraft: String = "",
        val link: PairLink? = null,
        val connection: ConnectionState = ConnectionState.Disconnected,
        val unpairing: Boolean = false
    ) {
        val nameChanged: Boolean get() = deviceNameDraft.isNotBlank() && deviceNameDraft.trim() != deviceName
    }

    private data class Local(val deviceName: String = "", val draft: String = "", val unpairing: Boolean = false)

    private val local = MutableStateFlow(Local())

    val state: StateFlow<UiState> = combine(
        container.settingsStore.settings,
        container.pairingStore.link,
        container.connectionState,
        local
    ) { settings, link, conn, l ->
        UiState(settings, l.deviceName, l.draft, link, conn, l.unpairing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            // A identidade pode tocar no Keystore: fora da thread principal.
            val name = withContext(Dispatchers.Default) { container.identity.name }
            local.update { it.copy(deviceName = name, draft = name) }
        }
    }

    fun setKeepConnected(v: Boolean) = container.setKeepConnected(v)
    fun setStartAtBoot(v: Boolean) = container.settingsStore.update { it.copy(startAtBoot = v) }
    fun setNotifyDone(v: Boolean) = container.settingsStore.update { it.copy(notifyDone = v) }
    fun setTheme(v: String) = container.settingsStore.update { it.copy(theme = v) }

    fun setNameDraft(v: String) = local.update { it.copy(draft = v) }

    /** Novo nome vale no próximo pareamento (o desktop guarda o nome enviado no `pair`). */
    fun saveName() {
        val name = local.value.draft.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.Default) {
                container.renameDevice(name)
                container.identity.name
            }
            local.update { it.copy(deviceName = saved, draft = saved) }
        }
    }

    fun unpair(onDone: () -> Unit) {
        if (local.value.unpairing) return
        local.update { it.copy(unpairing = true) }
        viewModelScope.launch {
            runCatching { container.unpair() }
            local.update { it.copy(unpairing = false) }
            onDone()
        }
    }
}
