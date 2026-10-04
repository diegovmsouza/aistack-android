package br.com.amberwrite.aistack.ui.designsystem.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiPreviews
import br.com.amberwrite.aistack.ui.designsystem.components.PreviewSurface
import br.com.amberwrite.aistack.ui.designsystem.tokens.Providers

/**
 * Logo vetorial de um provedor. Logos monocromáticos (Claude, Codex, GLM, Qwen) são tingidos
 * com [tint] — por padrão a cor do provedor no tema atual; logos coloridos (Gemini, Kimi,
 * DeepSeek) são desenhados com as cores originais.
 *
 * @param providerId id do provedor (aceita aliases: "gemini" → agy, "openai" → codex...).
 */
@Composable
fun ProviderLogo(
    providerId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color? = null,
    contentDescription: String? = null,
) {
    val spec = Providers.byId(providerId)
    val painter = painterResource(spec.logo)
    if (spec.tintable) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = tint ?: AiTheme.colors.providers.byId(spec.id),
        )
    } else {
        Image(painter = painter, contentDescription = contentDescription, modifier = modifier.size(size))
    }
}

@AiPreviews
@Composable
private fun ProviderLogoPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Providers.all.forEach { ProviderLogo(it.id, size = 28.dp) }
        }
    }
}
