package br.com.amberwrite.aistack.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.data.model.Conversation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lista de conversas do desktop, com estado da conexão e contagem de pendências. */
class SessionsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val conversations: List<Conversation> = emptyList(),
        val includeArchived: Boolean = false,
        val loading: Boolean = false,
        val loaded: Boolean = false,
        val error: String? = null,
        val connection: ConnectionState = ConnectionState.Disconnected,
        val pendingCount: Int = 0,
        /** Conversas com pedido de permissão aberto (para destacar na lista). */
        val pendingIds: Set<String> = emptySet()
    )

    val state: StateFlow<UiState> = combine(
        container.sessionsRepo.state,
        container.connectionState,
        container.pendingRepo.items
    ) { s, conn, pending ->
        UiState(
            conversations = s.conversations.sortedByDescending { it.updatedAt },
            includeArchived = s.includeArchived,
            loading = s.loading,
            loaded = s.loaded,
            error = s.error,
            connection = conn,
            pendingCount = pending.sumOf { it.permissions.size },
            pendingIds = pending.filter { it.permissions.isNotEmpty() }.map { it.conversationId }.toSet()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun refresh() {
        viewModelScope.launch { container.sessionsRepo.reload() }
    }

    fun setIncludeArchived(value: Boolean) = container.sessionsRepo.setIncludeArchived(value)

    fun retryConnection() = container.retry()
}
