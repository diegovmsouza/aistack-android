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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Estado da conexão com o desktop (via relay), do ponto de vista da UI. */
enum class ConnectionState {
    /** Tudo certo — o banner some. */
    Connected,

    /** Primeira conexão em andamento. */
    Connecting,

    /** Caiu e está tentando de novo (backoff). */
    Reconnecting,

    /** Sem rede no celular. */
    Offline,

    /** Relay alcançável, mas o desktop não está online. */
    HostOffline,

    /** Desktop recusou a chave (pareamento revogado/expirado). */
    AuthRejected,
}

private data class BannerSpec(val icon: ImageVector?, val tone: Color, val title: String, val busy: Boolean)

@Composable
private fun ConnectionState.spec(): BannerSpec {
    val c = AiTheme.colors
    return when (this) {
        ConnectionState.Connected -> BannerSpec(Lucide.CircleCheck, c.ok, "Conectado", false)
        ConnectionState.Connecting -> BannerSpec(null, c.fg2, "Conectando ao desktop…", true)
        ConnectionState.Reconnecting -> BannerSpec(null, c.warn, "Reconectando…", true)
        ConnectionState.Offline -> BannerSpec(Lucide.WifiOff, c.warn, "Sem internet", false)
        ConnectionState.HostOffline -> BannerSpec(Lucide.Monitor, c.warn, "Desktop offline", false)
        ConnectionState.AuthRejected -> BannerSpec(Lucide.ShieldAlert, c.danger, "Pareamento recusado", false)
    }
}

/**
 * Faixa fina de status de conexão, para o topo da tela (abaixo da top bar). Aparece/some
 * com expansão vertical; o conteúdo troca com crossfade. Em [ConnectionState.Connected] fica
 * oculta (use [showWhenConnected] para um “Conectado” momentâneo controlado pelo chamador).
 *
 * @param detail texto secundário (ex.: “Tentando de novo em 8 s”).
 * @param onRetry mostra “Tentar agora” (Reconnecting/Offline/HostOffline).
 * @param onRepair mostra “Parear de novo” (AuthRejected).
 */
@Composable
fun ConnectionBanner(
    state: ConnectionState,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onRetry: (() -> Unit)? = null,
    onRepair: (() -> Unit)? = null,
    showWhenConnected: Boolean = false,
) {
    val motion = AiTheme.motion
    AnimatedVisibility(
        visible = state != ConnectionState.Connected || showWhenConnected,
        modifier = modifier,
        enter = expandVertically(motion.enter()) + fadeIn(motion.fade()),
        exit = shrinkVertically(motion.exit()) + fadeOut(motion.fade(140)),
    ) {
        val spec = state.spec()
        val bg by animateColorAsState(spec.tone.copy(alpha = if (AiTheme.colors.isDark) 0.14f else 0.10f), motion.fade(), label = "bannerBg")
        Column(
            Modifier
                .fillMaxWidth()
                .background(bg)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade(90)) },
                label = "bannerContent",
            ) { s ->
                val sp = s.spec()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                        if (sp.busy) Spinner(size = 14.dp, color = sp.tone)
                        else if (sp.icon != null) Icon(sp.icon, null, Modifier.size(16.dp), tint = sp.tone)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(sp.title, style = AiTheme.typography.label, color = AiTheme.colors.fg, maxLines = 1)
                        if (detail != null) {
                            Text(detail, style = AiTheme.typography.caption, color = AiTheme.colors.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    when {
                        s == ConnectionState.AuthRejected && onRepair != null ->
                            AiButton("Parear de novo", onClick = onRepair, size = ButtonSize.Small, variant = ButtonVariant.Secondary, leadingIcon = Lucide.QrCode)
                        s in setOf(ConnectionState.Reconnecting, ConnectionState.Offline, ConnectionState.HostOffline) && onRetry != null ->
                            AiButton("Tentar agora", onClick = onRetry, size = ButtonSize.Small, variant = ButtonVariant.Ghost, leadingIcon = Lucide.RefreshCw)
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(spec.tone.copy(alpha = 0.25f)),
            )
        }
    }
}

@AiPreviews
@Composable
private fun ConnectionBannerPreview() {
    PreviewSurface {
        ConnectionBanner(ConnectionState.Connecting)
        ConnectionBanner(ConnectionState.Reconnecting, detail = "Tentando de novo em 8 s", onRetry = {})
        ConnectionBanner(ConnectionState.Offline, onRetry = {})
        ConnectionBanner(ConnectionState.HostOffline, detail = "Abra o aistack no computador", onRetry = {})
        ConnectionBanner(ConnectionState.AuthRejected, detail = "O desktop não reconhece este celular", onRepair = {})
    }
}
