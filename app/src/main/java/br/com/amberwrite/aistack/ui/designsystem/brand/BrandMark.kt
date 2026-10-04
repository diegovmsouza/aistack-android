package br.com.amberwrite.aistack.ui.designsystem.brand

import android.graphics.drawable.AnimatedVectorDrawable
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.PreviewSurface
import br.com.amberwrite.aistack.ui.designsystem.tokens.BrandColors
import br.com.amberwrite.aistack.ui.designsystem.tokens.Easings
import kotlin.math.PI
import kotlin.math.cos

/** Animações disponíveis para a marca. */
enum class BrandAnimation {
    /** Estática. */
    None,
    /** Montagem de abertura: quadrados saem empilhados do centro e se abrem (uma vez). */
    Assemble,
    /** Respiração escalonada contínua, para indicar streaming/atividade. */
    Pulse,
}

private const val UNIT = 1024f
private const val SIDE = 480f
private const val RADIUS = 120f
private val ORIGINS = floatArrayOf(152f, 272f, 392f)
private val COLORS = listOf(BrandColors.Claude, BrandColors.Codex, BrandColors.Gemini)

/** Estado de um quadrado num quadro da animação (unidades do viewport 1024). */
private class Square(val cx: Float, val cy: Float, val scale: Float, val alpha: Float) {
    fun path(): Path {
        val half = SIDE / 2f * scale
        val r = RADIUS * scale
        return Path().apply {
            addRoundRect(RoundRect(cx - half, cy - half, cx + half, cy + half, CornerRadius(r, r)))
        }
    }
}

/**
 * Marca do AiStack desenhada em Canvas. As interseções são calculadas a cada quadro com
 * operações de caminho, então o “multiply” do desktop continua correto mesmo com os
 * quadrados em movimento. Respeita movimento reduzido (fica estática).
 *
 * @param size lado do quadrado ocupado pela marca.
 * @param animation [BrandAnimation.None], [BrandAnimation.Assemble] ou [BrandAnimation.Pulse].
 * @param contentDescription descrição para leitores de tela; `null` = decorativa.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    animation: BrandAnimation = BrandAnimation.None,
    contentDescription: String? = null,
) {
    val reduced = AiTheme.reducedMotion
    val assemble = remember { Animatable(if (animation == BrandAnimation.Assemble && !reduced) 0f else 1f) }
    LaunchedEffect(animation, reduced) {
        if (animation == BrandAnimation.Assemble && !reduced) {
            assemble.snapTo(0f)
            assemble.animateTo(1f, tween(durationMillis = 1000, easing = LinearEasing))
        } else {
            assemble.snapTo(1f)
        }
    }
    val pulsing = animation == BrandAnimation.Pulse && !reduced
    val phase = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "brandPulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = "brandPulsePhase",
        ).value
    } else {
        0f
    }

    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.size(size).then(semantics)) {
        val p = assemble.value
        val squares = List(3) { i ->
            val center = ORIGINS[i] + SIDE / 2f
            // Montagem: atraso escalonado de 70ms, 720ms de movimento com ease-out.
            val local = ((p * 1000f - i * 70f) / 720f).coerceIn(0f, 1f)
            val t = Easings.Out.transform(local)
            val alpha = ((p * 1000f - i * 70f) / 260f).coerceIn(0f, 1f)
            val fromCenter = 512f
            val c = fromCenter + (center - fromCenter) * t
            var s = 0.55f + 0.45f * t
            if (pulsing) {
                val wave = 0.5f - 0.5f * cos(2f * PI.toFloat() * (phase - i * 0.1f))
                s *= 1f - 0.08f * wave
            }
            Square(c, c, s, alpha)
        }
        val overlapAlpha = if (p >= 1f) 1f else ((p * 1000f - 620f) / 280f).coerceIn(0f, 1f)
        scale(this.size.minDimension / UNIT, pivot = Offset.Zero) {
            drawBrand(squares, overlapAlpha)
        }
    }
}

private fun DrawScope.drawBrand(squares: List<Square>, overlapAlpha: Float) {
    val paths = squares.map { it.path() }
    squares.forEachIndexed { i, sq -> if (sq.alpha > 0f) drawPath(paths[i], COLORS[i], alpha = sq.alpha) }
    if (overlapAlpha <= 0f) return
    val cx = Path.combine(PathOperation.Intersect, paths[0], paths[1])
    val xg = Path.combine(PathOperation.Intersect, paths[1], paths[2])
    val all = Path.combine(PathOperation.Intersect, cx, paths[2])
    drawPath(cx, BrandColors.ClaudeCodex, alpha = overlapAlpha)
    drawPath(xg, BrandColors.CodexGemini, alpha = overlapAlpha)
    drawPath(all, BrandColors.AllThree, alpha = overlapAlpha)
}

/** Variante da marca com AnimatedVectorDrawable da plataforma (avd_brand_assemble / avd_brand_pulse). */
enum class BrandAvd(val res: Int) {
    Assemble(R.drawable.avd_brand_assemble),
    Pulse(R.drawable.avd_brand_pulse),
}

/**
 * Toca o AVD da marca via ImageView (o artefato animation-graphics do Compose não está no
 * classpath). Útil em splash/telas não-Compose; em Compose prefira [BrandMark]. Com movimento
 * reduzido exibe a marca estática.
 */
@Composable
fun BrandMarkAvd(
    avd: BrandAvd,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    playing: Boolean = true,
) {
    if (AiTheme.reducedMotion) {
        Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = modifier.size(size))
        return
    }
    AndroidView(
        modifier = modifier.size(size),
        factory = { ctx ->
            ImageView(ctx).apply {
                setImageDrawable(ctx.getDrawable(avd.res))
            }
        },
        update = { view ->
            val drawable = view.drawable as? AnimatedVectorDrawable
            if (playing) {
                if (drawable?.isRunning != true) drawable?.start()
            } else {
                drawable?.stop()
            }
        },
    )
}

@Preview(name = "Marca", showBackground = true)
@Composable
private fun BrandMarkPreview() {
    PreviewSurface {
        BrandMark(size = 96.dp)
    }
}

