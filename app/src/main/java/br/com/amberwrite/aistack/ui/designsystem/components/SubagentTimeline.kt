package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.brand.ProviderLogo
import br.com.amberwrite.aistack.ui.designsystem.tokens.Durations
import br.com.amberwrite.aistack.ui.designsystem.tokens.Easings
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Estado de um passo/subagente na linha do tempo. */
enum class StepState { Pending, Running, Done, Failed, Skipped }

/** Um passo da [SubagentTimeline]. */
data class TimelineStep(
    val id: String,
    val title: String,
    val state: StepState,
    val subtitle: String? = null,
    val providerId: String? = null,
    val meta: String? = null,
)

/**
 * Linha do tempo vertical de subagentes/passos. O nó em execução “respira” (halo que cresce
 * e esmaece em 1,8 s); com movimento reduzido fica um halo fixo.
 *
 * @param onStepClick `null` = linhas não clicáveis.
 */
@Composable
fun SubagentTimeline(
    steps: List<TimelineStep>,
    modifier: Modifier = Modifier,
    onStepClick: ((TimelineStep) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            TimelineRow(
                step = step,
                isFirst = index == 0,
                isLast = index == steps.lastIndex,
                nextState = steps.getOrNull(index + 1)?.state,
                onClick = onStepClick?.let { cb -> { cb(step) } },
            )
        }
    }
}

@Composable
private fun StepState.tone(): Color = when (this) {
    StepState.Pending, StepState.Skipped -> AiTheme.colors.fg3
    StepState.Running -> AiTheme.colors.accent
    StepState.Done -> AiTheme.colors.ok
    StepState.Failed -> AiTheme.colors.danger
}

@Composable
private fun TimelineRow(
    step: TimelineStep,
    isFirst: Boolean,
    isLast: Boolean,
    nextState: StepState?,
    onClick: (() -> Unit)?,
) {
    val c = AiTheme.colors
    val tone by animateColorAsState(step.state.tone(), AiTheme.motion.fade(), label = "stepTone")
    val lineDone = step.state == StepState.Done && nextState != null && nextState != StepState.Pending
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(AiTheme.shapes.md)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp),
    ) {
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            val lineColor = c.lineStrong
            val doneColor = c.ok.copy(alpha = 0.5f)
            Canvas(Modifier.fillMaxHeight().width(2.dp)) {
                val top = if (isFirst) 16.dp.toPx() else 0f
                val bottom = if (isLast) 16.dp.toPx() else size.height
                if (bottom > top) {
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(size.width / 2, top), androidx.compose.ui.geometry.Offset(size.width / 2, 16.dp.toPx()), strokeWidth = size.width)
                    drawLine(if (lineDone) doneColor else lineColor, androidx.compose.ui.geometry.Offset(size.width / 2, 16.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width / 2, bottom), strokeWidth = size.width)
                }
            }
            Box(Modifier.padding(top = 6.dp)) { StepNode(step.state, tone) }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (step.providerId != null) ProviderLogo(step.providerId, size = 13.dp)
                Text(
                    step.title,
                    style = AiTheme.typography.label,
                    color = if (step.state == StepState.Pending || step.state == StepState.Skipped) c.fg2 else c.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (step.meta != null) Text(step.meta, style = AiTheme.typography.caption, color = c.fg3)
            }
            if (step.subtitle != null) {
                if (step.state == StepState.Running) {
                    ShimmerText(step.subtitle, style = AiTheme.typography.caption)
                } else {
                    Text(step.subtitle, style = AiTheme.typography.caption, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StepNode(state: StepState, tone: Color) {
    val c = AiTheme.colors
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        if (state == StepState.Running) {
            val (haloScale, haloAlpha) = if (AiTheme.reducedMotion) {
                1.3f to 0.18f
            } else {
                val t = rememberInfiniteTransition(label = "breath")
                val p by t.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(Durations.Breath / 2, easing = Easings.InOut), RepeatMode.Reverse),
                    label = "breathPhase",
                )
                (1f + 0.45f * p) to (0.28f - 0.2f * p)
            }
            Box(
                Modifier
                    .size(14.dp)
                    .scale(haloScale)
                    .clip(AiTheme.shapes.pill)
                    .background(tone.copy(alpha = haloAlpha)),
            )
        }
        when (state) {
            StepState.Done -> Icon(Lucide.CircleCheck, "Concluído", Modifier.size(16.dp).background(c.bg, AiTheme.shapes.pill), tint = tone)
            StepState.Failed -> Icon(Lucide.CircleX, "Falhou", Modifier.size(16.dp).background(c.bg, AiTheme.shapes.pill), tint = tone)
            StepState.Running -> Box(Modifier.size(10.dp).clip(AiTheme.shapes.pill).background(tone))
            StepState.Pending, StepState.Skipped -> Box(
                Modifier
                    .size(10.dp)
                    .clip(AiTheme.shapes.pill)
                    .background(c.bg)
                    .border(1.5.dp, tone, AiTheme.shapes.pill),
            )
        }
    }
}

@AiPreviews
@Composable
private fun SubagentTimelinePreview() {
    PreviewSurface {
        SubagentTimeline(
            listOf(
                TimelineStep("1", "Explorar o repositório", StepState.Done, "34 arquivos lidos", providerId = "claude", meta = "12 s"),
                TimelineStep("2", "Revisar a API", StepState.Running, "Lendo RelayProtocol.kt…", providerId = "codex"),
                TimelineStep("3", "Escrever testes", StepState.Pending, providerId = "gemini"),
                TimelineStep("4", "Gerar changelog", StepState.Failed, "Sem permissão de escrita"),
            ),
        )
    }
}
