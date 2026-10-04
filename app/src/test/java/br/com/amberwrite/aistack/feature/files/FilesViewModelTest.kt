package br.com.amberwrite.aistack.feature.files

import br.com.amberwrite.aistack.core.rpc.RpcException
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.DirListing
import br.com.amberwrite.aistack.data.model.EntryKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class FilesViewModelTest {

    private fun file(name: String) = DirEntry(name, EntryKind.FILE, 10, null)
    private fun dir(name: String) = DirEntry(name, EntryKind.DIR, null, null)

    private class FakeLister {
        val listings = mutableMapOf<String?, DirListing>()
        val errors = mutableMapOf<String?, Exception>()
        val gates = mutableMapOf<String?, CompletableDeferred<Unit>>()
        val calls = mutableListOf<String?>()

        suspend fun list(path: String?): DirListing {
            calls += path
            gates[path]?.await()
            errors[path]?.let { throw it }
            return listings[path] ?: error("não encontrado")
        }
    }

    private lateinit var fake: FakeLister

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fake = FakeLister().apply {
            listings[null] = DirListing(null, listOf(dir("/proj")), false)
            listings["/proj"] = DirListing("/proj", listOf(file("b.txt"), dir("src"), file("A.kt")), false)
            listings["/proj/src"] = DirListing("/proj/src", listOf(file("Main.kt")), true)
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(initial: String? = null, project: String? = "/proj") =
        FilesViewModel("c1", initial, fake::list) { project }

    @Test
    fun `abre a pasta do projeto com pastas primeiro e trilha`() = runTest {
        val s = vm().state.value
        assertEquals("/proj", s.path)
        assertEquals(listOf("src", "A.kt", "b.txt"), s.entries.map { it.name })
        assertFalse(s.loading)
        assertEquals(listOf("/proj"), s.roots)
        assertEquals(listOf(null, "/proj"), s.crumbs.map { it.path })
    }

    @Test
    fun `sem projeto abre a lista de raízes`() = runTest {
        val s = vm(project = null).state.value
        assertTrue(s.atRoots)
        assertEquals(listOf("/proj"), s.entries.map { it.name })
    }

    @Test
    fun `entrar e voltar usa o histórico e o cache`() = runTest {
        val vm = vm()
        vm.openEntry(vm.state.value.entries.first())
        assertEquals("/proj/src", vm.state.value.path)
        assertTrue(vm.state.value.truncated)
        assertTrue(vm.state.value.canGoBack)
        assertEquals(1, vm.state.value.direction)

        val before = fake.calls.count { it == "/proj" }
        fake.gates["/proj"] = CompletableDeferred()
        assertTrue(vm.back())
        // Volta mostrando o cache enquanto atualiza por cima.
        val s = vm.state.value
        assertEquals("/proj", s.path)
        assertEquals(3, s.entries.size)
        assertTrue(s.refreshing)
        assertFalse(s.showSkeleton)
        assertEquals(-1, s.direction)
        fake.gates["/proj"]!!.complete(Unit)
        assertFalse(vm.state.value.refreshing)
        assertEquals(before + 1, fake.calls.count { it == "/proj" })
        assertFalse(vm.back())
    }

    @Test
    fun `subir da raiz do projeto leva às raízes`() = runTest {
        val vm = vm()
        vm.goUp()
        assertNull(vm.state.value.path)
        assertEquals(listOf("/proj"), vm.state.value.entries.map { it.name })
    }

    @Test
    fun `esqueleto enquanto a primeira carga não chega`() = runTest {
        fake.gates["/proj"] = CompletableDeferred()
        val vm = vm()
        assertTrue(vm.state.value.showSkeleton)
        fake.gates["/proj"]!!.complete(Unit)
        assertFalse(vm.state.value.showSkeleton)
    }

    @Test
    fun `erro de permissão vira estado de erro e tentar de novo recupera`() = runTest {
        fake.errors["/proj"] = RpcException(RpcException.Kind.NOT_ALLOWED, "não permitido")
        val vm = vm()
        val s = vm.state.value
        assertTrue(s.showFullError)
        assertEquals(FilesErrorKind.NoPermission, s.errorKind)

        fake.errors.remove("/proj")
        vm.reload()
        assertNull(vm.state.value.error)
        assertEquals(3, vm.state.value.entries.size)
    }

    @Test
    fun `pasta vazia`() = runTest {
        fake.listings["/proj"] = DirListing("/proj", emptyList(), false)
        assertTrue(vm().state.value.isEmptyFolder)
    }

    @Test
    fun `busca local e sem resultados`() = runTest {
        val vm = vm()
        vm.openSearch()
        vm.setQuery("kt")
        assertEquals(listOf("A.kt"), vm.state.value.visible.map { it.name })
        vm.setQuery("zzz")
        assertTrue(vm.state.value.noResults)
        // Voltar com a busca aberta só fecha a busca.
        assertTrue(vm.back())
        assertFalse(vm.state.value.searchOpen)
        assertEquals("", vm.state.value.query)
        assertEquals(3, vm.state.value.visible.size)
    }

    @Test
    fun `trilha conta como voltar`() = runTest {
        val vm = vm(initial = "/proj/src")
        val root = vm.state.value.crumbs.first { it.path == "/proj" }
        vm.openCrumb(root)
        assertEquals("/proj", vm.state.value.path)
        assertEquals(-1, vm.state.value.direction)
    }
}
