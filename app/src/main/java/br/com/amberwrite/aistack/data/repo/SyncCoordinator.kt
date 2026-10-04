package br.com.amberwrite.aistack.data.repo

import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.core.rpc.RpcCaller
import br.com.amberwrite.aistack.core.rpc.RpcException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Único coletor de [RpcCaller.events]: distribui cada evento aos repositórios na ordem de
 * chegada e refaz a sincronização a cada conexão (ou `resync`):
 *
 * 1. `subscribe {"conversations":"all","level":"summary"}` (primeiro: "all" apaga ajustes);
 * 2. `listPending` e `listConversations` em paralelo;
 * 3. as conversas abertas voltam a `full` e recarregam a página.
 */
class SyncCoordinator(
    private val rpc: RpcCaller,
    private val scope: CoroutineScope,
    private val sessions: SessionsRepo,
    private val chat: ChatRepo,
    private val pending: PendingRepo,
    private val accounts: AccountsRepo,
    private val devices: DevicesRepo
) {
    private var collectJob: Job? = null
    private var syncJob: Job? = null

    fun start() {
        if (collectJob != null) return
        collectJob = scope.launch {
            rpc.events.collect { ev -> dispatch(ev) }
        }
        if (rpc.connected.value) resync(newSession = true)
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        syncJob?.cancel()
    }

    internal fun dispatch(ev: HostEvent) {
        when (ev) {
            is HostEvent.Connected -> resync(newSession = true)
            is HostEvent.Resync -> resync(newSession = false)
            else -> Unit
        }
        chat.onEvent(ev)
        pending.onEvent(ev)
        sessions.onEvent(ev)
        accounts.onEvent(ev)
        devices.onEvent(ev)
    }

    private fun resync(newSession: Boolean) {
        syncJob?.cancel()
        syncJob = scope.launch {
            if (newSession) pending.resetSupport()
            try {
                rpc.call("subscribe", params("conversations" to "all", "level" to "summary"))
            } catch (e: RpcException) {
                // Host antigo: tudo chega como `full`. Sem túnel: a próxima conexão refaz.
                if (e.kind == RpcException.Kind.DISCONNECTED) return@launch
            }
            coroutineScope {
                launch { pending.reload() }
                launch { sessions.reload() }
            }
            chat.reattachAll()
        }
    }
}
