package br.com.amberwrite.aistack.core.relay

import com.google.gson.Gson
import com.google.gson.JsonElement

/**
 * Mensagens do handshake e do túnel (spec 01 §§4–6 e contrato v2 §2). A ordem dos campos
 * das classes é a ordem do JSON gerado pelo Gson; o `hello` é assinado pelo host como bytes
 * crus, então `caps` precisa vir depois de `aead`.
 */
object RelayProtocol {
    private val gson = Gson()

    /** Capacidade de fragmentação de respostas/pedidos grandes (`rpcPart`). */
    const val CAP_FRAG = "frag"

    fun toJson(obj: Any): String = gson.toJson(obj)
    fun <T> fromJson(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)

    data class HelloMessage(
        val t: String = "hello",
        val k: String,
        val aead: String? = "a",
        val caps: List<String>? = null,
        /** X25519 efêmera desta conexão (R-173): entra na derivação e dá sigilo futuro. */
        val e: String? = null
    )

    data class HostAuthMessage(
        val t: String,
        val pk: String,
        val sig: String
    )

    data class PairDevicePayload(
        val id: String,
        val name: String,
        val pk: String
    )

    data class PairMessage(
        val t: String = "pair",
        val code: String,
        val device: PairDevicePayload
    )

    data class PairResultMessage(
        val t: String,
        val ok: Boolean,
        val error: String? = null
    )

    data class RpcMessage(
        val t: String = "rpc",
        val id: Long,
        val method: String,
        val params: Any? = null
    )

    data class RpcResultMessage(
        val t: String,
        val id: Long,
        val result: JsonElement? = null,
        val error: String? = null
    )

    data class RpcPartMessage(
        val t: String = "rpcPart",
        val id: Long,
        val seq: Int,
        val last: Boolean,
        val data: String
    )

    data class EventMessage(
        val t: String,
        val event: String,
        val payload: JsonElement? = null
    )

    data class CloseMessage(
        val t: String = "close",
        val reason: String
    )
}
