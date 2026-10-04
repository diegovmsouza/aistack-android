package br.com.amberwrite.aistack.data.repo

import br.com.amberwrite.aistack.core.rpc.EngineEvent
import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.core.rpc.RpcCaller
import br.com.amberwrite.aistack.core.rpc.RpcException
import br.com.amberwrite.aistack.core.rpc.endsTurn
import br.com.amberwrite.aistack.core.rpc.isUnknownMethod
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.PendingConversation
import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pendências de todas as conversas: pedidos de permissão/perguntas e conversas ocupadas.
 *
 * A fonte é `listPending` (contrato v2). Em host antigo o método não existe: [supported] vira
 * falso e a lista passa a ser montada só pelos eventos ao vivo (`subscribe` all/summary).
 */
class PendingRepo(
    private val rpc: RpcCaller,
    /** Título/projeto/provedor de uma conversa conhecida (para entradas criadas por evento). */
    private val describe: (String) -> Triple<String, String, Provider>? = { null }
) {
    private val _items = MutableStateFlow<List<PendingConversation>>(emptyList())
    val items: StateFlow<List<PendingConversation>> = _items.asStateFlow()

    private val _supported = MutableStateFlow(true)
    val supported: StateFlow<Boolean> = _supported.asStateFlow()

    /** Todos os pedidos abertos, do mais antigo para o mais novo. */
    val allRequests: List<PermissionRequest>
        get() = _items.value.flatMap { it.permissions }

    suspend fun reload() {
        if (!_supported.value) return
        try {
            val list = PendingConversation.parseList(rpc.call("listPending"))
            _items.value = list
        } catch (e: RpcException) {
            if (e.isUnknownMethod) _supported.value = false
        }
    }

    /** Reinício de sessão do túnel: um host novo pode ter sido instalado. */
    fun resetSupport() {
        _supported.value = true
    }

    fun clear() {
        _items.value = emptyList()
    }

    fun onEvent(ev: HostEvent) {
        if (ev !is HostEvent.Conv) return
        val id = ev.conversationId
        when (val e = ev.event) {
            is EngineEvent.PermissionRequested -> upsert(id) { pc ->
                if (pc.permissions.any { it.requestId == e.request.requestId }) pc
                else pc.copy(permissions = pc.permissions + e.request, busy = true)
            }
            is EngineEvent.PermissionCancelled -> remove(id, e.requestId)
            is EngineEvent.TurnStarted -> upsert(id) { it.copy(busy = true, turn = ev.turn ?: it.turn) }
            else -> if (e.endsTurn) {
                // O host limpa as pendências no fim do turno.
                _items.update { list -> list.filterNot { it.conversationId == id } }
            }
        }
    }

    /**
     * Responde um pedido. Sucesso só depois do `rpcResult` ok; "pedido desconhecido" vira
     * [ActionResult.AlreadyResolved] e o cartão some sem alarde.
     */
    suspend fun answer(conversationId: String, requestId: String, decision: PermissionDecision): ActionResult =
        try {
            rpc.call(
                "answerPermission",
                params("id" to conversationId, "requestId" to requestId, "decision" to decision.toParams())
            )
            remove(conversationId, requestId)
            ActionResult.Ok
        } catch (e: RpcException) {
            if (e.message?.startsWith(RpcException.PREFIX_UNKNOWN_PERMISSION) == true) {
                remove(conversationId, requestId)
                ActionResult.AlreadyResolved
            } else ActionResult.Failed(e.userMessage)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Failed(e.userMessage)
        }

    fun find(conversationId: String, requestId: String): PermissionRequest? =
        _items.value.firstOrNull { it.conversationId == conversationId }
            ?.permissions?.firstOrNull { it.requestId == requestId }

    private fun remove(conversationId: String, requestId: String) {
        _items.update { list ->
            list.map { pc ->
                if (pc.conversationId == conversationId) pc.copy(permissions = pc.permissions.filterNot { it.requestId == requestId })
                else pc
            }.filter { it.busy || it.permissions.isNotEmpty() }
        }
    }

    private fun upsert(id: String, f: (PendingConversation) -> PendingConversation) {
        _items.update { list ->
            val idx = list.indexOfFirst { it.conversationId == id }
            if (idx >= 0) list.toMutableList().also { it[idx] = f(it[idx]) }
            else {
                val d = describe(id)
                list + f(
                    PendingConversation(
                        conversationId = id,
                        title = d?.first ?: "Conversa",
                        projectPath = d?.second ?: "",
                        provider = d?.third ?: Provider.fromId(null),
                        busy = false,
                        turn = null,
                        lastEventAt = System.currentTimeMillis(),
                        permissions = emptyList()
                    )
                )
            }
        }
    }
}
