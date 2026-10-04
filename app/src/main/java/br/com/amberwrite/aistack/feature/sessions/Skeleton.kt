package br.com.amberwrite.aistack.feature.sessions

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.tokens.Durations

/**
 * Brilho de carregamento (esqueleto). Com movimento reduzido fica um bloco estático.
 * Usado pelas telas de sessões e de nova sessão.
 */
fun Modifier.skeletonShimmer(shape: Shape? = null): Modifier = composed {
    val c = AiTheme.colors
    val base = c.surface2
    val shine = c.surface3
    val clipped = if (shape != null) this.clip(shape) else this
    if (AiTheme.reducedMotion) {
        clipped.background(base)
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val x by transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(Durations.Shimmer, easing = LinearEasing), RepeatMode.Restart),
            label = "skeletonX"
        )
        clipped.background(
            Brush.linearGradient(
                colors = listOf(base, shine, base),
                start = Offset(x * 600f - 300f, 0f),
                end = Offset(x * 600f + 300f, 120f)
            )
        )
    }
}

/** Bloco retangular do esqueleto. */
@Composable
fun SkeletonBlock(width: Dp?, height: Dp, modifier: Modifier = Modifier) {
    val m = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(m.height(height).skeletonShimmer(AiTheme.shapes.xs))
}

/** Esqueleto da lista de conversas: cabeçalho de grupo e algumas linhas. */
@Composable
fun SessionsSkeleton(modifier: Modifier = Modifier, description: String) {
    Column(
        modifier
            .padding(horizontal = AiTheme.spacing.gutter, vertical = 8.dp)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonBlock(width = 56.dp, height = 12.dp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        repeat(6) { i ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(AiTheme.shapes.lg)
                    .background(AiTheme.colors.surface)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).skeletonShimmer(AiTheme.shapes.pill))
                    Spacer(Modifier.width(10.dp))
                    SkeletonBlock(width = if (i % 2 == 0) 180.dp else 140.dp, height = 14.dp)
                    Spacer(Modifier.weight(1f))
                    SkeletonBlock(width = 36.dp, height = 10.dp)
                }
                SkeletonBlock(width = 110.dp, height = 10.dp)
                SkeletonBlock(width = null, height = 10.dp)
            }
        }
    }
}
