package br.com.amberwrite.aistack.feature.devices

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.SectionTitle
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.relativeTime
import br.com.amberwrite.aistack.feature.files.InfoBanner
import br.com.amberwrite.aistack.feature.files.kit.ErrorState
import br.com.amberwrite.aistack.feature.files.kit.F4Art
import br.com.amberwrite.aistack.feature.files.kit.F4EmptyState
import br.com.amberwrite.aistack.feature.files.kit.SkeletonCard
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.designsystem.components.TagBadge
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide

private enum class DevicesPhase { Skeleton, Error, Empty, List }

/**
 * Aparelhos pareados ao desktop. Este aparelho vem em destaque; os outros mostram o último acesso
 * relativo. O celular só pode revogar a si mesmo (confirmação forte).
 *
 * [onUnpaired] é opcional: a navegação já volta ao pareamento quando o link some.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(onBack: () -> Unit, onUnpaired: () -> Unit = {}) {
    val vm = containerViewModel(key = "devices") { DevicesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAiHaptics()
    var confirm by rememberSaveable { mutableStateOf(false) }

    val subtitle = state.relayState?.let { r ->
        listOfNotNull(
            stringResource(R.string.devices_relay, r),
            state.relaySessions?.let { stringResource(R.string.devices_relay_sessions, it.toInt()) }
        ).joinToString(" · ")
    }

    FeatureScaffold(
        title = stringResource(R.string.devices_title),
        subtitle = subtitle,
        onBack = onBack,
        actions = {
            AiIconButton(
                icon = Lucide.RefreshCw,
                contentDescription = stringResource(R.string.devices_reload),
                onClick = vm::reload,
                size = 48.dp,
                enabled = !state.refreshing,
                haptic = HapticKind.Tick,
                haptics = haptics
            )
        }
    ) {
        val phase = when {
            state.showSkeleton -> DevicesPhase.Skeleton
            state.showFullError -> DevicesPhase.Error
            state.showEmpty -> DevicesPhase.Empty
            else -> DevicesPhase.List
        }
        val motion = AiTheme.motion
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "devices-phase"
        ) { p ->
            when (p) {
                DevicesPhase.Skeleton -> DevicesSkeleton()
                DevicesPhase.Error -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = vm::reload,
                    title = stringResource(R.string.devices_error_title)
                )
                DevicesPhase.Empty -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = vm::reload,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(Modifier.fillMaxSize()) {
                        F4EmptyState(
                            title = stringResource(R.string.devices_empty_title),
                            body = stringResource(R.string.devices_empty_body),
                            art = F4Art.NoDevices,
                            modifier = Modifier.weight(1f),
                            action = {
                                AiButton(
                                    text = stringResource(R.string.unpair_action),
                                    onClick = { confirm = true },
                                    variant = ButtonVariant.Ghost,
                                    leadingIcon = Lucide.Unplug
                                )
                            }
                        )
                    }
                }
                DevicesPhase.List -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = vm::reload,
                    modifier = Modifier.fillMaxSize()
                ) {
                    DevicesList(state, onUnpair = { confirm = true }, onDismissError = vm::dismissRevokeError)
                }
            }
        }
    }

    if (confirm) {
        UnpairConfirmDialog(
            working = state.revoking,
            onConfirm = {
                vm.revokeSelf {
                    confirm = false
                    onUnpaired()
                }
            },
            onDismiss = { confirm = false }
        )
    }
}

@Composable
private fun DevicesSkeleton() {
    val desc = stringResource(R.string.devices_loading)
    Column(
        Modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = desc },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SkeletonCard(bars = 2)
        repeat(3) { SkeletonCard(bars = 1) }
    }
}

@Composable
private fun DevicesList(
    state: DevicesViewModel.UiState,
    onUnpair: () -> Unit,
    onDismissError: () -> Unit
) {
    val c = AiTheme.colors
    val me = state.thisDevice
    val others = state.others
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.error?.let { err ->
                item(key = "err") { InfoBanner(err, tone = c.danger, modifier = Modifier.animateItem()) }
            }
            state.relayError?.let { err ->
                item(key = "relay-err") {
                    InfoBanner(stringResource(R.string.devices_relay_error, err), modifier = Modifier.animateItem())
                }
            }
            item(key = "revoke-err") {
                AnimatedVisibility(
                    visible = state.revokeError != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InfoBanner(
                            stringResource(R.string.devices_revoke_error, state.revokeError.orEmpty()),
                            tone = c.danger,
                            modifier = Modifier.weight(1f)
                        )
                        AiIconButton(
                            icon = Lucide.CircleX,
                            contentDescription = stringResource(R.string.devices_dismiss),
                            onClick = onDismissError,
                            size = 48.dp
                        )
                    }
                }
            }

            item(key = "h-this") {
                SectionTitle(
                    stringResource(R.string.devices_section_this),
                    Modifier.animateItem().semantics { heading() }
                )
            }
            item(key = "this") {
                Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (me != null) {
                        DeviceCard(me, now = state.now, highlighted = true)
                    } else {
                        InfoBanner(stringResource(R.string.devices_not_listed))
                    }
                    AiButton(
                        text = stringResource(R.string.unpair_action),
                        onClick = onUnpair,
                        variant = ButtonVariant.Danger,
                        leadingIcon = Lucide.Unplug,
                        loading = state.revoking,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }
            }

            if (others.isNotEmpty()) {
                item(key = "h-others") {
                    SectionTitle(
                        stringResource(R.string.devices_section_others, others.size),
                        Modifier.animateItem().semantics { heading() }
                    )
                }
                items(others, key = { "d-" + it.id }) { d ->
                    DeviceCard(d, now = state.now, highlighted = false, modifier = Modifier.animateItem())
                }
                item(key = "note") {
                    Text(
                        stringResource(R.string.devices_others_note),
                        style = AiTheme.typography.caption,
                        color = c.fg3,
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(d: DeviceView, now: Long, highlighted: Boolean, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    val (icon, kindLabel) = kindIcon(d.kind)
    val lastSeen = when {
        d.activeNow -> stringResource(R.string.devices_active_now)
        d.lastSeenMs != null -> stringResource(R.string.devices_last_seen, relativeTime(d.lastSeenMs, now))
        else -> stringResource(R.string.devices_never_seen)
    }
    val paired = d.pairedAtMs?.let { stringResource(R.string.devices_paired_at, relativeTime(it, now)) }
    val desc = stringResource(R.string.devices_card_desc, kindLabel, d.name) + ". " +
        listOfNotNull(
            if (d.isThis) stringResource(R.string.devices_this_badge) else null,
            if (d.revoked) stringResource(R.string.devices_revoked_badge) else null,
            lastSeen,
            paired
        ).joinToString(". ")

    SurfaceCard(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc },
        color = if (highlighted) c.accentSoft else c.surface,
        borderColor = if (highlighted) c.accent.copy(alpha = 0.45f) else c.line
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(AiTheme.shapes.md)
                    .background(if (highlighted) c.accent.copy(alpha = 0.16f) else c.surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (highlighted) c.accent else c.fg2, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        d.name,
                        style = AiTheme.typography.heading,
                        color = if (d.revoked) c.fg3 else c.fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (d.isThis) TagBadge(stringResource(R.string.devices_this_badge), color = c.accent, background = c.accent.copy(alpha = 0.14f))
                    if (d.revoked) TagBadge(stringResource(R.string.devices_revoked_badge), color = c.danger, background = c.dangerSoft)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    PresenceDot(active = d.activeNow, revoked = d.revoked)
                    Text(
                        listOfNotNull(lastSeen, paired).joinToString(" · "),
                        style = AiTheme.typography.caption,
                        color = c.fg3,
                        maxLines = 2,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

/** Ponto de presença: pulsa suavemente quando ativo (parado com movimento reduzido). */
@Composable
private fun PresenceDot(active: Boolean, revoked: Boolean) {
    val c = AiTheme.colors
    val color = when {
        revoked -> c.danger
        active -> c.ok
        else -> c.fg3
    }
    val pulse = if (active && !AiTheme.reducedMotion) {
        rememberInfiniteTransition(label = "presence").animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
            label = "presence-alpha"
        ).value
    } else {
        1f
    }
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer { alpha = pulse }
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun kindIcon(kind: DeviceKind): Pair<ImageVector, String> = when (kind) {
    DeviceKind.Phone -> Lucide.Smartphone to stringResource(R.string.devices_desc_phone)
    DeviceKind.Laptop -> Lucide.Laptop to stringResource(R.string.devices_desc_laptop)
    DeviceKind.Desktop -> Lucide.Monitor to stringResource(R.string.devices_desc_desktop)
}
