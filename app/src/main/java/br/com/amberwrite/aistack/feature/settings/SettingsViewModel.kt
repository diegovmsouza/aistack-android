package br.com.amberwrite.aistack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.data.store.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** O que as configurações precisam do app. Separado para testar com fakes na JVM. */
interface SettingsGateway {
    val settings: StateFlow<SettingsStore.Settings>
    val materialYou: StateFlow<Boolean>
    val relayUrl: Flow<String?>
    val paired: Flow<Boolean>
    val connection: StateFlow<ConnectionState>
    val desktopVersion: Flow<String?>

    /** Nome deste aparelho (pode tocar no Keystore: chamar fora da thread principal). */
    suspend fun deviceName(): String

    /** Renomeia e devolve o nome efetivamente salvo. */
    suspend fun rename(name: String): String
    fun setKeepConnected(value: Boolean)
    fun update(transform: (SettingsStore.Settings) -> SettingsStore.Settings)
    fun setMaterialYou(enabled: Boolean)
    suspend fun unpair()

    /** Busca a versão do AiStack no desktop (melhor esforço). */
    suspend fun loadDesktopInfo() {}
}

private class ContainerSettingsGateway(private val c: AppContainer) : SettingsGateway {
    override val settings get() = c.settingsStore.settings
    override val materialYou get() = c.settingsStore.materialYou
    override val relayUrl: Flow<String?> get() = c.pairingStore.link.map { it?.relay }
    override val paired: Flow<Boolean> get() = c.pairingStore.link.map { it != null }
    override val connection get() = c.connectionState
    override val desktopVersion: Flow<String?> get() = c.accountsRepo.state.map { it.appInfo?.version }
    override suspend fun deviceName(): String = withContext(Dispatchers.Default) { c.identity.name }
    override suspend fun rename(name: String): String = withContext(Dispatchers.Default) {
        c.renameDevice(name)
        c.identity.name
    }
    override fun setKeepConnected(value: Boolean) = c.setKeepConnected(value)
    override fun update(transform: (SettingsStore.Settings) -> SettingsStore.Settings) = c.settingsStore.update(transform)
    override fun setMaterialYou(enabled: Boolean) = c.settingsStore.setMaterialYou(enabled)
    override suspend fun unpair() = c.unpair()
    override suspend fun loadDesktopInfo() {
        if (c.accountsRepo.state.value.appInfo == null && c.rpc.connected.value) c.accountsRepo.loadExtras()
    }
}

/** Preferências locais, aparência, nome do aparelho e desparear (§4.9). */
class SettingsViewModel(private val gateway: SettingsGateway) : ViewModel() {

    constructor(container: AppContainer) : this(ContainerSettingsGateway(container) as SettingsGateway)

    data class UiState(
        val settings: SettingsStore.Settings = SettingsStore.Settings(),
        val materialYou: Boolean = false,
        val deviceName: String = "",
        val deviceNameDraft: String = "",
        val nameLoaded: Boolean = false,
        val savingName: Boolean = false,
        val nameSaved: Boolean = false,
        val relayUrl: String? = null,
        val paired: Boolean = false,
        val connection: ConnectionState = ConnectionState.Disconnected,
        val desktopVersion: String? = null,
        val unpairing: Boolean = false
    ) {
        val theme: ThemeMode get() = ThemeMode.from(settings.theme)
        val nameChanged: Boolean get() = nameLoaded && SettingsLogic.nameChanged(deviceName, deviceNameDraft)
    }

    private data class Local(
        val deviceName: String = "",
        val draft: String = "",
        val nameLoaded: Boolean = false,
        val savingName: Boolean = false,
        val nameSaved: Boolean = false,
        val unpairing: Boolean = false
    )

    private data class Link(val relayUrl: String?, val paired: Boolean, val desktopVersion: String?)

    private val local = MutableStateFlow(Local())

    private val link: Flow<Link> = combine(gateway.relayUrl, gateway.paired, gateway.desktopVersion) { r, p, v -> Link(r, p, v) }

    val state: StateFlow<UiState> = combine(
        gateway.settings,
        gateway.materialYou,
        link,
        gateway.connection,
        local
    ) { settings, my, lk, conn, l ->
        UiState(
            settings = settings,
            materialYou = my,
            deviceName = l.deviceName,
            deviceNameDraft = l.draft,
            nameLoaded = l.nameLoaded,
            savingName = l.savingName,
            nameSaved = l.nameSaved,
            relayUrl = lk.relayUrl,
            paired = lk.paired,
            connection = conn,
            desktopVersion = lk.desktopVersion,
            unpairing = l.unpairing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            val name = try {
                gateway.deviceName()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ""
            }
            local.update { it.copy(deviceName = name, draft = if (it.draft.isEmpty()) name else it.draft, nameLoaded = true) }
        }
        viewModelScope.launch {
            try {
                gateway.loadDesktopInfo()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Só informativo.
            }
        }
    }

    fun setKeepConnected(v: Boolean) = gateway.setKeepConnected(v)
    fun setStartAtBoot(v: Boolean) = gateway.update { it.copy(startAtBoot = v) }
    fun setNotifyDone(v: Boolean) = gateway.update { it.copy(notifyDone = v) }
    fun setTheme(mode: ThemeMode) = gateway.update { it.copy(theme = mode.id) }
    fun setMaterialYou(v: Boolean) = gateway.setMaterialYou(v)

    fun setNameDraft(v: String) = local.update { it.copy(draft = v.take(SettingsLogic.NAME_MAX + 8), nameSaved = false) }

    /** Novo nome vale no próximo pareamento (o desktop guarda o nome enviado no `pair`). */
    fun saveName() {
        val l = local.value
        if (l.savingName || !SettingsLogic.nameChanged(l.deviceName, l.draft)) return
        val name = SettingsLogic.sanitizeName(l.draft)
        local.update { it.copy(savingName = true) }
        viewModelScope.launch {
            try {
                val saved = gateway.rename(name)
                local.update { it.copy(deviceName = saved, draft = saved, savingName = false, nameSaved = true) }
            } catch (e: CancellationException) {
                local.update { it.copy(savingName = false) }
                throw e
            } catch (_: Exception) {
                local.update { it.copy(savingName = false) }
            }
        }
    }

    /** Desparear: revoga no desktop (melhor esforço), apaga credenciais e volta ao pareamento. */
    fun unpair(onDone: () -> Unit) {
        if (local.value.unpairing) return
        local.update { it.copy(unpairing = true) }
        viewModelScope.launch {
            try {
                gateway.unpair()
            } catch (e: CancellationException) {
                local.update { it.copy(unpairing = false) }
                throw e
            } catch (_: Exception) {
                // O desparear local é o que importa; o aviso ao desktop é melhor esforço.
            }
            local.update { it.copy(unpairing = false) }
            onDone()
        }
    }
}
