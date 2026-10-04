package br.com.amberwrite.aistack.feature.pending

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.feature.common.CenteredLoading
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.PermissionCard
import br.com.amberwrite.aistack.ui.designsystem.components.PermissionState
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Pendências: pedidos de permissão abertos em qualquer conversa. Perguntas abrem o chat. */
@Composable
fun PendingScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val vm = containerViewModel { PendingViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = AiTheme.colors

    FeatureScaffold(
        title = "Pendências",
        onBack = onBack,
        actions = { AiIconButton(icon = Lucide.RefreshCw, contentDescription = "Recarregar", onClick = vm::reload) }
    ) {
        state.error?.let { ErrorStrip(it) }
        if (!state.supported) ErrorStrip("O desktop não oferece a lista de pendências. Atualize o AiStack.")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.items.isEmpty() -> CenteredLoading(text = "Carregando…")
                state.items.isEmpty() -> EmptyState(
                    title = "Nada pendente",
                    body = "Pedidos de permissão dos agentes aparecem aqui.",
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.conversationId }) { conv ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.fillMaxWidth().clickable { onOpenChat(conv.conversationId) }) {
                                Text(
                                    conv.title.ifBlank { "Conversa" },
                                    style = AiTheme.typography.heading,
                                    color = c.fg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    conv.projectPath,
                                    style = AiTheme.typography.caption,
                                    color = c.fg3,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            conv.permissions.forEach { req ->
                                if (req.isAskUserQuestion) {
                                    Text(
                                        "Pergunta do agente: toque no título para responder no chat.",
                                        style = AiTheme.typography.caption,
                                        color = c.warn,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    val busy = req.requestId in state.answering
                                    PermissionCard(
                                        toolName = req.tool,
                                        state = PermissionState.Pending,
                                        detail = req.inputPreview,
                                        description = req.reason,
                                        onAllow = { if (!busy) vm.allow(req) },
                                        onDeny = { if (!busy) vm.deny(req) },
                                        onAlwaysAllow = { if (!busy) vm.allow(req, remember = true) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
