package br.com.amberwrite.aistack.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.PendingConversation
import br.com.amberwrite.aistack.data.repo.SessionsRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** O que a lista de conversas usa do app (permite testar o ViewModel com fakes). */
interface SessionsDeps {
    val sessions: StateFlow<SessionsRepo.State>
    val connection: StateFlow<ConnectionState>
    val pending: StateFlow<List<PendingConversation>>
    val previews: StateFlow<Map<String, String>>
    suspend fun reload()
    suspend fun rename(id: String, title: String)
    suspend fun archive(id: String, archived: Boolean)
    suspend fun delete(id: String)
    fun setIncludeArchived(value: Boolean)
    fun retryConnection()
    fun now(): Long = System.currentTimeMillis()
    val zone: ZoneId get() = ZoneId.systemDefault()
}

private class ContainerSessionsDeps(private val c: AppContainer) : SessionsDeps {
    init {
        SessionPreviews.attach(c.rpc.events, c.appScope)
    }

    override val sessions get() = c.sessionsRepo.state
    override val connection get() = c.connectionState
    override val pending get() = c.pendingRepo.items
    override val previews get() = SessionPreviews.previews
    override suspend fun reload() = c.sessionsRepo.reload()
    override suspend fun rename(id: String, title: String) = c.sessionsRepo.rename(id, title)
    override suspend fun archive(id: String, archived: Boolean) = c.sessionsRepo.archive(id, archived)
    override suspend fun delete(id: String) = c.sessionsRepo.delete(id)
    override fun setIncludeArchived(value: Boolean) = c.sessionsRepo.setIncludeArchived(value)
    override fun retryConnection() = c.retry()
}

/**
 * Lista de conversas do desktop: busca, filtro por projeto, grupos de data, status ao vivo
 * (pendências) e ações de arquivar/renomear. A lista em si vem do [SessionsRepo], que já
 * recarrega em `conversations-changed` e em resync.
 */
class SessionsViewModel(private val deps: SessionsDeps) : ViewModel() {

    constructor(container: AppContainer) : this(ContainerSessionsDeps(container))

    /** Aviso pós-arquivamento com opção de desfazer. */
    data class ArchivedNotice(val id: String, val title: String, val archived: Boolean)

    /** Resultado de uma ação em lote (seleção múltipla). */
    data class BulkNotice(val kind: Kind, val count: Int) {
        enum class Kind { Archived, Unarchived, Deleted }
    }

    data class UiState(
        val sections: List<SessionSection> = emptyList(),
        val projects: List<String> = emptyList(),
        val query: String = "",
        val project: String? = null,
        val includeArchived: Boolean = false,
        /** Primeira carga ainda não terminou (mostra esqueleto). */
        val initialLoading: Boolean = true,
        val refreshing: Boolean = false,
        val loaded: Boolean = false,
        val error: String? = null,
        val connection: ConnectionState = ConnectionState.Disconnected,
        val pendingCount: Int = 0,
        /** Total de conversas antes de busca/filtro (distingue "vazio" de "sem resultados"). */
        val totalCount: Int = 0,
        val actionError: String? = null,
        val archivedNotice: ArchivedNotice? = null,
        val bulkNotice: BulkNotice? = null,
        /** Conversas marcadas no modo de seleção (vazio = fora do modo). */
        val selection: Set<String> = emptySet(),
        /** Toda a seleção já está arquivada: a ação em lote vira "desarquivar". */
        val selectionArchived: Boolean = false,
        val content: ListContent = ListContent.Loading
    ) {
        val isFiltering: Boolean get() = query.isNotBlank() || project != null
        val isEmpty: Boolean get() = sections.isEmpty()
    }

    private data class Local(
        val query: String = "",
        val project: String? = null,
        val refreshing: Boolean = false,
        val hidden: Set<String> = emptySet(),
        val actionError: String? = null,
        val notice: ArchivedNotice? = null,
        val bulk: BulkNotice? = null,
        val selection: Set<String> = emptySet(),
        val tick: Long = 0
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<UiState> = combine(
        deps.sessions,
        deps.connection,
        deps.pending,
        deps.previews,
        local
    ) { s, conn, pending, previews, l ->
        val visible = s.conversations.filterNot { it.id in l.hidden }
        val selected = visible.filter { it.id in l.selection }
        val projects = projectsOf(visible)
        val project = l.project?.takeIf { it in projects }
        UiState(
            sections = buildSessionSections(visible, pending, previews, l.query, project, deps.now(), deps.zone),
            projects = projects,
            query = l.query,
            project = project,
            includeArchived = s.includeArchived,
            initialLoading = !s.loaded && s.error == null,
            refreshing = l.refreshing,
            loaded = s.loaded,
            error = s.error,
            connection = conn,
            pendingCount = pending.sumOf { it.permissions.size },
            totalCount = visible.size,
            actionError = l.actionError,
            archivedNotice = l.notice,
            bulkNotice = l.bulk,
            selection = selected.mapTo(LinkedHashSet()) { it.id },
            selectionArchived = selected.isNotEmpty() && selected.all { it.archived }
        ).let { ui ->
            ui.copy(content = listContentOf(ui.totalCount, ui.sections.sumOf { it.items.size }, s.loaded, s.error, conn))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setQuery(q: String) = local.update { it.copy(query = q) }

    fun setProject(path: String?) = local.update { it.copy(project = path) }

    fun setIncludeArchived(value: Boolean) = deps.setIncludeArchived(value)

    /** Recalcula os grupos de data (ex.: a tela voltou a ficar visível depois da meia-noite). */
    fun touch() = local.update { it.copy(tick = it.tick + 1) }

    /** Puxar para atualizar. Também tenta reconectar se estiver sem conexão. */
    fun refresh() {
        if (local.value.refreshing) return
        local.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                if (deps.connection.value !is ConnectionState.Online) deps.retryConnection()
                deps.reload()
            } finally {
                local.update { it.copy(refreshing = false, tick = it.tick + 1) }
            }
        }
    }

    fun retryConnection() = deps.retryConnection()

    fun rename(id: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                deps.rename(id, clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(actionError = e.userMessage) }
            }
        }
    }

    /**
     * Arquiva (ou desarquiva) [id]. Fora do modo "arquivadas" a linha some na hora (otimista) e
     * volta se o desktop recusar.
     */
    fun archive(id: String, archived: Boolean = true) {
        val conv = deps.sessions.value.conversations.firstOrNull { it.id == id } ?: return
        if (!archiving.add(id)) return // um pedido por conversa de cada vez
        val hide = archived && !deps.sessions.value.includeArchived
        if (hide) local.update { it.copy(hidden = it.hidden + id) }
        viewModelScope.launch {
            try {
                deps.archive(id, archived)
                local.update {
                    it.copy(hidden = it.hidden - id, notice = ArchivedNotice(id, conv.displayTitle, archived))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(hidden = it.hidden - id, actionError = e.userMessage) }
            } finally {
                archiving.remove(id)
            }
        }
    }

    private val archiving = mutableSetOf<String>()

    /** Desfaz o último arquivamento avisado. */
    fun undoArchive() {
        val notice = local.value.notice ?: return
        local.update { it.copy(notice = null) }
        viewModelScope.launch {
            try {
                deps.archive(notice.id, !notice.archived)
                deps.reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(actionError = e.userMessage) }
            }
        }
    }

    fun consumeNotice() = local.update { it.copy(notice = null) }

    fun consumeBulkNotice() = local.update { it.copy(bulk = null) }

    /** Toque longo: entra no modo de seleção (ou alterna a conversa, se já estiver nele). */
    fun toggleSelection(id: String) = local.update {
        it.copy(selection = if (id in it.selection) it.selection - id else it.selection + id)
    }

    fun clearSelection() = local.update { it.copy(selection = emptySet()) }

    /** Arquiva a seleção; se ela toda já estiver arquivada, desarquiva. */
    fun archiveSelection() {
        val ui = state.value
        val ids = ui.selection.toList().takeIf { it.isNotEmpty() } ?: return
        val archived = !ui.selectionArchived
        val hide = archived && !deps.sessions.value.includeArchived
        local.update { it.copy(selection = emptySet(), hidden = if (hide) it.hidden + ids else it.hidden) }
        runBulk(ids, if (archived) BulkNotice.Kind.Archived else BulkNotice.Kind.Unarchived) { deps.archive(it, archived) }
    }

    /** Exclui a seleção no desktop (o diálogo de confirmação fica na tela). */
    fun deleteSelection() {
        val ids = state.value.selection.toList().takeIf { it.isNotEmpty() } ?: return
        local.update { it.copy(selection = emptySet(), hidden = it.hidden + ids) }
        runBulk(ids, BulkNotice.Kind.Deleted) { deps.delete(it) }
    }

    private fun runBulk(ids: List<String>, kind: BulkNotice.Kind, action: suspend (String) -> Unit) {
        viewModelScope.launch {
            var done = 0
            var error: String? = null
            for (id in ids) {
                try {
                    action(id)
                    done++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.userMessage
                }
            }
            local.update {
                it.copy(
                    hidden = it.hidden - ids.toSet(),
                    bulk = if (done > 0) BulkNotice(kind, done) else it.bulk,
                    actionError = error ?: it.actionError,
                )
            }
            if (error != null) runCatching { deps.reload() }
        }
    }

    fun consumeActionError() = local.update { it.copy(actionError = null) }
}
