package br.com.amberwrite.aistack.feature.newsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.ModelInfo
import br.com.amberwrite.aistack.data.model.PermissionMode
import br.com.amberwrite.aistack.data.model.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formulário de nova conversa: provedor, projeto, modo de permissão e primeira mensagem. */
class NewSessionViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val provider: Provider = Provider.CLAUDE,
        val projectPath: String = "",
        val recentProjects: List<String> = emptyList(),
        val permissionMode: PermissionMode = PermissionMode.ASK,
        val models: List<ModelInfo> = emptyList(),
        val model: String? = null,
        val message: String = "",
        val creating: Boolean = false,
        val error: String? = null
    ) {
        val canCreate: Boolean get() = projectPath.isNotBlank() && !creating
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val recent = runCatching { container.sessionsRepo.recentProjects() }.getOrDefault(emptyList())
            _state.update { s ->
                s.copy(recentProjects = recent, projectPath = s.projectPath.ifBlank { recent.firstOrNull().orEmpty() })
            }
        }
        loadModels(Provider.CLAUDE)
    }

    fun setProvider(p: Provider) {
        _state.update { it.copy(provider = p, models = emptyList(), model = null) }
        loadModels(p)
    }

    private fun loadModels(p: Provider) {
        viewModelScope.launch {
            val models = runCatching { container.sessionsRepo.getCatalog(p) }.getOrDefault(emptyList())
            _state.update { if (it.provider == p) it.copy(models = models) else it }
        }
    }

    fun setProjectPath(v: String) = _state.update { it.copy(projectPath = v, error = null) }
    fun setPermissionMode(m: PermissionMode) = _state.update { it.copy(permissionMode = m) }
    fun setModel(id: String?) = _state.update { it.copy(model = id) }
    fun setMessage(v: String) = _state.update { it.copy(message = v) }

    /** Cria a conversa e, se houver texto, envia a primeira mensagem. Chama [onCreated] com o id. */
    fun create(onCreated: (String) -> Unit) {
        val s = _state.value
        if (!s.canCreate) return
        _state.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            try {
                val conv = container.sessionsRepo.createConversation(
                    provider = s.provider,
                    projectPath = s.projectPath.trim(),
                    permissionMode = s.permissionMode,
                    model = s.model
                )
                if (s.message.isNotBlank()) container.chatRepo.send(conv.id, s.message.trim())
                _state.update { it.copy(creating = false) }
                onCreated(conv.id)
            } catch (e: Exception) {
                _state.update { it.copy(creating = false, error = e.userMessage) }
            }
        }
    }
}
