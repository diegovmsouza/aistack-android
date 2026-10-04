package br.com.amberwrite.aistack.feature.common

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.amberwrite.aistack.AiStackApplication
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiStackTopBar
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.icons.Lucide
import br.com.amberwrite.aistack.ui.designsystem.components.ConnectionState as BannerState

/**
 * ViewModel ligado ao [AppContainer] da aplicação. [key] separa instâncias da mesma classe
 * (ex.: um chat por conversa) dentro do mesmo destino de navegação.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM
): VM {
    val container = AiStackApplication.container(LocalContext.current)
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } }
    )
}

/** Converte o estado do núcleo no estado (mais simples) do banner do design system. */
fun ConnectionState.toBanner(): BannerState = when (this) {
    is ConnectionState.Online -> BannerState.Connected
    is ConnectionState.Connecting -> if (attempt <= 1) BannerState.Connecting else BannerState.Reconnecting
    ConnectionState.Handshaking -> BannerState.Connecting
    is ConnectionState.HostOffline -> BannerState.HostOffline
    is ConnectionState.AuthRejected, ConnectionState.Revoked -> BannerState.AuthRejected
    is ConnectionState.Error -> BannerState.Reconnecting
    ConnectionState.Disconnected -> BannerState.Offline
}

/** Detalhe opcional para o banner (mensagem de erro, contagem de nova tentativa). */
fun ConnectionState.bannerDetail(): String? = when (this) {
    is ConnectionState.AuthRejected -> message
    ConnectionState.Revoked -> "Este aparelho foi removido no desktop."
    is ConnectionState.Error -> retryInMs?.let { "$message · nova tentativa em ${(it + 999) / 1000}s" } ?: message
    is ConnectionState.HostOffline -> retryInMs?.let { "Nova tentativa em ${(it + 999) / 1000}s" }
    else -> null
}

/** Rótulo curto em português para o estado da conexão. */
fun ConnectionState.label(): String = when (this) {
    is ConnectionState.Online -> "Conectado"
    is ConnectionState.Connecting -> if (attempt <= 1) "Conectando…" else "Reconectando (tentativa $attempt)…"
    ConnectionState.Handshaking -> "Autenticando…"
    is ConnectionState.HostOffline -> "Desktop offline"
    is ConnectionState.AuthRejected -> "Pareamento recusado"
    ConnectionState.Revoked -> "Aparelho removido"
    is ConnectionState.Error -> "Erro de conexão"
    ConnectionState.Disconnected -> "Desconectado"
}

/** "há 5 min", "ontem", "em 2 horas"… a partir de epoch em milissegundos (passado ou futuro). */
fun relativeTime(epochMs: Long?, now: Long = System.currentTimeMillis()): String {
    if (epochMs == null || epochMs <= 0) return ""
    if (kotlin.math.abs(now - epochMs) < DateUtils.MINUTE_IN_MILLIS) return "agora"
    return DateUtils.getRelativeTimeSpanString(epochMs, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

/** Tamanho legível ("1,2 MB"). */
fun formatBytes(bytes: Long?): String {
    if (bytes == null) return ""
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale("pt", "BR"), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale("pt", "BR"), "%.1f MB", mb)
    return String.format(java.util.Locale("pt", "BR"), "%.1f GB", mb / 1024.0)
}

/** Esqueleto comum das telas internas: barra superior com voltar e conteúdo em coluna. */
@Composable
fun FeatureScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .background(AiTheme.colors.bg)
    ) {
        AiStackTopBar(
            title = title,
            subtitle = subtitle,
            navigationIcon = if (onBack != null) Lucide.ArrowLeft else null,
            navigationContentDescription = "Voltar",
            onNavigationClick = onBack,
            actions = actions
        )
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
            content = content
        )
    }
}

/** Indicador de carregamento centralizado. */
@Composable
fun CenteredLoading(modifier: Modifier = Modifier, text: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner(size = 24.dp)
            if (text != null) {
                Text(
                    text,
                    style = AiTheme.typography.caption,
                    color = AiTheme.colors.fg3,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/** Faixa de erro discreta (ex.: falha ao recarregar com dados antigos na tela). */
@Composable
fun ErrorStrip(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = AiTheme.typography.caption,
        color = AiTheme.colors.danger,
        modifier = modifier
            .fillMaxWidth()
            .background(AiTheme.colors.dangerSoft)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
