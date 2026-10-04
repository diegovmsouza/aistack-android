package br.com.amberwrite.aistack.feature.chat.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.EntryKind
import br.com.amberwrite.aistack.data.model.SlashCommand
import br.com.amberwrite.aistack.feature.common.formatBytes
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.GlassCard
import br.com.amberwrite.aistack.ui.designsystem.components.PaletteItem
import br.com.amberwrite.aistack.ui.designsystem.components.PaletteItemRow
import br.com.amberwrite.aistack.ui.designsystem.components.icon
import br.com.amberwrite.aistack.ui.designsystem.illustrations.Illustration
import br.com.amberwrite.aistack.ui.icons.Lucide

/**
 * Painel acima do composer com a paleta «/» ou a menção «@». Tem os quatro estados: esqueleto
 * enquanto carrega, erro com tentar de novo, vazio com ilustração e a lista.
 */
@Composable
fun ComposerPopupPanel(
    popup: ComposerPopup,
    onPickSlash: (SlashCommand) -> Unit,
    onPickMention: (DirEntry) -> Unit,
    onUp: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    haptics: AiHaptics = AiHaptics.None,
) {
    val motion = AiTheme.motion
    // Mantém o último popup visível durante a animação de saída.
    var last by remember { mutableStateOf<ComposerPopup>(ComposerPopup.None) }
    if (popup != ComposerPopup.None) last = popup
    AnimatedVisibility(
        visible = popup != ComposerPopup.None,
        modifier = modifier,
        enter = fadeIn(motion.fade()) + slideInVertically(motion.spring()) { it / 4 } +
            scaleIn(motion.spring(), initialScale = 0.96f),
        exit = fadeOut(motion.exit()) + slideOutVertically(motion.exit()) { it / 6 } +
            scaleOut(motion.exit(), targetScale = 0.98f),
    ) {
        when (val p = last) {
            is ComposerPopup.Slash -> SlashPanel(p, onPickSlash, onRetry, onDismiss, haptics)
            is ComposerPopup.Mention -> MentionPanel(p, onPickMention, onUp, onRetry, onDismiss, haptics)
            ComposerPopup.None -> Unit
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    onDismiss: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val c = AiTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = AiTheme.typography.overline,
            color = c.fg3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AiIconButton(
            icon = Lucide.X,
            contentDescription = stringResource(R.string.composer_popup_close),
            onClick = onDismiss,
            size = 48.dp,
            iconSize = 16.dp,
        )
    }
}

@Composable
private fun SlashPanel(
    p: ComposerPopup.Slash,
    onPick: (SlashCommand) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    haptics: AiHaptics,
) {
    val c = AiTheme.colors
    GlassCard(Modifier.fillMaxWidth(), shape = AiTheme.shapes.lg, contentPadding = PaddingValues(6.dp)) {
        PanelHeader(stringResource(R.string.composer_slash_title), onDismiss) {
            Icon(Lucide.Slash, null, Modifier.size(14.dp), tint = c.accent)
        }
        LoadContent(p.items, onRetry) { list ->
            if (list.isEmpty()) {
                PanelEmpty(
                    title = stringResource(R.string.composer_slash_empty_title),
                    body = if (p.trigger.query.isEmpty()) stringResource(R.string.composer_slash_empty_body_none)
                    else stringResource(R.string.composer_slash_empty_body, p.trigger.query),
                )
            } else {
                val desktopOnly = stringResource(R.string.composer_desktop_only)
                val disabledState = stringResource(R.string.composer_state_unavailable)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(list, key = { it.name }) { cmd ->
                        val item = PaletteItem(
                            key = cmd.name,
                            title = cmd.bareName,
                            subtitle = cmd.description?.takeIf { it.isNotBlank() },
                            icon = Lucide.Slash,
                            badge = if (cmd.desktopOnly) desktopOnly else cmd.source?.takeIf { it.isNotBlank() && it != "builtin" },
                        )
                        RowContainer(
                            enabled = !cmd.desktopOnly,
                            disabledState = disabledState,
                            onClick = {
                                haptics.perform(if (cmd.desktopOnly) HapticKind.Reject else HapticKind.Tick)
                                onPick(cmd)
                            },
                            modifier = Modifier.animateItem(),
                        ) {
                            PaletteItemRow(item, highlighted = false, query = p.trigger.query, prefix = "/", monoTitle = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MentionPanel(
    p: ComposerPopup.Mention,
    onPick: (DirEntry) -> Unit,
    onUp: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    haptics: AiHaptics,
) {
    val c = AiTheme.colors
    val title = if (p.dir.isEmpty()) stringResource(R.string.composer_mention_title_root)
    else stringResource(R.string.composer_mention_title_dir, p.dir)
    GlassCard(Modifier.fillMaxWidth(), shape = AiTheme.shapes.lg, contentPadding = PaddingValues(6.dp)) {
        PanelHeader(title, onDismiss) {
            if (p.dir.isNotEmpty()) {
                AiIconButton(
                    icon = Lucide.ChevronLeft,
                    contentDescription = stringResource(R.string.composer_mention_up),
                    onClick = {
                        haptics.perform(HapticKind.Tick)
                        onUp()
                    },
                    size = 48.dp,
                    iconSize = 16.dp,
                )
            } else {
                Icon(Lucide.AtSign, null, Modifier.size(14.dp), tint = c.accent)
            }
        }
        LoadContent(p.items, onRetry) { list ->
            if (list.isEmpty()) {
                PanelEmpty(
                    title = stringResource(R.string.composer_mention_empty_title),
                    body = if (p.filter.isEmpty()) stringResource(R.string.composer_mention_empty_body_dir)
                    else stringResource(R.string.composer_mention_empty_body, p.filter),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(list, key = { it.kind.name + "/" + it.name }) { entry ->
                        val isDir = entry.kind == EntryKind.DIR
                        val item = PaletteItem(
                            key = entry.name,
                            title = if (isDir) entry.name + "/" else entry.name,
                            subtitle = if (isDir) null else entry.size?.let { formatBytes(it) },
                            icon = if (isDir) Lucide.Folder else attachmentKindFor(entry.name, guessMime(entry.name)).icon(),
                        )
                        RowContainer(
                            enabled = true,
                            disabledState = "",
                            onClick = {
                                haptics.perform(HapticKind.Tick)
                                onPick(entry)
                            },
                            modifier = Modifier.animateItem(),
                        ) {
                            PaletteItemRow(item, highlighted = false, query = p.filter, monoTitle = true)
                        }
                    }
                    if (p.truncated) {
                        item(key = "__truncated") {
                            Text(
                                stringResource(R.string.composer_mention_truncated),
                                style = AiTheme.typography.caption,
                                color = c.fg3,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowContainer(
    enabled: Boolean,
    disabledState: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(AiTheme.shapes.md)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.semantics { stateDescription = disabledState })
            .alpha(if (enabled) 1f else 0.5f),
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

/** Troca animada entre esqueleto, erro e conteúdo (anima só na mudança de estado, não a cada filtro). */
@Composable
private fun <T> LoadContent(
    load: ComposerLoad<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    val motion = AiTheme.motion
    AnimatedContent(
        targetState = load,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(motion.fade()) + expandVertically(motion.spring())) togetherWith
                (fadeOut(motion.exit()) + shrinkVertically(motion.exit()))
        },
        label = "popup-load",
    ) { state ->
        when (state) {
            ComposerLoad.Loading -> Column(Modifier.fillMaxWidth()) { repeat(4) { SkeletonRow(titleFraction = 0.35f + it * 0.1f) } }
            is ComposerLoad.Failed -> RetryPanel(state.message, onRetry)
            is ComposerLoad.Ready -> content(state.value)
        }
    }
}

@Composable
private fun PanelEmpty(title: String, body: String) {
    EmptyState(
        title = title,
        body = body,
        illustration = Illustration.NoPending,
        illustrationWidth = 96.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}
