package br.com.amberwrite.aistack.feature.chat.markdown

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.TypingCaret
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CARET_ID = "caret"

/** Cores usadas pela renderização (resolvidas uma vez por composição). */
private data class MdColors(
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val link: Color,
    val codeBg: Color,
    val codeFg: Color,
    val line: Color,
    val accent: Color,
)

/**
 * Markdown completo do chat: títulos, listas (inclusive de tarefas), citações, tabelas
 * roláveis, links, código em linha e blocos com realce e botão de copiar.
 *
 * @param caret mostra o cursor piscante no fim do último parágrafo (resposta em streaming).
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AiTheme.typography.body,
    color: Color = AiTheme.colors.fg,
    caret: Boolean = false,
) {
    val blocks = rememberMarkdownBlocks(markdown, streaming = caret)
    val c = AiTheme.colors
    val colors = MdColors(color, c.fg2, c.fg3, c.accent, c.surface2, c.fg, c.line, c.accent)
    val uri = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { i, b ->
            MdBlockView(b, style, colors, uri, caret = caret && i == blocks.lastIndex)
        }
        val lastIsText = blocks.lastOrNull().let { it is MdBlock.Paragraph || it is MdBlock.Heading }
        if (caret && !lastIsText) TypingCaret(height = 14.dp)
    }
}

/** Textos até este tamanho são analisados na hora; acima, fora da thread principal. */
internal const val SYNC_PARSE_LIMIT = 8_000

/** Espera entre deltas de uma resposta grande em streaming antes de reanalisar. */
private const val STREAM_PARSE_DEBOUNCE_MS = 120L

/**
 * Blocos do [markdown]. Textos curtos são analisados de forma síncrona (sem piscar); os longos,
 * em [Dispatchers.Default], mantendo os blocos anteriores na tela até o novo resultado. Em
 * streaming, deltas seguidos se juntam numa análise só (o efeito anterior é cancelado).
 */
@Composable
private fun rememberMarkdownBlocks(markdown: String, streaming: Boolean): List<MdBlock> {
    val cache = remember { ParseCache() }
    // Lido aqui para recompor quando a análise em segundo plano termina.
    var landed by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observe = landed
    if (cache.source != markdown && (markdown.length <= SYNC_PARSE_LIMIT || cache.source == null)) {
        cache.blocks = MarkdownParser.parse(markdown)
        cache.source = markdown
    }
    LaunchedEffect(markdown) {
        if (cache.source == markdown) return@LaunchedEffect
        if (streaming) delay(STREAM_PARSE_DEBOUNCE_MS)
        val parsed = withContext(Dispatchers.Default) { MarkdownParser.parse(markdown) }
        cache.blocks = parsed
        cache.source = markdown
        landed++
    }
    return cache.blocks
}

private class ParseCache {
    var source: String? = null
    var blocks: List<MdBlock> = emptyList()
}

@Composable
private fun MdBlockView(block: MdBlock, base: TextStyle, colors: MdColors, uri: UriHandler, caret: Boolean) {
    val t = AiTheme.typography
    when (block) {
        is MdBlock.Heading -> {
            val style = when (block.level) {
                1 -> t.title
                2 -> t.heading
                else -> t.label.copy(fontWeight = FontWeight.SemiBold)
            }
            InlineText(block.inlines, style, colors, uri, caret, Modifier.padding(top = 4.dp).semantics { heading() })
        }
        is MdBlock.Paragraph -> InlineText(block.inlines, base, colors, uri, caret)
        is MdBlock.CodeBlock -> MdCodeBlock(block.code, block.language)
        is MdBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(AiTheme.shapes.pill).background(colors.accent.copy(alpha = 0.5f)))
            Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.blocks.forEachIndexed { i, b ->
                    MdBlockView(b, base.copy(color = colors.fg2), colors.copy(fg = colors.fg2), uri, caret && i == block.blocks.lastIndex)
                }
            }
        }
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { idx, item ->
                Row(Modifier.fillMaxWidth()) {
                    val marker = when {
                        item.checked == true -> "☑"
                        item.checked == false -> "☐"
                        block.ordered -> "${block.start + idx}."
                        else -> "•"
                    }
                    Text(
                        marker,
                        style = base,
                        color = if (item.checked == true) colors.accent else colors.fg3,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 18.dp).padding(end = 8.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.blocks.forEachIndexed { bi, b ->
                            val last = idx == block.items.lastIndex && bi == item.blocks.lastIndex
                            val itemStyle = if (item.checked == true) base.copy(textDecoration = TextDecoration.LineThrough) else base
                            MdBlockView(b, itemStyle, colors, uri, caret && last)
                        }
                    }
                }
            }
        }
        is MdBlock.Table -> MdTable(block, colors, uri)
        MdBlock.Rule -> HorizontalDivider(color = colors.line, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun InlineText(
    inlines: List<MdInline>,
    style: TextStyle,
    colors: MdColors,
    uri: UriHandler,
    caret: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = remember(inlines, colors, caret) {
        buildAnnotatedString {
            appendInlines(inlines, colors, uri)
            if (caret) {
                append('⁠')
                appendInlineContent(CARET_ID, "▍")
            }
        }
    }
    val inline = if (caret) {
        mapOf(
            CARET_ID to InlineTextContent(Placeholder(0.5.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                TypingCaret(height = 14.dp)
            },
        )
    } else {
        emptyMap()
    }
    Text(text, style = style, color = colors.fg, inlineContent = inline, modifier = modifier)
}

private fun AnnotatedString.Builder.appendInlines(list: List<MdInline>, colors: MdColors, uri: UriHandler) {
    for (i in list) when (i) {
        is MdInline.Plain -> append(i.text)
        is MdInline.Code -> withStyle(
            SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBg, color = colors.codeFg, fontSize = 0.92.em),
        ) { append(" ${i.text} ") }
        is MdInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(i.children, colors, uri) }
        is MdInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { appendInlines(i.children, colors, uri) }
        is MdInline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInlines(i.children, colors, uri) }
        is MdInline.Link -> {
            val url = i.url
            val safe = isSafeUrl(url)
            if (!safe) {
                withStyle(SpanStyle(color = colors.link)) { appendInlines(i.children, colors, uri) }
            } else {
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)),
                    ) { runCatching { uri.openUri(url) } },
                ) { appendInlines(i.children, colors, uri) }
            }
        }
        MdInline.SoftBreak -> append(' ')
        MdInline.HardBreak -> append('\n')
    }
}

/** Só abre http(s) e mailto: outros esquemas podem não ter app e derrubar a tela. */
internal fun isSafeUrl(url: String): Boolean {
    val u = url.trim().lowercase()
    return u.startsWith("https://") || u.startsWith("http://") || u.startsWith("mailto:")
}

/**
 * Bloco de código com rótulo da linguagem, botão de copiar e realce de sintaxe.
 * Rola na horizontal; a altura acompanha o conteúdo.
 */
@Composable
fun MdCodeBlock(code: String, language: String?, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    val shape = AiTheme.shapes.sm
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAiHaptics()
    val motion = AiTheme.motion
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    val palette = SyntaxPalette(
        keyword = c.accent,
        type = c.warn,
        string = c.ok,
        number = c.warn,
        comment = c.fg3,
        annotation = c.accent.copy(alpha = 0.8f),
        punctuation = c.fg3,
    )
    // Blocos enormes ficam sem realce: tokenizar dezenas de milhares de linhas trava a rolagem.
    val highlighted = remember(code, language, palette) {
        if (code.length > HIGHLIGHT_LIMIT) AnnotatedString(code) else highlight(code, language, palette)
    }
    val copyLabel = stringResource(R.string.chat_code_copy)
    val copiedLabel = stringResource(R.string.chat_code_copied)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface2)
            .border(1.dp, c.line, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.lowercase() ?: stringResource(R.string.chat_code_label),
                style = AiTheme.typography.monoSmall,
                color = c.fg3,
                modifier = Modifier.weight(1f),
            )
            AnimatedContent(
                targetState = copied,
                transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
                label = "copy",
            ) { done ->
                AiIconButton(
                    icon = if (done) Lucide.Check else Lucide.Copy,
                    contentDescription = if (done) copiedLabel else copyLabel,
                    onClick = {
                        haptics.perform(HapticKind.Tick)
                        scope.launch {
                            runCatching { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("código", code))) }
                            copied = true
                        }
                    },
                    size = 48.dp,
                    iconSize = 16.dp,
                    tint = if (done) c.ok else c.fg3,
                )
            }
        }
        HorizontalDivider(color = c.line, thickness = 1.dp)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                highlighted,
                style = AiTheme.typography.mono,
                color = c.fg,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/** Cores do realce de sintaxe. */
data class SyntaxPalette(
    val keyword: Color,
    val type: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val punctuation: Color,
)

/** Acima deste tamanho o bloco de código é mostrado sem realce. */
internal const val HIGHLIGHT_LIMIT = 40_000

fun highlight(code: String, language: String?, p: SyntaxPalette): AnnotatedString = buildAnnotatedString {
    append(code)
    for (t in SyntaxHighlighter.tokenize(code, language)) {
        val style = when (t.type) {
            TokenType.Keyword -> SpanStyle(color = p.keyword, fontWeight = FontWeight.Medium)
            TokenType.Type -> SpanStyle(color = p.type)
            TokenType.String -> SpanStyle(color = p.string)
            TokenType.Number -> SpanStyle(color = p.number)
            TokenType.Comment -> SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)
            TokenType.Annotation -> SpanStyle(color = p.annotation)
            TokenType.Punctuation -> SpanStyle(color = p.punctuation)
        }
        addStyle(style, t.start.coerceIn(0, code.length), t.end.coerceIn(0, code.length))
    }
}

@Composable
private fun MdTable(table: MdBlock.Table, colors: MdColors, uri: UriHandler) {
    val c = AiTheme.colors
    val shape = AiTheme.shapes.sm
    val widths = remember(table) {
        val all = listOf(table.header) + table.rows
        List(table.alignments.size) { col ->
            val chars = all.maxOf { row -> row.getOrNull(col)?.plainText()?.length ?: 0 }
            (chars.coerceIn(4, 36) * 7.5f + 24f).dp
        }
    }
    val desc = stringResource(R.string.chat_table_description, table.rows.size, table.alignments.size)
    Box(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc }
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(Modifier.clip(shape).border(1.dp, c.line, shape)) {
            TableRowView(table.header, widths, table.alignments, colors, uri, header = true)
            table.rows.forEachIndexed { i, row ->
                HorizontalDivider(color = c.line, thickness = 1.dp, modifier = Modifier.width(widths.fold(0.dp) { a, b -> a + b }))
                TableRowView(row, widths, table.alignments, colors, uri, header = false, zebra = i % 2 == 1)
            }
        }
    }
}

@Composable
private fun TableRowView(
    cells: List<List<MdInline>>,
    widths: List<androidx.compose.ui.unit.Dp>,
    aligns: List<MdAlign>,
    colors: MdColors,
    uri: UriHandler,
    header: Boolean,
    zebra: Boolean = false,
) {
    val c = AiTheme.colors
    val bg = when {
        header -> c.surface2
        zebra -> c.surface.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    Row(Modifier.background(bg)) {
        cells.forEachIndexed { i, cell ->
            val style = (if (header) AiTheme.typography.label else AiTheme.typography.bodySmall).copy(
                textAlign = when (aligns.getOrNull(i)) {
                    MdAlign.Center -> TextAlign.Center
                    MdAlign.End -> TextAlign.End
                    else -> TextAlign.Start
                },
            )
            Box(Modifier.width(widths.getOrElse(i) { 80.dp }).padding(horizontal = 10.dp, vertical = 8.dp)) {
                InlineText(cell, style, colors, uri, caret = false, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
