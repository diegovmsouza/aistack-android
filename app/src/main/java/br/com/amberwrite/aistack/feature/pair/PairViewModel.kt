package br.com.amberwrite.aistack.feature.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.relay.PairLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** O que o pareamento usa do app (permite testar o ViewModel com fakes). */
interface PairDeps {
    val connection: StateFlow<ConnectionState>
    val pairedLink: StateFlow<PairLink?>
    /** Nome do aparelho mostrado no desktop (lê a identidade do Keystore: chamar fora da main). */
    suspend fun deviceName(): String
    /** Dispara o pareamento no núcleo (o link só é salvo depois de `pair_result ok`). */
    fun pair(link: PairLink)
}

private class ContainerPairDeps(private val c: AppContainer) : PairDeps {
    override val connection get() = c.connectionState
    override val pairedLink get() = c.pairingStore.link
    override suspend fun deviceName() = withContext(Dispatchers.IO) { c.identity.name }
    override fun pair(link: PairLink) = c.pair(link)
}

/**
 * Pareamento com o desktop: lê o link (QR ou colado), confirma se já existe um desktop
 * pareado, dispara `pair` e acompanha o estado da conexão até o host confirmar.
 * Só a interface é desta camada; o motor (PairLink, handshake, keyAuth) é do núcleo.
 */
class PairViewModel(private val deps: PairDeps) : ViewModel() {

    constructor(container: AppContainer) : this(ContainerPairDeps(container))

    enum class Step { Choose, Scanning, Pairing }

    data class UiState(
        val step: Step = Step.Choose,
        val pasted: String = "",
        val inputError: PairInputError? = null,
        val pairingWith: PairLink? = null,
        val phase: PairPhase = PairPhase.Idle,
        val connection: ConnectionState = ConnectionState.Disconnected,
        /** Já existe um desktop pareado (este fluxo é um re-pareamento). */
        val alreadyPaired: Boolean = false,
        /** Link aguardando confirmação para substituir o pareamento atual. */
        val confirmRepair: PairLink? = null,
        val deviceName: String = "",
        /** Mostra o campo para colar o link (o QR é o caminho principal). */
        val pasteOpen: Boolean = false,
    ) {
        val paired: Boolean get() = phase == PairPhase.Success
    }

    private data class Local(
        val step: Step = Step.Choose,
        val pasted: String = "",
        val inputError: PairInputError? = null,
        val pairingWith: PairLink? = null,
        val stale: ConnectionState? = null,
        val confirmRepair: PairLink? = null,
        val deviceName: String = "",
        val pasteOpen: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<UiState> = combine(local, deps.connection, deps.pairedLink) { l, conn, saved ->
        val phase = pairPhaseOf(l.pairingWith, conn, saved, l.stale)
        UiState(
            step = l.step,
            pasted = l.pasted,
            inputError = l.inputError,
            pairingWith = l.pairingWith,
            phase = phase,
            connection = conn,
            alreadyPaired = saved != null && phase != PairPhase.Success,
            confirmRepair = l.confirmRepair,
            deviceName = l.deviceName,
            pasteOpen = l.pasteOpen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            val name = try {
                deps.deviceName()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ""
            }
            local.update { it.copy(deviceName = name) }
        }
    }

    fun onPastedChange(text: String) = local.update { it.copy(pasted = text, inputError = null) }

    fun togglePaste() = local.update { it.copy(pasteOpen = !it.pasteOpen, inputError = null) }

    fun openScanner() = local.update { it.copy(step = Step.Scanning, inputError = null) }

    fun closeScanner() = local.update { it.copy(step = Step.Choose) }

    fun submitPasted() {
        parsePairInput(local.value.pasted).fold(
            onSuccess = { request(it) },
            onFailure = { e ->
                val err = (e as? PairInputException)?.error ?: PairInputError.Invalid
                local.update { it.copy(inputError = err, pasteOpen = true) }
            }
        )
    }

    /**
     * Link lido pelo QR ou colado. Se já existe um desktop pareado, pede confirmação antes
     * de trocar o pareamento.
     */
    fun request(link: PairLink) {
        if (link.code.isNullOrBlank()) {
            local.update { it.copy(step = Step.Choose, inputError = PairInputError.MissingCode, pasteOpen = true) }
            return
        }
        if (deps.pairedLink.value != null) {
            local.update { it.copy(step = Step.Choose, confirmRepair = link) }
            return
        }
        start(link)
    }

    fun confirmRepair() {
        val link = local.value.confirmRepair ?: return
        local.update { it.copy(confirmRepair = null) }
        start(link)
    }

    fun dismissRepair() = local.update { it.copy(confirmRepair = null) }

    /** Começa o pareamento com [link] (já confirmado). Exige o código de uso único. */
    fun start(link: PairLink) {
        if (link.code.isNullOrBlank()) {
            local.update { it.copy(step = Step.Choose, inputError = PairInputError.MissingCode, pasteOpen = true) }
            return
        }
        local.update {
            it.copy(
                step = Step.Pairing,
                pairingWith = link,
                inputError = null,
                confirmRepair = null,
                stale = deps.connection.value
            )
        }
        deps.pair(link)
    }

    /** Tenta de novo com o mesmo link (desktop offline ou falha de rede; o código não foi usado). */
    fun retry() {
        val link = local.value.pairingWith ?: return
        start(link)
    }

    /** Volta para a escolha (após recusa ou para trocar de link). */
    fun reset() = local.update { it.copy(step = Step.Choose, pairingWith = null, stale = null) }
}
