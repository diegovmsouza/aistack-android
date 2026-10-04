package br.com.amberwrite.aistack.feature.chat.thread

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.ToolStatus
import br.com.amberwrite.aistack.feature.chat.markdown.MdCodeBlock
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.CodeBlock
import br.com.amberwrite.aistack.ui.designsystem.components.ShimmerText
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.designsystem.components.SubagentTimeline
import br.com.amberwrite.aistack.ui.designsystem.components.ToolCard
import br.com.amberwrite.aistack.ui.designsystem.tokens.Durations
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Limite de caracteres exibidos por bloco (entrada/saída) para não travar a rolagem. */
private const val MAX_SHOWN = 8_000

/**
 * Chamada de ferramenta: cartão com ícone por tipo, entrada e saída que expandem com mola,
 * erro destacado e «Carregar completo» quando o host cortou o bloco. Ferramentas de
 * sub-agente (`Task`) mostram a linha do tempo, que "respira" enquanto roda.
 */
@Composable
internal fun ToolItem(
    tool: ChatItem.Tool,
    expanding: Boolean,
    expandFailed: Boolean,
    onExpand: () -> Unit,
    onOpenAgents: () -> Unit,
    haptics: AiHaptics,
    modifier: Modifier = Modifier,
) {
    val c = AiTheme.colors
    val t = AiTheme.typography
    val running = tool.status == ToolStatus.RUNNING
    val isError = tool.status == ToolStatus.ERROR
    val sub = tool.isSubagent
    val steps = remember(tool) { if (sub) ThreadModel.subagentSteps(tool) else emptyList() }
    val input = remember(tool.input, tool.name) { ThreadModel.inputCode(tool) }
    val output = remember(tool.output) { ThreadModel.outputText(tool) }
    val hasContent = steps.isNotEmpty() || input != null || output != null || running || tool.truncated
    var expanded by rememberSaveable(tool.key) { mutableStateOf(sub && running) }

    val meta = when {
        tool.status == ToolStatus.INTERRUPTED -> stringResource(R.string.chat_tool_interrupted)
        sub && steps.isNotEmpty() -> pluralStringResource(R.plurals.chat_agent_steps, steps.size, steps.size)
        else -> null
    }

    ToolCard(
        title = if (sub) ThreadModel.agentTitle(tool) else tool.name,
        kind = ThreadModel.toolKindOf(tool.name, sub),
        status = ThreadModel.dsStatus(tool.status),
        modifier = modifier.fillMaxWidth(),
        detail = if (sub) tool.name.takeIf { it != ThreadModel.agentTitle(tool) } else ThreadModel.toolDetailOf(tool.input),
        meta = meta,
        expanded = if (hasContent) expanded else false,
        onExpandedChange = if (hasContent) ({ expanded = it }) else null,
        haptics = haptics,
        content = if (!hasContent) null else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (sub) {
                        if (steps.isNotEmpty()) BreathingTimeline(tool, running)
                        AiButton(
                            text = stringResource(R.string.chat_agents_open),
                            onClick = onOpenAgents,
                            variant = ButtonVariant.Ghost,
                            size = ButtonSize.Small,
                            leadingIcon = Lucide.Bot,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                    if (input != null) {
                        SectionLabel(stringResource(R.string.chat_tool_input))
                        MdCodeBlock(code = input.first.cap(), language = input.second)
                    }
                    when {
                        output != null -> {
                            SectionLabel(stringResource(if (isError) R.string.chat_tool_error else R.string.chat_tool_output), error = isError)
                            CodeBlock(
                                text = output.cap(),
                                tone = if (isError) c.danger else c.fg2,
                                background = if (isError) c.dangerSoft else c.surface2,
                            )
                        }
                        running -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Spinner(size = 12.dp)
                            Spacer(Modifier.width(8.dp))
                            ShimmerText(stringResource(R.string.chat_tool_running), style = t.caption)
                        }
                    }
                    if (tool.truncated || (input?.first?.length ?: 0) > MAX_SHOWN || (output?.length ?: 0) > MAX_SHOWN) {
                        if (tool.truncated) {
                            LoadFullRow(expanding, expandFailed, onExpand)
                        } else {
                            Text(stringResource(R.string.chat_tool_clipped), style = t.caption, color = c.fg3)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String, error: Boolean = false) {
    Text(
        text.uppercase(),
        style = AiTheme.typography.overline,
        color = if (error) AiTheme.colors.danger else AiTheme.colors.fg3,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

/** Linha do tempo do sub-agente; enquanto roda, pulsa suavemente (desligado com movimento reduzido). */
@Composable
private fun BreathingTimeline(tool: ChatItem.Tool, running: Boolean) {
    val steps = remember(tool) { ThreadModel.subagentSteps(tool) }
    val breathe = running && !AiTheme.reducedMotion
    val alpha: State<Float> = if (breathe) {
        rememberInfiniteTransition(label = "agentBreath").animateFloat(
            initialValue = 1f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(tween(Durations.Breath), RepeatMode.Reverse),
            label = "agentBreathAlpha",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    // Lido só na fase de desenho: o pulso não recompõe a linha do tempo.
    SubagentTimeline(steps = steps, modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha.value })
}

private fun String.cap(): String = if (length <= MAX_SHOWN) this else take(MAX_SHOWN) + "\n…"
