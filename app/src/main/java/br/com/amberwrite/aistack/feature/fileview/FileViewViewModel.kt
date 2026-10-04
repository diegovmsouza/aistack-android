package br.com.amberwrite.aistack.feature.fileview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.FileContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lê um arquivo do desktop (`readFile`); o limite de bytes depende de o host fragmentar. */
class FileViewViewModel(private val container: AppContainer, val path: String) : ViewModel() {

    data class UiState(val content: FileContent? = null, val loading: Boolean = true, val error: String? = null)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = try {
                UiState(content = container.filesRepo.readFile(path), loading = false)
            } catch (e: Exception) {
                UiState(content = _state.value.content, loading = false, error = e.userMessage)
            }
        }
    }
}
