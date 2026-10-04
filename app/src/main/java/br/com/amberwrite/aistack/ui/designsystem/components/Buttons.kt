package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Variantes visuais de [AiButton]. */
enum class ButtonVariant { Primary, Secondary, Ghost, Danger }

/** Tamanhos de [AiButton]. */
enum class ButtonSize(val height: Dp, val horizontal: Dp, val icon: Dp) {
    Small(32.dp, 12.dp, 14.dp),
    Medium(40.dp, 16.dp, 16.dp),
    Large(48.dp, 20.dp, 18.dp),
}

private data class ButtonColors(val container: Color, val content: Color, val border: Color)

@Composable
private fun ButtonVariant.colors(): ButtonColors {
    val c = AiTheme.colors
    return when (this) {
        ButtonVariant.Primary -> ButtonColors(c.accent, c.accentFg, Color.Transparent)
        ButtonVariant.Secondary -> ButtonColors(c.surface2, c.fg, c.line)
        ButtonVariant.Ghost -> ButtonColors(Color.Transparent, c.fg2, Color.Transparent)
        ButtonVariant.Danger -> ButtonColors(c.dangerSoft, c.danger, c.danger.copy(alpha = 0.35f))
    }
}

/** Escala de “pressionado” compartilhada pelos componentes clicáveis (0,97, mola). */
@Composable
internal fun pressScale(source: MutableInteractionSource, pressed: Float = 0.97f): Float {
    val isPressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (isPressed) pressed else 1f, AiTheme.motion.spring(), label = "press")
    return s
}

/**
 * Botão do design system.
 *
 * @param haptic tipo de retorno tátil disparado no clique (`null` = nenhum).
 * @param haptics implementação de vibração; use [AiHaptics.None] para silenciar.
 */
@Composable
fun AiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = AiTheme.shapes.md,
    haptic: HapticKind? = HapticKind.Tick,
    haptics: AiHaptics = AiHaptics.None,
) {
    val colors = variant.colors()
    val container by animateColorAsState(colors.container, AiTheme.motion.fade(), label = "btnBg")
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    Row(
        modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = size.height)
            .clip(shape)
            .background(container)
            .border(BorderStroke(1.dp, colors.border), shape)
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(color = colors.content),
                enabled = enabled && !loading,
                role = Role.Button,
            ) {
                haptic?.let(haptics::perform)
                onClick()
            }
            .padding(horizontal = size.horizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            loading -> Spinner(size = size.icon, color = colors.content)
            leadingIcon != null -> Icon(leadingIcon, null, Modifier.size(size.icon), tint = colors.content)
        }
        Text(text, style = AiTheme.typography.label, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailingIcon != null) Icon(trailingIcon, null, Modifier.size(size.icon), tint = colors.content)
    }
}

/** Botão só com ícone (alvo de toque de 40 dp). */
@Composable
fun AiIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Ghost,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
    tint: Color? = null,
    haptic: HapticKind? = null,
    haptics: AiHaptics = AiHaptics.None,
) {
    val colors = variant.colors()
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source, 0.92f)
    Box(
        modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .size(size)
            .clip(AiTheme.shapes.pill)
            .background(colors.container)
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(bounded = true, color = colors.content),
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic?.let(haptics::perform)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint ?: colors.content)
    }
}

/**
 * Chip selecionável/filtro. Selecionado usa o destaque suave; não selecionado, contorno.
 * @param onClick `null` = chip estático (rótulo).
 */
@Composable
fun AiChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    color: Color? = null,
    haptics: AiHaptics = AiHaptics.None,
) {
    val c = AiTheme.colors
    val tone = color ?: c.accent
    val bg by animateColorAsState(if (selected) tone.copy(alpha = 0.14f) else c.surface, AiTheme.motion.fade(), label = "chipBg")
    val border by animateColorAsState(if (selected) tone.copy(alpha = 0.45f) else c.line, AiTheme.motion.fade(), label = "chipBorder")
    val fg = if (selected) tone else c.fg2
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    val shape = AiTheme.shapes.pill
    Row(
        modifier
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Checkbox) {
                        haptics.perform(HapticKind.Tick)
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(PaddingValues(start = if (leadingIcon != null || leading != null) 8.dp else 12.dp, end = if (onRemove != null) 6.dp else 12.dp))
            .defaultMinSize(minHeight = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(14.dp), tint = fg)
        Text(text, style = AiTheme.typography.label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onRemove != null) {
            Icon(
                Lucide.X,
                contentDescription = "Remover",
                modifier = Modifier
                    .size(18.dp)
                    .clip(AiTheme.shapes.pill)
                    .clickable(role = Role.Button) { onRemove() }
                    .padding(2.dp),
                tint = c.fg3,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@AiPreviews
@Composable
private fun ButtonsPreview() {
    PreviewSurface {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ButtonVariant.entries.forEach { AiButton(it.name, onClick = {}, variant = it) }
            AiButton("Enviar", onClick = {}, leadingIcon = Lucide.Send, size = ButtonSize.Small)
            AiButton("Carregando", onClick = {}, loading = true, variant = ButtonVariant.Secondary)
            AiIconButton(Lucide.Settings, "Configurações", onClick = {})
        }
        var selected by remember { mutableStateOf(true) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiChip("Ativas", selected = selected, onClick = { selected = !selected })
            AiChip("Arquivadas", leadingIcon = Lucide.Archive, onClick = {})
            AiChip("main", leadingIcon = Lucide.GitBranch, onRemove = {})
        }
    }
}
