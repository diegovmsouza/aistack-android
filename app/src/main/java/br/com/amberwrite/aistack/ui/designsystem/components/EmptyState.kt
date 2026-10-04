package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.illustrations.Illustration
import br.com.amberwrite.aistack.ui.designsystem.illustrations.IllustrationImage
import br.com.amberwrite.aistack.ui.icons.Lucide

/**
 * Estado vazio centralizado: ilustração SVG, título, texto e ações opcionais.
 * Entra com fade + subida de 8 dp (instantâneo com movimento reduzido).
 *
 * @param illustration `null` = sem arte (ou use [art] para algo próprio, ex.: BrandMark).
 * @param accent cor da camada de destaque da ilustração (ex.: `warn` em “sem conexão”).
 * @param primaryAction/secondaryAction normalmente [AiButton]s; ficam empilhados.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    illustration: Illustration? = null,
    body: String? = null,
    accent: Color = AiTheme.colors.accent,
    illustrationWidth: Dp = 160.dp,
    art: (@Composable () -> Unit)? = null,
    primaryAction: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    val reduced = AiTheme.reducedMotion
    val motion = AiTheme.motion
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, motion.enter()) }
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * 8.dp.toPx()
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            art != null -> art()
            illustration != null -> IllustrationImage(illustration, width = illustrationWidth, accent = accent)
        }
        if (art != null || illustration != null) Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = AiTheme.typography.heading,
            color = AiTheme.colors.fg,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = AiTheme.typography.bodySmall,
                color = AiTheme.colors.fg2,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (primaryAction != null || secondaryAction != null) {
            Spacer(Modifier.height(20.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                primaryAction?.invoke()
                secondaryAction?.invoke()
            }
        }
    }
}

@AiPreviews
@Composable
private fun EmptyStatePreview() {
    PreviewSurface {
        EmptyState(
            title = "Nenhuma sessão ainda",
            body = "Comece uma conversa aqui ou no desktop — ela aparece nos dois lugares.",
            illustration = Illustration.NoSessions,
            primaryAction = { AiButton("Nova sessão", onClick = {}, leadingIcon = Lucide.Plus) },
        )
        EmptyState(
            title = "Sem conexão com o desktop",
            body = "Verifique se o aistack está aberto no computador.",
            illustration = Illustration.Offline,
            accent = AiTheme.colors.warn,
            illustrationWidth = 128.dp,
            primaryAction = { AiButton("Tentar de novo", onClick = {}, variant = ButtonVariant.Secondary, leadingIcon = Lucide.RefreshCw) },
        )
    }
}
