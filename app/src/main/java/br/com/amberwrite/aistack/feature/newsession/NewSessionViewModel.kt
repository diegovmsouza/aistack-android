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
import br.com.amberwrite.aistack.data.repo.HomeAccess
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
        extraDirs: List<String>,
    ): CreatedSession
    suspend fun send(conversationId: String, text: String)
    suspend fun listDir(path: String?): DirListing
    suspend fun homeAccess(): HomeAccess?
    suspend fun setHomeAccess(enabled: Boolean): HomeAccess
    suspend fun createDirectory(path: String): String
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
        extraDirs: List<String>,
    ): CreatedSession {
        val conv = c.sessionsRepo.createConversation(
            provider = provider,
            projectPath = projectPath,
            permissionMode = permissionMode,
            model = model,
            effort = effort,
            extraDirs = extraDirs,
        )
        return CreatedSession(conv.id, conv.warning?.takeIf { it.isNotBlank() })
    }
    override suspend fun send(conversationId: String, text: String) = c.chatRepo.send(conversationId, text)
    override suspend fun listDir(path: String?) = c.filesRepo.listDir(path)
    override suspend fun homeAccess() = c.filesRepo.homeAccess()
    override suspend fun setHomeAccess(enabled: Boolean) = c.filesRepo.setHomeAccess(enabled)
    override suspend fun createDirectory(path: String) = c.filesRepo.createDirectory(path)
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

    /** Para onde vai a pasta escolhida no navegador. */
    enum class BrowseTarget { Project, Extra }

    data class Browser(
        val path: String? = null,
        val dirs: List<DirEntry> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val truncated: Boolean = false,
        val target: BrowseTarget = BrowseTarget.Project,
        /** Criando uma pasta nova (diálogo aberto e pedido em curso). */
        val creatingDir: Boolean = false,
    ) {
        val canGoUp: Boolean get() = path != null
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
        /** Pastas lidas junto com o projeto (`extraDirs`). */
        val extraDirs: List<String> = emptyList(),
        /** Pasta pessoal liberada para o aparelho; `null` = ainda não sabe ou desktop sem o recurso. */
        val homeAccess: HomeAccess? = null,
        val homeAccessSupported: Boolean = true,
        /** Pedido de liberar a pasta pessoal aguardando a confirmação do usuário. */
        val confirmHome: Boolean = false,
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

    /** Raízes da última listagem sem caminho: subir de uma delas volta para a lista de raízes. */
    private var roots: List<String> = emptyList()

    fun openBrowser(target: BrowseTarget = BrowseTarget.Project) {
        val start = if (target == BrowseTarget.Project) {
            _state.value.projectPath.trim().takeIf { validateProjectPath(it) == null }
        } else {
            _state.value.homeAccess?.takeIf { it.granted }?.home?.ifBlank { null }
        }
        browse(start, target)
        if (_state.value.homeAccess == null && _state.value.homeAccessSupported) loadHomeAccess()
    }

    fun browse(path: String?, target: BrowseTarget = _state.value.browser?.target ?: BrowseTarget.Project) {
        browseJob?.cancel()
        _state.update { it.copy(browser = Browser(path = path, loading = true, target = target)) }
        browseJob = viewModelScope.launch {
            try {
                val listing = deps.listDir(path)
                val dirs = listing.entries
                    .filter { it.kind == EntryKind.DIR && (path == null || !it.name.startsWith(".")) }
                    .let { if (path == null) it else it.sortedBy { e -> e.name.lowercase() } }
                if (path == null) roots = dirs.map { it.name }
                _state.update {
                    it.copy(browser = Browser(path = listing.path ?: path, dirs = dirs, loading = false, truncated = listing.truncated, target = target))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(browser = Browser(path = path, loading = false, error = e.userMessage, target = target)) }
            }
        }
    }

    /** Na lista de raízes o nome já é o caminho inteiro; dentro de uma pasta, é só o nome. */
    fun browseInto(name: String) {
        val base = _state.value.browser?.path
        browse(if (base == null) name else joinPath(base, name))
    }

    fun browseUp() {
        val path = _state.value.browser?.path ?: return
        val parent = parentPath(path)
        val home = _state.value.homeAccess?.takeIf { it.granted }?.home
        // Acima de uma raiz (ou da pasta pessoal) o desktop não deixa ir: volta para as raízes.
        val s = _state.value
        val isRoot = path in roots || path in s.projects || path in s.extraDirs
        browse(if (parent == null || path == home || (isRoot && (home == null || !path.startsWith(home)))) null else parent)
    }

    fun retryBrowse() = browse(_state.value.browser?.path)

    /** Usa a pasta aberta no navegador como projeto (ou a acrescenta às pastas extras). */
    fun pickBrowsed() {
        val b = _state.value.browser ?: return
        val path = b.path ?: return
        _state.update {
            when (b.target) {
                BrowseTarget.Project -> it.copy(projectPath = path, browser = null, error = null)
                BrowseTarget.Extra -> it.copy(
                    extraDirs = if (path in it.extraDirs || path == it.projectPath.trim()) it.extraDirs else it.extraDirs + path,
                    browser = null,
                    error = null,
                )
            }
        }
        browseJob?.cancel()
    }

    fun removeExtraDir(path: String) = _state.update { it.copy(extraDirs = it.extraDirs - path) }

    // ---- Pasta pessoal ----

    private fun loadHomeAccess() {
        viewModelScope.launch {
            try {
                val access = deps.homeAccess()
                _state.update { it.copy(homeAccess = access, homeAccessSupported = access != null) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sem resposta: o navegador segue só com as pastas dos projetos.
            }
        }
    }

    /** Pede a confirmação antes de liberar a pasta pessoal do PC para este aparelho. */
    fun askHomeAccess() = _state.update { it.copy(confirmHome = true) }

    fun dismissHomeAccess() = _state.update { it.copy(confirmHome = false) }

    fun grantHomeAccess() {
        _state.update { it.copy(confirmHome = false) }
        viewModelScope.launch {
            try {
                val access = deps.setHomeAccess(true)
                _state.update { it.copy(homeAccess = access) }
                browse(access.home.ifBlank { null })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.userMessage) }
            }
        }
    }

    fun startNewFolder() = _state.update { s -> s.copy(browser = s.browser?.copy(creatingDir = true)) }

    fun cancelNewFolder() = _state.update { s -> s.copy(browser = s.browser?.copy(creatingDir = false)) }

    /** Cria [name] dentro da pasta aberta e entra nela. */
    fun createFolder(name: String) {
        val base = _state.value.browser?.path ?: return
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/') || clean.contains('\\') || clean == "." || clean == "..") return
        viewModelScope.launch {
            try {
                val created = deps.createDirectory(joinPath(base, clean))
                browse(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s -> s.copy(browser = s.browser?.copy(creatingDir = false, error = null), error = e.userMessage) }
            }
        }
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
                deps.create(s.provider, s.projectPath.trim(), s.permissionMode, s.modelId, s.effort, s.extraDirs)
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
