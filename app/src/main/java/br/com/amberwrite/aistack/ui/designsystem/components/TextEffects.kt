package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.tokens.Durations

/**
 * Texto com brilho deslizante (o `shimmer` do desktop, usado em “Pensando…”, “Executando…”).
 * O texto base usa [color] e uma faixa de [highlight] varre da direita para a esquerda.
 * Com movimento reduzido o texto fica estático em [color].
 *
 * @param active liga/desliga o brilho sem trocar o componente.
 */
@Composable
fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AiTheme.typography.label,
    color: Color = AiTheme.colors.fg3,
    highlight: Color = AiTheme.colors.fg,
    active: Boolean = true,
    maxLines: Int = 1,
) {
    val animate = active && !AiTheme.reducedMotion
    if (!animate) {
        Text(text, modifier, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 1f,
        targetValue = -0.5f,
        animationSpec = infiniteRepeatable(tween(Durations.Shimmer, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    Text(
        text = text,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val band = size.width.coerceAtLeast(1f) * 0.45f
                val center = progress * (size.width + band)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to highlight,
                        1f to Color.Transparent,
                        startX = center - band,
                        endX = center + band,
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            },
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Cursor de digitação piscando (fim do texto em streaming). Com movimento reduzido fica fixo.
 *
 * @param block cursor em bloco (largura de 0,55 em) em vez de barra.
 */
@Composable
fun TypingCaret(
    modifier: Modifier = Modifier,
    color: Color = AiTheme.colors.accent,
    height: Dp = 16.dp,
    block: Boolean = false,
    blinking: Boolean = true,
) {
    val alpha = if (blinking && !AiTheme.reducedMotion) {
        val transition = rememberInfiniteTransition(label = "caret")
        val a by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = Durations.Caret * 2
                    1f at 0
                    1f at Durations.Caret - 80
                    0f at Durations.Caret
                    0f at Durations.Caret * 2 - 80
                    1f at Durations.Caret * 2
                },
            ),
            label = "caretAlpha",
        )
        a
    } else {
        1f
    }
    androidx.compose.foundation.layout.Box(
        modifier
            .alpha(alpha)
            .size(width = if (block) height * 0.55f else 2.dp, height = height)
            .clip(AiTheme.shapes.xs)
            .background(color),
    )
}

@AiPreviews
@Composable
private fun TextEffectsPreview() {
    PreviewSurface {
        ShimmerText("Pensando…")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Escrevendo a resposta", style = AiTheme.typography.body, color = AiTheme.colors.fg)
            TypingCaret()
        }
        TypingCaret(block = true)
    }
}
