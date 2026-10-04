package br.com.amberwrite.aistack.feature.pair

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.core.relay.PairLink
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.ConfirmDialog
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.brand.BrandAvd
import br.com.amberwrite.aistack.ui.designsystem.brand.BrandMarkAvd
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiStackTopBar
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.delay

/**
 * Pareamento (§4.1). [initialLink] vem de um deep link `aistack://pair?…` já confirmado pelo
 * usuário (a navegação pede confirmação quando há outro desktop pareado). [onPaired] é
 * chamado uma vez, depois da animação de sucesso. [onBack] aparece só quando a tela foi
 * aberta a partir de outra (re-pareamento); no primeiro uso não há para onde voltar.
 *
 * Só a interface mudou: o link continua indo para `container.pair`, e o núcleo cuida do
 * handshake, do keyAuth e de salvar o par.
 */
@Composable
fun PairScreen(
    onPaired: () -> Unit,
    initialLink: PairLink? = null,
    onInitialLinkConsumed: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val vm = containerViewModel { PairViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAiHaptics()
    val reduced = AiTheme.reducedMotion

    LaunchedEffect(initialLink) {
        if (initialLink != null) {
            vm.start(initialLink)
            onInitialLinkConsumed()
        }
    }
    LaunchedEffect(state.paired) {
        if (state.paired) {
            haptics.perform(HapticKind.Confirm)
            delay(if (reduced) 300 else 1_100)
            onPaired()
        }
    }

    if (state.step == PairViewModel.Step.Scanning) {
        BackHandler { vm.closeScanner() }
        QrScanner(onPairFound = vm::request, onBack = vm::closeScanner)
        return
    }
    if (state.step == PairViewModel.Step.Pairing && !state.paired) {
        BackHandler { vm.reset() }
    }

    state.confirmRepair?.let { link ->
        ConfirmDialog(
            title = stringResource(R.string.pair_repair_title),
            text = stringResource(R.string.pair_repair_text, link.relay),
            confirmLabel = stringResource(R.string.pair_repair_confirm),
            onConfirm = vm::confirmRepair,
            onDismiss = vm::dismissRepair,
            dismissLabel = stringResource(R.string.pair_cancel),
        )
    }

    val c = AiTheme.colors
    val motion = AiTheme.motion
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        AiStackTopBar(
            title = stringResource(R.string.pair_screen_title),
            navigationIcon = if (onBack != null) Lucide.ArrowLeft else null,
            navigationContentDescription = stringResource(R.string.pair_back_cd),
            onNavigationClick = onBack,
            haptics = haptics,
        )
        AnimatedContent(
            targetState = state.step == PairViewModel.Step.Pairing,
            transitionSpec = {
                (fadeIn(motion.fade()) + slideInVertically(motion.enter()) { it / 8 }) togetherWith
                    (fadeOut(motion.exit()) + slideOutVertically(motion.exit()) { -it / 8 })
            },
            label = "pairStep",
            modifier = Modifier.fillMaxSize(),
        ) { pairing ->
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (pairing) {
                        PairingPane(
                            state = state,
                            haptics = haptics,
                            onRetry = vm::retry,
                            onCancel = vm::reset,
                            onScanAgain = {
                                vm.reset()
                                vm.openScanner()
                            },
                        )
                    } else {
                        OnboardingPane(state = state, vm = vm, haptics = haptics)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Onboarding
// ---------------------------------------------------------------------------------------------

@Composable
private fun OnboardingPane(state: PairViewModel.UiState, vm: PairViewModel, haptics: AiHaptics) {
    val c = AiTheme.colors
    val t = AiTheme.typography
    val motion = AiTheme.motion
    val brandCd = stringResource(R.string.pair_brand_cd)

    Spacer(Modifier.height(12.dp))
    Box(
        Modifier
            .size(112.dp)
            .semantics { contentDescription = brandCd },
        contentAlignment = Alignment.Center,
    ) {
        BrandMarkAvd(BrandAvd.Assemble, size = 96.dp)
    }
    Spacer(Modifier.height(20.dp))
    Staggered(index = 0) {
        Text(
            stringResource(R.string.pair_hero_title),
            style = t.title,
            color = c.fg,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
    }
    Spacer(Modifier.height(8.dp))
    Staggered(index = 1) {
        Text(
            stringResource(R.string.pair_hero_body),
            style = t.body,
            color = c.fg2,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(24.dp))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(R.string.pair_step1, R.string.pair_step2, R.string.pair_step3).forEachIndexed { i, res ->
            Staggered(index = i + 2) { InstructionRow(number = i + 1, text = stringResource(res)) }
        }
    }
    Spacer(Modifier.height(24.dp))

    AnimatedVisibility(visible = state.alreadyPaired) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(c.warnSoft, AiTheme.shapes.md)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Lucide.Info, contentDescription = null, tint = c.warn, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.pair_already_paired), style = t.bodySmall, color = c.fg)
        }
    }

    Staggered(index = 5) {
        AiButton(
            text = stringResource(R.string.pair_scan),
            onClick = vm::openScanner,
            leadingIcon = Lucide.QrCode,
            size = ButtonSize.Large,
            haptics = haptics,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(8.dp))
    AiButton(
        text = stringResource(if (state.pasteOpen) R.string.pair_paste_hide else R.string.pair_paste_toggle),
        onClick = vm::togglePaste,
        variant = ButtonVariant.Ghost,
        size = ButtonSize.Large,
        leadingIcon = Lucide.Link,
        haptics = haptics,
        modifier = Modifier.fillMaxWidth(),
    )
    AnimatedVisibility(
        visible = state.pasteOpen,
        enter = fadeIn(motion.fade()) + expandVertically(motion.spring()),
        exit = fadeOut(motion.exit()) + shrinkVertically(motion.spring()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AiTextInput(
                value = state.pasted,
                onValueChange = vm::onPastedChange,
                label = stringResource(R.string.pair_paste_label),
                placeholder = stringResource(R.string.pair_paste_placeholder),
                singleLine = true,
                imeAction = ImeAction.Done,
                mono = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(visible = state.inputError != null) {
                val err = state.inputError
                Text(
                    when (err) {
                        PairInputError.MissingCode -> stringResource(R.string.pair_error_missing_code)
                        else -> stringResource(R.string.pair_error_invalid)
                    },
                    style = t.caption,
                    color = c.danger,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            AiButton(
                text = stringResource(R.string.pair_submit),
                onClick = vm::submitPasted,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Large,
                enabled = state.pasted.isNotBlank(),
                haptics = haptics,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (state.deviceName.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.pair_device_name, state.deviceName),
            style = t.caption,
            color = c.fg3,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun InstructionRow(number: Int, text: String) {
    val c = AiTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(c.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = AiTheme.typography.label, color = c.accent, fontWeight = FontWeight.SemiBold)
        }
        Text(text, style = AiTheme.typography.body, color = c.fg, modifier = Modifier.weight(1f))
    }
}

/** Entrada escalonada (fade + subida) dos itens do onboarding; instantânea com movimento reduzido. */
@Composable
private fun Staggered(index: Int, content: @Composable () -> Unit) {
    val reduced = AiTheme.reducedMotion
    val motion = AiTheme.motion
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(120L + index * 70L)
            progress.animateTo(1f, motion.enter())
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 16.dp.toPx()
        }
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// Pareamento em curso
// ---------------------------------------------------------------------------------------------

private enum class HeroKind { Progress, Success, Rejected, Offline, Failed }

private fun PairPhase.hero(): HeroKind = when (this) {
    PairPhase.Success -> HeroKind.Success
    is PairPhase.AuthRejected -> HeroKind.Rejected
    is PairPhase.HostOffline -> HeroKind.Offline
    is PairPhase.Failed -> HeroKind.Failed
    else -> HeroKind.Progress
}

@Composable
private fun PairingPane(
    state: PairViewModel.UiState,
    haptics: AiHaptics,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onScanAgain: () -> Unit,
) {
    val c = AiTheme.colors
    val t = AiTheme.typography
    val motion = AiTheme.motion
    val phase = state.phase
    val hero = phase.hero()

    LaunchedEffect(hero) {
        if (hero == HeroKind.Rejected || hero == HeroKind.Failed) haptics.perform(HapticKind.Reject)
    }

    Spacer(Modifier.height(32.dp))
    AnimatedContent(
        targetState = hero,
        transitionSpec = {
            (fadeIn(motion.fade()) + scaleIn(motion.bouncy(), initialScale = 0.6f)) togetherWith fadeOut(motion.exit())
        },
        label = "pairHero",
    ) { kind ->
        when (kind) {
            HeroKind.Progress -> ProgressHero()
            HeroKind.Success -> SuccessHero()
            HeroKind.Rejected -> FailureHero(Lucide.ShieldAlert, c.danger, c.dangerSoft, shake = true)
            HeroKind.Offline -> FailureHero(Lucide.WifiOff, c.warn, c.warnSoft, shake = false)
            HeroKind.Failed -> FailureHero(Lucide.TriangleAlert, c.danger, c.dangerSoft, shake = false)
        }
    }
    Spacer(Modifier.height(24.dp))

    val title = when (phase) {
        is PairPhase.Connecting ->
            if (phase.attempt > 0) stringResource(R.string.pair_phase_connecting_attempt, phase.attempt + 1)
            else stringResource(R.string.pair_phase_connecting)
        PairPhase.Idle -> stringResource(R.string.pair_phase_connecting)
        PairPhase.Handshaking -> stringResource(R.string.pair_phase_handshaking)
        PairPhase.Verifying -> stringResource(R.string.pair_phase_verifying)
        PairPhase.Success -> stringResource(R.string.pair_phase_success)
        is PairPhase.AuthRejected -> stringResource(R.string.pair_phase_rejected)
        is PairPhase.HostOffline -> stringResource(R.string.pair_phase_offline)
        is PairPhase.Failed -> stringResource(R.string.pair_phase_failed)
    }
    AnimatedContent(
        targetState = title,
        transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.exit()) },
        label = "pairTitle",
    ) { text ->
        Text(
            text,
            style = t.title,
            color = c.fg,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    val body = when (phase) {
        PairPhase.Success -> stringResource(R.string.pair_hint_success)
        is PairPhase.AuthRejected -> phase.message?.takeIf { it.isNotBlank() }
            ?.let { "$it\n\n" + stringResource(R.string.pair_hint_rejected) }
            ?: stringResource(R.string.pair_hint_rejected)
        is PairPhase.HostOffline -> stringResource(R.string.pair_hint_offline)
        is PairPhase.Failed -> phase.message
        else -> stringResource(R.string.pair_hint_progress)
    }
    Text(body, style = t.body, color = c.fg2, textAlign = TextAlign.Center)

    val retryMs = when (phase) {
        is PairPhase.HostOffline -> phase.retryInMs
        is PairPhase.Failed -> phase.retryInMs
        else -> null
    }
    val secs = rememberCountdown(key = phase, totalMs = retryMs)
    if (secs != null && secs > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pair_retry_in, secs),
            style = t.monoSmall,
            color = c.fg3,
        )
    }

    Spacer(Modifier.height(28.dp))
    PairStepper(phase)
    Spacer(Modifier.height(28.dp))

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (phase) {
            is PairPhase.AuthRejected -> {
                AiButton(
                    text = stringResource(R.string.pair_scan_again),
                    onClick = onScanAgain,
                    leadingIcon = Lucide.QrCode,
                    size = ButtonSize.Large,
                    haptics = haptics,
                    modifier = Modifier.fillMaxWidth(),
                )
                AiButton(
                    text = stringResource(R.string.pair_cancel),
                    onClick = onCancel,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Large,
                    haptics = haptics,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is PairPhase.HostOffline, is PairPhase.Failed -> {
                AiButton(
                    text = stringResource(
                        if (phase is PairPhase.HostOffline) R.string.pair_retry_now else R.string.pair_retry
                    ),
                    onClick = onRetry,
                    leadingIcon = Lucide.RefreshCw,
                    size = ButtonSize.Large,
                    haptics = haptics,
                    modifier = Modifier.fillMaxWidth(),
                )
                AiButton(
                    text = stringResource(R.string.pair_cancel),
                    onClick = onCancel,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Large,
                    haptics = haptics,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PairPhase.Success -> Unit
            else -> AiButton(
                text = stringResource(R.string.pair_cancel),
                onClick = onCancel,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Large,
                haptics = haptics,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ProgressHero() {
    val c = AiTheme.colors
    val reduced = AiTheme.reducedMotion
    val cd = stringResource(R.string.pair_progress_cd)
    val pulse = if (reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "pairPulse")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Restart),
            label = "pairPulseValue",
        )
        v
    }
    Box(
        Modifier
            .size(132.dp)
            .clearAndSetSemantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        if (!reduced) {
            Box(
                Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        scaleX = 0.7f + pulse * 0.3f
                        scaleY = 0.7f + pulse * 0.3f
                        alpha = 1f - pulse
                    }
                    .border(2.dp, c.accent, CircleShape)
            )
        }
        Box(
            Modifier
                .size(96.dp)
                .background(c.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BrandMarkAvd(BrandAvd.Pulse, size = 56.dp)
        }
    }
}

@Composable
private fun SuccessHero() {
    val c = AiTheme.colors
    val motion = AiTheme.motion
    val reduced = AiTheme.reducedMotion
    val cd = stringResource(R.string.pair_success_cd)
    val check = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(90)
            check.animateTo(1f, motion.bouncy())
        }
    }
    Box(
        Modifier
            .size(132.dp)
            .clearAndSetSemantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(112.dp)
                .background(c.okSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = check.value
                        scaleY = check.value
                        rotationZ = (1f - check.value) * -45f
                    }
                    .background(c.ok, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun FailureHero(icon: ImageVector, tint: Color, background: Color, shake: Boolean) {
    val reduced = AiTheme.reducedMotion
    val cd = stringResource(R.string.pair_failure_cd)
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake && !reduced) {
            offset.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    -14f at 60
                    12f at 130
                    -9f at 200
                    6f at 270
                    -3f at 340
                },
            )
        }
    }
    Box(
        Modifier
            .size(132.dp)
            .clearAndSetSemantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(112.dp)
                .graphicsLayer { translationX = offset.value * density }
                .background(background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
        }
    }
}

/** Indicador de etapas (relay → chaves → desktop). */
@Composable
private fun PairStepper(phase: PairPhase) {
    val labels = listOf(
        stringResource(R.string.pair_stage_relay),
        stringResource(R.string.pair_stage_keys),
        stringResource(R.string.pair_stage_desktop),
    )
    val current = phase.stepIndex()
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        labels.forEachIndexed { i, label ->
            val status = when {
                i < current -> StageStatus.Done
                i == current && phase.isFailure -> StageStatus.Failed
                i == current -> StageStatus.Active
                else -> StageStatus.Pending
            }
            StageItem(index = i, label = label, status = status, modifier = Modifier.weight(1f))
        }
    }
}

private enum class StageStatus { Done, Active, Pending, Failed }

@Composable
private fun StageItem(index: Int, label: String, status: StageStatus, modifier: Modifier) {
    val c = AiTheme.colors
    val motion = AiTheme.motion
    val statusText = stringResource(
        when (status) {
            StageStatus.Done -> R.string.pair_stage_done
            StageStatus.Active -> R.string.pair_stage_active
            StageStatus.Pending -> R.string.pair_stage_pending
            StageStatus.Failed -> R.string.pair_stage_failed
        }
    )
    val cd = stringResource(R.string.pair_stage_cd, index + 1, label, statusText)
    val bg by animateColorAsState(
        when (status) {
            StageStatus.Done -> c.ok
            StageStatus.Active -> c.accentSoft
            StageStatus.Pending -> c.surface2
            StageStatus.Failed -> c.dangerSoft
        },
        motion.fade(),
        label = "stageBg",
    )
    val fg by animateColorAsState(
        when (status) {
            StageStatus.Done, StageStatus.Active -> c.fg
            StageStatus.Pending -> c.fg3
            StageStatus.Failed -> c.danger
        },
        motion.fade(),
        label = "stageFg",
    )
    Column(
        modifier.clearAndSetSemantics { contentDescription = cd },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = status,
                transitionSpec = { (fadeIn(motion.fade()) + scaleIn(motion.bouncy())) togetherWith fadeOut(motion.exit()) },
                label = "stageIcon",
            ) { s ->
                when (s) {
                    StageStatus.Done -> Icon(Lucide.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    StageStatus.Active -> Spinner(size = 16.dp, strokeWidth = 2.dp)
                    StageStatus.Failed -> Icon(Lucide.X, null, tint = c.danger, modifier = Modifier.size(16.dp))
                    StageStatus.Pending -> Text(
                        "${index + 1}",
                        style = AiTheme.typography.label,
                        color = c.fg3,
                    )
                }
            }
        }
        Text(label, style = AiTheme.typography.caption, color = fg, textAlign = TextAlign.Center)
    }
}

/** Contagem regressiva (em segundos) até a próxima tentativa automática do núcleo. */
@Composable
private fun rememberCountdown(key: Any, totalMs: Long?): Int? {
    if (totalMs == null) return null
    var left by remember(key) { mutableLongStateOf(totalMs) }
    LaunchedEffect(key, totalMs) {
        val start = System.nanoTime()
        while (left > 0) {
            delay(250)
            left = (totalMs - (System.nanoTime() - start) / 1_000_000).coerceAtLeast(0)
        }
    }
    return secondsLeft(left)
}
