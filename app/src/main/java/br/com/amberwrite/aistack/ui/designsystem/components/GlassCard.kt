package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme

/**
 * Cartão “vidro”: fundo `glass` translúcido, borda fina e um brilho sutil no topo (como o
 * `backdrop-filter` do desktop, sem o blur — caro no Android < 12). Use sobre conteúdo
 * rolando (barras flutuantes, composer, popovers).
 *
 * @param elevation sombra suave; 0 desliga.
 * @param onClick torna o cartão clicável (com leve escala ao pressionar).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = AiTheme.shapes.lg,
    elevation: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    borderColor: Color = AiTheme.colors.line,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AiTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale = if (onClick != null) pressScale(source, 0.985f) else 1f
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (c.isDark) 0.045f else 0.55f),
        0.35f to Color.Transparent,
    )
    Column(
        modifier
            .scale(scale)
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = c.shadow, spotColor = c.shadow) else Modifier)
            .clip(shape)
            .background(c.glass)
            .background(sheen)
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick) else Modifier,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Cartão sólido (`surface` + borda), para listas e blocos que não flutuam. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = AiTheme.shapes.lg,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    color: Color = AiTheme.colors.surface,
    borderColor: Color = AiTheme.colors.line,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale = if (onClick != null) pressScale(source, 0.985f) else 1f
    Column(
        modifier
            .scale(scale)
            .clip(shape)
            .background(color)
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick) else Modifier,
            )
            .padding(contentPadding),
        content = content,
    )
}

@AiPreviews
@Composable
private fun GlassCardPreview() {
    PreviewSurface {
        GlassCard {
            Text("Cartão de vidro", style = AiTheme.typography.heading, color = AiTheme.colors.fg)
            Text("Fundo translúcido com borda fina.", style = AiTheme.typography.bodySmall, color = AiTheme.colors.fg2)
        }
        SurfaceCard(onClick = {}) {
            Text("Cartão sólido clicável", style = AiTheme.typography.heading, color = AiTheme.colors.fg)
        }
    }
}
