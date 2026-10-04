package br.com.amberwrite.aistack.core.rpc

import br.com.amberwrite.aistack.core.crypto.CryptoEngine
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcPartTest {

    private class Part(val id: Long, val seq: Int, val last: Boolean, val data: String)

    private fun parts(id: Long, json: String): List<Part> = RpcFragmenter.split(id, json).map {
        val o = JsonParser.parseString(it).asJsonObject
        assertEquals("rpcPart", o["t"].asString)
        Part(o["id"].asLong, o["seq"].asInt, o["last"].asBoolean, o["data"].asString)
    }

    /** `rpcResult` com [size] bytes de payload, para forçar a fragmentação. */
    private fun bigResult(id: Long, size: Int): String =
        """{"t":"rpcResult","id":$id,"result":{"text":"${"x".repeat(size)}"}}"""

    @Test
    fun shouldFragment_respeitaLimiarEEcoDeFrag() {
        assertFalse(RpcFragmenter.shouldFragment(RpcFrag.THRESHOLD, hostFrag = true))
        assertTrue(RpcFragmenter.shouldFragment(RpcFrag.THRESHOLD + 1, hostFrag = true))
        assertFalse(RpcFragmenter.shouldFragment(RpcFrag.THRESHOLD + 1, hostFrag = false))
    }

    @Test
    fun split_geraPedacosEmOrdemComUltimoMarcado() {
        val json = bigResult(7, 100_000)
        val ps = parts(7, json)
        assertEquals((json.length + RpcFrag.CHUNK - 1) / RpcFrag.CHUNK, ps.size)
        ps.forEachIndexed { i, p ->
            assertEquals(7L, p.id)
            assertEquals(i, p.seq)
            assertEquals(i == ps.lastIndex, p.last)
            assertTrue(CryptoEngine.b64uDecode(p.data).size <= RpcFrag.CHUNK)
        }
    }

    @Test
    fun assembler_remontaMensagemCompleta() {
        val json = bigResult(9, 130_000)
        val asm = RpcPartAssembler { 0L }
        val ps = parts(9, json)
        ps.dropLast(1).forEach {
            assertEquals(RpcPartAssembler.Result.Incomplete, asm.accept(it.id, it.seq, it.last, it.data, true))
        }
        val last = ps.last()
        val r = asm.accept(last.id, last.seq, last.last, last.data, true)
        assertTrue(r is RpcPartAssembler.Result.Complete)
        r as RpcPartAssembler.Result.Complete
        assertEquals(9L, r.id)
        assertEquals(130_000, r.frame.getAsJsonObject("result")["text"].asString.length)
        assertEquals(0, asm.inFlight)
    }

    @Test
    fun assembler_foraDeOrdemFalhaEDescartaORestante() {
        val ps = parts(3, bigResult(3, 150_000))
        assertTrue(ps.size >= 3)
        val asm = RpcPartAssembler { 0L }
        asm.accept(3, ps[0].seq, ps[0].last, ps[0].data, true)
        val r = asm.accept(3, ps[2].seq, ps[2].last, ps[2].data, true)
        assertTrue(r is RpcPartAssembler.Result.Failed)
        // O id fica envenenado: os pedaços seguintes são ignorados até o `last`.
        ps.drop(3).forEach {
            assertEquals(RpcPartAssembler.Result.Ignored, asm.accept(3, it.seq, it.last, it.data, true))
        }
        assertEquals(0, asm.inFlight)
    }

    @Test
    fun assembler_primeiroPedacoComSeqDiferenteDeZeroFalha() {
        val asm = RpcPartAssembler { 0L }
        val r = asm.accept(5, 1, false, CryptoEngine.b64uEncode(byteArrayOf(1)), true)
        assertTrue(r is RpcPartAssembler.Result.Failed)
    }

    @Test
    fun assembler_semFragNegociadoFalha() {
        val asm = RpcPartAssembler { 0L }
        val r = asm.accept(5, 0, true, CryptoEngine.b64uEncode("{}".toByteArray()), false)
        assertTrue(r is RpcPartAssembler.Result.Failed)
    }

    @Test
    fun assembler_limitaRemontagensSimultaneas() {
        val asm = RpcPartAssembler { 0L }
        val chunk = CryptoEngine.b64uEncode("{".toByteArray())
        for (id in 10L until 10L + RpcFrag.MAX_INFLIGHT) {
            assertEquals(RpcPartAssembler.Result.Incomplete, asm.accept(id, 0, false, chunk, true))
        }
        val r = asm.accept(99, 0, false, chunk, true)
        assertTrue(r is RpcPartAssembler.Result.Failed)
        assertEquals(RpcFrag.MAX_INFLIGHT, asm.inFlight)
    }

    @Test
    fun assembler_excedeMaxPartsFalha() {
        val asm = RpcPartAssembler { 0L }
        val chunk = CryptoEngine.b64uEncode("x".toByteArray())
        for (seq in 0 until RpcFrag.MAX_PARTS) {
            assertEquals(RpcPartAssembler.Result.Incomplete, asm.accept(1, seq, false, chunk, true))
        }
        val r = asm.accept(1, RpcFrag.MAX_PARTS, false, chunk, true)
        assertTrue(r is RpcPartAssembler.Result.Failed)
        assertEquals(RpcPartAssembler.Result.Ignored, asm.accept(1, RpcFrag.MAX_PARTS + 1, true, chunk, true))
    }

    @Test
    fun assembler_resultadoInternoInvalidoFalha() {
        val asm = RpcPartAssembler { 0L }
        val wrongId = CryptoEngine.b64uEncode("""{"t":"rpcResult","id":2,"result":null}""".toByteArray())
        val r = asm.accept(1, 0, true, wrongId, true)
        assertTrue(r is RpcPartAssembler.Result.Failed)
    }

    @Test
    fun assembler_tempoEsgotadoComRelogioFalso() {
        var now = 1_000L
        val asm = RpcPartAssembler { now }
        val chunk = CryptoEngine.b64uEncode("{".toByteArray())
        asm.accept(4, 0, false, chunk, true)
        now += RpcFrag.TIMEOUT_MS
        assertTrue(asm.sweepExpired().isEmpty())
        now += 1
        val expired = asm.sweepExpired()
        assertEquals(1, expired.size)
        assertEquals(4L, expired[0].id)
        assertTrue(expired[0].timeout)
        assertEquals(RpcPartAssembler.Result.Ignored, asm.accept(4, 1, true, chunk, true))
        // Depois do `last`, o id volta a ser aceito.
        assertEquals(RpcPartAssembler.Result.Incomplete, asm.accept(4, 0, false, chunk, true))
    }
}
