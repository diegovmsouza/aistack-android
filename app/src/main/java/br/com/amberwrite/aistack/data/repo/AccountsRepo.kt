package br.com.amberwrite.aistack.data.repo

import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.core.rpc.RpcCaller
import br.com.amberwrite.aistack.core.rpc.asObj
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.AccountStatus
import br.com.amberwrite.aistack.data.model.HostAppInfo
import br.com.amberwrite.aistack.data.model.McpEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Contas e cotas dos provedores, MCP e versão do desktop (somente leitura). */
class AccountsRepo(
    private val rpc: RpcCaller,
    private val scope: CoroutineScope
) {
    data class State(
        val accounts: List<AccountStatus> = emptyList(),
        val mcp: List<McpEntry> = emptyList(),
        val appInfo: HostAppInfo? = null,
        val loading: Boolean = false,
        val loaded: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun reload() {
        _state.update { it.copy(loading = true) }
        try {
            val accounts = AccountStatus.parseList(rpc.call("listAccounts"))
            _state.update { it.copy(accounts = accounts, loading = false, loaded = true, error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.userMessage) }
        }
    }

    suspend fun loadExtras() {
        runCatching { McpEntry.parseList(rpc.call("listMcp")) }.onSuccess { list -> _state.update { it.copy(mcp = list) } }
        runCatching { rpc.call("appInfo").asObj()?.let(HostAppInfo::parse) }
            .onSuccess { info -> _state.update { it.copy(appInfo = info) } }
    }

    fun clear() {
        _state.value = State()
    }

    fun onEvent(ev: HostEvent) {
        when (ev) {
            is HostEvent.AccountsUpdate -> {
                val list = ev.accounts
                if (list == null) scope.launch { reload() }
                else _state.update { it.copy(accounts = list, loaded = true) }
            }
            is HostEvent.UsageUpdate -> if (_state.value.loaded) scope.launch { reload() }
            is HostEvent.McpUpdate -> if (_state.value.mcp.isNotEmpty()) scope.launch { loadExtras() }
            else -> Unit
        }
    }
}
