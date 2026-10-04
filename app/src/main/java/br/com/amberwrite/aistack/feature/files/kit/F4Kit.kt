package br.com.amberwrite.aistack.feature.files.kit

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.tokens.Durations
import br.com.amberwrite.aistack.ui.icons.Lucide

/*
 * Kit visual compartilhado pelas telas da F4 (arquivos, visualizador, contas, ajustes e aparelhos):
 * shimmer/esqueletos de carregamento, ilustrações vetoriais próprias e estado de erro com "tentar de novo".
 * Tudo vetorial (nenhum bitmap) e respeitando o modo de movimento reduzido.
 */

// ---------------------------------------------------------------------------------------------
// Shimmer e esqueletos
// ---------------------------------------------------------------------------------------------

/** Fundo de "carregando" com brilho deslizante; estático quando o movimento está reduzido. */
fun Modifier.f4Shimmer(): Modifier = composed {
    val c = AiTheme.colors
    if (AiTheme.reducedMotion) return@composed background(c.surface2)
    val transition = rememberInfiniteTransition(label = "f4-shimmer")
    val phase = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(Durations.Shimmer, easing = LinearEasing), RepeatMode.Restart),
        label = "f4-shimmer-phase"
    )
    val base = c.surface2
    val glow = c.surface3
    drawBehind {
        val w = size.width.coerceAtLeast(1f)
        val x = phase.value * w
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, glow, base),
                start = Offset(x - w * 0.6f, 0f),
                end = Offset(x + w * 0.6f, size.height)
            )
        )
    }
}

/** Bloco retangular de esqueleto. */
@Composable
fun SkeletonBlock(width: Dp?, height: Dp, modifier: Modifier = Modifier, shape: Shape = AiTheme.shapes.sm) {
    val sized = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(sized.height(height).clip(shape).f4Shimmer())
}

/** Linha de esqueleto: ícone + duas linhas de texto (usada nas listas). */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier, widthFraction: Float = 0.6f) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBlock(width = 28.dp, height = 28.dp, shape = AiTheme.shapes.sm)
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(widthFraction).height(12.dp).clip(AiTheme.shapes.xs).f4Shimmer())
            Box(Modifier.fillMaxWidth(widthFraction * 0.55f).height(10.dp).clip(AiTheme.shapes.xs).f4Shimmer())
        }
    }
}

/** Lista de esqueletos com larguras variadas (parece conteúdo real). */
@Composable
fun SkeletonList(rows: Int = 8, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.files_kit_loading)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = false)
            .semantics { contentDescription = label }
            .padding(vertical = 8.dp)
    ) {
        repeat(rows) { i ->
            SkeletonRow(widthFraction = SKELETON_WIDTHS[i % SKELETON_WIDTHS.size])
        }
    }
}

/** Cartão de esqueleto (contas, aparelhos): título, linha e barras. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, bars: Int = 2) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(AiTheme.shapes.lg)
            .background(AiTheme.colors.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(width = 32.dp, height = 32.dp, shape = AiTheme.shapes.pill)
            Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.45f).height(12.dp).clip(AiTheme.shapes.xs).f4Shimmer())
                Box(Modifier.fillMaxWidth(0.3f).height(10.dp).clip(AiTheme.shapes.xs).f4Shimmer())
            }
        }
        repeat(bars) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(AiTheme.shapes.pill).f4Shimmer())
        }
    }
}

private val SKELETON_WIDTHS = floatArrayOf(0.62f, 0.45f, 0.78f, 0.53f, 0.7f, 0.38f, 0.66f, 0.5f)

// ---------------------------------------------------------------------------------------------
// Ilustrações vetoriais (viewport 160×120, duas camadas: neutra + destaque)
// ---------------------------------------------------------------------------------------------

/** Tons usados pelas camadas das ilustrações; resolvidos contra o tema atual. */
enum class ArtTone { Blob, Neutral, NeutralSoft, Accent, AccentSoft, Surface }

/** Um caminho SVG (`d`) com preenchimento e/ou traço. */
data class ArtLayer(val d: String, val fill: ArtTone? = null, val stroke: ArtTone? = null, val width: Float = 3f)

private const val BLOB =
    "M20 70 C20 35 55 18 85 20 C120 22 145 42 142 70 C139 98 112 108 80 106 C45 104 20 100 20 70 Z"

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy Z"

/** Ilustrações próprias da F4 (o design system só tem sessões, pendências, offline e pareamento). */
enum class F4Art(val layers: List<ArtLayer>) {
    EmptyFolder(
        listOf(
            ArtLayer(BLOB, fill = ArtTone.Blob),
            ArtLayer(
                "M42 44 L68 44 L76 52 L118 52 Q122 52 122 56 L122 92 Q122 96 118 96 L42 96 Q38 96 38 92 L38 48 Q38 44 42 44 Z",
                fill = ArtTone.NeutralSoft, stroke = ArtTone.Neutral
            ),
            ArtLayer(
                "M40 96 L48 66 Q49 62 53 62 L126 62 Q130 62 129 66 L121 92 Q120 96 116 96 Z",
                fill = ArtTone.Surface, stroke = ArtTone.Accent
            ),
            ArtLayer("M62 80 L98 80", stroke = ArtTone.AccentSoft, width = 4f),
            ArtLayer(circle(132f, 30f, 3f), fill = ArtTone.Accent),
            ArtLayer("M28 32 L28 40 M24 36 L32 36", stroke = ArtTone.Accent, width = 2.5f)
        )
    ),
    NoResults(
        listOf(
            ArtLayer(BLOB, fill = ArtTone.Blob),
            ArtLayer(circle(72f, 56f, 24f), fill = ArtTone.Surface, stroke = ArtTone.Neutral, width = 4f),
            ArtLayer("M90 74 L112 96", stroke = ArtTone.Accent, width = 8f),
            ArtLayer("M62 56 L82 56", stroke = ArtTone.AccentSoft, width = 4f),
            ArtLayer(circle(126f, 34f, 3f), fill = ArtTone.Accent)
        )
    ),
    Locked(
        listOf(
            ArtLayer(BLOB, fill = ArtTone.Blob),
            ArtLayer("M64 58 L64 46 Q64 30 80 30 Q96 30 96 46 L96 58", stroke = ArtTone.Neutral, width = 5f),
            ArtLayer(
                "M56 58 L104 58 Q108 58 108 62 L108 96 Q108 100 104 100 L56 100 Q52 100 52 96 L52 62 Q52 58 56 58 Z",
                fill = ArtTone.Surface, stroke = ArtTone.Accent
            ),
            ArtLayer(circle(80f, 74f, 5f), fill = ArtTone.Accent),
            ArtLayer("M80 79 L80 88", stroke = ArtTone.Accent, width = 4f)
        )
    ),
    BinaryFile(
        listOf(
            ArtLayer(BLOB, fill = ArtTone.Blob),
            ArtLayer(
                "M54 20 L94 20 L114 40 L114 100 Q114 104 110 104 L54 104 Q50 104 50 100 L50 24 Q50 20 54 20 Z",
                fill = ArtTone.Surface, stroke = ArtTone.Neutral
            ),
            ArtLayer("M94 20 L94 40 L114 40", stroke = ArtTone.Neutral),
            ArtLayer("M62 52 L62 70 M72 52 L82 52 L82 70 L72 70 Z M92 52 L92 70 M102 52 L102 70", stroke = ArtTone.Accent),
            ArtLayer("M62 84 L102 84 M62 93 L88 93", stroke = ArtTone.NeutralSoft, width = 4f)
        )
    ),
    NoAccounts(
        listOf(
            ArtLayer(BLOB, fill = ArtTone.Blob),
            ArtLayer("M36 92 A44 44 0 0 1 124 92", stroke = ArtTone.NeutralSoft, width = 10f),
            ArtLayer("M36 92 A44 44 0 0 1 66 50", stroke = ArtTone.Accent, width = 10f),
            ArtLayer("M80 92 L100 64", stroke = ArtTone.Neutral, width = 4f),
            ArtLayer(circle(80f, 92f, 6f), fill = ArtTone.Neutral),
            ArtLayer(circle(130f, 30f, 3f), fill = ArtTone.Accent)
        )
    ),
    NoDevices(
        listOf(
            ArtLayer(BLOB, fill = ArtTone.Blob),
            ArtLayer(
                "M44 36 L112 36 Q116 36 116 40 L116 80 L40 80 L40 40 Q40 36 44 36 Z",
                fill = ArtTone.NeutralSoft, stroke = ArtTone.Neutral
            ),
            ArtLayer("M28 80 L128 80 L124 90 L32 90 Z", fill = ArtTone.Surface, stroke = ArtTone.Neutral),
            ArtLayer(
                "M104 56 L124 56 Q128 56 128 60 L128 100 Q128 104 124 104 L104 104 Q100 104 100 100 L100 60 Q100 56 104 56 Z",
                fill = ArtTone.Surface, stroke = ArtTone.Accent
            ),
            ArtLayer("M110 97 L118 97", stroke = ArtTone.Accent, width = 2.5f)
        )
    ),
    Error(
        listOf(
            ArtLayer(BLOB, fill = ArtTone.Blob),
            ArtLayer(
                "M52 88 Q34 88 34 72 Q34 57 50 56 Q54 36 76 36 Q94 36 100 50 Q104 48 108 48 Q126 48 126 68 Q126 88 108 88 Z",
                fill = ArtTone.Surface, stroke = ArtTone.Neutral
            ),
            ArtLayer("M82 56 L72 74 L86 74 L76 96", stroke = ArtTone.Accent, width = 4f)
        )
    )
}

@Composable
private fun rememberArtVector(art: F4Art, accent: Color): ImageVector {
    val c = AiTheme.colors
    return remember(art, accent, c) {
        fun tone(t: ArtTone): Color = when (t) {
            ArtTone.Blob -> c.surface2
            ArtTone.Neutral -> c.fg3
            ArtTone.NeutralSoft -> c.fg3.copy(alpha = 0.22f)
            ArtTone.Accent -> accent
            ArtTone.AccentSoft -> accent.copy(alpha = 0.35f)
            ArtTone.Surface -> c.surface
        }
        ImageVector.Builder(
            name = "f4-${art.name}",
            defaultWidth = 160.dp,
            defaultHeight = 120.dp,
            viewportWidth = 160f,
            viewportHeight = 120f
        ).apply {
            art.layers.forEach { l ->
                addPath(
                    pathData = addPathNodes(l.d),
                    fill = l.fill?.let { SolidColor(tone(it)) },
                    stroke = l.stroke?.let { SolidColor(tone(it)) },
                    strokeLineWidth = if (l.stroke != null) l.width else 0f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()
    }
}

/** Desenha uma [F4Art] com flutuação suave (parada com movimento reduzido). Decorativa. */
@Composable
fun F4Illustration(
    art: F4Art,
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    accent: Color = AiTheme.colors.accent
) {
    val vector = rememberArtVector(art, accent)
    val reduced = AiTheme.reducedMotion
    val floatY = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "f4-art").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(Durations.Breath * 2, easing = LinearEasing), RepeatMode.Reverse),
            label = "f4-art-float"
        )
    }
    Image(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier
            .size(width, width * 0.75f)
            .graphicsLayer { translationY = (floatY?.value ?: 0f) * 3.dp.toPx() }
    )
}

// ---------------------------------------------------------------------------------------------
// Estados de erro / vazio
// ---------------------------------------------------------------------------------------------

/** Estado de erro de tela inteira com ilustração e "Tentar de novo". */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.files_kit_error_title),
    art: F4Art = F4Art.Error,
    accent: Color = AiTheme.colors.danger
) {
    CenteredScroll(modifier) {
        EmptyState(
            title = title,
            body = message,
            art = { F4Illustration(art, accent = accent) },
            primaryAction = onRetry?.let {
                {
                    AiButton(
                        text = stringResource(R.string.files_kit_retry),
                        onClick = it,
                        variant = ButtonVariant.Secondary,
                        leadingIcon = Lucide.RefreshCw
                    )
                }
            }
        )
    }
}

/** Estado vazio com uma [F4Art]. */
@Composable
fun F4EmptyState(
    title: String,
    art: F4Art,
    modifier: Modifier = Modifier,
    body: String? = null,
    accent: Color = AiTheme.colors.accent,
    action: (@Composable () -> Unit)? = null
) {
    CenteredScroll(modifier) {
        EmptyState(
            title = title,
            body = body,
            art = { F4Illustration(art, accent = accent) },
            primaryAction = action
        )
    }
}

/** Centraliza verticalmente, mas rola se não couber (paisagem em telas baixas). */
@Composable
fun CenteredScroll(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val minH = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = minH), contentAlignment = Alignment.Center) { content() }
        }
    }
}

/** Largura a partir da qual as telas usam duas colunas / grade. */
val WideBreakpoint: Dp = 600.dp
