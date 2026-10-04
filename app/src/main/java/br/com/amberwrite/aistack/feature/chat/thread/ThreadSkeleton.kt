package br.com.amberwrite.aistack.feature.chat.thread

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.tokens.Durations

/** Esqueleto da thread enquanto a primeira página chega (estático com movimento reduzido). */
@Composable
fun ThreadSkeleton(modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    val alpha: State<Float> = if (AiTheme.reducedMotion) {
        remember { mutableFloatStateOf(0.7f) }
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(Durations.Shimmer / 2, easing = LinearEasing), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
    }
    val desc = stringResource(R.string.chat_loading)
    Box(modifier.fillMaxSize().semantics { contentDescription = desc }, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = ThreadMaxWidth)
                .fillMaxWidth()
                .padding(16.dp)
                .graphicsLayer { this.alpha = alpha.value },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Bar(Alignment.End, 0.55f, 40.dp, true)
            Bar(Alignment.Start, 0.92f, 14.dp)
            Bar(Alignment.Start, 0.85f, 14.dp)
            Bar(Alignment.Start, 0.6f, 14.dp)
            Bar(Alignment.Start, 1f, 48.dp)
            Bar(Alignment.End, 0.4f, 40.dp, true)
            Bar(Alignment.Start, 0.9f, 14.dp)
            Bar(Alignment.Start, 0.7f, 14.dp)
        }
    }
}

@Composable
private fun Bar(align: Alignment.Horizontal, fraction: Float, height: Dp, bubble: Boolean = false) {
    val c = AiTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = if (align == Alignment.End) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(height)
                .clip(if (bubble) AiTheme.shapes.userBubble else AiTheme.shapes.sm)
                .background(if (bubble) c.surface3 else c.surface2),
        )
    }
}

/**
 * Ilustração vetorial do estado vazio: camada neutra tingida de `fg` e camada de destaque
 * tingida de `accent` (mesmo formato das ilustrações do design system, 160×120).
 */
@Composable
fun ChatEmptyArt(modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    Box(modifier.aspectRatio(160f / 120f)) {
        Image(
            painter = painterResource(R.drawable.ill_chat_empty),
            contentDescription = null,
            colorFilter = ColorFilter.tint(c.fg),
            modifier = Modifier.matchParentSize(),
        )
        Image(
            painter = painterResource(R.drawable.ill_chat_empty_accent),
            contentDescription = null,
            colorFilter = ColorFilter.tint(c.accent),
            modifier = Modifier.matchParentSize(),
        )
    }
}
