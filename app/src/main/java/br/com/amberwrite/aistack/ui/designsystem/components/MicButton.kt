package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.tokens.Durations
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Estado do botão de microfone (ditado). */
enum class MicState { Idle, Listening, Processing, Disabled }

/**
 * Botão de microfone do composer. Ouvindo: círculo cheio no acento com um halo que cresce
 * com [level] (0..1, nível RMS normalizado do áudio) e um anel que “respira”.
 * Processando: spinner. Com movimento reduzido o anel some, mas o halo ainda reage ao nível
 * (é a única pista de que o microfone está captando).
 *
 * @param onLongPress opcional — ex.: segurar para falar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MicButton(
    state: MicState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    level: Float = 0f,
    size: Dp = 44.dp,
    onLongPress: (() -> Unit)? = null,
    haptics: AiHaptics = AiHaptics.None,
) {
    val c = AiTheme.colors
    val reduced = AiTheme.reducedMotion
    val listening = state == MicState.Listening
    val container by animateColorAsState(if (listening) c.accent else c.surface2, AiTheme.motion.fade(), label = "micBg")
    val content = if (listening) c.accentFg else c.fg2
    val smoothLevel by animateFloatAsState(
        if (listening) level.coerceIn(0f, 1f) else 0f,
        spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "micLevel",
    )
    val breath = if (listening && !reduced) {
        val t = rememberInfiniteTransition(label = "micBreath")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(Durations.Breath, easing = LinearEasing)), label = "micBreathV")
        v
    } else {
        -1f
    }
    Box(
        modifier
            .size(size)
            .semantics {
                contentDescription = "Ditado por voz"
                stateDescription = when (state) {
                    MicState.Idle -> "Parado"
                    MicState.Listening -> "Ouvindo"
                    MicState.Processing -> "Transcrevendo"
                    MicState.Disabled -> "Indisponível"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (listening) {
            // Desenha além dos limites de propósito: o halo não ocupa espaço no layout.
            Canvas(Modifier.size(size)) {
                val base = size.toPx() / 2f
                // Halo proporcional ao nível de áudio (essencial: sempre ativo).
                drawCircle(c.accent.copy(alpha = 0.22f), radius = base * (1f + 0.55f * smoothLevel))
                // Anel que respira (decorativo: desligado com movimento reduzido).
                if (breath >= 0f) {
                    drawCircle(
                        c.accent.copy(alpha = 0.35f * (1f - breath)),
                        radius = base * (1f + 0.6f * breath),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(size)
                .scale(if (listening) 1f + 0.06f * smoothLevel else 1f)
                .alpha(if (state == MicState.Disabled) 0.4f else 1f)
                .clip(AiTheme.shapes.pill)
                .background(container)
                .combinedClickable(
                    enabled = state != MicState.Disabled,
                    role = Role.Button,
                    onLongClick = onLongPress?.let { cb ->
                        {
                            haptics.perform(HapticKind.LongPress)
                            cb()
                        }
                    },
                ) {
                    haptics.perform(if (listening) HapticKind.Confirm else HapticKind.Tick)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                MicState.Processing -> Spinner(size = size * 0.42f, color = c.fg2)
                MicState.Disabled -> Icon(Lucide.MicOff, null, Modifier.size(size * 0.45f), tint = content)
                MicState.Listening -> Icon(Lucide.Square, null, Modifier.size(size * 0.36f), tint = content)
                MicState.Idle -> Icon(Lucide.Mic, null, Modifier.size(size * 0.45f), tint = content)
            }
        }
    }
}

/**
 * Onda de voz em barras que reage a [level] (0..1). Com [active], guarda um histórico que
 * rola da direita para a esquerda (~16 amostras/s). Com movimento reduzido não há rolagem:
 * todas as barras seguem o nível atual num perfil fixo.
 */
@Composable
fun VoiceWave(
    level: Float,
    modifier: Modifier = Modifier,
    color: Color = AiTheme.colors.accent,
    bars: Int = 32,
    active: Boolean = true,
    height: Dp = 28.dp,
) {
    val reduced = AiTheme.reducedMotion
    val currentLevel by rememberUpdatedState(level.coerceIn(0f, 1f))
    val history = remember(bars) { mutableStateListOf<Float>().apply { repeat(bars) { add(0f) } } }
    LaunchedEffect(active, reduced, bars) {
        if (!active || reduced) return@LaunchedEffect
        var tick = 0
        while (true) {
            delay(60)
            tick++
            // Pequena variação para não parecer um degrau quando o nível fica parado.
            val jitter = 0.85f + 0.15f * sin(tick * 1.7f)
            history.removeAt(0)
            history.add((currentLevel * jitter).coerceIn(0f, 1f))
        }
    }
    val smooth by animateFloatAsState(currentLevel, spring(stiffness = 600f), label = "waveLevel")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val gap = 3.dp.toPx()
        val w = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(1.5f)
        val minH = 2.dp.toPx()
        for (i in 0 until bars) {
            val v = if (active && !reduced) {
                history.getOrElse(i) { 0f }
            } else {
                // Perfil em “sino”: centro mais alto.
                val profile = sin(PI * (i + 0.5) / bars).toFloat()
                if (active) smooth * (0.35f + 0.65f * profile) else 0f
            }
            val h = (minH + v * (size.height - minH)).coerceAtMost(size.height)
            drawRoundRect(
                color.copy(alpha = if (active) 0.45f + 0.55f * v else 0.3f),
                topLeft = Offset(i * (w + gap), (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f),
            )
        }
    }
}

@AiPreviews
@Composable
private fun MicButtonPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MicButton(MicState.Idle, onClick = {})
            MicButton(MicState.Listening, onClick = {}, level = 0.6f)
            MicButton(MicState.Processing, onClick = {})
            MicButton(MicState.Disabled, onClick = {})
        }
        VoiceWave(level = 0.5f, active = false)
    }
}
