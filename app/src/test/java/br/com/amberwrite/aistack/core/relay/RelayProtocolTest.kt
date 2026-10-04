package br.com.amberwrite.aistack.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayProtocolTest {

    @Test
    fun serialize_helloMessage_containsExpectedFields() {
        val hello = RelayProtocol.HelloMessage(k = "test_pubkey", aead = "a")
        val json = RelayProtocol.toJson(hello)

        assertTrue(json.contains("\"t\":\"hello\""))
        assertTrue(json.contains("\"k\":\"test_pubkey\""))
        assertTrue(json.contains("\"aead\":\"a\""))
    }

    @Test
    fun deserialize_pairResultMessage_parsedCorrectly() {
        val json = """{"t":"pair_result","ok":true}"""
        val result = RelayProtocol.fromJson(json, RelayProtocol.PairResultMessage::class.java)

        assertEquals("pair_result", result.t)
        assertTrue(result.ok)
        assertEquals(null, result.error)
    }

    @Test
    fun serialize_rpcMessage_preservesMethodAndId() {
        val rpc = RelayProtocol.RpcMessage(id = 42, method = "chat/send", params = mapOf("text" to "hello"))
        val json = RelayProtocol.toJson(rpc)

        assertTrue(json.contains("\"id\":42"))
        assertTrue(json.contains("\"method\":\"chat/send\""))
    }
}
