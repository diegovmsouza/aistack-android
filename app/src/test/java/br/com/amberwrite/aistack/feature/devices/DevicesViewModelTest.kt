package br.com.amberwrite.aistack.feature.devices

import br.com.amberwrite.aistack.data.model.PairedDevice
import br.com.amberwrite.aistack.data.repo.DevicesRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {

    private val source = MutableStateFlow(DevicesRepo.State(loading = true))
    private val connected = MutableStateFlow(true)
    private var reloads = 0
    private var revokes = 0
    private var revokeFails = false

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(devices: List<PairedDevice>) = DevicesViewModel(
        source = source,
        selfId = { "me" },
        connected = connected,
        reloader = {
            reloads++
            source.value = DevicesRepo.State(devices = devices, loading = false)
        },
        revoker = {
            revokes++
            if (revokeFails) error("desktop recusou")
        },
        clock = { 1_000L },
        tickMs = Long.MAX_VALUE
    )

    @Test
    fun `destaca este aparelho e separa os outros`() = runTest {
        val vm = vm(listOf(PairedDevice("x", "Outro", null, null, null, false), PairedDevice("me", "Pixel", null, null, null, false)))
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val s = vm.state.value
        assertEquals("me", s.thisDevice?.id)
        assertEquals(listOf("x"), s.others.map { it.id })
        assertFalse(s.showSkeleton)
        assertEquals(1, reloads)
        job.cancel()
    }

    @Test
    fun `lista vazia depois de carregar mostra o vazio`() = runTest {
        val vm = vm(emptyList())
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertTrue(vm.state.value.showEmpty)
        vm.reload()
        assertEquals(2, reloads)
        assertFalse(vm.state.value.refreshing)
        job.cancel()
    }

    @Test
    fun `revogar chama o desktop e avisa o fim`() = runTest {
        val vm = vm(emptyList())
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        var done = false
        vm.revokeSelf { done = true }
        assertTrue(done)
        assertEquals(1, revokes)
        assertFalse(vm.state.value.revoking)
        job.cancel()
    }

    @Test
    fun `falha ao revogar mostra o erro e não navega`() = runTest {
        revokeFails = true
        val vm = vm(emptyList())
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        var done = false
        vm.revokeSelf { done = true }
        assertFalse(done)
        assertNotNull(vm.state.value.revokeError)
        vm.dismissRevokeError()
        assertNull(vm.state.value.revokeError)
        job.cancel()
    }
}
