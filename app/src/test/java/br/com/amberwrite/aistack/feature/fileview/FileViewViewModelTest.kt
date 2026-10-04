package br.com.amberwrite.aistack.feature.fileview

import br.com.amberwrite.aistack.core.rpc.RpcException
import br.com.amberwrite.aistack.data.model.FileContent
import br.com.amberwrite.aistack.data.repo.FilesRepo
import br.com.amberwrite.aistack.feature.files.FilesErrorKind
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
class FileViewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Arquivo de texto falso de [total] bytes: devolve até o limite pedido. */
    private class FakeReader(val total: Int, val binary: Boolean = false) {
        val limits = mutableListOf<Int>()
        var error: Exception? = null

        suspend fun read(path: String, max: Int): FileContent {
            limits += max
            error?.let { throw it }
            if (binary) return FileContent.Binary(path, total.toLong(), false, "image/png", "arquivo binário")
            val line = "val x = 1\n"
            val text = line.repeat(total / line.length + 1).take(minOf(max, total))
            return FileContent.Text(path, text, total.toLong(), truncated = max < total, language = null)
        }
    }

    private fun vm(reader: FakeReader, frag: Boolean) =
        FileViewViewModel("/proj/Main.kt", reader::read, { frag }, dispatcher)

    @Test
    fun `primeira leitura pequena, realce e carregar mais até o teto`() = runTest {
        val reader = FakeReader(total = 2_000_000)
        val vm = vm(reader, frag = true)
        var s = vm.state.value
        assertEquals(FileViewViewModel.INITIAL_BYTES, reader.limits.single())
        assertEquals("Main.kt", s.fileName)
        assertEquals("Kotlin", s.language)
        assertTrue(s.lines.isNotEmpty())
        assertEquals(s.lines.size, s.tokens.size)
        assertTrue(s.canLoadMore)
        assertFalse(s.hitCeiling)

        vm.loadMore()
        s = vm.state.value
        assertEquals(FilesRepo.READ_MAX_FRAG, reader.limits.last())
        assertFalse(s.canLoadMore)
        assertTrue(s.hitCeiling)
        vm.loadMore()
        assertEquals(2, reader.limits.size)
    }

    @Test
    fun `sem frag o teto é menor`() = runTest {
        val reader = FakeReader(total = 100_000)
        val s = vm(reader, frag = false).state.value
        assertEquals(FilesRepo.READ_MAX_NO_FRAG, reader.limits.single())
        assertTrue(s.hitCeiling)
    }

    @Test
    fun `arquivo pequeno não oferece mais`() = runTest {
        val s = vm(FakeReader(total = 30), frag = true).state.value
        assertTrue(s.isText)
        assertFalse(s.truncated)
        assertFalse(s.canLoadMore)
        assertFalse(s.hitCeiling)
    }

    @Test
    fun `binário vira aviso sem linhas`() = runTest {
        val s = vm(FakeReader(total = 5_000, binary = true), frag = true).state.value
        assertTrue(s.isBinary)
        assertTrue(s.lines.isEmpty())
        assertNull(s.language)
    }

    @Test
    fun `erro e tentar de novo`() = runTest {
        val reader = FakeReader(total = 100).apply { error = RpcException(RpcException.Kind.NOT_ALLOWED, "não") }
        val vm = vm(reader, frag = true)
        assertTrue(vm.state.value.showFullError)
        assertEquals(FilesErrorKind.NoPermission, vm.state.value.errorKind)
        reader.error = null
        vm.reload()
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.isText)
    }

    @Test
    fun `quebra de linha alterna`() = runTest {
        val vm = vm(FakeReader(total = 10), frag = true)
        assertFalse(vm.state.value.wrap)
        vm.toggleWrap()
        assertTrue(vm.state.value.wrap)
    }

    @Test
    fun `linhas e próximo limite`() {
        assertEquals(emptyList<String>(), FileViewViewModel.splitLines(""))
        assertEquals(listOf("a", "b"), FileViewViewModel.splitLines("a\r\nb\n"))
        assertEquals(listOf("a", "", "b"), FileViewViewModel.splitLines("a\n\nb"))
        assertEquals(1024, FileViewViewModel.nextLimit(256, 4096))
        assertEquals(4096, FileViewViewModel.nextLimit(2048, 4096))
        assertEquals(1, FileViewViewModel.nextLimit(0, 10))
    }
}
