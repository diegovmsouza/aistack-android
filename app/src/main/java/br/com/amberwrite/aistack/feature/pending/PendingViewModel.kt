package br.com.amberwrite.aistack.feature.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.data.model.PendingConversation
import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.repo.ActionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Caixa de entrada de permissões de todas as conversas (`listPending`). */
class PendingViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val items: List<PendingConversation> = emptyList(),
        val supported: Boolean = true,
        val loading: Boolean = false,
        val answering: Set<String> = emptySet(),
        val error: String? = null,
        val connection: ConnectionState = ConnectionState.Disconnected
    )

    private data class Local(val loading: Boolean = false, val answering: Set<String> = emptySet(), val error: String? = null)

    private val local = MutableStateFlow(Local())

    val state: StateFlow<UiState> = combine(
        container.pendingRepo.items,
        container.pendingRepo.supported,
        local,
        container.connectionState
    ) { items, supported, l, conn ->
        UiState(items.filter { it.permissions.isNotEmpty() }, supported, l.loading, l.answering, l.error, conn)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        reload()
    }

    fun reload() {
        local.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching { container.pendingRepo.reload() }
            local.update { it.copy(loading = false) }
        }
    }

    fun allow(req: PermissionRequest, remember: Boolean = false) = answer(req, PermissionDecision.Allow(remember = remember))

    fun deny(req: PermissionRequest) = answer(req, PermissionDecision.Deny())

    private fun answer(req: PermissionRequest, decision: PermissionDecision) {
        if (req.requestId in local.value.answering) return
        local.update { it.copy(answering = it.answering + req.requestId, error = null) }
        viewModelScope.launch {
            val r = container.pendingRepo.answer(req.conversationId, req.requestId, decision)
            local.update {
                it.copy(answering = it.answering - req.requestId, error = (r as? ActionResult.Failed)?.message)
            }
        }
    }
}
