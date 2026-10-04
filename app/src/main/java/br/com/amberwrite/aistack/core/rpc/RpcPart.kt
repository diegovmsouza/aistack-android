package br.com.amberwrite.aistack.core.rpc

import br.com.amberwrite.aistack.core.crypto.CryptoEngine
import br.com.amberwrite.aistack.core.relay.RelayProtocol
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream

/** Constantes normativas da fragmentação `rpcPart` (contrato v2 §3.3). */
object RpcFrag {
    const val THRESHOLD = 60_000
    const val CHUNK = 45_000
    const val MAX_TOTAL = 8_388_608
    const val MAX_PARTS = 187
    const val TIMEOUT_MS = 30_000L
    const val MAX_INFLIGHT = 4

    /** Teto prático de um quadro inteiro sem fragmentação (o selo cabe em 65 536 B). */
    const val MAX_UNFRAGMENTED = 65_000
}

/** Divide o JSON de um quadro `rpc` em quadros `rpcPart` (sentido c2h). */
object RpcFragmenter {
    /** Só fragmenta com o eco de `frag` e acima do limiar; nunca fragmenta o que cabe inteiro. */
    fun shouldFragment(jsonBytes: Int, hostFrag: Boolean): Boolean =
        hostFrag && jsonBytes > RpcFrag.THRESHOLD

    /** Gera os quadros `rpcPart` (já em JSON) em ordem de `seq`. */
    fun split(id: Long, json: String): List<String> {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size <= RpcFrag.MAX_TOTAL) { "mensagem acima de ${RpcFrag.MAX_TOTAL} bytes" }
        val parts = ArrayList<String>((bytes.size + RpcFrag.CHUNK - 1) / RpcFrag.CHUNK)
        var offset = 0
        var seq = 0
        while (offset < bytes.size) {
            val end = minOf(offset + RpcFrag.CHUNK, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            parts += RelayProtocol.toJson(
                RelayProtocol.RpcPartMessage(
                    id = id, seq = seq, last = end == bytes.size,
                    data = CryptoEngine.b64uEncode(chunk)
                )
            )
            offset = end
            seq++
        }
        return parts
    }
}

/**
 * Remonta respostas `rpcPart` (sentido h2c). Classe pura, sem threads: quem usa chama
 * [accept] para cada pedaço e [sweepExpired] periodicamente. Não é thread-safe por si só.
 */
class RpcPartAssembler(private val clock: () -> Long = System::currentTimeMillis) {

    sealed interface Result {
        /** Pedaço aceito; ainda faltam outros. */
        data object Incomplete : Result
        /** Mensagem completa: o `rpcResult` interno, já validado. */
        data class Complete(val id: Long, val frame: JsonObject) : Result
        /** A remontagem falhou; a chamada [id] deve falhar com [message]. */
        data class Failed(val id: Long, val message: String, val timeout: Boolean = false) : Result
        /** Pedaço de um id já falho; descartado em silêncio. */
        data object Ignored : Result
    }

    private class Buffer(val startedAt: Long) {
        var nextSeq = 0
        val data = ByteArrayOutputStream()
    }

    private val buffers = LinkedHashMap<Long, Buffer>()
    private val poisoned = HashSet<Long>()

    val inFlight: Int get() = buffers.size

    fun accept(id: Long, seq: Int, last: Boolean, data: String, fragNegotiated: Boolean): Result {
        if (id in poisoned) {
            if (last) poisoned.remove(id)
            return Result.Ignored
        }
        if (!fragNegotiated) return fail(id, last, "mensagem fragmentada inválida (id $id)")

        var buf = buffers[id]
        if (buf == null) {
            if (seq != 0) return fail(id, last, "fragmento fora de ordem (id $id, esperado 0, recebido $seq)")
            if (buffers.size >= RpcFrag.MAX_INFLIGHT) return fail(id, last, "fragmentações simultâneas demais (id $id)")
            buf = Buffer(clock())
            buffers[id] = buf
        }
        if (clock() - buf.startedAt > RpcFrag.TIMEOUT_MS) {
            return fail(id, last, "fragmentação incompleta: tempo esgotado (id $id)", timeout = true)
        }
        if (seq != buf.nextSeq) {
            return fail(id, last, "fragmento fora de ordem (id $id, esperado ${buf.nextSeq}, recebido $seq)")
        }
        if (seq >= RpcFrag.MAX_PARTS) {
            return fail(id, last, "mensagem fragmentada excede o limite de ${RpcFrag.MAX_TOTAL} bytes (id $id)")
        }
        val chunk = try {
            CryptoEngine.b64uDecode(data)
        } catch (e: IllegalArgumentException) {
            return fail(id, last, "fragmento inválido (id $id): base64url")
        }
        if (buf.data.size() + chunk.size > RpcFrag.MAX_TOTAL) {
            return fail(id, last, "mensagem fragmentada excede o limite de ${RpcFrag.MAX_TOTAL} bytes (id $id)")
        }
        buf.data.write(chunk)
        buf.nextSeq++
        if (!last) return Result.Incomplete

        buffers.remove(id)
        val text = buf.data.toByteArray().toString(Charsets.UTF_8)
        val frame = parseJsonOrNull(text).asObj()
        if (frame == null || frame.str("t") != "rpcResult" || frame.long("id") != id) {
            return Result.Failed(id, "mensagem fragmentada inválida (id $id)")
        }
        return Result.Complete(id, frame)
    }

    /** Falha (e esquece) remontagens que passaram de [RpcFrag.TIMEOUT_MS] sem o `last`. */
    fun sweepExpired(): List<Result.Failed> {
        val now = clock()
        val expired = buffers.filterValues { now - it.startedAt > RpcFrag.TIMEOUT_MS }.keys.toList()
        return expired.map { id ->
            buffers.remove(id)
            poisoned += id
            Result.Failed(id, "fragmentação incompleta: tempo esgotado (id $id)", timeout = true)
        }
    }

    /** Hora de início da remontagem de [id], se houver uma em andamento. */
    fun startedAt(id: Long): Long? = buffers[id]?.startedAt

    /** Esquece tudo (nova sessão do túnel). */
    fun reset() {
        buffers.clear()
        poisoned.clear()
    }

    private fun fail(id: Long, last: Boolean, message: String, timeout: Boolean = false): Result.Failed {
        buffers.remove(id)
        if (!last) poisoned += id
        return Result.Failed(id, message, timeout)
    }
}
