package br.com.amberwrite.aistack.feature.chat.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.Attachment
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.feature.chat.markdown.MarkdownContent
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.ShimmerText
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Largura máxima de leitura confortável em telas largas (tablet/paisagem). */
internal val ThreadMaxWidth = 760.dp

/** Mensagem do usuário (ou direcionamento enviado no meio do turno), à direita. */
@Composable
internal fun UserBubble(text: String, attachments: List<Attachment>, steer: Boolean, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    val t = AiTheme.typography
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (steer) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, end = 4.dp),
            ) {
                Icon(Lucide.Zap, contentDescription = null, tint = c.fg3, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.chat_steer_label), style = t.caption, color = c.fg3)
            }
        }
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.86f),
            horizontalAlignment = Alignment.End,
        ) {
            if (text.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text,
                        style = t.body,
                        color = c.userBubbleFg,
                        modifier = Modifier
                            .clip(AiTheme.shapes.userBubble)
                            .background(c.userBubble)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            if (attachments.isNotEmpty()) {
                Column(
                    Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    attachments.forEach { AttachmentPill(it) }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPill(attachment: Attachment) {
    val c = AiTheme.colors
    val name = attachment.path.substringAfterLast('/').ifBlank { attachment.path }
    val icon = if (attachment.mime.startsWith("image/")) Lucide.Image else Lucide.Paperclip
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(AiTheme.shapes.pill)
            .background(c.surface2)
            .border(1.dp, c.line, AiTheme.shapes.pill)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Icon(icon, contentDescription = null, tint = c.fg2, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(name, style = AiTheme.typography.caption, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Resposta do assistente em Markdown, com cursor enquanto chega ao vivo. */
@Composable
internal fun AssistantText(
    text: String,
    caret: Boolean,
    truncated: Boolean,
    expanding: Boolean,
    expandFailed: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        SelectionContainer {
            MarkdownContent(markdown = text, caret = caret)
        }
        if (truncated) LoadFullRow(expanding, expandFailed, onExpand, Modifier.padding(top = 4.dp))
    }
}

/** Raciocínio do modelo: recolhido por padrão, expande com mola. */
@Composable
internal fun ThinkingItem(key: String, text: String, live: Boolean, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    val t = AiTheme.typography
    val motion = AiTheme.motion
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, motion.spring(), label = "thinkingChevron")
    val stateText = stringResource(if (expanded) R.string.chat_expanded else R.string.chat_collapsed)
    Column(
        modifier
            .fillMaxWidth()
            .clip(AiTheme.shapes.md)
            .border(1.dp, c.line, AiTheme.shapes.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = stringResource(if (expanded) R.string.chat_collapse else R.string.chat_expand)) {
                    expanded = !expanded
                }
                .semantics { stateDescription = stateText }
                .padding(horizontal = 12.dp),
        ) {
            Icon(Lucide.Brain, contentDescription = null, tint = c.fg3, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (live) {
                    ShimmerText(stringResource(R.string.chat_thinking_live), style = t.label)
                } else {
                    Text(stringResource(R.string.chat_thinking), style = t.label, color = c.fg3)
                }
            }
            Icon(
                Lucide.ChevronDown,
                contentDescription = null,
                tint = c.fg3,
                modifier = Modifier.size(16.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.spring()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.spring()) + fadeOut(motion.fade()),
        ) {
            SelectionContainer {
                MarkdownContent(
                    markdown = text,
                    style = t.bodySmall,
                    color = c.fg3,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

/** Erro de turno, com "tentar de novo" quando é o último. */
@Composable
internal fun ErrorItem(
    item: ChatItem.Error,
    retry: Boolean,
    retrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AiTheme.colors
    val t = AiTheme.typography
    Column(
        modifier
            .fillMaxWidth()
            .clip(AiTheme.shapes.md)
            .background(c.dangerSoft)
            .border(1.dp, c.danger.copy(alpha = 0.35f), AiTheme.shapes.md)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Lucide.TriangleAlert, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (item.kind == "interrupted") R.string.chat_turn_interrupted else R.string.chat_turn_error),
                    style = t.label,
                    color = c.danger,
                    modifier = Modifier.semantics { heading() },
                )
                if (item.message.isNotBlank()) {
                    SelectionContainer {
                        Text(item.message, style = t.bodySmall, color = c.fg2, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        if (retry) {
            AiButton(
                text = stringResource(R.string.chat_retry),
                onClick = onRetry,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
                leadingIcon = Lucide.RefreshCw,
                loading = retrying,
                modifier = Modifier.padding(top = 8.dp).minimumInteractiveComponentSize(),
            )
        }
    }
}

/** Aviso discreto do sistema, centralizado. */
@Composable
internal fun NoticeItem(text: String, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Info, contentDescription = null, tint = c.fg3, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = AiTheme.typography.caption, color = c.fg3)
    }
}

/** O que o agente está fazendo agora, com brilho percorrendo o texto. */
@Composable
internal fun StatusRow(kind: StatusKind, modifier: Modifier = Modifier) {
    val text = when (kind) {
        is StatusKind.Custom -> kind.text
        is StatusKind.RunningTool -> stringResource(R.string.chat_status_tool, kind.name)
        StatusKind.Thinking -> stringResource(R.string.chat_status_thinking)
        StatusKind.Working -> stringResource(R.string.chat_status_working)
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .padding(horizontal = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spinner(size = 14.dp)
        Spacer(Modifier.width(10.dp))
        ShimmerText(text, style = AiTheme.typography.label)
    }
}

/** Topo da thread: carrega turnos anteriores (automático ao rolar, ou por toque). */
@Composable
internal fun OlderRow(loading: Boolean, onLoad: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spinner(size = 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chat_loading_older), style = AiTheme.typography.caption, color = AiTheme.colors.fg3)
            }
        } else {
            AiButton(
                text = stringResource(R.string.chat_load_older),
                onClick = onLoad,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                leadingIcon = Lucide.History,
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
        }
    }
}

/** «Carregar completo» de um bloco cortado pelo host. */
@Composable
internal fun LoadFullRow(expanding: Boolean, failed: Boolean, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AiButton(
            text = stringResource(if (failed) R.string.chat_load_full_retry else R.string.chat_load_full),
            onClick = onExpand,
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Small,
            leadingIcon = if (failed) Lucide.RefreshCw else Lucide.ChevronDown,
            loading = expanding,
            enabled = !expanding,
            modifier = Modifier.minimumInteractiveComponentSize(),
        )
        if (failed && !expanding) {
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.chat_load_full_failed), style = AiTheme.typography.caption, color = c.danger)
        }
    }
}
