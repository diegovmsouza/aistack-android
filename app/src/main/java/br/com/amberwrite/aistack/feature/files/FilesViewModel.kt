package br.com.amberwrite.aistack.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.EntryKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lista um diretório do desktop. Sem [path] explícito, usa a pasta do projeto da conversa
 * (`projectPath`); se nem isso existir, o desktop decide (normalmente a pasta pessoal).
 */
class FilesViewModel(
    private val container: AppContainer,
    val conversationId: String,
    private val path: String?
) : ViewModel() {

    data class UiState(
        val path: String? = null,
        val entries: List<DirEntry> = emptyList(),
        val truncated: Boolean = false,
        val loading: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState(path = path))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val base = path ?: container.sessionsRepo.find(conversationId)?.projectPath
            try {
                val listing = container.filesRepo.listDir(base)
                val sorted = listing.entries.sortedWith(
                    compareBy<DirEntry> { if (it.kind == EntryKind.DIR) 0 else 1 }.thenBy { it.name.lowercase() }
                )
                _state.update {
                    UiState(path = listing.path ?: base, entries = sorted, truncated = listing.truncated, loading = false)
                }
            } catch (e: Exception) {
                _state.update { it.copy(path = base, loading = false, error = e.userMessage) }
            }
        }
    }

    /** Caminho absoluto de uma entrada da listagem atual. */
    fun childPath(entry: DirEntry): String {
        val base = _state.value.path.orEmpty().trimEnd('/')
        return if (base.isEmpty()) entry.name else "$base/${entry.name}"
    }
}
