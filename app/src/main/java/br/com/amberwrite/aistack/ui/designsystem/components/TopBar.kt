package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.brand.ProviderLogo
import br.com.amberwrite.aistack.ui.icons.Lucide

/**
 * Barra superior do app (56 dp + inset da status bar). Navegação à esquerda, título com
 * subtítulo opcional (ex.: projeto/branch), logo do provedor e ponto de status, ações à direita.
 * Com [scrolled] ganha fundo `sidebar` e linha inferior (transição suave).
 *
 * @param navigationIcon `null` esconde o botão; padrão é o menu (abre a gaveta de sessões).
 * @param windowInsets passe `WindowInsets(0)` quando o pai já trata a status bar.
 */
@Composable
fun AiStackTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector? = Lucide.Menu,
    navigationContentDescription: String = "Abrir menu",
    onNavigationClick: (() -> Unit)? = null,
    providerId: String? = null,
    status: AgentStatus? = null,
    scrolled: Boolean = false,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    haptics: AiHaptics = AiHaptics.None,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = AiTheme.colors
    val bg by animateColorAsState(if (scrolled) c.sidebar else c.bg, AiTheme.motion.fade(), label = "topBarBg")
    val line by animateColorAsState(if (scrolled) c.line else Color.Transparent, AiTheme.motion.fade(), label = "topBarLine")
    Column(modifier.fillMaxWidth().background(bg).windowInsetsPadding(windowInsets)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null && onNavigationClick != null) {
                AiIconButton(navigationIcon, navigationContentDescription, onNavigationClick, haptics = haptics)
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                if (titleContent != null) {
                    titleContent()
                } else {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (providerId != null) ProviderLogo(providerId, size = 16.dp)
                            Text(
                                title,
                                style = AiTheme.typography.title,
                                color = c.fg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                            )
                            if (status != null) StatusDot(status)
                        }
                        if (subtitle != null) {
                            Text(subtitle, style = AiTheme.typography.caption, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
    }
}

@AiPreviews
@Composable
private fun TopBarPreview() {
    PreviewSurface {
        AiStackTopBar(
            title = "Refatorar o relay",
            subtitle = "aistack-android · main",
            onNavigationClick = {},
            providerId = "claude",
            status = AgentStatus.Busy,
            windowInsets = WindowInsets(0),
        ) {
            AiIconButton(Lucide.Ellipsis, "Mais opções", onClick = {})
        }
        AiStackTopBar(title = "Sessões", onNavigationClick = {}, scrolled = true, windowInsets = WindowInsets(0)) {
            AiIconButton(Lucide.Search, "Buscar", onClick = {})
            AiIconButton(Lucide.Plus, "Nova sessão", onClick = {})
        }
    }
}
