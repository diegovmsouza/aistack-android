package br.com.amberwrite.aistack.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.AiStackApplication
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.ConversationOrigin
import br.com.amberwrite.aistack.data.model.QueuedMessage
import br.com.amberwrite.aistack.feature.chat.composer.ChatComposer
import br.com.amberwrite.aistack.feature.chat.thread.AgentsSheet
import br.com.amberwrite.aistack.feature.chat.thread.ChatEmptyArt
import br.com.amberwrite.aistack.feature.chat.thread.ThreadActions
import br.com.amberwrite.aistack.feature.chat.thread.ThreadList
import br.com.amberwrite.aistack.feature.chat.thread.ThreadMaxWidth
import br.com.amberwrite.aistack.feature.chat.thread.ThreadSkeleton
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.ConfirmDialog
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.bannerDetail
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.toBanner
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
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
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.designsystem.components.StatusDot
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Fase principal da área da thread (para a transição entre esqueleto, erro, vazio e lista). */
private enum class Phase { Loading, Failed, Empty, Thread }

/**
 * Chat de uma conversa: cabeçalho com provedor/origem/estado, thread paginada em Markdown
 * com ferramentas, sub-agentes, permissões e perguntas inline, fila acima do compositor e
 * o compositor (F3). Sair da tela devolve a assinatura a `summary` sem interromper nada.
 */
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenFiles: (String?) -> Unit,
    onRepair: () -> Unit,
    initialMention: String? = null,
) {
    val vm = containerViewModel(key = "chat:$conversationId") { ChatViewModel(it, conversationId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = AiStackApplication.container(LocalContext.current)
    val haptics = rememberAiHaptics()
    val c = AiTheme.colors

    DisposableEffect(conversationId) {
        container.setVisibleChat(conversationId)
        onDispose { container.clearVisibleChat(conversationId) }
    }
    LaunchedEffect(state.local.archived) { if (state.local.archived) onBack() }

    var showAgents by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showArchive by rememberSaveable { mutableStateOf(false) }

    val actions = remember(vm) {
        ThreadActions(
            onLoadOlder = vm::loadOlder,
            onExpand = vm::expand,
            onRetry = vm::retryLastTurn,
            onAllow = vm::allow,
            onDeny = vm::deny,
            onAnswerQuestion = vm::answerQuestion,
            onDismissQuestion = vm::dismissQuestion,
            onAnswerTool = vm::answerToolQuestion,
            onOpenAgents = { showAgents = true },
        )
    }
    val listState = rememberLazyListState()

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .imePadding(),
    ) {
        ChatHeader(
            state = state,
            scrolled = listState.canScrollForward,
            haptics = haptics,
            onBack = onBack,
            onRename = { showRename = true },
            onArchive = { showArchive = true },
            onOpenFiles = { onOpenFiles(state.conversation?.projectPath?.ifBlank { null }) },
            onAgents = { showAgents = true },
            onRefresh = vm::refresh,
        )
        ConnectionBanner(
            state = state.connection.toBanner(),
            detail = state.connection.bannerDetail(),
            onRetry = container::retry,
            onRepair = onRepair,
        )
        Strip(state.chat.warning, onDismiss = null)
        Strip(state.local.actionError, onDismiss = vm::dismissError)

        val phase = when {
            state.initialLoading -> Phase.Loading
            state.loadFailed -> Phase.Failed
            state.isEmpty -> Phase.Empty
            else -> Phase.Thread
        }
        val motion = AiTheme.motion
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
                label = "chatPhase",
                modifier = Modifier.fillMaxSize(),
            ) { p ->
                when (p) {
                    Phase.Loading -> ThreadSkeleton()
                    Phase.Failed -> CenteredBox {
                        EmptyState(
                            title = stringResource(R.string.chat_load_failed_title),
                            body = state.chat.error,
                            art = { ChatEmptyArt(Modifier.width(140.dp)) },
                            accent = c.danger,
                            primaryAction = {
                                AiButton(
                                    text = stringResource(R.string.chat_retry),
                                    onClick = vm::refresh,
                                    leadingIcon = Lucide.RefreshCw,
                                    loading = state.chat.loading,
                                )
                            },
                        )
                    }
                    Phase.Empty -> CenteredBox {
                        EmptyState(
                            title = stringResource(R.string.chat_empty_title),
                            body = stringResource(if (state.online) R.string.chat_empty_body else R.string.chat_empty_body_offline),
                            art = { ChatEmptyArt(Modifier.width(160.dp)) },
                        )
                    }
                    Phase.Thread -> ThreadList(
                        rows = state.rows,
                        local = state.local,
                        streaming = state.chat.isStreaming,
                        actions = actions,
                        haptics = haptics,
                        listState = listState,
                    )
                }
            }
        }

        QueueBar(
            queue = state.chat.queue,
            busyIds = state.local.queueBusy,
            haptics = haptics,
            onSendNow = vm::sendNow,
            onRemove = vm::unqueue,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            ChatComposer(
                conversationId = conversationId,
                busy = state.chat.busy,
                online = state.online,
                onInterrupt = vm::interrupt,
                modifier = Modifier.widthIn(max = ThreadMaxWidth).navigationBarsPadding(),
                initialMention = initialMention,
            )
        }
    }

    if (showAgents) AgentsSheet(agents = state.agents, onDismiss = { showAgents = false })
    if (showRename) {
        RenameDialog(
            initial = state.title,
            busy = state.local.renaming,
            onConfirm = {
                vm.rename(it)
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }
    if (showArchive) {
        ConfirmDialog(
            title = stringResource(R.string.chat_archive_title),
            text = stringResource(R.string.chat_archive_text),
            confirmLabel = stringResource(R.string.chat_archive_confirm),
            onConfirm = {
                showArchive = false
                vm.archive()
            },
            onDismiss = { showArchive = false },
            dismissLabel = stringResource(R.string.chat_cancel),
            destructive = true,
        )
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ChatHeader(
    state: ChatViewModel.UiState,
    scrolled: Boolean,
    haptics: AiHaptics,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onOpenFiles: () -> Unit,
    onAgents: () -> Unit,
    onRefresh: () -> Unit,
) {
    val c = AiTheme.colors
    val t = AiTheme.typography
    val conv = state.conversation
    val status = when {
        !state.online -> AgentStatus.Offline
        state.chat.pending.isNotEmpty() -> AgentStatus.Pending
        state.chat.busy -> AgentStatus.Busy
        else -> AgentStatus.Online
    }
    val statusLabel = stringResource(
        when (status) {
            AgentStatus.Offline -> R.string.chat_status_offline
            AgentStatus.Pending -> R.string.chat_status_pending
            AgentStatus.Busy -> R.string.chat_status_busy
            else -> R.string.chat_status_idle
        },
    )
    var menu by remember { mutableStateOf(false) }
    val runningAgents = state.agents.count { it.running }

    AiStackTopBar(
        title = state.title,
        navigationIcon = Lucide.ArrowLeft,
        navigationContentDescription = stringResource(R.string.chat_back),
        onNavigationClick = onBack,
        scrolled = scrolled,
        haptics = haptics,
        titleContent = {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (conv != null) ProviderBadge(conv.providerId, compact = true)
                    Text(
                        state.title.ifBlank { stringResource(R.string.chat_untitled) },
                        style = t.title,
                        color = c.fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                    )
                    StatusDot(status = status, pulse = status == AgentStatus.Busy && !AiTheme.reducedMotion)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (conv != null) {
                        val mobile = conv.origin == ConversationOrigin.MOBILE
                        OriginBadge(
                            origin = if (mobile) Origin.Mobile else Origin.Desktop,
                            label = conv.originDevice?.name?.takeIf { mobile } ?: (if (mobile) Origin.Mobile.label else Origin.Desktop.label),
                        )
                    }
                    Text(
                        listOfNotNull(
                            statusLabel,
                            conv?.projectPath?.substringAfterLast('/')?.ifBlank { null },
                        ).joinToString(" · "),
                        style = t.caption,
                        color = c.fg3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) {
        Box {
            AiIconButton(
                icon = Lucide.Bot,
                contentDescription = if (runningAgents > 0) {
                    stringResource(R.string.chat_agents_open_running, runningAgents)
                } else {
                    stringResource(R.string.chat_agents_open)
                },
                onClick = onAgents,
                size = 48.dp,
                tint = if (runningAgents > 0) c.accent else null,
                haptics = haptics,
            )
        }
        Box {
            AiIconButton(
                icon = Lucide.EllipsisVertical,
                contentDescription = stringResource(R.string.chat_more),
                onClick = { menu = true },
                size = 48.dp,
                haptics = haptics,
            )
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = c.surface,
                shape = AiTheme.shapes.md,
            ) {
                MenuItem(Lucide.Pencil, stringResource(R.string.chat_menu_rename)) { menu = false; onRename() }
                MenuItem(Lucide.FolderOpen, stringResource(R.string.chat_menu_files)) { menu = false; onOpenFiles() }
                MenuItem(Lucide.Bot, stringResource(R.string.chat_menu_agents)) { menu = false; onAgents() }
                MenuItem(Lucide.RefreshCw, stringResource(R.string.chat_menu_refresh)) { menu = false; onRefresh() }
                MenuItem(Lucide.Archive, stringResource(R.string.chat_menu_archive), danger = true) { menu = false; onArchive() }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val tone = if (danger) AiTheme.colors.danger else AiTheme.colors.fg
    DropdownMenuItem(
        text = { Text(text, style = AiTheme.typography.body, color = tone) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}

/** Faixa de aviso/erro acima da thread; com [onDismiss], um toque a fecha. */
@Composable
private fun Strip(message: String?, onDismiss: (() -> Unit)?) {
    var last by remember { mutableStateOf(message.orEmpty()) }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(AiTheme.motion.spring()) + fadeIn(AiTheme.motion.fade()),
        exit = shrinkVertically(AiTheme.motion.spring()) + fadeOut(AiTheme.motion.fade()),
    ) {
        Row(Modifier.fillMaxWidth().background(AiTheme.colors.dangerSoft), verticalAlignment = Alignment.CenterVertically) {
            ErrorStrip(last, Modifier.weight(1f))
            if (onDismiss != null) {
                AiIconButton(
                    icon = Lucide.X,
                    contentDescription = stringResource(R.string.chat_dismiss),
                    onClick = onDismiss,
                    size = 48.dp,
                    iconSize = 16.dp,
                    tint = AiTheme.colors.danger,
                )
            }
        }
    }
}

/** Mensagens na fila: tocar envia já (interrompendo o turno); o "x" remove. */
@Composable
private fun QueueBar(
    queue: List<QueuedMessage>,
    busyIds: Set<String>,
    haptics: AiHaptics,
    onSendNow: (QueuedMessage) -> Unit,
    onRemove: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = queue.isNotEmpty(),
        enter = expandVertically(AiTheme.motion.spring()) + fadeIn(AiTheme.motion.fade()),
        exit = shrinkVertically(AiTheme.motion.spring()) + fadeOut(AiTheme.motion.fade()),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                stringResource(R.string.chat_queue_hint, queue.size),
                style = AiTheme.typography.caption,
                color = AiTheme.colors.fg3,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                queue.forEach { q ->
                    val busy = q.id in busyIds
                    val label = q.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(48)
                        ?: stringResource(R.string.chat_queue_attachments, q.attachments.size)
                    AiChip(
                        text = label,
                        onClick = if (busy) null else ({ onSendNow(q) }),
                        leading = {
                            if (busy) Spinner(size = 12.dp) else Icon(Lucide.Clock, null, Modifier.size(14.dp), tint = AiTheme.colors.fg3)
                        },
                        onRemove = if (busy) null else ({ onRemove(q.id) }),
                        haptics = haptics,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, busy: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val c = AiTheme.colors
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.chat_rename_title), style = AiTheme.typography.title, color = c.fg) },
        text = {
            AiTextInput(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.chat_rename_placeholder),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            AiButton(
                text = stringResource(R.string.chat_rename_confirm),
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && !busy,
            )
        },
        dismissButton = {
            AiButton(text = stringResource(R.string.chat_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
        },
    )
}
