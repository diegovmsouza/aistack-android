package br.com.amberwrite.aistack.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.AiStackApplication
import br.com.amberwrite.aistack.core.rpc.asObj
import br.com.amberwrite.aistack.core.rpc.displayText
import br.com.amberwrite.aistack.core.rpc.str
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.CenteredLoading
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.bannerDetail
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.toBanner
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiChip
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.ConnectionBanner
import br.com.amberwrite.aistack.ui.designsystem.components.PermissionCard
import br.com.amberwrite.aistack.ui.designsystem.components.PermissionState
import br.com.amberwrite.aistack.ui.designsystem.components.ProviderBadge
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionAnswer
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionCard
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.designsystem.components.ToolCard
import br.com.amberwrite.aistack.ui.designsystem.components.ToolKind
import br.com.amberwrite.aistack.ui.icons.Lucide
import br.com.amberwrite.aistack.data.model.ToolStatus as ModelToolStatus
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionOption as DsQuestionOption
import br.com.amberwrite.aistack.ui.designsystem.components.ToolStatus as DsToolStatus

/**
 * Chat de uma conversa. Versão funcional da Wave 1: transcrição em texto simples (sem
 * Markdown), cartões de ferramenta, permissões, perguntas, fila e compositor.
 */
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenFiles: (String) -> Unit,
    onRepair: () -> Unit,
    initialMention: String? = null
) {
    val vm = containerViewModel(key = "chat:$conversationId") { ChatViewModel(it, conversationId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = AiStackApplication.container(LocalContext.current)
    val chat = state.chat

    DisposableEffect(conversationId) {
        container.setVisibleChat(conversationId)
        onDispose { container.clearVisibleChat(conversationId) }
    }

    FeatureScaffold(
        title = state.title,
        subtitle = chat.conversation?.projectPath,
        onBack = onBack,
        modifier = Modifier.imePadding(),
        actions = {
            AiIconButton(icon = Lucide.Folder, contentDescription = "Arquivos", onClick = { onOpenFiles(conversationId) })
            AiIconButton(icon = Lucide.RefreshCw, contentDescription = "Recarregar", onClick = vm::refresh)
        }
    ) {
        ConnectionBanner(
            state = state.connection.toBanner(),
            detail = state.connection.bannerDetail(),
            onRetry = container::retry,
            onRepair = onRepair
        )
        chat.error?.let { ErrorStrip(it) }
        chat.warning?.let { ErrorStrip(it) }
        state.local.actionError?.let { ErrorStrip(it) }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (chat.loading && chat.items.isEmpty()) {
                CenteredLoading(text = "Carregando conversa…")
            } else {
                Transcript(state, vm)
            }
        }
        BottomPanel(state, vm)
    }
}

@Composable
private fun Transcript(state: ChatViewModel.UiState, vm: ChatViewModel) {
    val chat = state.chat
    val listState = rememberLazyListState()
    val total = chat.items.size + chat.pending.size
    LaunchedEffect(total, chat.items.lastOrNull()?.key) {
        if (total > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (chat.hasMore) {
            item(key = "older") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AiButton(
                        text = "Carregar anteriores",
                        onClick = vm::loadOlder,
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Small,
                        loading = chat.loadingOlder
                    )
                }
            }
        }
        items(chat.items, key = { it.key }) { item -> ChatItemView(item, onExpand = { vm.expand(item) }) }
        items(chat.pending, key = { "P:" + it.requestId }) { req ->
            PendingRequestView(req, answering = req.requestId in state.local.answering, vm = vm)
        }
        state.toolQuestion?.question?.let { tq ->
            item(key = "toolQuestion") {
                val q = tq.questions.first()
                QuestionCard(
                    question = q.question,
                    header = q.header,
                    options = q.options.map { DsQuestionOption(it.label, it.description) },
                    onSubmit = { ans ->
                        when (ans) {
                            is QuestionAnswer.Choice -> vm.answerToolQuestion(q, ans.number - 1, null)
                            is QuestionAnswer.Custom -> vm.answerToolQuestion(q, null, ans.value)
                        }
                    },
                    onSkip = {}
                )
            }
        }
        if (chat.busy) {
            item(key = "status") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    Spinner(size = 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        chat.status ?: if (chat.isStreaming) "Escrevendo…" else "Trabalhando…",
                        style = AiTheme.typography.caption,
                        color = AiTheme.colors.fg3
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatItemView(item: ChatItem, onExpand: () -> Unit) {
    val c = AiTheme.colors
    when (item) {
        is ChatItem.User, is ChatItem.Steer -> {
            val text = if (item is ChatItem.User) item.text else (item as ChatItem.Steer).text
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text,
                    style = AiTheme.typography.body,
                    color = c.userBubbleFg,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .background(c.userBubble, AiTheme.shapes.lg)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        is ChatItem.Text -> Text(item.text, style = AiTheme.typography.body, color = c.fg, modifier = Modifier.padding(horizontal = 4.dp))
        is ChatItem.Thinking -> Text(
            item.text,
            style = AiTheme.typography.caption,
            color = c.fg3,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        is ChatItem.Tool -> ToolCard(
            title = item.name,
            kind = toolKind(item.name),
            status = item.status.toDs(),
            detail = toolDetail(item),
            meta = if (item.subagent.isNotEmpty()) "${item.subagent.size} passos" else null
        ) {
            val out = item.output?.displayText().orEmpty()
            if (out.isNotBlank()) {
                Text(out.take(4000), style = AiTheme.typography.mono, color = c.fg2)
            }
            if (item.truncated) {
                AiButton(text = "Ver completo", onClick = onExpand, variant = ButtonVariant.Ghost, size = ButtonSize.Small)
            }
        }
        is ChatItem.Error -> Text(
            item.message,
            style = AiTheme.typography.body,
            color = c.danger,
            modifier = Modifier.fillMaxWidth().background(c.dangerSoft, AiTheme.shapes.md).padding(10.dp)
        )
        is ChatItem.Notice -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(item.text, style = AiTheme.typography.caption, color = c.fg3)
        }
    }
}

@Composable
private fun PendingRequestView(req: PermissionRequest, answering: Boolean, vm: ChatViewModel) {
    if (req.isAskUserQuestion && req.questions.isNotEmpty()) {
        val answers = remember(req.requestId) { mutableStateMapOf<Question, List<String>>() }
        val next = req.questions.firstOrNull { it !in answers } ?: return
        // `key` zera a seleção interna do cartão a cada pergunta nova.
        key(req.requestId, req.questions.indexOf(next)) { QuestionCard(
            question = next.question,
            header = next.header,
            options = next.options.map { DsQuestionOption(it.label, it.description) },
            onSubmit = { ans ->
                answers[next] = when (ans) {
                    is QuestionAnswer.Choice -> listOf(ans.option.label)
                    is QuestionAnswer.Custom -> listOf(ans.value)
                }
                if (req.questions.all { it in answers }) vm.answerQuestion(req, answers.toMap())
            },
            onSkip = { vm.dismissQuestion(req) }
        ) }
        return
    }
    PermissionCard(
        toolName = req.tool,
        state = PermissionState.Pending,
        detail = req.inputPreview,
        description = req.reason,
        onAllow = { if (!answering) vm.allow(req) },
        onDeny = { if (!answering) vm.deny(req) },
        onAlwaysAllow = { if (!answering) vm.allow(req, remember = true) }
    )
}

@Composable
private fun BottomPanel(state: ChatViewModel.UiState, vm: ChatViewModel) {
    val c = AiTheme.colors
    val chat = state.chat
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (chat.queue.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chat.queue.forEach { q ->
                    AiChip(text = q.text.take(40), leadingIcon = Lucide.Clock, onRemove = { vm.unqueue(q.id) })
                }
            }
        }
        chat.conversation?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderBadge(providerId = it.providerId, model = it.model, compact = true)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            AiTextInput(
                value = state.local.draft,
                onValueChange = vm::setDraft,
                placeholder = if (chat.busy) "Mensagem (vai para a fila)" else "Mensagem",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            if (chat.busy) {
                AiIconButton(icon = Lucide.CircleStop, contentDescription = "Interromper", onClick = vm::interrupt, tint = c.danger)
            }
            AiIconButton(
                icon = Lucide.Send,
                contentDescription = if (chat.busy) "Enfileirar" else "Enviar",
                onClick = vm::send,
                variant = ButtonVariant.Primary,
                enabled = state.local.draft.isNotBlank() && !state.local.sending && state.online
            )
        }
        if (chat.busy && state.local.draft.isNotBlank()) {
            AiButton(
                text = "Interromper e enviar agora",
                onClick = vm::sendNow,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                enabled = state.online
            )
        }
    }
}

private fun ModelToolStatus.toDs(): DsToolStatus = when (this) {
    ModelToolStatus.RUNNING -> DsToolStatus.Running
    ModelToolStatus.DONE -> DsToolStatus.Success
    ModelToolStatus.ERROR -> DsToolStatus.Error
    ModelToolStatus.INTERRUPTED -> DsToolStatus.Error
}

private fun toolKind(name: String): ToolKind = when (name) {
    "Read", "Glob", "LS", "read_file", "list_dir" -> ToolKind.File
    "Edit", "MultiEdit", "Write", "NotebookEdit", "apply_patch", "edit_file", "write_file" -> ToolKind.Edit
    "Bash", "shell", "run_shell_command", "exec_command" -> ToolKind.Terminal
    "Grep", "search", "grep" -> ToolKind.Search
    "WebFetch", "WebSearch", "web_search", "web_fetch" -> ToolKind.Web
    "Task", "Agent" -> ToolKind.Agent
    "TodoWrite", "update_plan" -> ToolKind.Todo
    else -> ToolKind.Tool
}

private fun toolDetail(item: ChatItem.Tool): String? {
    val o = item.input.asObj() ?: return null
    return o.str("command") ?: o.str("file_path") ?: o.str("path") ?: o.str("pattern")
        ?: o.str("url") ?: o.str("query") ?: o.str("description")
}
