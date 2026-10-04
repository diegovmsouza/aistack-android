package br.com.amberwrite.aistack.feature.chat.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.rpc.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado do composer de uma conversa (rascunho e envio). */
class ComposerViewModel(private val container: AppContainer, private val conversationId: String) : ViewModel() {
    data class UiState(val draft: String = "", val sending: Boolean = false, val error: String? = null)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setDraft(text: String) = _state.update { it.copy(draft = text) }

    fun insertMention(path: String) = _state.update {
        val sep = if (it.draft.isEmpty() || it.draft.endsWith(' ')) "" else " "
        it.copy(draft = "${it.draft}$sep@$path ")
    }

    /** Envia o rascunho; com a conversa ocupada, põe na fila. */
    fun send(busy: Boolean) {
        val text = _state.value.draft.trim()
        if (text.isEmpty() || _state.value.sending) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                if (busy) container.chatRepo.queue(conversationId, text) else container.chatRepo.send(conversationId, text)
                _state.update { it.copy(draft = "", sending = false) }
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, error = e.userMessage) }
            }
        }
    }
}
