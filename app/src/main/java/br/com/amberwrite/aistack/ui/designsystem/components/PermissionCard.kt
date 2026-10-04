package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Estado de um pedido de permissão. */
enum class PermissionState { Pending, Allowed, Denied, Cancelled }

/**
 * Cartão de pedido de permissão (somente visual; a decisão vai pelos callbacks).
 * Pendente: borda/fundo `warn`, título “Permitir <ferramenta>?”, bloco mono com o detalhe
 * (comando, caminho…) e botões Permitir / Sempre permitir neste projeto / Negar.
 * Resolvido: vira um resumo compacto (Permitido / Negado / Cancelado).
 *
 * @param onAlwaysAllow `null` esconde “Sempre permitir neste projeto” (sem sugestões do CLI).
 * @param haptics vibração: Confirm ao permitir, Reject ao negar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionCard(
    toolName: String,
    state: PermissionState,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    description: String? = null,
    onAlwaysAllow: (() -> Unit)? = null,
    haptics: AiHaptics = AiHaptics.None,
) {
    val c = AiTheme.colors
    val motion = AiTheme.motion
    val pending = state == PermissionState.Pending
    val (tone, icon, title) = when (state) {
        PermissionState.Pending -> Triple(c.warn, Lucide.ShieldAlert, "Permitir $toolName?")
        PermissionState.Allowed -> Triple(c.ok, Lucide.ShieldCheck, "Permitido")
        PermissionState.Denied -> Triple(c.danger, Lucide.CircleX, "Negado")
        PermissionState.Cancelled -> Triple(c.fg3, Lucide.CircleX, "Cancelado")
    }
    val border by animateColorAsState(tone.copy(alpha = if (pending) 0.45f else 0.25f), motion.fade(), label = "permBorder")
    val bg by animateColorAsState(if (pending) c.warnSoft else c.surface, motion.fade(), label = "permBg")
    val shape = AiTheme.shapes.lg
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .background(bg)
            .border(1.dp, border, shape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade(120)) },
                label = "permIcon",
            ) { ic: ImageVector -> Icon(ic, null, Modifier.size(16.dp), tint = tone) }
            Column(Modifier.weight(1f)) {
                Text(title, style = AiTheme.typography.heading, color = c.fg)
                if (!pending) {
                    Text(toolName, style = AiTheme.typography.caption, color = c.fg3)
                } else if (description != null) {
                    Text(description, style = AiTheme.typography.bodySmall, color = c.fg2)
                }
            }
        }
        if (detail != null) {
            CodeBlock(detail, maxHeight = if (pending) 160.dp else 64.dp, tone = c.fg, background = c.surface2)
        }
        AnimatedVisibility(
            visible = pending,
            enter = expandVertically(motion.spring()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.spring()) + fadeOut(motion.fade(120)),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiButton(
                    "Permitir",
                    onClick = onAllow,
                    leadingIcon = Lucide.Check,
                    size = ButtonSize.Small,
                    haptic = HapticKind.Confirm,
                    haptics = haptics,
                )
                if (onAlwaysAllow != null) {
                    AiButton(
                        "Sempre permitir neste projeto",
                        onClick = onAlwaysAllow,
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Small,
                        haptic = HapticKind.Confirm,
                        haptics = haptics,
                    )
                }
                AiButton(
                    "Negar",
                    onClick = onDeny,
                    leadingIcon = Lucide.X,
                    variant = ButtonVariant.Danger,
                    size = ButtonSize.Small,
                    haptic = HapticKind.Reject,
                    haptics = haptics,
                )
            }
        }
    }
}

@AiPreviews
@Composable
private fun PermissionCardPreview() {
    PreviewSurface {
        var state by remember { mutableStateOf(PermissionState.Pending) }
        PermissionCard(
            toolName = "Bash",
            state = state,
            detail = "rm -rf build/ && ./gradlew assembleDebug",
            description = "O agente quer executar um comando no terminal.",
            onAllow = { state = PermissionState.Allowed },
            onAlwaysAllow = { state = PermissionState.Allowed },
            onDeny = { state = PermissionState.Denied },
        )
        PermissionCard("Edit", PermissionState.Denied, onAllow = {}, onDeny = {}, detail = "app/build.gradle.kts")
    }
}

