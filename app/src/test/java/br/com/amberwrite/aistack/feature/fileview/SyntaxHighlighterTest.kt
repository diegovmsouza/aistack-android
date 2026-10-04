package br.com.amberwrite.aistack.feature.fileview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    private fun pieces(line: String, tokens: List<Token>) = tokens.map { line.substring(it.start, it.end) to it.kind }

    @Test
    fun `detecta pela extensão, nome especial e dica do host`() {
        assertEquals(SyntaxHighlighter.KOTLIN, SyntaxHighlighter.detect("/a/Main.kt"))
        assertEquals(SyntaxHighlighter.TS, SyntaxHighlighter.detect("C:\\a\\app.tsx"))
        assertEquals(SyntaxHighlighter.SHELL, SyntaxHighlighter.detect("Dockerfile"))
        assertEquals(SyntaxHighlighter.INI, SyntaxHighlighter.detect(".env.local"))
        assertEquals(SyntaxHighlighter.PYTHON, SyntaxHighlighter.detect("sem-extensao", hint = "py"))
        assertEquals(SyntaxHighlighter.PLAIN, SyntaxHighlighter.detect("algo.xyz"))
    }

    @Test
    fun `kotlin com palavra-chave, texto e comentário`() {
        val line = "val nome = \"oi\" // nota"
        val toks = pieces(line, SyntaxHighlighter.highlight(listOf(line), SyntaxHighlighter.KOTLIN).single())
        assertTrue(("val" to TokenKind.Keyword) in toks)
        assertTrue(toks.any { it.second == TokenKind.Str && it.first.contains("oi") })
        assertTrue(toks.any { it.second == TokenKind.Comment && it.first.startsWith("//") })
    }

    @Test
    fun `comentário de bloco atravessa linhas`() {
        val lines = listOf("/* início", "meio", "fim */ val x = 1")
        val all = SyntaxHighlighter.highlight(lines, SyntaxHighlighter.KOTLIN)
        assertEquals(3, all.size)
        assertTrue(all[1].any { it.kind == TokenKind.Comment && it.start == 0 })
        val last = pieces(lines[2], all[2])
        assertTrue(last.any { it.second == TokenKind.Comment && it.first.endsWith("*/") })
        assertTrue(("val" to TokenKind.Keyword) in last)
        assertTrue(("1" to TokenKind.Number) in last)
    }

    @Test
    fun `chaves de json e de yaml`() {
        val json = "{ \"nome\": \"x\", \"n\": 2 }"
        val jt = pieces(json, SyntaxHighlighter.highlight(listOf(json), SyntaxHighlighter.JSON).single())
        assertTrue(jt.any { it.second == TokenKind.Key && it.first.contains("nome") })
        assertTrue(jt.any { it.second == TokenKind.Str && it.first.contains("x") })

        val yaml = "porta: 8080 # comentário"
        val yt = pieces(yaml, SyntaxHighlighter.highlight(listOf(yaml), SyntaxHighlighter.YAML).single())
        assertTrue(yt.any { it.second == TokenKind.Key && it.first.contains("porta") })
        assertTrue(yt.any { it.second == TokenKind.Comment })
    }

    @Test
    fun `marcação com tag e atributo`() {
        val line = "<a href=\"/x\">link</a>"
        val toks = pieces(line, SyntaxHighlighter.highlight(listOf(line), SyntaxHighlighter.HTML).single())
        assertTrue(toks.any { it.second == TokenKind.Tag })
        assertTrue(toks.any { it.second == TokenKind.Attr && it.first == "href" })
    }

    @Test
    fun `texto puro não gera tokens e tokens ficam dentro da linha`() {
        val plain = SyntaxHighlighter.highlight(listOf("a", "b"), SyntaxHighlighter.PLAIN)
        assertEquals(listOf(emptyList<Token>(), emptyList()), plain)

        val long = "val s = \"" + "x".repeat(SyntaxHighlighter.MAX_LINE_SCAN * 2)
        val toks = SyntaxHighlighter.highlight(listOf(long), SyntaxHighlighter.KOTLIN).single()
        assertTrue(toks.all { it.start in 0..it.end && it.end <= SyntaxHighlighter.MAX_LINE_SCAN })
    }
}
