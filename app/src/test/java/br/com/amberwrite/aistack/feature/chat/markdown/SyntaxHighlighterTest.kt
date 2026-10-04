package br.com.amberwrite.aistack.feature.chat.markdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    private fun List<CodeToken>.textsOf(code: String, type: TokenType) =
        filter { it.type == type }.map { code.substring(it.start, it.end) }

    @Test
    fun `kotlin destaca palavra-chave string numero e comentario`() {
        val code = "val x = \"oi\" + 42 // nota"
        val tokens = SyntaxHighlighter.tokenize(code, "kotlin")
        assertTrue("val" in tokens.textsOf(code, TokenType.Keyword))
        assertTrue("\"oi\"" in tokens.textsOf(code, TokenType.String))
        assertTrue("42" in tokens.textsOf(code, TokenType.Number))
        assertTrue(tokens.textsOf(code, TokenType.Comment).any { it.startsWith("// nota") })
    }

    @Test
    fun `tokens ficam ordenados sem sobreposicao e dentro do texto`() {
        val code = "fun main() {\n  /* bloco */ println(\"a\\\"b\") // fim\n}\n"
        val tokens = SyntaxHighlighter.tokenize(code, "kt")
        var last = 0
        for (t in tokens) {
            assertTrue(t.start >= last)
            assertTrue(t.end > t.start && t.end <= code.length)
            last = t.end
        }
    }

    @Test
    fun `comentario de bloco sem fechamento vai ate o fim`() {
        val code = "x = 1 /* aberto"
        val tokens = SyntaxHighlighter.tokenize(code, "js")
        assertTrue(tokens.any { it.type == TokenType.Comment && it.end == code.length })
    }

    @Test
    fun `aliases conhecidos e desconhecidos`() {
        assertTrue(SyntaxHighlighter.isKnown("Kotlin"))
        assertTrue(SyntaxHighlighter.isKnown("bash"))
        assertFalse(SyntaxHighlighter.isKnown("brainfuck"))
        assertFalse(SyntaxHighlighter.isKnown(null))
    }
}
