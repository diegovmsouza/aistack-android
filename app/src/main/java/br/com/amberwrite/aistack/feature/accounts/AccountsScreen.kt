package br.com.amberwrite.aistack.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import br.com.amberwrite.aistack.data.model.AccountStatus
import br.com.amberwrite.aistack.feature.common.CenteredLoading
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.SectionTitle
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.relativeTime
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.ProviderBadge
import br.com.amberwrite.aistack.ui.designsystem.components.QuotaBar
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.icons.Lucide

@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { AccountsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = AiTheme.colors

    FeatureScaffold(
        title = "Contas e cotas",
        subtitle = state.appInfo?.version?.let { "AiStack $it no desktop" },
        onBack = onBack,
        actions = { AiIconButton(icon = Lucide.RefreshCw, contentDescription = "Recarregar", onClick = vm::reload) }
    ) {
        state.error?.let { ErrorStrip(it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.loaded && state.accounts.isEmpty() -> CenteredLoading(text = "Carregando contas…")
                state.accounts.isEmpty() && state.mcp.isEmpty() ->
                    EmptyState(title = "Nenhuma conta", modifier = Modifier.fillMaxSize())
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.accounts, key = { it.providerId + ":" + it.slot }) { AccountCard(it) }
                    if (state.mcp.isNotEmpty()) {
                        item(key = "mcpTitle") { SectionTitle("Servidores MCP") }
                        items(state.mcp, key = { "mcp:" + it.name }) { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name, style = AiTheme.typography.body, color = if (m.enabled) c.fg else c.fg3, modifier = Modifier.weight(1f))
                                Text(
                                    listOfNotNull(m.transport, if (m.enabled) null else "desativado").joinToString(" · "),
                                    style = AiTheme.typography.caption,
                                    color = c.fg3
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(a: AccountStatus) {
    val c = AiTheme.colors
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderBadge(providerId = a.providerId)
            Spacer(Modifier.width(8.dp))
            Text(
                a.label ?: a.email ?: a.slot,
                style = AiTheme.typography.heading,
                color = c.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        val meta = listOfNotNull(
            a.email?.takeIf { it != a.label },
            a.plan,
            a.authState,
            if (!a.installed) "CLI não instalada" else null
        ).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, style = AiTheme.typography.caption, color = c.fg3)
        a.usageWindows.forEach { w ->
            Spacer(Modifier.width(4.dp))
            QuotaBar(
                fraction = ((w.usedPct ?: 0.0) / 100.0).toFloat(),
                label = w.label,
                valueText = w.usedPct?.let { "${it.toInt()}%" },
                resetText = w.resetsAt?.let { "renova " + relativeTime(it) },
                providerId = a.providerId,
                modifier = Modifier.fillMaxWidth()
            )
        }
        a.usageAt?.let { Text("Atualizado ${relativeTime(it)}", style = AiTheme.typography.caption, color = c.fg3) }
    }
}
