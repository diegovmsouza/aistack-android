package br.com.amberwrite.aistack.relay

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

object RelayProtocol {
    private val gson = Gson()

    fun toJson(obj: Any): String = gson.toJson(obj)
    fun <T> fromJson(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)

    // Handshake Messages
    data class HelloMessage(
        val t: String = "hello",
        val k: String,          // pública X25519 (base64url)
        val aead: String? = "a", // Perfil AES-GCM
        val e: String? = null    // X25519 efêmera desta conexão (só no hello do aparelho; sigilo futuro)
    )

    data class HostAuthMessage(
        val t: String,
        val pk: String,  // pública Ed25519 do host (base64url)
        val sig: String  // assinatura Ed25519 (base64url)
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

    // Tunnel RPC & Events
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
