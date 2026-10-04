package br.com.amberwrite.aistack.feature.accounts

import br.com.amberwrite.aistack.data.model.AccountStatus
import br.com.amberwrite.aistack.data.model.Provider
import br.com.amberwrite.aistack.data.repo.AccountsRepo
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {

    private val source = MutableStateFlow(AccountsRepo.State())
    private var reloads = 0
    private var extras = 0

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(fail: Boolean = false) = AccountsViewModel(
        source = source,
        reloader = {
            reloads++
            if (fail) {
                source.value = source.value.copy(loading = false, error = "sem conexão")
                error("sem conexão")
            }
            source.value = source.value.copy(
                loading = false,
                loaded = true,
                accounts = listOf(
                    AccountStatus(Provider.CODEX, "codex", "a", null, null, null, "authenticated", null, null, true),
                    AccountStatus(Provider.CLAUDE, "claude", "a", null, null, null, "authenticated", null, null, true)
                )
            )
        },
        extras = { extras++ },
        clock = { 42L },
        tickMs = Long.MAX_VALUE
    )

    @Test
    fun `carrega ao abrir e agrupa por provedor`() = runTest {
        val vm = vm()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val s = vm.state.value
        assertEquals(1, reloads)
        assertEquals(1, extras)
        assertEquals(listOf("claude", "codex"), s.groups.map { it.providerId })
        assertEquals(2, s.accountCount)
        assertFalse(s.showSkeleton)
        assertFalse(s.showEmpty)
        assertEquals(42L, s.now)
        job.cancel()
    }

    @Test
    fun `erro sem dados mostra a tela de erro e recarregar tenta de novo`() = runTest {
        val vm = vm(fail = true)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertTrue(vm.state.value.showFullError)
        assertEquals(1, extras) // extras seguem mesmo com a falha das contas
        vm.reload()
        assertEquals(2, reloads)
        assertFalse(vm.state.value.refreshing)
        job.cancel()
    }

    @Test
    fun `evento do repositório atualiza a tela`() = runTest {
        val vm = vm()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        source.value = source.value.copy(accounts = emptyList())
        assertTrue(vm.state.value.showEmpty)
        job.cancel()
    }
}
