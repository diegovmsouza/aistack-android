package br.com.amberwrite.aistack.feature.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.data.model.Conversation
import br.com.amberwrite.aistack.data.model.ConversationOrigin
import br.com.amberwrite.aistack.feature.common.CenteredLoading
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.bannerDetail
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.relativeTime
import br.com.amberwrite.aistack.feature.common.toBanner
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AgentStatus
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.AiChip
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ConnectionBanner
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.Origin
import br.com.amberwrite.aistack.ui.designsystem.components.OriginBadge
import br.com.amberwrite.aistack.ui.designsystem.components.ProviderBadge
import br.com.amberwrite.aistack.ui.designsystem.components.StatusDot
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Tela inicial (pareado): conversas do desktop. */
@Composable
fun SessionsScreen(
    onOpenChat: (String) -> Unit,
    onNewSession: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenSettings: () -> Unit,
    onRepair: () -> Unit
) {
    val vm = containerViewModel { SessionsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    FeatureScaffold(
        title = "AiStack",
        onBack = null,
        subtitle = if (state.loading) "Atualizando…" else null,
        actions = {
            Box {
                AiIconButton(icon = Lucide.Bell, contentDescription = "Pendências", onClick = onOpenPending)
                if (state.pendingCount > 0) {
                    StatusDot(AgentStatus.Pending, Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
            AiIconButton(icon = Lucide.RefreshCw, contentDescription = "Atualizar", onClick = vm::refresh)
            AiIconButton(icon = Lucide.Settings, contentDescription = "Configurações", onClick = onOpenSettings)
        }
    ) {
        ConnectionBanner(
            state = state.connection.toBanner(),
            detail = state.connection.bannerDetail(),
            onRetry = vm::retryConnection,
            onRepair = onRepair
        )
        state.error?.let { ErrorStrip(it) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AiChip(text = "Ativas", selected = !state.includeArchived, onClick = { vm.setIncludeArchived(false) })
            AiChip(text = "Incluir arquivadas", selected = state.includeArchived, onClick = { vm.setIncludeArchived(true) })
            Spacer(Modifier.weight(1f))
            AiButton(text = "Nova", onClick = onNewSession, leadingIcon = Lucide.Plus, size = ButtonSize.Small)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.loaded && state.conversations.isEmpty() -> CenteredLoading(text = "Carregando conversas…")
                state.conversations.isEmpty() -> EmptyState(
                    title = "Nenhuma conversa",
                    body = "Comece uma conversa nova aqui ou no desktop.",
                    modifier = Modifier.fillMaxSize(),
                    primaryAction = { AiButton(text = "Nova conversa", onClick = onNewSession, leadingIcon = Lucide.Plus) }
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.conversations, key = { it.id }) { conv ->
                        ConversationRow(conv, hasPending = conv.id in state.pendingIds, onClick = { onOpenChat(conv.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: Conversation, hasPending: Boolean, onClick: () -> Unit) {
    val c = AiTheme.colors
    SurfaceCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasPending) {
                StatusDot(AgentStatus.Pending)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                conv.displayTitle,
                style = AiTheme.typography.heading,
                color = if (conv.archived) c.fg3 else c.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(relativeTime(conv.updatedAt), style = AiTheme.typography.caption, color = c.fg3)
        }
        Text(
            conv.projectPath,
            style = AiTheme.typography.caption,
            color = c.fg3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ProviderBadge(providerId = conv.providerId, model = conv.model, compact = true)
            if (conv.origin == ConversationOrigin.MOBILE) {
                OriginBadge(Origin.Mobile, label = conv.originDevice?.name ?: Origin.Mobile.label)
            }
        }
        conv.warning?.let {
            Column(Modifier.padding(top = 6.dp)) {
                Text(it, style = AiTheme.typography.caption, color = c.warn)
            }
        }
    }
}
