package br.com.amberwrite.aistack.feature.files

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.EntryKind
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.formatBytes
import br.com.amberwrite.aistack.feature.common.relativeTime
import br.com.amberwrite.aistack.feature.files.kit.ErrorState
import br.com.amberwrite.aistack.feature.files.kit.F4Art
import br.com.amberwrite.aistack.feature.files.kit.F4EmptyState
import br.com.amberwrite.aistack.feature.files.kit.SkeletonList
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.tokens.AiStackColors
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.launch

/** Limite de entradas por pasta no contrato §9.3 (mostrado quando `truncated`). */
private const val LIST_DIR_MAX = 500

/**
 * Explorador de arquivos do desktop (§4.6): trilha animada, ícones por tipo, pastas primeiro,
 * busca local e estados de vazio / erro / sem permissão. A navegação entre pastas acontece
 * dentro da tela (o voltar do sistema volta no histórico antes de fechar).
 *
 * @param onOpenDir mantido por compatibilidade com a navegação; as pastas abrem na própria tela.
 * @param onMention "Mencionar no chat": a navegação deve abrir `Routes.chat(convId, mention = path)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    conversationId: String,
    path: String?,
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenDir: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onMention: (String) -> Unit = {}
) {
    val vm = containerViewModel(key = "files:$conversationId:$path") { FilesViewModel(it, conversationId, path) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAiHaptics()
    val copyPath = rememberCopyText(stringResource(R.string.files_path_copied))

    BackHandler(enabled = state.canGoBack || state.searchOpen) { vm.back() }

    val title = when {
        state.path == null -> stringResource(R.string.files_roots_title)
        else -> FilesLogic.displayName(state.path!!)
    }
    FeatureScaffold(
        title = title,
        subtitle = state.path,
        onBack = { if (!vm.back()) onBack() },
        actions = {
            AiIconButton(
                icon = Lucide.Search,
                contentDescription = stringResource(R.string.files_search),
                onClick = { if (state.searchOpen) vm.closeSearch() else vm.openSearch() },
                size = 48.dp,
                enabled = state.entries.isNotEmpty(),
                haptic = HapticKind.Tick,
                haptics = haptics
            )
            AiIconButton(
                icon = Lucide.RefreshCw,
                contentDescription = stringResource(R.string.files_reload),
                onClick = vm::reload,
                size = 48.dp,
                haptic = HapticKind.Tick,
                haptics = haptics
            )
        }
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxSize().widthIn(max = 840.dp)) {
                Breadcrumbs(crumbs = state.crumbs, onCrumb = { haptics.perform(HapticKind.Tick); vm.openCrumb(it) })
                AnimatedVisibility(
                    visible = state.searchOpen,
                    enter = expandVertically(AiTheme.motion.spring()) + fadeIn(AiTheme.motion.fade()),
                    exit = shrinkVertically(AiTheme.motion.exit()) + fadeOut(AiTheme.motion.fade())
                ) {
                    SearchBar(query = state.query, onQuery = vm::setQuery, onClose = vm::closeSearch)
                }
                AnimatedVisibility(visible = state.truncated) {
                    InfoBanner(stringResource(R.string.files_truncated, LIST_DIR_MAX))
                }
                val motion = AiTheme.motion
                val reduced = AiTheme.reducedMotion
                AnimatedContent(
                    targetState = state.path,
                    transitionSpec = {
                        val dir = state.direction
                        if (reduced) {
                            fadeIn(motion.fade()) togetherWith fadeOut(motion.fade())
                        } else {
                            (slideInHorizontally(motion.enter()) { w -> dir * w / 4 } + fadeIn(motion.fade()))
                                .togetherWith(slideOutHorizontally(motion.exit()) { w -> -dir * w / 6 } + fadeOut(motion.fade()))
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "files-dir"
                ) { shownPath ->
                    // Durante a transição a pasta antiga continua com o último estado conhecido.
                    if (shownPath != state.path) {
                        SkeletonList()
                        return@AnimatedContent
                    }
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = vm::reload,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        DirBody(
                            state = state,
                            onEntry = { entry ->
                                haptics.perform(HapticKind.Tick)
                                when (entry.kind) {
                                    EntryKind.DIR -> vm.openEntry(entry)
                                    EntryKind.FILE -> onOpenFile(vm.childPath(entry))
                                    EntryKind.OTHER -> Unit
                                }
                            },
                            onMention = { entry -> onMention(vm.childPath(entry)) },
                            onCopy = { entry -> copyPath(vm.childPath(entry)) },
                            onRetry = vm::reload,
                            onRoots = { vm.open(null) },
                            onClearSearch = vm::closeSearch,
                            onLongPress = { haptics.perform(HapticKind.LongPress) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirBody(
    state: FilesViewModel.UiState,
    onEntry: (DirEntry) -> Unit,
    onMention: (DirEntry) -> Unit,
    onCopy: (DirEntry) -> Unit,
    onRetry: () -> Unit,
    onRoots: () -> Unit,
    onClearSearch: () -> Unit,
    onLongPress: () -> Unit
) {
    val c = AiTheme.colors
    when {
        state.showSkeleton -> SkeletonList()
        state.showFullError -> {
            val kind = state.errorKind ?: FilesErrorKind.Generic
            val title = when (kind) {
                FilesErrorKind.NoPermission -> stringResource(R.string.files_error_permission_title)
                FilesErrorKind.NotFound -> stringResource(R.string.files_error_not_found_title)
                FilesErrorKind.NotDirectory -> stringResource(R.string.files_error_not_dir_title)
                FilesErrorKind.Offline -> stringResource(R.string.files_error_offline_title)
                FilesErrorKind.Generic -> stringResource(R.string.files_error_generic_title)
            }
            if (kind == FilesErrorKind.NoPermission) {
                F4EmptyState(
                    title = title,
                    art = F4Art.Locked,
                    body = stringResource(R.string.files_error_permission_body),
                    accent = c.warn,
                    action = if (!state.atRoots) {
                        {
                            AiButton(
                                text = stringResource(R.string.files_back_to_roots),
                                onClick = onRoots,
                                variant = ButtonVariant.Secondary,
                                leadingIcon = Lucide.FolderOpen
                            )
                        }
                    } else null
                )
            } else {
                ErrorState(message = state.error.orEmpty(), onRetry = onRetry, title = title)
            }
        }
        state.isEmptyFolder -> F4EmptyState(
            title = stringResource(if (state.atRoots) R.string.files_roots_empty_title else R.string.files_empty_title),
            body = stringResource(if (state.atRoots) R.string.files_roots_empty_body else R.string.files_empty_body),
            art = F4Art.EmptyFolder
        )
        state.noResults -> F4EmptyState(
            title = stringResource(R.string.files_no_results_title),
            body = stringResource(R.string.files_no_results_body, state.query.trim()),
            art = F4Art.NoResults,
            action = {
                AiButton(
                    text = stringResource(R.string.files_clear_search),
                    onClick = onClearSearch,
                    variant = ButtonVariant.Ghost,
                    leadingIcon = Lucide.X
                )
            }
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 24.dp)
        ) {
            if (state.error != null) {
                item(key = "error") {
                    InfoBanner(state.error, tone = c.danger, modifier = Modifier.animateItem())
                }
            }
            item(key = "count") {
                val total = state.entries.size
                val shown = state.visible.size
                Text(
                    text = if (state.query.isBlank()) stringResource(R.string.files_items_count, total)
                    else stringResource(R.string.files_filtered_count, shown, total),
                    style = AiTheme.typography.caption,
                    color = c.fg3,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).animateItem()
                )
            }
            items(state.visible, key = { "e:" + it.name }) { entry ->
                EntryRow(
                    entry = entry,
                    showFullPath = state.atRoots,
                    onClick = { onEntry(entry) },
                    onMention = { onMention(entry) },
                    onCopy = { onCopy(entry) },
                    onLongPress = onLongPress,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun Breadcrumbs(crumbs: List<Crumb>, onCrumb: (Crumb) -> Unit) {
    val c = AiTheme.colors
    val listState = rememberLazyListState()
    val rootsLabel = stringResource(R.string.files_roots)
    val desc = stringResource(R.string.files_breadcrumb)
    LaunchedEffect(crumbs.size, crumbs.lastOrNull()?.path) {
        if (crumbs.isNotEmpty()) listState.animateScrollToItem(crumbs.lastIndex)
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc },
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(crumbs, key = { _, cr -> "c:" + (cr.path ?: "\u0000") }) { index, crumb ->
            val last = index == crumbs.lastIndex
            val label = if (crumb.isRoots) rootsLabel else crumb.label
            val goTo = stringResource(R.string.files_crumb_desc, label)
            Row(Modifier.animateItem(), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    Icon(Lucide.ChevronRight, contentDescription = null, tint = c.fg3, modifier = Modifier.size(14.dp))
                }
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(AiTheme.shapes.sm)
                        .combinedClickable(
                            enabled = !last,
                            role = Role.Button,
                            onClickLabel = goTo,
                            onClick = { onCrumb(crumb) }
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (crumb.isRoots) {
                        Icon(Lucide.Layers, contentDescription = null, tint = if (last) c.accent else c.fg3, modifier = Modifier.size(14.dp).padding(end = 0.dp))
                    }
                    Text(
                        label,
                        style = AiTheme.typography.label,
                        color = if (last) c.fg else c.fg2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = if (crumb.isRoots) 6.dp else 0.dp)
                            .widthIn(max = 220.dp)
                            .let { if (last) it.semantics { heading() } else it }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AiTextInput(
            value = query,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.files_search_placeholder),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        AiIconButton(
            icon = Lucide.X,
            contentDescription = stringResource(R.string.files_search_close),
            onClick = onClose,
            size = 48.dp
        )
    }
}

@Composable
internal fun InfoBanner(text: String, modifier: Modifier = Modifier, tone: Color = AiTheme.colors.warn) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(AiTheme.shapes.md)
            .background(tone.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Lucide.Info, contentDescription = null, tint = tone, modifier = Modifier.size(16.dp))
        Text(text, style = AiTheme.typography.caption, color = AiTheme.colors.fg2, modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: DirEntry,
    showFullPath: Boolean,
    onClick: () -> Unit,
    onMention: () -> Unit,
    onCopy: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = AiTheme.colors
    var menu by remember { mutableStateOf(false) }
    val type = FilesLogic.fileType(entry.name, entry.kind)
    val (icon, tint) = iconFor(type, c)
    val name = if (showFullPath) FilesLogic.displayName(entry.name) else entry.name
    val kindLabel = stringResource(
        when (entry.kind) {
            EntryKind.DIR -> R.string.files_kind_folder
            EntryKind.FILE -> R.string.files_kind_file
            EntryKind.OTHER -> R.string.files_kind_other
        }
    )
    val optionsLabel = stringResource(R.string.files_entry_options, name)
    val openLabel = stringResource(R.string.files_open)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(AiTheme.shapes.md)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = openLabel,
                    onLongClickLabel = optionsLabel,
                    onClick = { if (entry.kind != EntryKind.OTHER) onClick() else menu = true },
                    onLongClick = {
                        onLongPress()
                        menu = true
                    }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(AiTheme.shapes.sm)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = kindLabel, tint = tint, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    name,
                    style = AiTheme.typography.body,
                    color = if (entry.kind == EntryKind.OTHER) c.fg3 else c.fg,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                val meta = buildList {
                    if (showFullPath) add(entry.name)
                    if (entry.kind == EntryKind.FILE && entry.size != null) add(formatBytes(entry.size))
                    if (entry.mtime != null) add(relativeTime(entry.mtime))
                }.filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = AiTheme.typography.caption, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            AiIconButton(
                icon = Lucide.EllipsisVertical,
                contentDescription = optionsLabel,
                onClick = { menu = true },
                size = 48.dp,
                iconSize = 18.dp,
                tint = c.fg3
            )
            if (entry.kind == EntryKind.DIR) {
                Icon(Lucide.ChevronRight, contentDescription = null, tint = c.fg3, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surface) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_mention), color = c.fg) },
                leadingIcon = { Icon(Lucide.AtSign, contentDescription = null, tint = c.accent) },
                onClick = { menu = false; onMention() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_copy_path), color = c.fg) },
                leadingIcon = { Icon(Lucide.Copy, contentDescription = null, tint = c.fg2) },
                onClick = { menu = false; onCopy() }
            )
        }
    }
}

private fun iconFor(type: FileType, c: AiStackColors): Pair<ImageVector, Color> = when (type) {
    FileType.Folder -> Lucide.Folder to c.accent
    FileType.Code -> Lucide.FileCode to c.ok
    FileType.Markup -> Lucide.FileCode to c.providers.agy
    FileType.Config -> Lucide.Wrench to c.warn
    FileType.Text -> Lucide.FileText to c.fg2
    FileType.Image -> Lucide.Image to c.providers.kimi
    FileType.Archive -> Lucide.Archive to c.warn
    FileType.Data -> Lucide.Layers to c.fg3
    FileType.Lock -> Lucide.Lock to c.fg3
    FileType.Other -> Lucide.File to c.fg3
}

/**
 * Copia texto para a área de transferência (suspensa, fora do clique) e confirma com um aviso
 * curto em versões sem o painel de cópia do sistema (Android 12 ou anterior).
 */
@Composable
internal fun rememberCopyText(confirmation: String): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = rememberAiHaptics()
    return remember(clipboard, scope, confirmation) {
        { text: String ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("AiStack", text)))
                haptics.perform(HapticKind.Confirm)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
                }
            }
            Unit
        }
    }
}
