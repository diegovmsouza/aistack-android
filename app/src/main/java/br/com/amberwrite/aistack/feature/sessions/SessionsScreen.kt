package br.com.amberwrite.aistack.feature.sessions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.ConfirmDialog
import br.com.amberwrite.aistack.feature.common.bannerDetail
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.label
import br.com.amberwrite.aistack.feature.common.relativeTime
import br.com.amberwrite.aistack.feature.common.toBanner
import br.com.amberwrite.aistack.navigation.SharedKeys
import br.com.amberwrite.aistack.navigation.aiSharedBounds
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AgentStatus
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiChip
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiStackTopBar
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.ConnectionBanner
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.Origin
import br.com.amberwrite.aistack.ui.designsystem.components.OriginBadge
import br.com.amberwrite.aistack.ui.designsystem.components.ProviderBadge
import br.com.amberwrite.aistack.ui.designsystem.components.StatusDot
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.designsystem.components.TagBadge
import br.com.amberwrite.aistack.ui.designsystem.illustrations.Illustration
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.launch

/**
 * Tela inicial (pareado): conversas do desktop com busca, filtro por projeto, grupos de data,
 * status ao vivo, deslizar para arquivar/renomear e o botão «Nova sessão».
 *
 * @param selectedId conversa aberta no painel de detalhe (layout expandido), destacada na lista.
 */
@Composable
fun SessionsScreen(
    onOpenChat: (String) -> Unit,
    onNewSession: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenSettings: () -> Unit,
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
) {
    val vm = containerViewModel { SessionsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAiHaptics()
    val c = AiTheme.colors
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }

    // Grupos de data ("Hoje", "Ontem") podem mudar enquanto o app estava em segundo plano.
    LifecycleResumeEffect(vm) {
        vm.touch()
        onPauseOrDispose { }
    }

    val archivedMsg = state.archivedNotice?.let {
        stringResource(if (it.archived) R.string.sessions_archived_notice else R.string.sessions_unarchived_notice, it.title)
    }
    val undoLabel = stringResource(R.string.sessions_undo)
    LaunchedEffect(state.archivedNotice) {
        val msg = archivedMsg ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(msg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) vm.undoArchive() else vm.consumeNotice()
    }
    val bulkMsg = state.bulkNotice?.let {
        pluralStringResource(
            when (it.kind) {
                SessionsViewModel.BulkNotice.Kind.Archived -> R.plurals.sessions_archived_many_notice
                SessionsViewModel.BulkNotice.Kind.Unarchived -> R.plurals.sessions_unarchived_many_notice
                SessionsViewModel.BulkNotice.Kind.Deleted -> R.plurals.sessions_deleted_notice
            },
            it.count, it.count,
        )
    }
    LaunchedEffect(state.bulkNotice) {
        val msg = bulkMsg ?: return@LaunchedEffect
        snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
        vm.consumeBulkNotice()
    }
    val selecting = state.selection.isNotEmpty()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = selecting) { vm.clearSelection() }
    LaunchedEffect(state.actionError) {
        val msg = state.actionError ?: return@LaunchedEffect
        haptics.perform(HapticKind.Reject)
        snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
        vm.consumeActionError()
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
            if (selecting) AiStackTopBar(
                title = pluralStringResource(R.plurals.sessions_selected_count, state.selection.size, state.selection.size),
                navigationIcon = Lucide.X,
                navigationContentDescription = stringResource(R.string.sessions_selection_clear),
                onNavigationClick = vm::clearSelection,
                scrolled = scrolled,
                haptics = haptics,
                actions = {
                    AiIconButton(
                        icon = Lucide.Archive,
                        contentDescription = stringResource(
                            if (state.selectionArchived) R.string.sessions_selection_unarchive else R.string.sessions_selection_archive
                        ),
                        onClick = { haptics.perform(HapticKind.Confirm); vm.archiveSelection() },
                        size = 48.dp,
                        haptics = haptics,
                    )
                    AiIconButton(
                        icon = Lucide.Trash2,
                        contentDescription = stringResource(R.string.sessions_selection_delete),
                        onClick = { confirmDelete = true },
                        size = 48.dp,
                        haptics = haptics,
                    )
                },
            ) else AiStackTopBar(
                title = stringResource(R.string.sessions_title),
                subtitle = state.connection.label(),
                navigationIcon = null,
                status = state.connection.toAgentStatus(),
                scrolled = scrolled,
                haptics = haptics,
                actions = {
                    PendingBell(count = state.pendingCount, onClick = onOpenPending, haptics = haptics)
                    AiIconButton(
                        icon = Lucide.Settings,
                        contentDescription = stringResource(R.string.sessions_settings_cd),
                        onClick = onOpenSettings,
                        size = 48.dp,
                        haptics = haptics,
                    )
                },
            )
            ConnectionBanner(
                state = state.connection.toBanner(),
                detail = state.connection.bannerDetail(),
                onRetry = vm::retryConnection,
                onRepair = onRepair,
            )
            SearchField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.padding(horizontal = AiTheme.spacing.gutter, vertical = 8.dp),
            )
            FilterRow(
                projects = state.projects,
                selected = state.project,
                includeArchived = state.includeArchived,
                onSelect = vm::setProject,
                onToggleArchived = { vm.setIncludeArchived(!state.includeArchived) },
                haptics = haptics,
            )
            SessionsBody(
                state = state,
                listState = listState,
                selectedId = selectedId,
                haptics = haptics,
                onRefresh = vm::refresh,
                onRetry = vm::refresh,
                onClearFilters = { vm.setQuery(""); vm.setProject(null) },
                onNewSession = onNewSession,
                onOpenChat = onOpenChat,
                onArchive = { item -> vm.archive(item.id, !item.conversation.archived) },
                onRename = { item -> renameId = item.id },
                onToggleSelect = { item -> haptics.perform(HapticKind.Tick); vm.toggleSelection(item.id) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
        if (!selecting) NewSessionFab(
            expanded = fabExpanded,
            onClick = onNewSession,
            haptics = haptics,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(AiTheme.spacing.gutter),
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp, start = 16.dp, end = 16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = AiTheme.shapes.md,
                containerColor = c.fg,
                contentColor = c.bg,
                actionColor = c.accent,
            )
        }
    }

    val renameTarget = renameId?.let { id -> state.sections.firstNotNullOfOrNull { s -> s.items.firstOrNull { it.id == id } } }
    if (confirmDelete) {
        val n = state.selection.size
        ConfirmDialog(
            title = pluralStringResource(R.plurals.sessions_delete_title, n, n),
            text = stringResource(R.string.sessions_delete_text),
            confirmLabel = stringResource(R.string.sessions_delete_confirm),
            dismissLabel = stringResource(R.string.sessions_cancel),
            destructive = true,
            onConfirm = { confirmDelete = false; haptics.perform(HapticKind.Confirm); vm.deleteSelection() },
            onDismiss = { confirmDelete = false },
        )
    }
        if (renameId != null) {
        if (renameTarget == null) {
            LaunchedEffect(renameId, state.loaded) { if (state.loaded) renameId = null }
        } else {
            RenameDialog(
                initial = renameTarget.conversation.displayTitle,
                onConfirm = { title -> vm.rename(renameTarget.id, title); renameId = null },
                onDismiss = { renameId = null },
            )
        }
    }
}

private fun ConnectionState.toAgentStatus(): AgentStatus = when (this) {
    is ConnectionState.Online -> AgentStatus.Online
    is ConnectionState.Connecting, ConnectionState.Handshaking -> AgentStatus.Busy
    is ConnectionState.AuthRejected, is ConnectionState.Error, ConnectionState.Revoked -> AgentStatus.Error
    else -> AgentStatus.Offline
}

@Composable
private fun PendingBell(count: Int, onClick: () -> Unit, haptics: AiHaptics) {
    val c = AiTheme.colors
    val cd = if (count > 0) pluralStringResource(R.plurals.sessions_pending_count_cd, count, count)
    else stringResource(R.string.sessions_pending_cd)
    Box {
        AiIconButton(icon = Lucide.Bell, contentDescription = cd, onClick = onClick, size = 48.dp, haptics = haptics)
        AnimatedVisibility(
            visible = count > 0,
            enter = scaleIn(AiTheme.motion.bouncy()) + fadeIn(AiTheme.motion.fade()),
            exit = scaleOut(AiTheme.motion.exit()) + fadeOut(AiTheme.motion.fade()),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp),
        ) {
            val motion = AiTheme.motion
            AnimatedContent(
                targetState = count,
                transitionSpec = { (scaleIn(motion.bouncy()) + fadeIn()) togetherWith fadeOut() },
                label = "pendingCount",
            ) { n ->
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(AiTheme.shapes.pill)
                        .background(c.warn)
                        .padding(horizontal = 4.dp)
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (n > 99) "99+" else n.toString(),
                        style = AiTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    val focus = LocalFocusManager.current
    val placeholder = stringResource(R.string.sessions_search_placeholder)
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(AiTheme.shapes.md)
            .background(c.surface)
            .border(1.dp, c.line, AiTheme.shapes.md)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Search, contentDescription = null, tint = c.fg3, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = AiTheme.typography.body.copy(color = c.fg),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = AiTheme.typography.body, color = c.fg3, maxLines = 1)
                    inner()
                }
            },
        )
        AnimatedVisibility(
            visible = value.isNotEmpty(),
            enter = fadeIn(AiTheme.motion.fade()) + scaleIn(AiTheme.motion.spring()),
            exit = fadeOut(AiTheme.motion.fade()) + scaleOut(AiTheme.motion.exit()),
        ) {
            AiIconButton(
                icon = Lucide.X,
                contentDescription = stringResource(R.string.sessions_search_clear),
                onClick = { onValueChange("") },
                size = 48.dp,
            )
        }
    }
}

@Composable
private fun FilterRow(
    projects: List<String>,
    selected: String?,
    includeArchived: Boolean,
    onSelect: (String?) -> Unit,
    onToggleArchived: () -> Unit,
    haptics: AiHaptics,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = AiTheme.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "all") {
            AiChip(
                text = stringResource(R.string.sessions_filter_all),
                selected = selected == null,
                onClick = { onSelect(null) },
                haptics = haptics,
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
        }
        items(projects, key = { "p:$it" }) { path ->
            AiChip(
                text = projectNameOf(path),
                selected = selected == path,
                leadingIcon = Lucide.Folder,
                onClick = { onSelect(if (selected == path) null else path) },
                haptics = haptics,
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
        }
        item(key = "archived") {
            AiChip(
                text = stringResource(R.string.sessions_filter_archived),
                selected = includeArchived,
                leadingIcon = Lucide.Archive,
                onClick = onToggleArchived,
                color = AiTheme.colors.warn,
                haptics = haptics,
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsBody(
    state: SessionsViewModel.UiState,
    listState: LazyListState,
    selectedId: String?,
    haptics: AiHaptics,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onClearFilters: () -> Unit,
    onNewSession: () -> Unit,
    onOpenChat: (String) -> Unit,
    onArchive: (SessionItem) -> Unit,
    onRename: (SessionItem) -> Unit,
    onToggleSelect: (SessionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AiTheme.colors
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { haptics.perform(HapticKind.Tick); onRefresh() },
        state = pullState,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = state.refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = c.surface,
                color = c.accent,
            )
        },
    ) {
        val motion = AiTheme.motion
        AnimatedContent(
            targetState = state.content,
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
            label = "sessionsContent",
            modifier = Modifier.fillMaxSize(),
        ) { content ->
            when (content) {
                ListContent.Loading -> SessionsSkeleton(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    description = stringResource(R.string.sessions_loading_cd),
                )
                ListContent.Offline -> ScrollableEmpty {
                    EmptyState(
                        title = stringResource(R.string.sessions_offline_title),
                        body = state.connection.bannerDetail() ?: stringResource(R.string.sessions_offline_body),
                        illustration = Illustration.Offline,
                        accent = c.warn,
                        illustrationWidth = 128.dp,
                        primaryAction = {
                            AiButton(
                                text = stringResource(R.string.sessions_retry),
                                onClick = onRetry,
                                variant = ButtonVariant.Secondary,
                                leadingIcon = Lucide.RefreshCw,
                            )
                        },
                    )
                }
                ListContent.Error -> ScrollableEmpty {
                    EmptyState(
                        title = stringResource(R.string.sessions_error_title),
                        body = state.error,
                        illustration = Illustration.Offline,
                        accent = c.danger,
                        illustrationWidth = 128.dp,
                        primaryAction = {
                            AiButton(
                                text = stringResource(R.string.sessions_retry),
                                onClick = onRetry,
                                variant = ButtonVariant.Secondary,
                                leadingIcon = Lucide.RefreshCw,
                            )
                        },
                    )
                }
                ListContent.Empty -> ScrollableEmpty {
                    EmptyState(
                        title = stringResource(R.string.sessions_empty_title),
                        body = stringResource(R.string.sessions_empty_body),
                        illustration = Illustration.NoSessions,
                        primaryAction = {
                            AiButton(
                                text = stringResource(R.string.sessions_new),
                                onClick = onNewSession,
                                leadingIcon = Lucide.Plus,
                            )
                        },
                    )
                }
                ListContent.NoResults -> ScrollableEmpty {
                    EmptyState(
                        title = stringResource(R.string.sessions_no_results_title),
                        body = stringResource(R.string.sessions_no_results_body),
                        illustration = Illustration.NoSessions,
                        illustrationWidth = 128.dp,
                        primaryAction = {
                            AiButton(
                                text = stringResource(R.string.sessions_clear_filters),
                                onClick = onClearFilters,
                                variant = ButtonVariant.Secondary,
                                leadingIcon = Lucide.X,
                            )
                        },
                    )
                }
                ListContent.List -> SessionsList(
                    sections = state.sections,
                    listState = listState,
                    selectedId = selectedId,
                    haptics = haptics,
                    onOpenChat = onOpenChat,
                    onArchive = onArchive,
                    onRename = onRename,
                    selection = state.selection,
                    onToggleSelect = onToggleSelect,
                )
            }
        }
    }
}

/** Estados vazios ficam roláveis para o "puxar para atualizar" continuar funcionando. */
@Composable
private fun ScrollableEmpty(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) { content() }
}

@Composable
private fun SessionsList(
    sections: List<SessionSection>,
    listState: LazyListState,
    selectedId: String?,
    haptics: AiHaptics,
    onOpenChat: (String) -> Unit,
    onArchive: (SessionItem) -> Unit,
    onRename: (SessionItem) -> Unit,
    selection: Set<String>,
    onToggleSelect: (SessionItem) -> Unit,
) {
    val reduced = AiTheme.reducedMotion
    val selecting = selection.isNotEmpty()
    val now = System.currentTimeMillis()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = AiTheme.spacing.gutter, end = AiTheme.spacing.gutter, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            item(key = "h:${section.group.name}", contentType = "header") {
                GroupHeader(
                    section.group,
                    Modifier.then(if (reduced) Modifier else Modifier.animateItem()),
                )
            }
            items(section.items, key = { it.id }, contentType = { "row" }) { item ->
                SwipeableSessionRow(
                    item = item,
                    selected = item.id == selectedId || item.id in selection,
                    selecting = selecting,
                    now = now,
                    haptics = haptics,
                    onOpen = { if (selecting) onToggleSelect(item) else onOpenChat(item.id) },
                    onLongPress = { onToggleSelect(item) },
                    onArchive = { onArchive(item) },
                    onRename = { onRename(item) },
                    modifier = Modifier.then(if (reduced) Modifier else Modifier.animateItem()),
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(group: DateGroup, modifier: Modifier = Modifier) {
    val text = stringResource(
        when (group) {
            DateGroup.Today -> R.string.sessions_group_today
            DateGroup.Yesterday -> R.string.sessions_group_yesterday
            DateGroup.Last7Days -> R.string.sessions_group_week
            DateGroup.Older -> R.string.sessions_group_older
        }
    )
    Text(
        text.uppercase(),
        style = AiTheme.typography.overline,
        color = AiTheme.colors.fg3,
        modifier = modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SwipeableSessionRow(
    item: SessionItem,
    selected: Boolean,
    selecting: Boolean,
    now: Long,
    haptics: AiHaptics,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onArchive: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val swipe = rememberSwipeToDismissBoxState()
    val archiveLabel = stringResource(
        if (item.conversation.archived) R.string.sessions_swipe_unarchive else R.string.sessions_swipe_archive
    )
    val renameLabel = stringResource(R.string.sessions_swipe_rename)

    // Vibra ao cruzar o limiar do gesto.
    LaunchedEffect(swipe.targetValue) {
        if (swipe.targetValue != SwipeToDismissBoxValue.Settled) haptics.perform(HapticKind.Tick)
    }
    // O SwipeToDismissBox chama onDismiss num LaunchedEffect(settledValue, onDismiss): a lambda
    // precisa ser estável, senão cada mudança da linha (ex.: arquivada ↔ não) refaz a ação em laço.
    val currentArchive by rememberUpdatedState(onArchive)
    val currentRename by rememberUpdatedState(onRename)
    val onDismiss: (SwipeToDismissBoxValue) -> Unit = remember(swipe) {
        { value ->
            scope.launch { swipe.reset() }
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { haptics.perform(HapticKind.Confirm); currentArchive() }
                SwipeToDismissBoxValue.StartToEnd -> currentRename()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        }
    }

    SwipeToDismissBox(
        state = swipe,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(archiveLabel) { onArchive(); true },
                CustomAccessibilityAction(renameLabel) { onRename(); true },
            )
        },
        onDismiss = onDismiss,
        gesturesEnabled = !selecting,
        backgroundContent = {
            SwipeBackground(
                direction = swipe.dismissDirection,
                archiveLabel = archiveLabel,
                renameLabel = renameLabel,
            )
        },
    ) {
        SessionRow(item = item, selected = selected, now = now, onClick = onOpen, onLongClick = onLongPress)
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, archiveLabel: String, renameLabel: String) {
    val c = AiTheme.colors
    val bg by animateColorAsState(
        when (direction) {
            SwipeToDismissBoxValue.EndToStart -> c.warnSoft
            SwipeToDismissBoxValue.StartToEnd -> c.accentSoft
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        },
        AiTheme.motion.fade(),
        label = "swipeBg",
    )
    Row(
        Modifier.fillMaxSize().clip(AiTheme.shapes.lg).background(bg).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (direction == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
    ) {
        when (direction) {
            SwipeToDismissBoxValue.EndToStart -> {
                Text(archiveLabel, style = AiTheme.typography.label, color = c.warn)
                Spacer(Modifier.width(8.dp))
                Icon(Lucide.Archive, contentDescription = null, tint = c.warn, modifier = Modifier.size(20.dp))
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                Icon(Lucide.PencilLine, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(renameLabel, style = AiTheme.typography.label, color = c.accent)
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
}

@Composable
private fun SessionRow(item: SessionItem, selected: Boolean, now: Long, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = AiTheme.colors
    val conv = item.conversation
    val openCd = stringResource(R.string.sessions_open_cd, conv.displayTitle)
    val bg by animateColorAsState(if (selected) c.accentSoft else c.surface, AiTheme.motion.fade(), label = "rowBg")
    val border by animateColorAsState(if (selected) c.accent.copy(alpha = 0.5f) else c.line, AiTheme.motion.fade(), label = "rowBorder")
    SurfaceCard(
        onClick = onClick,
        onLongClick = onLongClick,
        color = bg,
        borderColor = border,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .aiSharedBounds(SharedKeys.conversation(conv.id))
            .semantics { contentDescription = openCd },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                conv.displayTitle,
                style = AiTheme.typography.heading,
                color = if (conv.archived) c.fg3 else c.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = item.status != SessionStatus.Idle,
                enter = scaleIn(AiTheme.motion.bouncy()) + fadeIn(),
                exit = scaleOut(AiTheme.motion.exit()) + fadeOut(),
            ) {
                StatusDot(
                    if (item.status == SessionStatus.Pending) AgentStatus.Pending else AgentStatus.Busy,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Text(relativeTime(conv.updatedAt, now), style = AiTheme.typography.caption, color = c.fg3)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Icon(Lucide.Folder, contentDescription = null, tint = c.fg3, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                item.projectName,
                style = AiTheme.typography.caption,
                color = c.fg2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.preview?.let { preview ->
            Text(
                preview,
                style = AiTheme.typography.bodySmall,
                color = c.fg2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            ProviderBadge(providerId = conv.providerId, model = conv.model, compact = true)
            if (item.fromMobile) {
                OriginBadge(Origin.Mobile, label = conv.originDevice?.name ?: stringResource(R.string.sessions_from_mobile))
            }
            if (conv.archived) {
                TagBadge(text = stringResource(R.string.sessions_archived_tag), icon = Lucide.Archive)
            }
            when (item.status) {
                SessionStatus.Pending -> TagBadge(
                    text = if (item.pendingCount > 0) {
                        pluralStringResource(R.plurals.sessions_status_pending_count, item.pendingCount, item.pendingCount)
                    } else stringResource(R.string.sessions_status_pending),
                    color = c.warn,
                    background = c.warnSoft,
                )
                SessionStatus.Busy -> TagBadge(
                    text = stringResource(R.string.sessions_status_busy),
                    color = c.accent,
                    background = c.accentSoft,
                )
                SessionStatus.Idle -> Unit
            }
        }
        conv.warning?.let {
            Text(it, style = AiTheme.typography.caption, color = c.warn, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** FAB «Nova sessão»: pílula de destaque que recolhe ao rolar; container transform até a tela. */
@Composable
private fun NewSessionFab(expanded: Boolean, onClick: () -> Unit, haptics: AiHaptics, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    val label = stringResource(R.string.sessions_new)
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .aiSharedBounds(SharedKeys.NEW_SESSION)
            .shadow(8.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(c.accent)
            .clickable(role = Role.Button, onClickLabel = label) {
                haptics.perform(HapticKind.Tick)
                onClick()
            }
            .semantics { contentDescription = label }
            .heightIn(min = 56.dp)
            .defaultMinSize(minWidth = 56.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Lucide.Plus, contentDescription = null, tint = c.accentFg, modifier = Modifier.size(22.dp))
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(AiTheme.motion.spring()) + fadeIn(AiTheme.motion.fade()),
            exit = shrinkHorizontally(AiTheme.motion.spring()) + fadeOut(AiTheme.motion.fade()),
        ) {
            Text(
                label,
                style = AiTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                color = c.accentFg,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val c = AiTheme.colors
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.fg,
        textContentColor = c.fg2,
        shape = AiTheme.shapes.xl,
        title = { Text(stringResource(R.string.sessions_rename_title), style = AiTheme.typography.title) },
        text = {
            AiTextInput(
                value = text,
                onValueChange = { text = it.take(120) },
                placeholder = stringResource(R.string.sessions_rename_placeholder),
                singleLine = true,
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            AiButton(
                text = stringResource(R.string.sessions_rename_confirm),
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && text.trim() != initial,
            )
        },
        dismissButton = {
            AiButton(
                text = stringResource(R.string.sessions_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
            )
        },
    )
}
