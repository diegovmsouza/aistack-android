package br.com.amberwrite.aistack.feature.newsession

import br.com.amberwrite.aistack.data.model.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewSessionLogicTest {

    private fun model(id: String, efforts: List<String> = emptyList(), default: String? = null, isDefault: Boolean = false) =
        ModelInfo(id, id, null, efforts, default, isDefault)

    @Test
    fun projetos_semRepetirEComRecentesPrimeiro() {
        val out = mergeProjects(listOf("/a/", " /b "), listOf("/a", "/c", ""), max = 3)
        assertEquals(listOf("/a/", "/b", "/c"), out)
        assertEquals(1, mergeProjects(listOf("/a", "/b"), emptyList(), max = 1).size)
    }

    @Test
    fun rotulosDeEsforco() {
        assertEquals("Muito alto", effortLabel("XHIGH"))
        assertEquals("Custom", effortLabel("custom"))
    }

    @Test
    fun modeloEEsforcoPadrao() {
        val a = model("a")
        val b = model("b", listOf("low", "medium", "high"), default = "high", isDefault = true)
        assertEquals(b, defaultModelOf(listOf(a, b)))
        assertEquals(a, defaultModelOf(listOf(a)))
        assertNull(defaultModelOf(emptyList()))
        assertEquals(2, defaultEffortIndex(b))
        assertEquals(1, defaultEffortIndex(model("c", listOf("low", "medium", "high"))))
        assertEquals(1, defaultEffortIndex(model("d", listOf("low", "high", "max"), default = "zzz")))
        assertEquals(-1, defaultEffortIndex(a))
        assertEquals(-1, defaultEffortIndex(null))
    }

    @Test
    fun validacaoDoCaminho() {
        assertEquals(ProjectPathError.Missing, validateProjectPath("  "))
        assertEquals(ProjectPathError.NotAbsolute, validateProjectPath("projeto"))
        assertNull(validateProjectPath("/home/x"))
        assertNull(validateProjectPath("~/x"))
        assertNull(validateProjectPath("C:\\x"))
        assertNull(validateProjectPath("\\\\srv\\x"))
    }

    @Test
    fun navegacaoDePastas() {
        assertNull(parentPath("/"))
        assertNull(parentPath(null))
        assertEquals("/", parentPath("/home"))
        assertEquals("/home", parentPath("/home/x/"))
        assertEquals("C:\\", parentPath("C:\\x"))
        assertNull(parentPath("C:\\"))
        assertEquals("/a/b", joinPath("/a", "b"))
        assertEquals("/a/b", joinPath("/a/", "b"))
        assertEquals("C:\\a\\b", joinPath("C:\\a", "b"))
        assertEquals("x", shortProjectName("/home/x/"))
        assertEquals("y", shortProjectName("C:\\y"))
    }
}
