package br.com.amberwrite.aistack.data.repo

import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.core.rpc.RpcCaller
import br.com.amberwrite.aistack.core.rpc.asObj
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.HostRelayStatus
import br.com.amberwrite.aistack.data.model.PairedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Aparelhos pareados ao desktop e estado do relay no host. */
class DevicesRepo(
    private val rpc: RpcCaller,
    /** Id deste aparelho (só ele pode ser revogado pelo celular). */
    private val selfId: () -> String
) {
    data class State(
        val devices: List<PairedDevice> = emptyList(),
        val relay: HostRelayStatus? = null,
        val loading: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val currentDeviceId: String get() = selfId()

    suspend fun reload() {
        _state.update { it.copy(loading = true) }
        try {
            val devices = PairedDevice.parseList(rpc.call("listDevices"))
            val relay = runCatching { rpc.call("relayStatus").asObj()?.let(HostRelayStatus::parse) }.getOrNull()
            _state.update { it.copy(devices = devices, relay = relay ?: it.relay, loading = false, error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.userMessage) }
        }
    }

    fun onEvent(ev: HostEvent) {
        when (ev) {
            is HostEvent.DevicesChanged -> _state.update { it.copy(devices = ev.devices) }
            is HostEvent.RelayStatus -> _state.update { s ->
                s.copy(relay = (s.relay ?: HostRelayStatus(ev.state ?: "?", null, null, null, null))
                    .copy(state = ev.state ?: s.relay?.state ?: "?", error = ev.error))
            }
            else -> Unit
        }
    }

    /** Revoga este aparelho no desktop. O host fecha o túnel com `reason:"revoked"`. */
    suspend fun revokeSelf() {
        rpc.call("revokeDevice", params("id" to selfId()))
    }

    fun clear() {
        _state.value = State()
    }
}
