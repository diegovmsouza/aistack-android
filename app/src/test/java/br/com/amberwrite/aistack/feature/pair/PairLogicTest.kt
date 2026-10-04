package br.com.amberwrite.aistack.feature.pair

import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.relay.PairLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairLogicTest {

    private val raw = "aistack://pair?relay=wss%3A%2F%2Faistack.amberwrite.com.br&host=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855&pk=k_test_pk12345&code=994821"
    private val target = PairLink.parse(raw)!!

    @Test
    fun semAlvo_ficaOcioso() {
        assertEquals(PairPhase.Idle, pairPhaseOf(null, ConnectionState.Disconnected, null))
    }

    @Test
    fun estadosDaConexao_viramEtapas() {
        assertEquals(PairPhase.Connecting(2), pairPhaseOf(target, ConnectionState.Connecting(2), null))
        assertEquals(PairPhase.Handshaking, pairPhaseOf(target, ConnectionState.Handshaking, null))
        assertEquals(PairPhase.Verifying, pairPhaseOf(target, ConnectionState.Online(true), null))
        assertEquals(PairPhase.AuthRejected("x"), pairPhaseOf(target, ConnectionState.AuthRejected("x", true), null))
        assertEquals(PairPhase.AuthRejected(null), pairPhaseOf(target, ConnectionState.Revoked, null))
        assertEquals(PairPhase.HostOffline(5000), pairPhaseOf(target, ConnectionState.HostOffline(5000), null))
    }

    @Test
    fun linkSalvoEOnline_eSucesso() {
        val phase = pairPhaseOf(target, ConnectionState.Online(true), target.withoutCode())
        assertEquals(PairPhase.Success, phase)
        assertEquals(3, phase.stepIndex())
    }

    @Test
    fun falhaAntiga_naoApareceComoResultadoDaTentativaNova() {
        val stale = ConnectionState.AuthRejected("velho", true)
        assertEquals(PairPhase.Connecting(0), pairPhaseOf(target, stale, null, stale = stale))
    }

    @Test
    fun etapasDeFalha() {
        assertTrue(PairPhase.Failed("x", null).isFailure)
        assertTrue(PairPhase.HostOffline(null).isFailure)
        assertFalse(PairPhase.Verifying.isFailure)
        assertEquals(2, PairPhase.AuthRejected(null).stepIndex())
    }

    @Test
    fun entradaColada() {
        assertTrue(parsePairInput("  $raw  ").isSuccess)
        val semCodigo = parsePairInput("aistack://pair?relay=wss%3A%2F%2Faistack.amberwrite.com.br&host=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855&pk=k_test_pk12345").exceptionOrNull() as PairInputException
        assertEquals(PairInputError.MissingCode, semCodigo.error)
        val invalido = parsePairInput("https://exemplo.com").exceptionOrNull() as PairInputException
        assertEquals(PairInputError.Invalid, invalido.error)
    }

    @Test
    fun contagemArredondaParaCima() {
        assertEquals(0, secondsLeft(-5))
        assertEquals(1, secondsLeft(1))
        assertEquals(2, secondsLeft(1001))
        assertEquals(60, secondsLeft(60_000))
    }
}
