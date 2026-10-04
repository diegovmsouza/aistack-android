package br.com.amberwrite.aistack.feature.pending

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.bannerDetail
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.toBanner
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ConnectionBanner
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.illustrations.Illustration
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.delay

/** Fases da tela (cada uma com sua animação de entrada). */
private enum class Phase { Loading, Error, Empty, Content }

/**
 * Pendências: um cartão por conversa com os pedidos de permissão e as perguntas abertas,
 * respondidos daqui mesmo (Permitir / Negar / Responder). O cabeçalho do cartão abre o chat.
 * Grade adaptativa: uma coluna no celular, mais colunas em telas largas.
 */
@Composable
fun PendingScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val vm = containerViewModel { PendingViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAiHaptics()
    val motion = AiTheme.motion
    val reduced = AiTheme.reducedMotion

    // "pedido há 3 min" se atualiza sozinho.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    val actions = remember(vm, onOpenChat) {
        PendingActions(
            onAllow = { req, remember -> vm.allow(req, remember) },
            onDeny = { req, msg -> vm.deny(req, msg) },
            onAnswer = { req, answers -> vm.answerQuestions(req, answers) },
            onDismiss = { req -> vm.dismiss(req) },
            onOpenChat = onOpenChat,
        )
    }

    val waiting = state.waitingCount
    FeatureScaffold(
        title = stringResource(R.string.pending_title),
        onBack = onBack,
        subtitle = if (waiting > 0) pluralStringResource(R.plurals.pending_subtitle_count, waiting, waiting)
        else if (!state.loading) stringResource(R.string.pending_subtitle_empty) else null,
        actions = {
            AiIconButton(
                icon = Lucide.RefreshCw,
                contentDescription = stringResource(R.string.pending_refresh_cd),
                onClick = vm::reload,
                size = 48.dp,
                enabled = !state.loading,
                haptics = haptics,
            )
        },
    ) {
        ConnectionBanner(
            state = state.connection.toBanner(),
            detail = state.connection.bannerDetail(),
            onRetry = vm::reload,
        )
        AnimatedVisibility(
            visible = !state.supported,
            enter = fadeIn(motion.fade()) + expandVertically(motion.spring()),
            exit = fadeOut(motion.exit()) + shrinkVertically(motion.exit()),
        ) { ErrorStrip(stringResource(R.string.pending_unsupported)) }

        val phase = when {
            state.cards.isNotEmpty() -> Phase.Content
            state.loading -> Phase.Loading
            state.loadError || (state.connection !is ConnectionState.Online && state.supported) -> Phase.Error
            else -> Phase.Empty
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(motion.enter()) togetherWith fadeOut(motion.exit()) },
                label = "pendingPhase",
            ) { p ->
                when (p) {
                    Phase.Loading -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = false).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NotificationRationale(state.rationaleDismissed, vm::dismissRationale, haptics)
                        repeat(3) { PendingSkeletonCard() }
                    }

                    Phase.Error -> Column(Modifier.fillMaxSize()) {
                        EmptyState(
                            title = stringResource(R.string.pending_error_title),
                            body = stringResource(R.string.pending_error_body),
                            illustration = Illustration.Offline,
                            accent = AiTheme.colors.warn,
                            modifier = Modifier.fillMaxSize(),
                            primaryAction = {
                                AiButton(
                                    text = stringResource(R.string.pending_retry),
                                    onClick = vm::reload,
                                    size = ButtonSize.Large,
                                    leadingIcon = Lucide.RefreshCw,
                                    haptic = HapticKind.Tick,
                                    haptics = haptics,
                                )
                            },
                        )
                    }

                    Phase.Empty -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        NotificationRationale(
                            state.rationaleDismissed,
                            vm::dismissRationale,
                            haptics,
                            Modifier.padding(top = 16.dp),
                        )
                        EmptyState(
                            title = stringResource(R.string.pending_empty_title),
                            body = stringResource(R.string.pending_empty_body),
                            illustration = Illustration.NoPending,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }

                    Phase.Content -> LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(360.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalItemSpacing = 12.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "rationale", span = StaggeredGridItemSpan.FullLine) {
                            NotificationRationale(state.rationaleDismissed, vm::dismissRationale, haptics)
                        }
                        items(state.cards, key = { it.key }) { card ->
                            ConversationPendingCard(
                                card = card,
                                now = now,
                                actions = actions,
                                haptics = haptics,
                                modifier = if (reduced) Modifier.animateItem(null, null, null)
                                else Modifier.animateItem(
                                    fadeInSpec = motion.fade(),
                                    placementSpec = motion.spring(),
                                    fadeOutSpec = motion.exit(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
