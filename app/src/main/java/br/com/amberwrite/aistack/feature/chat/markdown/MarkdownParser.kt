package br.com.amberwrite.aistack.feature.chat.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/** Trecho em linha de um bloco Markdown (modelo puro, sem Compose). */
sealed interface MdInline {
    data class Plain(val text: String) : MdInline
    data class Code(val text: String) : MdInline
    data class Emphasis(val children: List<MdInline>) : MdInline
    data class Strong(val children: List<MdInline>) : MdInline
    data class Strike(val children: List<MdInline>) : MdInline
    data class Link(val url: String, val children: List<MdInline>) : MdInline
    data object SoftBreak : MdInline
    data object HardBreak : MdInline
}

/** Alinhamento de uma coluna de tabela GFM. */
enum class MdAlign { Start, Center, End }

/** Bloco Markdown já interpretado, pronto para a renderização em Compose. */
sealed interface MdBlock {
    data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock
    data class Paragraph(val inlines: List<MdInline>) : MdBlock
    data class CodeBlock(val language: String?, val code: String) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<MdListItem>) : MdBlock
    data class Table(
        val header: List<List<MdInline>>,
        val rows: List<List<List<MdInline>>>,
        val alignments: List<MdAlign>,
    ) : MdBlock
    data object Rule : MdBlock
}

/** Item de lista; [checked] é `null` fora de listas de tarefas (`- [ ]` / `- [x]`). */
data class MdListItem(val blocks: List<MdBlock>, val checked: Boolean? = null)

/**
 * Converte Markdown (CommonMark + tabelas, tachado e autolinks do GFM) no modelo [MdBlock].
 * Puro e sem estado: roda na JVM nos testes. Cercas de código ainda abertas (streaming)
 * viram bloco de código até o fim do texto, como no CommonMark.
 */
object MarkdownParser {

    private val parser: Parser by lazy {
        Parser.builder()
            .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create()))
            .build()
    }

    fun parse(markdown: String): List<MdBlock> {
        if (markdown.isBlank()) return emptyList()
        val doc = synchronized(parser) { parser.parse(markdown) }
        return blocks(doc)
    }

    private fun children(node: Node): Sequence<Node> = generateSequence(node.firstChild) { it.next }

    private fun blocks(parent: Node): List<MdBlock> = children(parent).mapNotNull(::block).toList()

    private fun block(n: Node): MdBlock? = when (n) {
        is Heading -> MdBlock.Heading(n.level.coerceIn(1, 6), inlines(n))
        is Paragraph -> MdBlock.Paragraph(inlines(n)).takeIf { it.inlines.isNotEmpty() }
        is FencedCodeBlock -> MdBlock.CodeBlock(
            n.info?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() },
            n.literal.orEmpty().removeSuffix("\n"),
        )
        is IndentedCodeBlock -> MdBlock.CodeBlock(null, n.literal.orEmpty().removeSuffix("\n"))
        is HtmlBlock -> MdBlock.CodeBlock("html", n.literal.orEmpty().removeSuffix("\n"))
        is BlockQuote -> MdBlock.Quote(blocks(n))
        is BulletList -> MdBlock.ListBlock(false, 1, children(n).filterIsInstance<ListItem>().map(::listItem).toList())
        is OrderedList -> MdBlock.ListBlock(
            true,
            @Suppress("DEPRECATION") n.startNumber,
            children(n).filterIsInstance<ListItem>().map(::listItem).toList(),
        )
        is ThematicBreak -> MdBlock.Rule
        is TableBlock -> table(n)
        else -> null
    }

    private fun listItem(item: ListItem): MdListItem {
        val inner = blocks(item)
        val first = inner.firstOrNull() as? MdBlock.Paragraph
        val lead = (first?.inlines?.firstOrNull() as? MdInline.Plain)?.text
        if (first != null && lead != null) {
            val m = TASK.find(lead)
            if (m != null) {
                val checked = m.groupValues[1].isNotBlank()
                val rest = lead.substring(m.range.last + 1)
                val newInlines = buildList {
                    if (rest.isNotEmpty()) add(MdInline.Plain(rest))
                    addAll(first.inlines.drop(1))
                }
                return MdListItem(listOf(MdBlock.Paragraph(newInlines)) + inner.drop(1), checked)
            }
        }
        return MdListItem(inner)
    }

    private fun table(t: TableBlock): MdBlock.Table {
        var header: List<List<MdInline>> = emptyList()
        val rows = mutableListOf<List<List<MdInline>>>()
        var aligns: List<MdAlign> = emptyList()
        children(t).forEach { section ->
            when (section) {
                is TableHead -> children(section).filterIsInstance<TableRow>().firstOrNull()?.let { row ->
                    val cells = children(row).filterIsInstance<TableCell>().toList()
                    header = cells.map(::inlines)
                    aligns = cells.map { it.alignment.toMd() }
                }
                is TableBody -> children(section).filterIsInstance<TableRow>().forEach { row ->
                    rows += children(row).filterIsInstance<TableCell>().map(::inlines).toList()
                }
            }
        }
        val cols = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
        return MdBlock.Table(
            header = header.padTo(cols),
            rows = rows.map { it.padTo(cols) },
            alignments = List(cols) { aligns.getOrNull(it) ?: MdAlign.Start },
        )
    }

    private fun List<List<MdInline>>.padTo(n: Int): List<List<MdInline>> =
        if (size >= n) this else this + List(n - size) { emptyList() }

    private fun TableCell.Alignment?.toMd(): MdAlign = when (this) {
        TableCell.Alignment.CENTER -> MdAlign.Center
        TableCell.Alignment.RIGHT -> MdAlign.End
        else -> MdAlign.Start
    }

    private fun inlines(parent: Node): List<MdInline> = merge(children(parent).flatMap(::inline).toList())

    private fun inline(n: Node): Sequence<MdInline> = when (n) {
        is Text -> sequenceOf(MdInline.Plain(n.literal.orEmpty()))
        is Code -> sequenceOf(MdInline.Code(n.literal.orEmpty()))
        is Emphasis -> sequenceOf(MdInline.Emphasis(inlines(n)))
        is StrongEmphasis -> sequenceOf(MdInline.Strong(inlines(n)))
        is Strikethrough -> sequenceOf(MdInline.Strike(inlines(n)))
        is Link -> sequenceOf(MdInline.Link(n.destination.orEmpty(), inlines(n).ifEmpty { listOf(MdInline.Plain(n.destination.orEmpty())) }))
        // Imagens não são carregadas (sem rede arbitrária): viram link com o texto alternativo.
        is Image -> sequenceOf(MdInline.Link(n.destination.orEmpty(), inlines(n).ifEmpty { listOf(MdInline.Plain(n.destination.orEmpty())) }))
        is SoftLineBreak -> sequenceOf(MdInline.SoftBreak)
        is HardLineBreak -> sequenceOf(MdInline.HardBreak)
        is HtmlInline -> sequenceOf(MdInline.Plain(n.literal.orEmpty()))
        else -> children(n).flatMap(::inline)
    }

    /** Junta textos simples vizinhos (o CommonMark os divide em vários nós). */
    private fun merge(list: List<MdInline>): List<MdInline> {
        val out = ArrayList<MdInline>(list.size)
        for (i in list) {
            val last = out.lastOrNull()
            if (i is MdInline.Plain && last is MdInline.Plain) out[out.lastIndex] = MdInline.Plain(last.text + i.text)
            else if (!(i is MdInline.Plain && i.text.isEmpty())) out += i
        }
        return out
    }

    private val TASK = Regex("""^\[([ xX])]\s""")
}

/** Texto puro de uma sequência de trechos (acessibilidade, cópia, testes). */
fun List<MdInline>.plainText(): String = buildString { appendPlain(this@plainText) }

private fun StringBuilder.appendPlain(list: List<MdInline>) {
    for (i in list) when (i) {
        is MdInline.Plain -> append(i.text)
        is MdInline.Code -> append(i.text)
        is MdInline.Emphasis -> appendPlain(i.children)
        is MdInline.Strong -> appendPlain(i.children)
        is MdInline.Strike -> appendPlain(i.children)
        is MdInline.Link -> appendPlain(i.children)
        MdInline.SoftBreak -> append(' ')
        MdInline.HardBreak -> append('\n')
    }
}
