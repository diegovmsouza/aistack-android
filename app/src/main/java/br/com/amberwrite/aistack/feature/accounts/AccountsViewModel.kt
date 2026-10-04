package br.com.amberwrite.aistack.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.data.model.McpEntry
import br.com.amberwrite.aistack.data.repo.AccountsRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Contas dos provedores e cotas (§4.8). O repositório já se atualiza pelos eventos
 * `accounts-update`/`usage-update`; aqui só agrupamos, ordenamos e mantemos um relógio
 * de 30 s para os textos "renova em…" andarem sozinhos.
 */
class AccountsViewModel(
    private val source: StateFlow<AccountsRepo.State>,
    private val reloader: suspend () -> Unit,
    private val extras: suspend () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickMs: Long = TICK_MS
) : ViewModel() {

    constructor(container: AppContainer) : this(
        source = container.accountsRepo.state,
        reloader = { container.accountsRepo.reload() },
        extras = { container.accountsRepo.loadExtras() }
    )

    data class UiState(
        val groups: List<ProviderGroup> = emptyList(),
        val mcp: List<McpEntry> = emptyList(),
        val desktopVersion: String? = null,
        val loading: Boolean = true,
        val loaded: Boolean = false,
        val refreshing: Boolean = false,
        val error: String? = null,
        val now: Long = 0L
    ) {
        val accountCount: Int get() = groups.sumOf { it.accounts.size }
        val isEmpty: Boolean get() = groups.isEmpty()
        val showSkeleton: Boolean get() = isEmpty && !loaded && error == null
        val showFullError: Boolean get() = isEmpty && error != null && !loading
        val showEmpty: Boolean get() = isEmpty && loaded && error == null
    }

    private val refreshing = MutableStateFlow(false)
    private var job: Job? = null

    private val ticker = flow {
        while (true) {
            emit(clock())
            delay(tickMs)
        }
    }

    val state: StateFlow<UiState> = combine(source, refreshing, ticker) { s, r, now ->
        UiState(
            groups = AccountsLogic.group(s.accounts),
            mcp = s.mcp,
            desktopVersion = s.appInfo?.version,
            loading = s.loading,
            loaded = s.loaded,
            refreshing = r,
            error = s.error,
            now = now
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(now = clock()))

    init {
        load(pull = false)
    }

    /** Recarrega contas e extras (MCP, versão do desktop). */
    fun reload() = load(pull = true)

    private fun load(pull: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            if (pull) refreshing.value = true
            try {
                safely(reloader)
                safely(extras)
            } finally {
                refreshing.value = false
            }
        }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // O repositório já registra o erro no próprio estado.
        }
    }

    companion object {
        const val TICK_MS = 30_000L
    }
}
