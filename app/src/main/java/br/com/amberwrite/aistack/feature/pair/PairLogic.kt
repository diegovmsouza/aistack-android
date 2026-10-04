package br.com.amberwrite.aistack.feature.pair

import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.relay.PairLink

/*
 * Lógica pura do pareamento (sem Android), testada em PairLogicTest. O motor (PairLink,
 * handshake, keyAuth, `container.pair`) fica no núcleo; aqui só se traduz o estado da
 * conexão em etapas legíveis para a tela.
 */

/** Etapa visível do pareamento em curso. */
sealed interface PairPhase {
    /** Nenhum pareamento em curso. */
    data object Idle : PairPhase

    /** Abrindo o WebSocket com o relay ([attempt] > 0 em novas tentativas). */
    data class Connecting(val attempt: Int) : PairPhase

    /** Trocando chaves com o desktop (handshake E2E). */
    data object Handshaking : PairPhase

    /** Canal cifrado aberto; aguardando o desktop aceitar o código e salvar o par. */
    data object Verifying : PairPhase

    /** Desktop confirmou: o link (sem código) já está salvo e a conexão está online. */
    data object Success : PairPhase

    /** Código recusado, expirado ou aparelho revogado. Precisa de um QR novo. */
    data class AuthRejected(val message: String?) : PairPhase

    /** Relay respondeu, mas o desktop não está online. O núcleo tenta de novo sozinho. */
    data class HostOffline(val retryInMs: Long?) : PairPhase

    /** Falha de rede/protocolo. */
    data class Failed(val message: String, val retryInMs: Long?) : PairPhase
}

/** Ordem das etapas no indicador de progresso (0 = relay, 1 = chaves, 2 = desktop, 3 = pronto). */
fun PairPhase.stepIndex(): Int = when (this) {
    PairPhase.Idle, is PairPhase.Connecting, is PairPhase.HostOffline, is PairPhase.Failed -> 0
    PairPhase.Handshaking -> 1
    PairPhase.Verifying, is PairPhase.AuthRejected -> 2
    PairPhase.Success -> 3
}

/** Etapa terminal com erro (mostra ação de recuperação). */
val PairPhase.isFailure: Boolean
    get() = this is PairPhase.AuthRejected || this is PairPhase.HostOffline || this is PairPhase.Failed

/**
 * Traduz o estado da conexão em etapa do pareamento com [target].
 *
 * @param saved link salvo pelo núcleo (só é gravado depois de `pair_result ok`).
 * @param stale estado da conexão no instante em que o pareamento começou: enquanto ele não
 *   mudar, uma falha antiga (ex.: recusa da tentativa anterior) não é mostrada como resultado
 *   da tentativa nova.
 */
fun pairPhaseOf(
    target: PairLink?,
    connection: ConnectionState,
    saved: PairLink?,
    stale: ConnectionState? = null,
): PairPhase {
    if (target == null) return PairPhase.Idle
    if (saved != null && saved == target.withoutCode() && connection is ConnectionState.Online) return PairPhase.Success
    if (stale != null && connection === stale && connection.isFailureState()) return PairPhase.Connecting(0)
    return when (connection) {
        ConnectionState.Disconnected -> PairPhase.Connecting(0)
        is ConnectionState.Connecting -> PairPhase.Connecting(connection.attempt)
        ConnectionState.Handshaking -> PairPhase.Handshaking
        is ConnectionState.Online -> PairPhase.Verifying
        is ConnectionState.AuthRejected -> PairPhase.AuthRejected(connection.message)
        ConnectionState.Revoked -> PairPhase.AuthRejected(null)
        is ConnectionState.HostOffline -> PairPhase.HostOffline(connection.retryInMs)
        is ConnectionState.Error -> PairPhase.Failed(connection.message, connection.retryInMs)
    }
}

private fun ConnectionState.isFailureState(): Boolean =
    this is ConnectionState.AuthRejected || this is ConnectionState.Revoked ||
        this is ConnectionState.HostOffline || this is ConnectionState.Error

/** Problema no link digitado/lido. */
enum class PairInputError {
    /** Não é um link de pareamento do AiStack. */
    Invalid,

    /** Link sem o código de uso único (QR antigo ou link de reconexão). */
    MissingCode,
}

/** Valida o texto colado: devolve o link ou o erro. */
fun parsePairInput(text: String): Result<PairLink> {
    val link = PairLink.parse(text.trim()) ?: return Result.failure(PairInputException(PairInputError.Invalid))
    if (link.code.isNullOrBlank()) return Result.failure(PairInputException(PairInputError.MissingCode))
    return Result.success(link)
}

class PairInputException(val error: PairInputError) : IllegalArgumentException(error.name)

/** Segundos inteiros restantes (arredondando para cima) para exibir numa contagem. */
fun secondsLeft(remainingMs: Long): Int = if (remainingMs <= 0) 0 else ((remainingMs + 999) / 1000).toInt()
