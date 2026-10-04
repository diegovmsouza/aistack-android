package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.brand.ProviderLogo
import br.com.amberwrite.aistack.ui.designsystem.tokens.Providers
import br.com.amberwrite.aistack.ui.icons.Lucide

/**
 * Selo do provedor: logo + nome (e opcionalmente o modelo), com fundo tingido pela cor do
 * provedor a 12%.
 *
 * @param model texto secundário (ex.: "opus-4.5"); `null` omite.
 * @param compact só o logo dentro do selo.
 */
@Composable
fun ProviderBadge(
    providerId: String?,
    modifier: Modifier = Modifier,
    model: String? = null,
    compact: Boolean = false,
) {
    val spec = Providers.byId(providerId)
    val tone = AiTheme.colors.providers.byId(spec.id)
    val shape = AiTheme.shapes.pill
    Row(
        modifier
            .clip(shape)
            .background(tone.copy(alpha = 0.12f))
            .border(1.dp, tone.copy(alpha = 0.22f), shape)
            .padding(horizontal = if (compact) 5.dp else 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ProviderLogo(spec.id, size = 13.dp, contentDescription = if (compact) spec.displayName else null)
        if (!compact) {
            Text(spec.displayName, style = AiTheme.typography.caption, color = AiTheme.colors.fg)
            if (model != null) {
                Text(model, style = AiTheme.typography.monoSmall, color = AiTheme.colors.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** De onde partiu uma sessão/mensagem. */
enum class Origin(val label: String) {
    Desktop("Desktop"),
    Mobile("Celular"),
    Cli("Terminal"),
    Scheduled("Agendada"),
}

private fun Origin.icon(): ImageVector = when (this) {
    Origin.Desktop -> Lucide.Monitor
    Origin.Mobile -> Lucide.Smartphone
    Origin.Cli -> Lucide.SquareTerminal
    Origin.Scheduled -> Lucide.Clock
}

/** Selo discreto de origem (ícone + rótulo), em `fg2` sobre `surface2`. */
@Composable
fun OriginBadge(
    origin: Origin,
    modifier: Modifier = Modifier,
    label: String = origin.label,
    showLabel: Boolean = true,
) {
    TagBadge(text = if (showLabel) label else null, icon = origin.icon(), modifier = modifier)
}

/** Selo genérico pequeno (ícone opcional + texto), usado por [OriginBadge] e contagens. */
@Composable
fun TagBadge(
    text: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = AiTheme.colors.fg2,
    background: Color = AiTheme.colors.surface2,
) {
    val shape = AiTheme.shapes.xs
    Row(
        modifier
            .clip(shape)
            .background(background)
            .border(1.dp, AiTheme.colors.line, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = text, Modifier.size(12.dp), tint = color)
        if (text != null) Text(text, style = AiTheme.typography.caption, color = color, maxLines = 1)
    }
}

@AiPreviews
@Composable
private fun BadgesPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProviderBadge("claude", model = "opus")
            ProviderBadge("codex")
            ProviderBadge("gemini", compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Origin.entries.forEach { OriginBadge(it) }
        }
    }
}
