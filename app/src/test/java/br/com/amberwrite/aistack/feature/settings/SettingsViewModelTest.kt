package br.com.amberwrite.aistack.feature.settings

import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private class FakeGateway : SettingsGateway {
        override val settings = MutableStateFlow(SettingsStore.Settings())
        override val materialYou = MutableStateFlow(false)
        val link = MutableStateFlow<String?>("wss://relay.exemplo.com/ws")
        override val relayUrl: Flow<String?> get() = link
        override val paired: Flow<Boolean> get() = link.map { it != null }
        override val connection: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
        override val desktopVersion: Flow<String?> = MutableStateFlow("1.2.3")
        var name = "Pixel 10"
        var unpaired = 0
        override suspend fun deviceName() = name
        override suspend fun rename(name: String): String { this.name = name; return name }
        override fun setKeepConnected(value: Boolean) { settings.value = settings.value.copy(keepConnected = value) }
        override fun update(transform: (SettingsStore.Settings) -> SettingsStore.Settings) { settings.value = transform(settings.value) }
        override fun setMaterialYou(enabled: Boolean) { materialYou.value = enabled }
        override suspend fun unpair() { unpaired++; link.value = null }
    }

    private lateinit var gw: FakeGateway

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        gw = FakeGateway()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `estado inicial reflete preferências, relay e nome`() = runTest {
        val vm = SettingsViewModel(gw)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val s = vm.state.value
        assertEquals(ThemeMode.System, s.theme)
        assertEquals("Pixel 10", s.deviceName)
        assertEquals("Pixel 10", s.deviceNameDraft)
        assertTrue(s.paired)
        assertEquals("wss://relay.exemplo.com/ws", s.relayUrl)
        assertEquals("1.2.3", s.desktopVersion)
        assertFalse(s.nameChanged)
        job.cancel()
    }

    @Test
    fun `tema e Material You persistem pelo gateway`() = runTest {
        val vm = SettingsViewModel(gw)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.setTheme(ThemeMode.Dark)
        vm.setMaterialYou(true)
        assertEquals("dark", gw.settings.value.theme)
        assertEquals(ThemeMode.Dark, vm.state.value.theme)
        assertTrue(vm.state.value.materialYou)
        job.cancel()
    }

    @Test
    fun `renomear limpa o texto e marca como salvo`() = runTest {
        val vm = SettingsViewModel(gw)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.setNameDraft("  Meu   Pixel  ")
        assertTrue(vm.state.value.nameChanged)
        vm.saveName()
        assertEquals("Meu Pixel", gw.name)
        assertEquals("Meu Pixel", vm.state.value.deviceName)
        assertTrue(vm.state.value.nameSaved)
        assertFalse(vm.state.value.nameChanged)
        job.cancel()
    }

    @Test
    fun `desparear chama o gateway uma vez e avisa o fim`() = runTest {
        val vm = SettingsViewModel(gw)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        var done = 0
        vm.unpair { done++ }
        assertEquals(1, gw.unpaired)
        assertEquals(1, done)
        assertFalse(vm.state.value.paired)
        assertNull(vm.state.value.relayUrl)
        assertFalse(vm.state.value.unpairing)
        job.cancel()
    }
}
