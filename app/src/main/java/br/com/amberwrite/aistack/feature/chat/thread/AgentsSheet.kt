package br.com.amberwrite.aistack.feature.chat.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.ToolStatus
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AgentStatus
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.StatusDot
import br.com.amberwrite.aistack.ui.designsystem.components.SubagentTimeline
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard

/** Folha «Agentes»: todos os sub-agentes desta conversa e o que cada um fez. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsSheet(agents: List<AgentInfo>, onDismiss: () -> Unit) {
    val c = AiTheme.colors
    val t = AiTheme.typography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        contentColor = c.fg,
        scrimColor = c.scrim,
        shape = AiTheme.shapes.sheet,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = ThreadMaxWidth).fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.chat_agents_title), style = t.title, color = c.fg, modifier = Modifier.semantics { heading() })
                val running = agents.count { it.running }
                Text(
                    if (agents.isEmpty()) {
                        stringResource(R.string.chat_agents_none_short)
                    } else {
                        pluralStringResource(R.plurals.chat_agents_count, agents.size, agents.size) +
                            if (running > 0) " · " + pluralStringResource(R.plurals.chat_agents_running, running, running) else ""
                    },
                    style = t.caption,
                    color = c.fg3,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )
            }
            if (agents.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.chat_agents_empty_title),
                    body = stringResource(R.string.chat_agents_empty_body),
                    modifier = Modifier.padding(vertical = 24.dp),
                    art = { ChatEmptyArt(Modifier.width(120.dp)) },
                )
            } else {
                LazyColumn(
                    Modifier.widthIn(max = ThreadMaxWidth).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(agents, key = { it.key }) { agent -> AgentCard(agent) }
                }
            }
        }
    }
}

@Composable
private fun AgentCard(agent: AgentInfo) {
    val c = AiTheme.colors
    val t = AiTheme.typography
    val status = when (agent.status) {
        ToolStatus.RUNNING -> AgentStatus.Busy
        ToolStatus.DONE -> AgentStatus.Online
        ToolStatus.ERROR -> AgentStatus.Error
        ToolStatus.INTERRUPTED -> AgentStatus.Offline
    }
    val statusText = stringResource(
        when (agent.status) {
            ToolStatus.RUNNING -> R.string.chat_agent_running
            ToolStatus.DONE -> R.string.chat_agent_done
            ToolStatus.ERROR -> R.string.chat_agent_failed
            ToolStatus.INTERRUPTED -> R.string.chat_agent_interrupted
        },
    )
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            StatusDot(status = status, pulse = agent.running && !AiTheme.reducedMotion)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(agent.title, style = t.heading, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(statusText, agent.subtitle).joinToString(" · "),
                    style = t.caption,
                    color = c.fg3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (agent.steps.isNotEmpty()) {
            SubagentTimeline(agent.steps, Modifier.padding(top = 10.dp))
        } else if (!agent.running) {
            Text(stringResource(R.string.chat_agent_no_steps), style = t.caption, color = c.fg3, modifier = Modifier.padding(top = 8.dp))
        } else {
            Text(stringResource(R.string.chat_agent_starting), style = t.caption, color = c.fg3, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
