package br.com.amberwrite.aistack.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.DirListing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Explorador de pastas do desktop (somente leitura, §4.6). Navega dentro da própria tela
 * (com histórico para o botão voltar), guarda em cache as pastas já vistas e filtra localmente.
 *
 * Sem caminho inicial, abre a pasta do projeto da conversa; sem projeto, a lista de raízes do
 * escopo (`listDir` sem `path`).
 */
class FilesViewModel(
    val conversationId: String,
    initialPath: String?,
    private val lister: suspend (String?) -> DirListing,
    private val projectRoot: () -> String?
) : ViewModel() {

    constructor(container: AppContainer, conversationId: String, path: String?) : this(
        conversationId = conversationId,
        initialPath = path,
        lister = { p -> container.filesRepo.listDir(p) },
        projectRoot = { container.sessionsRepo.find(conversationId)?.projectPath }
    )

    data class UiState(
        /** Pasta atual; `null` = lista de raízes do escopo. */
        val path: String? = null,
        /** Entradas ordenadas (pastas primeiro). */
        val entries: List<DirEntry> = emptyList(),
        /** Entradas depois da busca local. */
        val visible: List<DirEntry> = emptyList(),
        val query: String = "",
        val searchOpen: Boolean = false,
        val truncated: Boolean = false,
        /** Primeira carga desta pasta (sem nada em cache): mostra esqueleto. */
        val loading: Boolean = true,
        /** Atualizando por cima de dados em cache. */
        val refreshing: Boolean = false,
        val error: String? = null,
        val errorKind: FilesErrorKind? = null,
        val crumbs: List<Crumb> = listOf(Crumb("", null, isRoots = true)),
        val roots: List<String> = emptyList(),
        /** +1 ao entrar numa pasta, -1 ao voltar (direção da animação). */
        val direction: Int = 1,
        val canGoBack: Boolean = false
    ) {
        val atRoots: Boolean get() = path == null
        val showSkeleton: Boolean get() = loading && entries.isEmpty() && error == null
        val showFullError: Boolean get() = error != null && entries.isEmpty() && !loading
        val isEmptyFolder: Boolean get() = !loading && error == null && entries.isEmpty()
        val noResults: Boolean get() = entries.isNotEmpty() && visible.isEmpty() && query.isNotBlank()
    }

    private val cache = HashMap<String, DirListing>()
    private val history = ArrayDeque<String?>()
    private var loadJob: Job? = null
    private var rootsJob: Job? = null

    private val start: String? = initialPath?.takeIf { it.isNotBlank() }
        ?: projectRoot()?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(UiState(path = start))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadRoots()
        load(start, direction = 1)
    }

    private fun key(path: String?) = path ?: ROOTS_KEY

    /** Carrega a lista de raízes (para a trilha e para saber até onde dá para subir). */
    private fun loadRoots() {
        if (rootsJob?.isActive == true) return
        rootsJob = viewModelScope.launch {
            try {
                val listing = cache[ROOTS_KEY] ?: lister(null).also { cache[ROOTS_KEY] = it }
                val roots = listing.entries.map { it.name }
                _state.update { s -> s.copy(roots = roots, crumbs = FilesLogic.crumbs(s.path, roots)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sem raízes a trilha usa o caminho absoluto inteiro; nada a mostrar.
            }
        }
    }

    private fun load(path: String?, direction: Int) {
        loadJob?.cancel()
        val cached = cache[key(path)]
        _state.update { s ->
            val entries = cached?.let { FilesLogic.sort(it.entries) } ?: emptyList()
            s.copy(
                path = path,
                entries = entries,
                visible = FilesLogic.filter(entries, if (s.path == path) s.query else ""),
                query = if (s.path == path) s.query else "",
                searchOpen = if (s.path == path) s.searchOpen else false,
                truncated = cached?.truncated ?: false,
                loading = cached == null,
                refreshing = cached != null,
                error = null,
                errorKind = null,
                crumbs = FilesLogic.crumbs(path, s.roots),
                direction = direction,
                canGoBack = history.isNotEmpty()
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val listing = lister(path)
                cache[key(path)] = listing
                if (path == null) {
                    val roots = listing.entries.map { it.name }
                    _state.update { it.copy(roots = roots) }
                }
                _state.update { s ->
                    if (s.path != path) return@update s
                    val entries = FilesLogic.sort(listing.entries)
                    s.copy(
                        entries = entries,
                        visible = FilesLogic.filter(entries, s.query),
                        truncated = listing.truncated,
                        loading = false,
                        refreshing = false,
                        error = null,
                        errorKind = null,
                        crumbs = FilesLogic.crumbs(path, s.roots)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s ->
                    if (s.path != path) s
                    else s.copy(loading = false, refreshing = false, error = e.userMessage, errorKind = FilesLogic.classify(e))
                }
            }
        }
    }

    /** Recarrega a pasta atual ignorando o cache (puxar para atualizar / tentar de novo). */
    fun reload() {
        val s = _state.value
        cache.remove(key(s.path))
        if (s.roots.isEmpty()) loadRoots()
        load(s.path, direction = s.direction)
    }

    /** Entra numa pasta (empilha a atual no histórico). */
    fun open(path: String?) {
        val current = _state.value.path
        if (path == current) return
        history.addLast(current)
        load(path, direction = 1)
    }

    /** Abre uma entrada de pasta da listagem atual. */
    fun openEntry(entry: DirEntry) = open(childPath(entry))

    /** Pula para um item da trilha (conta como voltar se for um ancestral). */
    fun openCrumb(crumb: Crumb) {
        val current = _state.value.path
        if (crumb.path == current) return
        history.addLast(current)
        load(crumb.path, direction = -1)
    }

    /** Sobe um nível no escopo; nas raízes do projeto vai para a lista de raízes. */
    fun goUp() {
        val s = _state.value
        if (s.path == null) return
        history.addLast(s.path)
        load(FilesLogic.parentOf(s.path, s.roots), direction = -1)
    }

    /** Volta no histórico. Devolve `false` se não havia para onde voltar (a tela fecha). */
    fun back(): Boolean {
        if (_state.value.searchOpen) {
            closeSearch()
            return true
        }
        if (history.isEmpty()) return false
        val previous = history.removeLast()
        load(previous, direction = -1)
        return true
    }

    fun openSearch() = _state.update { it.copy(searchOpen = true) }

    fun closeSearch() = _state.update { it.copy(searchOpen = false, query = "", visible = it.entries) }

    fun setQuery(q: String) = _state.update { it.copy(query = q, visible = FilesLogic.filter(it.entries, q)) }

    /** Caminho absoluto de uma entrada da listagem atual. */
    fun childPath(entry: DirEntry): String = FilesLogic.childPath(_state.value.path, entry.name)

    private companion object {
        const val ROOTS_KEY = "\u0000roots"
    }
}
