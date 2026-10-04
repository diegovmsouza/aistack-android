package br.com.amberwrite.aistack.feature.newsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.DirListing
import br.com.amberwrite.aistack.data.model.EntryKind
import br.com.amberwrite.aistack.data.model.ModelInfo
import br.com.amberwrite.aistack.data.model.PermissionMode
import br.com.amberwrite.aistack.data.model.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Resultado de `createConversation` que a tela precisa. */
data class CreatedSession(val id: String, val warning: String?)

/** O que a tela de nova sessão usa do app (permite testar o ViewModel com fakes). */
interface NewSessionDeps {
    suspend fun recentProjects(): List<String>
    /** Caminhos das conversas já conhecidas, da mais recente para a mais antiga. */
    fun conversationPaths(): List<String>
    suspend fun catalog(provider: Provider, refresh: Boolean): List<ModelInfo>
    suspend fun create(
        provider: Provider,
        projectPath: String,
        permissionMode: PermissionMode,
        model: String?,
        effort: String?,
    ): CreatedSession
    suspend fun send(conversationId: String, text: String)
    suspend fun listDir(path: String?): DirListing
}

private class ContainerNewSessionDeps(private val c: AppContainer) : NewSessionDeps {
    override suspend fun recentProjects() = c.sessionsRepo.recentProjects()
    override fun conversationPaths() =
        c.sessionsRepo.state.value.conversations.sortedByDescending { it.updatedAt }.map { it.projectPath }
    override suspend fun catalog(provider: Provider, refresh: Boolean) = c.sessionsRepo.getCatalog(provider, refresh)
    override suspend fun create(
        provider: Provider,
        projectPath: String,
        permissionMode: PermissionMode,
        model: String?,
        effort: String?,
    ): CreatedSession {
        val conv = c.sessionsRepo.createConversation(
            provider = provider,
            projectPath = projectPath,
            permissionMode = permissionMode,
            model = model,
            effort = effort,
        )
        return CreatedSession(conv.id, conv.warning?.takeIf { it.isNotBlank() })
    }
    override suspend fun send(conversationId: String, text: String) = c.chatRepo.send(conversationId, text)
    override suspend fun listDir(path: String?) = c.filesRepo.listDir(path)
}

/**
 * Nova sessão: projeto (recentes ou navegando pelas pastas do desktop), provedor/modelo,
 * esforço, modo de permissão e primeira mensagem.
 *
 * Fluxo de criação: `createConversation` → se o desktop devolveu `warning`, espera o usuário
 * confirmar → `send` da primeira mensagem (se houver) → [UiState.navigateTo] com o id. Se o
 * envio falhar depois da criação, a conversa já existe: o usuário pode tentar de novo ou
 * abrir sem enviar (nunca cria outra).
 */
class NewSessionViewModel(private val deps: NewSessionDeps) : ViewModel() {

    constructor(container: AppContainer) : this(ContainerNewSessionDeps(container))

    sealed interface Phase {
        data object Editing : Phase
        data object Creating : Phase
        /** Conversa criada com aviso do desktop; aguarda «Continuar». */
        data class Warning(val conversationId: String, val message: String) : Phase
        data class Sending(val conversationId: String) : Phase
        /** Conversa criada, mas a primeira mensagem não foi enviada. */
        data class SendFailed(val conversationId: String, val message: String) : Phase
        data class Done(val conversationId: String) : Phase
    }

    data class Browser(
        val path: String? = null,
        val dirs: List<DirEntry> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val truncated: Boolean = false,
    ) {
        val canGoUp: Boolean get() = parentPath(path) != null
    }

    data class UiState(
        val provider: Provider = Provider.CLAUDE,
        val models: List<ModelInfo> = emptyList(),
        val modelsLoading: Boolean = true,
        val modelsError: String? = null,
        val modelId: String? = null,
        val effortIndex: Int = -1,
        val permissionMode: PermissionMode = PermissionMode.ASK,
        val projects: List<String> = emptyList(),
        val projectsLoading: Boolean = true,
        val projectPath: String = "",
        val message: String = "",
        val phase: Phase = Phase.Editing,
        val error: String? = null,
        val browser: Browser? = null,
        /** Id da conversa para abrir; a tela chama [onNavigated] depois de navegar. */
        val navigateTo: String? = null,
    ) {
        val selectedModel: ModelInfo? get() = models.firstOrNull { it.id == modelId }
        val effortLevels: List<String> get() = selectedModel?.efforts.orEmpty()
        val effort: String? get() = effortLevels.getOrNull(effortIndex)
        val pathError: ProjectPathError? get() = validateProjectPath(projectPath)
        val busy: Boolean get() = phase !is Phase.Editing
        val canCreate: Boolean get() = pathError == null && phase is Phase.Editing
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var catalogJob: Job? = null
    private var browseJob: Job? = null
    private var flowJob: Job? = null

    init {
        loadProjects()
        loadCatalog(Provider.CLAUDE, refresh = false)
    }

    fun loadProjects() {
        _state.update { it.copy(projectsLoading = true) }
        viewModelScope.launch {
            val recent = try {
                deps.recentProjects()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            val merged = mergeProjects(recent, deps.conversationPaths())
            _state.update { s ->
                s.copy(
                    projects = merged,
                    projectsLoading = false,
                    projectPath = s.projectPath.ifBlank { merged.firstOrNull().orEmpty() },
                )
            }
        }
    }

    fun setProvider(p: Provider) {
        if (_state.value.busy || p == _state.value.provider) return
        _state.update { it.copy(provider = p, models = emptyList(), modelId = null, effortIndex = -1) }
        loadCatalog(p, refresh = false)
    }

    fun retryCatalog() = loadCatalog(_state.value.provider, refresh = true)

    private fun loadCatalog(p: Provider, refresh: Boolean) {
        catalogJob?.cancel()
        _state.update { it.copy(modelsLoading = true, modelsError = null) }
        catalogJob = viewModelScope.launch {
            try {
                val models = deps.catalog(p, refresh)
                val def = defaultModelOf(models)
                _state.update {
                    if (it.provider != p) it
                    else it.copy(
                        models = models,
                        modelsLoading = false,
                        modelId = def?.id,
                        effortIndex = defaultEffortIndex(def),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { if (it.provider != p) it else it.copy(modelsLoading = false, modelsError = e.userMessage) }
            }
        }
    }

    fun setModel(id: String) = _state.update { s ->
        val m = s.models.firstOrNull { it.id == id } ?: return@update s
        // Mantém o mesmo nível de esforço se o novo modelo também o tiver.
        val keep = s.effort?.let { m.efforts.indexOf(it) }?.takeIf { it >= 0 }
        s.copy(modelId = id, effortIndex = keep ?: defaultEffortIndex(m))
    }

    fun setEffortIndex(i: Int) = _state.update { if (i in it.effortLevels.indices) it.copy(effortIndex = i) else it }

    fun setPermissionMode(m: PermissionMode) = _state.update { it.copy(permissionMode = m) }

    fun setProjectPath(v: String) = _state.update { it.copy(projectPath = v, error = null) }

    fun setMessage(v: String) = _state.update { it.copy(message = v) }

    fun consumeError() = _state.update { it.copy(error = null) }

    // ---- Navegação de pastas ----

    fun openBrowser() {
        val start = _state.value.projectPath.trim().takeIf { validateProjectPath(it) == null }
        browse(start)
    }

    fun browse(path: String?) {
        browseJob?.cancel()
        _state.update { it.copy(browser = Browser(path = path, loading = true)) }
        browseJob = viewModelScope.launch {
            try {
                val listing = deps.listDir(path)
                val dirs = listing.entries
                    .filter { it.kind == EntryKind.DIR && !it.name.startsWith(".") }
                    .sortedBy { it.name.lowercase() }
                _state.update {
                    it.copy(browser = Browser(path = listing.path ?: path, dirs = dirs, loading = false, truncated = listing.truncated))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(browser = Browser(path = path, loading = false, error = e.userMessage)) }
            }
        }
    }

    fun browseInto(name: String) {
        val base = _state.value.browser?.path ?: return
        browse(joinPath(base, name))
    }

    fun browseUp() {
        val parent = parentPath(_state.value.browser?.path) ?: return
        browse(parent)
    }

    fun retryBrowse() = browse(_state.value.browser?.path)

    /** Usa a pasta aberta no navegador como projeto. */
    fun pickBrowsed() {
        val path = _state.value.browser?.path ?: return
        _state.update { it.copy(projectPath = path, browser = null, error = null) }
        browseJob?.cancel()
    }

    fun closeBrowser() {
        browseJob?.cancel()
        _state.update { it.copy(browser = null) }
    }

    // ---- Criação ----

    fun create() {
        val s = _state.value
        if (!s.canCreate) return
        _state.update { it.copy(phase = Phase.Creating, error = null) }
        flowJob = viewModelScope.launch {
            val created = try {
                deps.create(s.provider, s.projectPath.trim(), s.permissionMode, s.modelId, s.effort)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.Editing, error = e.userMessage) }
                return@launch
            }
            val warning = created.warning?.takeIf { it.isNotBlank() }
            if (warning != null) {
                _state.update { it.copy(phase = Phase.Warning(created.id, warning)) }
            } else {
                sendFirstMessage(created.id)
            }
        }
    }

    /** Usuário leu o aviso do desktop e quer seguir. */
    fun continueAfterWarning() {
        val w = _state.value.phase as? Phase.Warning ?: return
        flowJob = viewModelScope.launch { sendFirstMessage(w.conversationId) }
    }

    fun retrySend() {
        val f = _state.value.phase as? Phase.SendFailed ?: return
        flowJob = viewModelScope.launch { sendFirstMessage(f.conversationId) }
    }

    /** Abre a conversa já criada sem enviar a primeira mensagem. */
    fun openWithoutSending() {
        val id = when (val p = _state.value.phase) {
            is Phase.SendFailed -> p.conversationId
            is Phase.Warning -> p.conversationId
            else -> return
        }
        finish(id)
    }

    fun onNavigated() = _state.update { it.copy(navigateTo = null) }

    private suspend fun sendFirstMessage(id: String) {
        val text = _state.value.message.trim()
        if (text.isEmpty()) {
            finish(id)
            return
        }
        _state.update { it.copy(phase = Phase.Sending(id)) }
        try {
            deps.send(id, text)
            finish(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(phase = Phase.SendFailed(id, e.userMessage)) }
        }
    }

    private fun finish(id: String) = _state.update { it.copy(phase = Phase.Done(id), navigateTo = id) }
}
