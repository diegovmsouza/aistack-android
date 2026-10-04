package br.com.amberwrite.aistack.feature.chat.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.feature.chat.ChatViewModel
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Ações da thread (lambdas estáveis vindas do ViewModel). */
@Stable
class ThreadActions(
    val onLoadOlder: () -> Unit,
    val onExpand: (ChatItem) -> Unit,
    val onRetry: () -> Unit,
    val onAllow: (PermissionRequest, Boolean) -> Unit,
    val onDeny: (PermissionRequest) -> Unit,
    val onAnswerQuestion: (PermissionRequest, Map<Question, List<String>>) -> Unit,
    val onDismissQuestion: (PermissionRequest) -> Unit,
    val onAnswerTool: (Question, Int?, String?) -> Unit,
    val onOpenAgents: () -> Unit,
)

/** Distância (px) do fim abaixo da qual a thread continua "grudada" no fim. */
private const val STICK_SLOP = 48

/**
 * A thread: lista invertida (o fim fica embaixo e carregar anteriores no topo não faz a
 * posição pular), gruda no fim enquanto o usuário está lá e pagina ao chegar ao topo.
 */
@Composable
fun ThreadList(
    rows: List<ThreadRow>,
    local: ChatViewModel.Local,
    streaming: Boolean,
    actions: ThreadActions,
    haptics: AiHaptics,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val reduced = AiTheme.reducedMotion
    val motion = AiTheme.motion
    val scope = rememberCoroutineScope()
    // Mais nova primeiro: com reverseLayout, o índice 0 fica embaixo.
    val reversed = remember(rows) { rows.asReversed() }
    val lastItemKey = remember(rows) { rows.lastOrNull { it is ThreadRow.Item }?.key }

    val atBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= STICK_SLOP }
    }
    var stick by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect { stick = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= STICK_SLOP }
    }

    // Conteúdo novo no fim: acompanha se estava no fim (ou se o próprio usuário enviou).
    val newestKey = reversed.firstOrNull()?.key
    val newestIsUser = (reversed.firstOrNull() as? ThreadRow.Item)?.item.let { it is ChatItem.User || it is ChatItem.Steer }
    LaunchedEffect(newestKey, reversed.size) {
        val follow = stick || newestIsUser
        if (follow && (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)) {
            if (reduced) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
        if (follow) stick = true
    }

    // Paginação: perto do topo (fim da lista invertida) pede turnos anteriores.
    val onLoadOlder by rememberUpdatedState(actions.onLoadOlder)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadOlder() }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(reversed, key = { it.key }, contentType = { it::class }) { row ->
                val anim = if (reduced) {
                    Modifier
                } else {
                    Modifier.animateItem(
                        fadeInSpec = motion.fade(),
                        placementSpec = if (streaming) null else motion.spring<IntOffset>(),
                        fadeOutSpec = motion.exit(),
                    )
                }
                Box(anim.widthIn(max = ThreadMaxWidth).fillMaxWidth()) {
                    RowContent(row, local, streaming && row.key == lastItemKey, actions, haptics)
                }
            }
        }

        AnimatedVisibility(
            visible = !atBottom && reversed.isNotEmpty(),
            enter = fadeIn(motion.fade()) + scaleIn(motion.spring(), initialScale = 0.8f),
            exit = fadeOut(motion.fade()) + scaleOut(motion.spring(), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            AiIconButton(
                icon = Lucide.ArrowDown,
                contentDescription = stringResource(R.string.chat_scroll_to_end),
                onClick = {
                    stick = true
                    scope.launch { if (reduced) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
                },
                variant = ButtonVariant.Secondary,
                size = 48.dp,
            )
        }
    }
}

@Composable
private fun RowContent(
    row: ThreadRow,
    local: ChatViewModel.Local,
    live: Boolean,
    actions: ThreadActions,
    haptics: AiHaptics,
) {
    when (row) {
        is ThreadRow.Older -> OlderRow(row.loading, actions.onLoadOlder)
        is ThreadRow.Status -> StatusRow(row.kind)
        is ThreadRow.Pending -> PendingView(row.request, local, actions, haptics)
        is ThreadRow.ToolAsk -> ToolAskView(row.tool, row.question, local, actions, haptics)
        is ThreadRow.Item -> when (val item = row.item) {
            is ChatItem.User -> UserBubble(item.text, item.attachments, steer = false)
            is ChatItem.Steer -> UserBubble(item.text, item.attachments, steer = true)
            is ChatItem.Text -> AssistantText(
                text = item.text,
                caret = row.caret,
                truncated = false,
                expanding = item.key in local.expanding,
                expandFailed = item.key in local.expandFailed,
                onExpand = { actions.onExpand(item) },
            )
            is ChatItem.Thinking -> ThinkingItem(item.key, item.text, live = live)
            is ChatItem.Tool -> ToolItem(
                tool = item,
                expanding = item.key in local.expanding,
                expandFailed = item.key in local.expandFailed,
                onExpand = { actions.onExpand(item) },
                onOpenAgents = actions.onOpenAgents,
                haptics = haptics,
            )
            is ChatItem.Error -> ErrorItem(item, retry = row.retry, retrying = local.retrying, onRetry = actions.onRetry)
            is ChatItem.Notice -> NoticeItem(item.text)
        }
    }
}
