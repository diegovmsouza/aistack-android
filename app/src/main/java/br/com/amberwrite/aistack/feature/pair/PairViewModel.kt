package br.com.amberwrite.aistack.feature.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.relay.PairLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Pareamento com o desktop: lê o link (QR ou colado), dispara `pair` e acompanha o estado
 * da conexão até o host confirmar. O link só é salvo pelo núcleo depois de `pair_result ok`.
 */
class PairViewModel(private val container: AppContainer) : ViewModel() {

    enum class Step { Choose, Scanning, Pairing, Done }

    data class UiState(
        val step: Step = Step.Choose,
        val pasted: String = "",
        val inputError: String? = null,
        val pairingWith: PairLink? = null,
        val connection: ConnectionState = ConnectionState.Disconnected,
        val paired: Boolean = false,
        val deviceName: String = ""
    ) {
        /** Falha do pareamento em curso (código recusado/expirado), se houver. */
        val failure: String?
            get() = when (val c = connection) {
                is ConnectionState.AuthRejected -> c.message
                is ConnectionState.Error -> if (step == Step.Pairing) c.message else null
                else -> null
            }
    }

    private val local = MutableStateFlow(UiState(deviceName = container.identity.name))

    val state: StateFlow<UiState> = combine(local, container.connectionState, container.pairingStore.link) { s, conn, link ->
        val target = s.pairingWith?.withoutCode()
        val paired = target != null && link == target && conn is ConnectionState.Online
        s.copy(
            connection = conn,
            paired = paired,
            step = if (paired) Step.Done else s.step
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun onPastedChange(text: String) = local.update { it.copy(pasted = text, inputError = null) }

    fun openScanner() = local.update { it.copy(step = Step.Scanning, inputError = null) }

    fun closeScanner() = local.update { it.copy(step = Step.Choose) }

    fun submitPasted() {
        val link = PairLink.parse(local.value.pasted)
        if (link == null) {
            local.update { it.copy(inputError = "Link inválido. Copie o link de pareamento mostrado no desktop.") }
            return
        }
        start(link)
    }

    /** Começa o pareamento com [link]; exige o código de uso único. */
    fun start(link: PairLink) {
        if (link.code.isNullOrBlank()) {
            local.update {
                it.copy(step = Step.Choose, inputError = "Este link não tem código de pareamento. Gere um novo QR no desktop.")
            }
            return
        }
        local.update { it.copy(step = Step.Pairing, pairingWith = link, inputError = null) }
        container.pair(link)
    }

    /** Volta para a escolha (após recusa ou para trocar de link). */
    fun reset() = local.update { it.copy(step = Step.Choose, pairingWith = null) }
}
