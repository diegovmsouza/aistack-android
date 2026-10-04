package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.tokens.Durations
import br.com.amberwrite.aistack.ui.designsystem.tokens.Easings

/** Estado de um agente/sessão/conexão representado por um [StatusDot]. */
enum class AgentStatus { Online, Busy, Pending, Error, Offline }

/** Cor semântica de cada [AgentStatus] no tema atual. */
@Composable
fun AgentStatus.color(): Color = when (this) {
    AgentStatus.Online -> AiTheme.colors.ok
    AgentStatus.Busy -> AiTheme.colors.accent
    AgentStatus.Pending -> AiTheme.colors.warn
    AgentStatus.Error -> AiTheme.colors.danger
    AgentStatus.Offline -> AiTheme.colors.fg3
}

/**
 * Ponto de status. Em [AgentStatus.Busy] e [AgentStatus.Pending] pulsa como o `pulse-dot` do
 * desktop (opacidade .35↔1, escala .85↔1, 1,2 s); com movimento reduzido fica estático.
 *
 * @param pulse força/desliga o pulso; `null` decide pelo status.
 * @param color sobrescreve a cor semântica.
 */
@Composable
fun StatusDot(
    status: AgentStatus,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    pulse: Boolean? = null,
    color: Color? = null,
) {
    val target = color ?: status.color()
    val tint by animateColorAsState(target, AiTheme.motion.fade(), label = "statusDotColor")
    val shouldPulse = (pulse ?: (status == AgentStatus.Busy || status == AgentStatus.Pending)) && !AiTheme.reducedMotion
    var alpha = 1f
    var scale = 1f
    if (shouldPulse) {
        val transition = rememberInfiniteTransition(label = "statusDot")
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(Durations.Pulse / 2, easing = Easings.InOut), RepeatMode.Reverse),
            label = "statusDotPulse",
        )
        alpha = 1f - 0.65f * t
        scale = 1f - 0.15f * t
    }
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        if (shouldPulse) drawCircle(tint.copy(alpha = 0.18f * (1f - alpha + 0.35f)), radius = r)
        drawCircle(tint, radius = r * scale * 0.92f, alpha = alpha)
    }
}


@AiPreviews
@Composable
private fun StatusDotPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AgentStatus.entries.forEach { StatusDot(it, size = 10.dp) }
        }
    }
}
