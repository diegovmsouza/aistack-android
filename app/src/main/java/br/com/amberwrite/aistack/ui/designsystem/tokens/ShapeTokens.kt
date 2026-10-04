package br.com.amberwrite.aistack.ui.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Raios do desktop (rounded-md/lg, composer 22, squircles 32/56). */
@Immutable
data class AiStackShapes(
    val xs: RoundedCornerShape = RoundedCornerShape(6.dp),
    val sm: RoundedCornerShape = RoundedCornerShape(8.dp),
    val md: RoundedCornerShape = RoundedCornerShape(10.dp),
    val lg: RoundedCornerShape = RoundedCornerShape(14.dp),
    val xl: RoundedCornerShape = RoundedCornerShape(18.dp),
    /** Caixa do composer. */
    val composer: RoundedCornerShape = RoundedCornerShape(22.dp),
    /** Folhas inferiores (só cantos de cima). */
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    /** Balão do usuário (canto inferior direito mais fechado). */
    val userBubble: RoundedCornerShape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
)

/** Raios avulsos para quem precisa do valor em Dp. */
object Radii {
    val xs: Dp = 6.dp
    val sm: Dp = 8.dp
    val md: Dp = 10.dp
    val lg: Dp = 14.dp
    val xl: Dp = 18.dp
    val composer: Dp = 22.dp
    /** Avatar/squircle de agente pequeno e grande. */
    val squircleSmall: Dp = 32.dp
    val squircleLarge: Dp = 56.dp
}

fun AiStackShapes.toMaterial(): Shapes = Shapes(
    extraSmall = xs,
    small = sm,
    medium = lg,
    large = xl,
    extraLarge = RoundedCornerShape(28.dp),
)

/** Escala de espaçamento em passos de 4dp. */
@Immutable
data class AiStackSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    /** Margem lateral padrão das telas. */
    val gutter: Dp = 16.dp,
)
