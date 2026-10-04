package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme

/**
 * Indicador de carregamento circular fino (arco de 270° girando). Com movimento reduzido,
 * mostra o arco parado.
 */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AiTheme.colors.fg2,
    strokeWidth: Dp = 2.dp,
) {
    val rotation = if (AiTheme.reducedMotion) {
        0f
    } else {
        val t = rememberInfiniteTransition(label = "spinner")
        val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinnerRot")
        r
    }
    Canvas(modifier.size(size)) {
        val w = strokeWidth.toPx()
        drawArc(
            color = color.copy(alpha = 0.2f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(w / 2, w / 2),
            size = Size(this.size.width - w, this.size.height - w),
            style = Stroke(w),
        )
        drawArc(
            color = color,
            startAngle = rotation - 90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(w / 2, w / 2),
            size = Size(this.size.width - w, this.size.height - w),
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }
}
