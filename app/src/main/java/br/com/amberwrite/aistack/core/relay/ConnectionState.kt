package br.com.amberwrite.aistack.core.relay

/** Estado observável da ligação com o desktop (via relay). */
sealed interface ConnectionState {
    /** Sem pareamento ou parado de propósito. */
    data object Disconnected : ConnectionState

    /** Abrindo o WebSocket. [attempt] começa em 1 e cresce a cada nova tentativa. */
    data class Connecting(val attempt: Int) : ConnectionState

    /** WebSocket aberto; trocando hello/hostAuth/keyAuth (ou pair). */
    data object Handshaking : ConnectionState

    /** Túnel autenticado. [hostFrag] indica que o host ecoou a capacidade `frag`. */
    data class Online(val hostFrag: Boolean) : ConnectionState

    /**
     * O host recusou a chave do aparelho (`keyAuth` com erro) ou o código de pareamento.
     * Estado terminal: não reconecta sozinho; o usuário precisa parear de novo.
     */
    data class AuthRejected(val message: String, val duringPairing: Boolean) : ConnectionState

    /** Falha transitória; nova tentativa em [retryInMs] ms (se não nulo). */
    data class Error(val message: String, val retryInMs: Long?) : ConnectionState

    /** O relay avisou que o desktop está offline; tentando de novo em [retryInMs] ms. */
    data class HostOffline(val retryInMs: Long?) : ConnectionState

    /** O desktop revogou este aparelho (`close` com `reason:"revoked"`). Credenciais apagadas. */
    data object Revoked : ConnectionState
}

val ConnectionState.isOnline: Boolean get() = this is ConnectionState.Online

/** Estados em que a reconexão automática fica desligada. */
val ConnectionState.isTerminal: Boolean
    get() = this is ConnectionState.AuthRejected || this is ConnectionState.Revoked
