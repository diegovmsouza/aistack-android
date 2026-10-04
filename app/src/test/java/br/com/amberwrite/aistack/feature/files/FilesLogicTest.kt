package br.com.amberwrite.aistack.feature.files

import br.com.amberwrite.aistack.core.rpc.RpcException
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.EntryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilesLogicTest {

    private fun file(name: String) = DirEntry(name, EntryKind.FILE, 10, null)
    private fun dir(name: String) = DirEntry(name, EntryKind.DIR, null, null)

    @Test
    fun `pastas primeiro e nomes sem diferenciar maiúsculas`() {
        val sorted = FilesLogic.sort(listOf(file("b.txt"), dir("zeta"), file("A.kt"), dir("Alpha")))
        assertEquals(listOf("Alpha", "zeta", "A.kt", "b.txt"), sorted.map { it.name })
    }

    @Test
    fun `filtro local por trecho do nome`() {
        val list = listOf(file("Main.kt"), file("README.md"), dir("main"))
        assertEquals(listOf("Main.kt", "main"), FilesLogic.filter(list, " MAIN ").map { it.name })
        assertEquals(list, FilesLogic.filter(list, "  "))
    }

    @Test
    fun `caminho do filho respeita o separador`() {
        assertEquals("/proj/src", FilesLogic.childPath("/proj", "src"))
        assertEquals("/proj/src", FilesLogic.childPath("/proj/", "src"))
        assertEquals("C:\\proj\\src", FilesLogic.childPath("C:\\proj", "src"))
        assertEquals("/abs", FilesLogic.childPath(null, "/abs"))
    }

    @Test
    fun `pasta mãe para na raiz do escopo`() {
        val roots = listOf("/home/u/proj")
        assertEquals("/home/u/proj", FilesLogic.parentOf("/home/u/proj/src", roots))
        assertNull(FilesLogic.parentOf("/home/u/proj", roots))
        assertNull(FilesLogic.parentOf("/home/u/proj/", roots))
        assertNull(FilesLogic.parentOf(null, roots))
    }

    @Test
    fun `raiz mais longa vence com raízes aninhadas`() {
        val roots = listOf("/home/u", "/home/u/proj")
        assertEquals("/home/u/proj", FilesLogic.rootOf("/home/u/proj/a", roots))
        assertEquals("/home/u", FilesLogic.rootOf("/home/u/other", roots))
        assertNull(FilesLogic.rootOf("/etc", roots))
        // Prefixo de nome não conta como dentro.
        assertNull(FilesLogic.rootOf("/home/u/project", listOf("/home/u/proj")))
    }

    @Test
    fun `trilha a partir da raiz do escopo`() {
        val crumbs = FilesLogic.crumbs("/home/u/proj/src/main", listOf("/home/u/proj"))
        assertEquals(listOf("", "proj", "src", "main"), crumbs.map { it.label })
        assertEquals(listOf(null, "/home/u/proj", "/home/u/proj/src", "/home/u/proj/src/main"), crumbs.map { it.path })
        assertEquals(true, crumbs.first().isRoots)
    }

    @Test
    fun `trilha sem raízes quebra o caminho inteiro`() {
        val crumbs = FilesLogic.crumbs("/a/b", emptyList())
        assertEquals(listOf(null, "/a", "/a/b"), crumbs.map { it.path })
        assertEquals(1, FilesLogic.crumbs(null, emptyList()).size)
    }

    @Test
    fun `nome exibido e extensão`() {
        assertEquals("proj", FilesLogic.displayName("/home/u/proj/"))
        assertEquals("/", FilesLogic.displayName("/"))
        assertEquals("kt", FilesLogic.extension("Main.KT"))
        assertEquals("", FilesLogic.extension(".gitignore"))
        assertEquals("", FilesLogic.extension("Makefile"))
    }

    @Test
    fun `família do arquivo`() {
        assertEquals(FileType.Folder, FilesLogic.fileType("src", EntryKind.DIR))
        assertEquals(FileType.Code, FilesLogic.fileType("Main.kt", EntryKind.FILE))
        assertEquals(FileType.Markup, FilesLogic.fileType("README.md", EntryKind.FILE))
        assertEquals(FileType.Config, FilesLogic.fileType("app.yaml", EntryKind.FILE))
        assertEquals(FileType.Config, FilesLogic.fileType(".env.local", EntryKind.FILE))
        assertEquals(FileType.Image, FilesLogic.fileType("logo.PNG", EntryKind.FILE))
        assertEquals(FileType.Archive, FilesLogic.fileType("a.zip", EntryKind.FILE))
        assertEquals(FileType.Lock, FilesLogic.fileType("package-lock.json", EntryKind.FILE))
        assertEquals(FileType.Lock, FilesLogic.fileType("yarn.lock", EntryKind.FILE))
        assertEquals(FileType.Text, FilesLogic.fileType("Dockerfile", EntryKind.FILE))
        assertEquals(FileType.Other, FilesLogic.fileType("algo.xyz", EntryKind.FILE))
    }

    @Test
    fun `classificação dos erros`() {
        assertEquals(FilesErrorKind.NoPermission, FilesLogic.classify(RpcException(RpcException.Kind.NOT_ALLOWED, "x")))
        assertEquals(FilesErrorKind.Offline, FilesLogic.classify(RpcException(RpcException.Kind.DISCONNECTED, "x")))
        assertEquals(FilesErrorKind.Offline, FilesLogic.classify(RpcException(RpcException.Kind.TIMEOUT, "x")))
        assertEquals(FilesErrorKind.NoPermission, FilesLogic.classify(RpcException(RpcException.Kind.REMOTE, "caminho fora do escopo")))
        assertEquals(FilesErrorKind.NotFound, FilesLogic.classify(IllegalStateException("arquivo não encontrado")))
        assertEquals(FilesErrorKind.NotDirectory, FilesLogic.classify(IllegalStateException("não é uma pasta")))
        assertEquals(FilesErrorKind.Generic, FilesLogic.classify(IllegalStateException("boom")))
    }
}
