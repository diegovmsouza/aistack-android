package br.com.amberwrite.aistack.feature.accounts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.McpEntry
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.relativeTime
import br.com.amberwrite.aistack.feature.files.InfoBanner
import br.com.amberwrite.aistack.feature.files.kit.ErrorState
import br.com.amberwrite.aistack.feature.files.kit.F4Art
import br.com.amberwrite.aistack.feature.files.kit.F4EmptyState
import br.com.amberwrite.aistack.feature.files.kit.SkeletonCard
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ProviderBadge
import br.com.amberwrite.aistack.ui.designsystem.components.QuotaBar
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.designsystem.components.TagBadge
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide

private enum class AccountsPhase { Skeleton, Error, Empty, List }

/** Contas e cotas (§4.8): slots por provedor, estado do login e barras de cota animadas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val vm = containerViewModel(key = "accounts") { AccountsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAiHaptics()

    FeatureScaffold(
        title = stringResource(R.string.accounts_title),
        subtitle = state.desktopVersion?.let { stringResource(R.string.accounts_subtitle_version, it) },
        onBack = onBack,
        actions = {
            AiIconButton(
                icon = Lucide.RefreshCw,
                contentDescription = stringResource(R.string.accounts_reload),
                onClick = vm::reload,
                size = 48.dp,
                enabled = !state.refreshing,
                haptic = HapticKind.Tick,
                haptics = haptics
            )
        }
    ) {
        val phase = when {
            state.showSkeleton -> AccountsPhase.Skeleton
            state.showFullError -> AccountsPhase.Error
            state.showEmpty -> AccountsPhase.Empty
            else -> AccountsPhase.List
        }
        val motion = AiTheme.motion
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "accounts-phase"
        ) { p ->
            when (p) {
                AccountsPhase.Skeleton -> AccountsSkeleton()
                AccountsPhase.Error -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = vm::reload,
                    title = stringResource(R.string.accounts_error_title)
                )
                AccountsPhase.Empty -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = vm::reload,
                    modifier = Modifier.fillMaxSize()
                ) {
                    F4EmptyState(
                        title = stringResource(R.string.accounts_empty_title),
                        body = stringResource(R.string.accounts_empty_body),
                        art = F4Art.NoAccounts
                    )
                }
                AccountsPhase.List -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = vm::reload,
                    modifier = Modifier.fillMaxSize()
                ) {
                    AccountsGrid(state)
                }
            }
        }
    }
}

@Composable
private fun AccountsSkeleton() {
    val desc = stringResource(R.string.accounts_loading)
    Column(
        Modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = desc },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) { SkeletonCard(bars = 2) }
    }
}

@Composable
private fun AccountsGrid(state: AccountsViewModel.UiState) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.error?.let { err ->
            item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                InfoBanner(err, tone = AiTheme.colors.danger, modifier = Modifier.animateItem())
            }
        }
        state.groups.forEach { group ->
            item(key = "group:${group.providerId}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                GroupHeader(group, Modifier.animateItem())
            }
            items(group.accounts, key = { it.key }, contentType = { "account" }) { acc ->
                AccountCard(acc, now = state.now, modifier = Modifier.animateItem())
            }
        }
        if (state.mcp.isNotEmpty()) {
            item(key = "mcp-title", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Text(
                    stringResource(R.string.accounts_mcp_title),
                    style = AiTheme.typography.overline,
                    color = AiTheme.colors.fg3,
                    modifier = Modifier.animateItem().padding(top = 12.dp).semantics { heading() }
                )
            }
            items(state.mcp, key = { "mcp:" + it.name }, contentType = { "mcp" }) { m ->
                McpRow(m, Modifier.animateItem())
            }
        }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.animateItem().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Lucide.Info, contentDescription = null, tint = AiTheme.colors.fg3, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.accounts_footer), style = AiTheme.typography.caption, color = AiTheme.colors.fg3)
            }
        }
    }
}

@Composable
private fun GroupHeader(group: ProviderGroup, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProviderBadge(providerId = group.providerId)
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.accounts_group_count, group.signedIn, group.accounts.size),
            style = AiTheme.typography.caption,
            color = c.fg3
        )
    }
}

private data class Tone(val fg: Color, val bg: Color)

@Composable
private fun authTone(auth: AuthStatus): Tone {
    val c = AiTheme.colors
    return when (auth) {
        AuthStatus.Authenticated -> Tone(c.ok, c.ok.copy(alpha = 0.12f))
        AuthStatus.LoggedOut -> Tone(c.warn, c.warnSoft)
        AuthStatus.Error -> Tone(c.danger, c.dangerSoft)
        AuthStatus.NotInstalled, AuthStatus.Unknown -> Tone(c.fg3, c.surface2)
    }
}

@Composable
private fun authLabel(auth: AuthStatus): String = stringResource(
    when (auth) {
        AuthStatus.Authenticated -> R.string.accounts_auth_ok
        AuthStatus.LoggedOut -> R.string.accounts_auth_logged_out
        AuthStatus.Error -> R.string.accounts_auth_error
        AuthStatus.NotInstalled -> R.string.accounts_auth_not_installed
        AuthStatus.Unknown -> R.string.accounts_auth_unknown
    }
)

@Composable
private fun windowLabel(w: WindowView): String = when (w.kind) {
    WindowKind.FiveHour -> stringResource(R.string.accounts_window_five_hour)
    WindowKind.Weekly -> stringResource(R.string.accounts_window_weekly)
    WindowKind.Monthly -> stringResource(R.string.accounts_window_monthly)
    WindowKind.Other -> w.label
}

@Composable
private fun AccountCard(a: AccountView, now: Long, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    val tone = authTone(a.auth)
    val slotDesc = stringResource(R.string.accounts_slot_desc, a.slotLabel)
    SurfaceCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagBadge(
                text = stringResource(R.string.accounts_slot, a.slotLabel),
                modifier = Modifier.semantics { contentDescription = slotDesc },
                color = c.fg2,
                background = c.surface2
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(AiTheme.shapes.pill).background(tone.bg).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.size(6.dp).clip(CircleShape).background(tone.fg))
                Spacer(Modifier.width(6.dp))
                Text(authLabel(a.auth), style = AiTheme.typography.caption, color = tone.fg)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            a.title,
            style = AiTheme.typography.heading,
            color = c.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val meta = listOfNotNull(a.email, a.plan?.replaceFirstChar { it.uppercase() }).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, style = AiTheme.typography.caption, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (a.limit) {
            LimitStatus.Warning -> LimitNote(stringResource(R.string.accounts_limit_warning), c.warn)
            LimitStatus.Rejected -> LimitNote(stringResource(R.string.accounts_limit_rejected), c.danger)
            else -> Unit
        }
        Spacer(Modifier.height(12.dp))
        if (a.windows.isEmpty()) {
            Text(stringResource(R.string.accounts_no_usage), style = AiTheme.typography.bodySmall, color = c.fg3)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                a.windows.forEach { w ->
                    val reset = AccountsLogic.resetBreakdown(w.resetsAt, now)
                    val resetText = when {
                        reset != null -> stringResource(R.string.accounts_reset_in, AccountsLogic.formatReset(reset))
                        w.resetsAt != null -> stringResource(R.string.accounts_reset_due)
                        else -> null
                    }
                    QuotaBar(
                        fraction = w.fraction ?: 0f,
                        label = windowLabel(w),
                        valueText = w.usedPct?.let { "$it%" } ?: stringResource(R.string.accounts_window_unknown),
                        resetText = resetText,
                        providerId = a.providerId,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        a.usageAt?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.accounts_updated, relativeTime(it, now)),
                style = AiTheme.typography.caption,
                color = c.fg3
            )
        }
    }
}

@Composable
private fun LimitNote(text: String, color: Color) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.TriangleAlert, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = AiTheme.typography.caption, color = color)
    }
}

@Composable
private fun McpRow(m: McpEntry, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    val desc = stringResource(R.string.accounts_mcp_desc, m.name)
    SurfaceCard(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Layers, contentDescription = null, tint = if (m.enabled) c.accent else c.fg3, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = AiTheme.typography.body, color = if (m.enabled) c.fg else c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(m.transport, if (m.enabled) null else stringResource(R.string.accounts_mcp_disabled)).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = AiTheme.typography.monoSmall, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (m.statuses.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                m.statuses.take(4).forEach { s ->
                    val ok = s.status.equals("connected", true) || s.status.equals("ok", true)
                    TagBadge(text = "${s.name}: ${s.status}", color = if (ok) c.ok else c.fg3)
                }
            }
        }
    }
}
