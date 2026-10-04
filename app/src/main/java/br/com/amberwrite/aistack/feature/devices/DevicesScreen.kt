package br.com.amberwrite.aistack.feature.devices

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.feature.common.CenteredLoading
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.relativeTime
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AgentStatus
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.StatusDot
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.icons.Lucide

@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val vm = containerViewModel { DevicesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selfId = remember { vm.currentDeviceId }
    val c = AiTheme.colors

    FeatureScaffold(
        title = "Aparelhos pareados",
        subtitle = state.relay?.let { r -> listOfNotNull("Relay: ${r.state}", r.sessions?.let { "$it sessões" }).joinToString(" · ") },
        onBack = onBack,
        actions = { AiIconButton(icon = Lucide.RefreshCw, contentDescription = "Recarregar", onClick = vm::reload) }
    ) {
        state.error?.let { ErrorStrip(it) }
        state.relay?.error?.let { ErrorStrip(it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.devices.isEmpty() -> CenteredLoading(text = "Carregando…")
                state.devices.isEmpty() -> EmptyState(title = "Nenhum aparelho", modifier = Modifier.fillMaxSize())
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.devices, key = { it.id }) { d ->
                        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(if (d.revoked) AgentStatus.Offline else AgentStatus.Online)
                                Spacer(Modifier.width(8.dp))
                                Text(d.name, style = AiTheme.typography.heading, color = if (d.revoked) c.fg3 else c.fg, modifier = Modifier.weight(1f))
                                if (d.id == selfId) Text("Este aparelho", style = AiTheme.typography.caption, color = c.accent)
                            }
                            val meta = listOfNotNull(
                                d.pairedAtMs?.let { "pareado " + relativeTime(it) },
                                d.lastSeenMs?.let { "visto " + relativeTime(it) },
                                if (d.revoked) "revogado" else null
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, style = AiTheme.typography.caption, color = c.fg3)
                        }
                    }
                }
            }
        }
    }
}
