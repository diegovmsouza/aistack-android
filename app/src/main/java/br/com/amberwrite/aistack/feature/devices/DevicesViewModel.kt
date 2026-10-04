package br.com.amberwrite.aistack.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.repo.DevicesRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aparelhos pareados com o desktop. A lista se atualiza sozinha pelo evento `devices-changed`;
 * um relógio de 30 s mantém o "último acesso" relativo em dia. O celular só revoga a si mesmo.
 */
class DevicesViewModel(
    private val source: StateFlow<DevicesRepo.State>,
    /** Id deste aparelho; pode tocar no Keystore, então é resolvido fora da thread principal. */
    private val selfId: suspend () -> String?,
    private val connected: StateFlow<Boolean>,
    private val reloader: suspend () -> Unit,
    /** Revoga este aparelho no desktop e apaga as credenciais locais. */
    private val revoker: suspend () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickMs: Long = TICK_MS
) : ViewModel() {

    constructor(container: AppContainer) : this(
        source = container.devicesRepo.state,
        selfId = {
            withContext(Dispatchers.Default) { runCatching { container.devicesRepo.currentDeviceId }.getOrNull() }
        },
        connected = container.rpc.connected,
        reloader = { container.devicesRepo.reload() },
        revoker = { container.unpair() }
    )

    data class UiState(
        val devices: List<DeviceView> = emptyList(),
        val relayState: String? = null,
        val relayUrl: String? = null,
        val relaySessions: Long? = null,
        val relayError: String? = null,
        val loading: Boolean = true,
        val attempted: Boolean = false,
        val refreshing: Boolean = false,
        val error: String? = null,
        val revoking: Boolean = false,
        val revokeError: String? = null,
        val now: Long = 0L
    ) {
        val thisDevice: DeviceView? get() = devices.firstOrNull { it.isThis }
        val others: List<DeviceView> get() = devices.filterNot { it.isThis }
        val showSkeleton: Boolean get() = devices.isEmpty() && error == null && (loading || !attempted)
        val showFullError: Boolean get() = devices.isEmpty() && error != null && !loading
        val showEmpty: Boolean get() = devices.isEmpty() && error == null && attempted && !loading
    }

    private data class Local(
        val attempted: Boolean = false,
        val refreshing: Boolean = false,
        val revoking: Boolean = false,
        val revokeError: String? = null
    )

    private val local = MutableStateFlow(Local())
    private val self = MutableStateFlow<String?>(null)
    private var job: Job? = null
    private var revokeJob: Job? = null

    private val ticker = flow {
        while (true) {
            emit(clock())
            delay(tickMs)
        }
    }

    val state: StateFlow<UiState> = combine(source, local, connected, self, ticker) { s, l, conn, me, now ->
        UiState(
            devices = DevicesLogic.order(s.devices, me, now, conn),
            relayState = s.relay?.state,
            relayUrl = s.relay?.relayUrl,
            relaySessions = s.relay?.sessions,
            relayError = s.relay?.error,
            loading = s.loading,
            attempted = l.attempted,
            refreshing = l.refreshing,
            error = s.error,
            revoking = l.revoking,
            revokeError = l.revokeError,
            now = now
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(now = clock()))

    init {
        viewModelScope.launch {
            self.value = try {
                selfId()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        load(pull = false)
    }

    fun reload() = load(pull = true)

    private fun load(pull: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            if (pull) local.update { it.copy(refreshing = true) }
            try {
                reloader()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // O repositório guarda o erro no próprio estado.
            } finally {
                local.update { it.copy(attempted = true, refreshing = false) }
            }
        }
    }

    /** Revoga este aparelho. Ao terminar, as credenciais somem e a navegação volta ao pareamento. */
    fun revokeSelf(onDone: () -> Unit = {}) {
        if (local.value.revoking) return
        local.update { it.copy(revoking = true, revokeError = null) }
        revokeJob = viewModelScope.launch {
            try {
                revoker()
                local.update { it.copy(revoking = false) }
                onDone()
            } catch (e: CancellationException) {
                local.update { it.copy(revoking = false) }
                throw e
            } catch (e: Exception) {
                local.update { it.copy(revoking = false, revokeError = e.userMessage) }
            }
        }
    }

    fun dismissRevokeError() = local.update { it.copy(revokeError = null) }

    companion object {
        const val TICK_MS = 30_000L
    }
}
