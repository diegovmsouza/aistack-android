package br.com.amberwrite.aistack.feature.chat.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `texto vazio nao gera blocos`() {
        assertTrue(MarkdownParser.parse("   \n").isEmpty())
    }

    @Test
    fun `titulo paragrafo e enfase`() {
        val blocks = MarkdownParser.parse("# Título\n\nUm **forte** e *leve* com `código`.")
        assertEquals(2, blocks.size)
        val h = blocks[0] as MdBlock.Heading
        assertEquals(1, h.level)
        assertEquals("Título", h.inlines.plainText())
        val p = blocks[1] as MdBlock.Paragraph
        assertTrue(p.inlines.any { it is MdInline.Strong })
        assertTrue(p.inlines.any { it is MdInline.Emphasis })
        assertTrue(p.inlines.any { it is MdInline.Code && it.text == "código" })
    }

    @Test
    fun `cerca de codigo guarda a linguagem`() {
        val b = MarkdownParser.parse("```kotlin\nval x = 1\n```").single() as MdBlock.CodeBlock
        assertEquals("kotlin", b.language)
        assertEquals("val x = 1", b.code)
    }

    @Test
    fun `cerca aberta durante o streaming vira codigo ate o fim`() {
        val b = MarkdownParser.parse("```py\nprint(1)\nprint(2)").single() as MdBlock.CodeBlock
        assertEquals("py", b.language)
        assertEquals("print(1)\nprint(2)", b.code)
    }

    @Test
    fun `listas ordenadas comecam no numero certo`() {
        val l = MarkdownParser.parse("3. a\n4. b").single() as MdBlock.ListBlock
        assertTrue(l.ordered)
        assertEquals(3, l.start)
        assertEquals(2, l.items.size)
    }

    @Test
    fun `tabela gfm com alinhamentos`() {
        val t = MarkdownParser.parse("| a | b |\n|:--|--:|\n| 1 | 2 |\n| 3 | 4 |").single() as MdBlock.Table
        assertEquals(2, t.header.size)
        assertEquals(2, t.rows.size)
        assertEquals(listOf(MdAlign.Start, MdAlign.End), t.alignments)
    }

    @Test
    fun `tachado e autolink`() {
        val p = MarkdownParser.parse("~~velho~~ veja https://example.com").single() as MdBlock.Paragraph
        assertTrue(p.inlines.any { it is MdInline.Strike })
        assertTrue(p.inlines.any { it is MdInline.Link && it.url == "https://example.com" })
    }

    @Test
    fun `citacao e regra`() {
        val blocks = MarkdownParser.parse("> dito\n\n---")
        assertTrue(blocks[0] is MdBlock.Quote)
        assertEquals(MdBlock.Rule, blocks[1])
    }
    @Test
    fun largeResponseParsesInLinearTime() {
        val chunk = buildString {
            append("## Seção\n\nParágrafo com **negrito**, `código` e [link](https://x.y).\n\n")
            append("- item um\n- item dois\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n")
            append("```kotlin\nval x = 1\nfun f() = x + 1\n```\n\n> citação\n\n")
        }
        val small = chunk.repeat(100)
        val big = chunk.repeat(1_000) // ~200 KB
        MarkdownParser.parse(small) // aquece o JIT
        val t0 = System.nanoTime()
        val blocks = MarkdownParser.parse(big)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("${blocks.size} blocos", blocks.size >= 6_000)
        assertTrue("análise de ${big.length} caracteres levou $ms ms", ms < 3_000)
    }
}
