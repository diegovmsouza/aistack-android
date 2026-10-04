package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.brand.ProviderLogo

/** Cor semântica de uso de cota: ok < 60% ≤ warn < 85% ≤ danger. */
@Composable
fun quotaColor(fraction: Float): Color {
    val c = AiTheme.colors
    return when {
        fraction >= 0.85f -> c.danger
        fraction >= 0.6f -> c.warn
        else -> c.ok
    }
}

/**
 * Barra de cota/uso (janela de 5 h, semanal, contexto…). A barra cresce com mola ao aparecer e
 * a cada mudança de [fraction] (instantânea com movimento reduzido); a cor segue [quotaColor]
 * a menos que [color] seja passado.
 *
 * @param fraction 0..1 de uso.
 * @param valueText texto à direita (ex.: “72%”); `null` = porcentagem automática.
 * @param resetText linha de rodapé (ex.: “Renova em 2 h 14 min”).
 * @param providerId mostra o logo do provedor antes do rótulo.
 */
@Composable
fun QuotaBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    valueText: String? = null,
    resetText: String? = null,
    providerId: String? = null,
    color: Color? = null,
    height: Dp = 6.dp,
) {
    val c = AiTheme.colors
    val target = fraction.coerceIn(0f, 1f)
    val reduced = AiTheme.reducedMotion
    val anim = remember { Animatable(if (reduced) target else 0f) }
    val spec = AiTheme.motion.spring<Float>()
    LaunchedEffect(target) { anim.animateTo(target, spec) }
    val tone by animateColorAsState(color ?: quotaColor(target), AiTheme.motion.fade(), label = "quotaTone")
    Column(
        modifier
            .fillMaxWidth()
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null || providerId != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (providerId != null) {
                    ProviderLogo(providerId, size = 14.dp)
                    Spacer(Modifier.width(6.dp))
                }
                if (label != null) Text(label, style = AiTheme.typography.label, color = c.fg2, modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                Text(
                    valueText ?: "${(target * 100).toInt()}%",
                    style = AiTheme.typography.monoSmall,
                    color = if (target >= 0.85f) tone else c.fg2,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(AiTheme.shapes.pill)
                .background(c.surface3),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(anim.value.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(AiTheme.shapes.pill)
                    .background(tone),
            )
        }
        if (resetText != null) Text(resetText, style = AiTheme.typography.caption, color = c.fg3)
    }
}

/** Versão compacta para listas/top bar: só a barrinha (sem textos), 3 dp de altura. */
@Composable
fun QuotaMeter(fraction: Float, modifier: Modifier = Modifier, width: Dp = 40.dp, color: Color? = null) {
    QuotaBar(fraction, modifier.size(width = width, height = 3.dp), color = color, height = 3.dp)
}

@AiPreviews
@Composable
private fun QuotaBarPreview() {
    PreviewSurface {
        QuotaBar(0.32f, label = "Sessão de 5 h", providerId = "claude", resetText = "Renova em 2 h 14 min")
        QuotaBar(0.71f, label = "Semanal", providerId = "codex")
        QuotaBar(0.93f, label = "Contexto", valueText = "186k / 200k")
        QuotaMeter(0.5f)
    }
}
