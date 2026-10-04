package br.com.amberwrite.aistack.feature.fileview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.FileContent
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.formatBytes
import br.com.amberwrite.aistack.feature.files.FilesErrorKind
import br.com.amberwrite.aistack.feature.files.InfoBanner
import br.com.amberwrite.aistack.feature.files.kit.ErrorState
import br.com.amberwrite.aistack.feature.files.kit.F4Art
import br.com.amberwrite.aistack.feature.files.kit.F4EmptyState
import br.com.amberwrite.aistack.feature.files.kit.SkeletonBlock
import br.com.amberwrite.aistack.feature.files.rememberCopyText
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiChip
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.TagBadge
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Linhas maiores que isto são cortadas na exibição (o texto completo continua no "copiar"). */
private const val MAX_RENDER_CHARS = 4_000

private enum class Phase { Skeleton, Error, Binary, Empty, Text }

/**
 * Visualizador somente leitura (§4.6): realce de sintaxe, números de linha, quebra opcional,
 * copiar tudo / copiar linha (toque longo), leitura progressiva e aviso de binário ou arquivo grande.
 *
 * @param onMention "Mencionar no chat": a navegação deve abrir `Routes.chat(convId, mention = path)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewScreen(
    conversationId: String,
    path: String,
    onBack: () -> Unit,
    onMention: (String) -> Unit = {}
) {
    val vm = containerViewModel(key = "fileView:$conversationId:$path") { FileViewViewModel(it, path) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAiHaptics()
    val copyAll = rememberCopyText(stringResource(R.string.fileview_copied))
    val content = state.content

    val parent = path.substringBeforeLast('/', "").ifEmpty { path.substringBeforeLast('\\', "") }
    FeatureScaffold(
        title = state.fileName,
        subtitle = listOfNotNull(parent.ifBlank { null }, content?.size?.let { formatBytes(it) }).joinToString(" · "),
        onBack = onBack,
        actions = {
            AiIconButton(
                icon = Lucide.AtSign,
                contentDescription = stringResource(R.string.fileview_mention),
                onClick = { onMention(path) },
                size = 48.dp,
                haptic = HapticKind.Tick,
                haptics = haptics
            )
            AiIconButton(
                icon = Lucide.Copy,
                contentDescription = stringResource(R.string.fileview_copy_all),
                onClick = { (content as? FileContent.Text)?.let { copyAll(it.text) } },
                size = 48.dp,
                enabled = state.isText && !state.isEmptyText
            )
            AiIconButton(
                icon = Lucide.RefreshCw,
                contentDescription = stringResource(R.string.fileview_reload),
                onClick = vm::reload,
                size = 48.dp,
                haptic = HapticKind.Tick,
                haptics = haptics
            )
        }
    ) {
        val phase = when {
            state.showSkeleton -> Phase.Skeleton
            state.showFullError -> Phase.Error
            state.isBinary -> Phase.Binary
            state.isEmptyText -> Phase.Empty
            content != null -> Phase.Text
            else -> Phase.Skeleton
        }
        val motion = AiTheme.motion
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "fileview-phase"
        ) { p ->
            when (p) {
                Phase.Skeleton -> CodeSkeleton()
                Phase.Error -> ViewError(state, onRetry = vm::reload)
                Phase.Binary -> {
                    val bin = content as? FileContent.Binary
                    val meta = listOfNotNull(bin?.mime, bin?.size?.let { formatBytes(it) }).joinToString(" · ")
                    F4EmptyState(
                        title = stringResource(R.string.fileview_binary_title),
                        art = F4Art.BinaryFile,
                        body = listOfNotNull(
                            bin?.warning ?: stringResource(R.string.fileview_binary_body),
                            meta.ifBlank { null }
                        ).joinToString("\n"),
                        accent = AiTheme.colors.warn,
                        action = {
                            AiButton(
                                text = stringResource(R.string.fileview_mention),
                                onClick = { onMention(path) },
                                variant = ButtonVariant.Secondary,
                                leadingIcon = Lucide.AtSign
                            )
                        }
                    )
                }
                Phase.Empty -> F4EmptyState(
                    title = stringResource(R.string.fileview_empty_title),
                    body = stringResource(R.string.fileview_empty_body),
                    art = F4Art.EmptyFolder
                )
                Phase.Text -> PullToRefreshBox(
                    isRefreshing = state.loading && content != null,
                    onRefresh = vm::reload,
                    modifier = Modifier.fillMaxSize()
                ) {
                    TextBody(state = state, onToggleWrap = vm::toggleWrap, onLoadMore = vm::loadMore)
                }
            }
        }
    }
}

@Composable
private fun ViewError(state: FileViewViewModel.UiState, onRetry: () -> Unit) {
    when (state.errorKind) {
        FilesErrorKind.NoPermission -> F4EmptyState(
            title = stringResource(R.string.fileview_error_permission),
            body = stringResource(R.string.fileview_error_permission_body),
            art = F4Art.Locked,
            accent = AiTheme.colors.warn
        )
        else -> ErrorState(
            message = state.error.orEmpty(),
            onRetry = onRetry,
            title = stringResource(
                when (state.errorKind) {
                    FilesErrorKind.NotFound -> R.string.fileview_error_not_found
                    FilesErrorKind.Offline -> R.string.fileview_error_offline
                    else -> R.string.fileview_error_title
                }
            )
        )
    }
}

@Composable
private fun CodeSkeleton() {
    val widths = remember { listOf(0.55f, 0.8f, 0.4f, 0.7f, 0.9f, 0.3f, 0.65f, 0.5f, 0.85f, 0.45f, 0.6f, 0.35f) }
    val desc = stringResource(R.string.fileview_loading)
    Column(
        Modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = desc },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        widths.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(width = 18.dp, height = 12.dp)
                Spacer(Modifier.width(14.dp))
                Box(Modifier.fillMaxWidth(f)) { SkeletonBlock(width = null, height = 12.dp) }
            }
        }
    }
}

@Composable
private fun TextBody(state: FileViewViewModel.UiState, onToggleWrap: () -> Unit, onLoadMore: () -> Unit) {
    val c = AiTheme.colors
    val content = state.content as? FileContent.Text ?: return
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.language?.let { TagBadge(text = it, icon = Lucide.FileCode, color = c.accent) }
            Text(
                listOfNotNull(
                    stringResource(R.string.fileview_lines, state.lines.size),
                    content.size?.let { formatBytes(it) }
                ).joinToString(" · "),
                style = AiTheme.typography.caption,
                color = c.fg3,
                modifier = Modifier.weight(1f)
            )
            AiChip(
                text = stringResource(R.string.fileview_wrap),
                selected = state.wrap,
                onClick = onToggleWrap,
                leadingIcon = if (state.wrap) Lucide.Check else null,
                modifier = Modifier.minimumInteractiveComponentSize()
            )
        }
        state.error?.let { InfoBanner(it, tone = c.danger) }
        AnimatedVisibility(
            visible = state.truncated,
            enter = expandVertically(AiTheme.motion.spring()) + fadeIn(AiTheme.motion.fade()),
            exit = shrinkVertically(AiTheme.motion.exit()) + fadeOut(AiTheme.motion.fade())
        ) {
            TruncationBanner(state, onLoadMore)
        }
        CodeLines(state)
    }
}

@Composable
private fun TruncationBanner(state: FileViewViewModel.UiState, onLoadMore: () -> Unit) {
    val c = AiTheme.colors
    val shown = formatBytes(state.shownBytes)
    val total = state.content?.size?.let { formatBytes(it) }
    val text = when {
        state.hitCeiling -> if (state.shownBytes > 0) stringResource(R.string.fileview_ceiling, shown) else stringResource(R.string.fileview_ceiling_unknown)
        total != null -> stringResource(R.string.fileview_partial, shown, total)
        else -> stringResource(R.string.fileview_partial_unknown, shown)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(AiTheme.shapes.md)
            .background(c.warnSoft)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Lucide.TriangleAlert, contentDescription = null, tint = c.warn, modifier = Modifier.size(16.dp))
        Text(text, style = AiTheme.typography.caption, color = c.fg2, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        if (state.canLoadMore) {
            AiButton(
                text = stringResource(R.string.fileview_load_more),
                onClick = onLoadMore,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Large,
                loading = state.loadingMore,
                leadingIcon = Lucide.ArrowDown
            )
        }
    }
}

@Immutable
private data class CodePalette(
    val keyword: Color, val type: Color, val str: Color, val comment: Color, val number: Color,
    val annotation: Color, val tag: Color, val attr: Color, val key: Color, val heading: Color, val punct: Color
)

@Composable
private fun rememberPalette(): CodePalette {
    val c = AiTheme.colors
    return remember(c) {
        CodePalette(
            keyword = c.accent, type = c.providers.agy, str = c.ok, comment = c.fg3, number = c.warn,
            annotation = c.providers.kimi, tag = c.accent, attr = c.warn, key = c.providers.claude,
            heading = c.accent, punct = c.fg3
        )
    }
}

private fun annotate(text: String, tokens: List<Token>, p: CodePalette): AnnotatedString = buildAnnotatedString {
    append(text)
    val len = text.length
    tokens.forEach { t ->
        if (t.start >= len) return@forEach
        val end = minOf(t.end, len)
        val style = when (t.kind) {
            TokenKind.Keyword -> SpanStyle(color = p.keyword, fontWeight = FontWeight.Medium)
            TokenKind.Type -> SpanStyle(color = p.type)
            TokenKind.Str -> SpanStyle(color = p.str)
            TokenKind.Comment -> SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)
            TokenKind.Number -> SpanStyle(color = p.number)
            TokenKind.Annotation -> SpanStyle(color = p.annotation)
            TokenKind.Tag -> SpanStyle(color = p.tag)
            TokenKind.Attr -> SpanStyle(color = p.attr)
            TokenKind.Key -> SpanStyle(color = p.key)
            TokenKind.Heading -> SpanStyle(color = p.heading, fontWeight = FontWeight.SemiBold)
            TokenKind.Punct -> SpanStyle(color = p.punct)
        }
        addStyle(style, t.start, end)
    }
}

@Composable
private fun CodeLines(state: FileViewViewModel.UiState) {
    val c = AiTheme.colors
    val style = AiTheme.typography.monoSmall
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val charPx = remember(style, density) { measurer.measure("0", style).size.width.toFloat() }
    val digits = state.lines.size.coerceAtLeast(1).toString().length
    val gutter = with(density) { (charPx * digits).toDp() } + 20.dp
    val renderChars = minOf(state.maxLineLength, MAX_RENDER_CHARS + 1)
    val contentWidth = with(density) { (charPx * renderChars).toDp() } + 24.dp
    val palette = rememberPalette()
    val hScroll = rememberScrollState()
    val copyLine = rememberCopyText(stringResource(R.string.fileview_copied))
    val haptics = rememberAiHaptics()
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
    ) {
        itemsIndexed(state.lines, key = { i, _ -> i }) { index, line ->
            CodeLine(
                number = index + 1,
                line = line,
                tokens = state.tokens.getOrNull(index).orEmpty(),
                wrap = state.wrap,
                gutter = gutter,
                contentWidth = contentWidth,
                hScroll = hScroll,
                style = style,
                palette = palette,
                gutterColor = c.fg3,
                onCopy = {
                    haptics.perform(HapticKind.LongPress)
                    copyLine(line)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodeLine(
    number: Int,
    line: String,
    tokens: List<Token>,
    wrap: Boolean,
    gutter: Dp,
    contentWidth: Dp,
    hScroll: ScrollState,
    style: TextStyle,
    palette: CodePalette,
    gutterColor: Color,
    onCopy: () -> Unit
) {
    val shown = if (line.length > MAX_RENDER_CHARS) line.substring(0, MAX_RENDER_CHARS) + "…" else line
    val annotated = remember(shown, tokens, palette) { annotate(shown, tokens, palette) }
    val copyLabel = stringResource(R.string.fileview_copy_line, number)
    val desc = stringResource(R.string.fileview_line_desc, number, shown)
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onCopy, onLongClickLabel = copyLabel)
            .semantics(mergeDescendants = true) { contentDescription = desc }
    ) {
        Text(
            text = number.toString(),
            style = style,
            color = gutterColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(gutter).padding(end = 10.dp)
        )
        if (wrap) {
            Text(annotated, style = style, color = AiTheme.colors.fg, softWrap = true, modifier = Modifier.weight(1f).padding(end = 12.dp))
        } else {
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                Text(
                    annotated,
                    style = style,
                    color = AiTheme.colors.fg,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.width(contentWidth).padding(end = 12.dp)
                )
            }
        }
    }
}
