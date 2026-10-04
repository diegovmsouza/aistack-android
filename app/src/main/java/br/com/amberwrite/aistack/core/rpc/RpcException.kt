package br.com.amberwrite.aistack.core.rpc

/**
 * Falha de uma chamada RPC. [kind] permite que as camadas de cima reajam sem comparar
 * mensagens (por exemplo, desligar um recurso em silêncio quando o host é antigo).
 */
class RpcException(
    val kind: Kind,
    message: String,
    val method: String? = null
) : Exception(message) {

    enum class Kind {
        /** Erro devolvido pelo host em `rpcResult.error` (texto em português). */
        REMOTE,
        /** O prazo da chamada (ou da remontagem de `rpcPart`) se esgotou. */
        TIMEOUT,
        /** Túnel fechado antes da resposta, ou sem túnel ao chamar. */
        DISCONNECTED,
        /** Falha na remontagem/fragmentação (`rpcPart`). */
        FRAGMENT,
        /** `"método desconhecido: X"`: host antigo, o recurso deve ser desligado em silêncio. */
        UNKNOWN_METHOD,
        /** `"método não permitido para aparelho remoto: X"`: política do host. */
        NOT_ALLOWED,
        /** A mensagem não cabe no túnel (sem `frag`, ou acima do teto de 8 MiB). */
        TOO_LARGE
    }

    val isUnknownMethod: Boolean get() = kind == Kind.UNKNOWN_METHOD

    companion object {
        const val PREFIX_UNKNOWN_METHOD = "método desconhecido"
        const val PREFIX_NOT_ALLOWED = "método não permitido para aparelho remoto"
        const val PREFIX_TOO_LARGE = "resposta excede o limite do túnel"
        const val PREFIX_UNKNOWN_PERMISSION = "pedido de permissão desconhecido"

        /** Classifica o texto de erro do host. */
        fun fromRemote(method: String?, error: String): RpcException {
            val kind = when {
                error.startsWith(PREFIX_UNKNOWN_METHOD) -> Kind.UNKNOWN_METHOD
                error.startsWith(PREFIX_NOT_ALLOWED) -> Kind.NOT_ALLOWED
                error.startsWith(PREFIX_TOO_LARGE) -> Kind.TOO_LARGE
                else -> Kind.REMOTE
            }
            return RpcException(kind, error, method)
        }

        fun disconnected(method: String? = null) =
            RpcException(Kind.DISCONNECTED, "Sem conexão com o desktop.", method)
    }
}

/** Atalho: verdadeiro quando o erro indica host antigo sem o método. */
val Throwable.isUnknownMethod: Boolean get() = (this as? RpcException)?.isUnknownMethod == true

/** Mensagem curta e legível para mostrar ao usuário. */
val Throwable.userMessage: String
    get() = when (this) {
        is RpcException -> when (kind) {
            RpcException.Kind.TIMEOUT -> "O desktop demorou demais para responder."
            RpcException.Kind.DISCONNECTED -> "Sem conexão com o desktop."
            RpcException.Kind.NOT_ALLOWED -> "Ação não permitida pelo celular."
            RpcException.Kind.UNKNOWN_METHOD -> "O AiStack do desktop é antigo para esta ação."
            else -> message ?: "Erro desconhecido."
        }
        else -> message ?: javaClass.simpleName
    }
