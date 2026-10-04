package br.com.amberwrite.aistack.feature.pending

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.PendingConversation
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.relativeTime
import br.com.amberwrite.aistack.service.NotificationMapper
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AgentStatus
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.PermissionCard
import br.com.amberwrite.aistack.ui.designsystem.components.PermissionState
import br.com.amberwrite.aistack.ui.designsystem.components.ProviderBadge
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionAnswer
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionCard
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionOption
import br.com.amberwrite.aistack.ui.designsystem.components.ShimmerText
import br.com.amberwrite.aistack.ui.designsystem.components.StatusDot
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.icons.Lucide
import com.google.gson.JsonNull

/** Alvo de toque mínimo (acessibilidade). */
private val MinTouch: Dp = 48.dp

// ---------------------------------------------------------------------------------------------
// Cartão da conversa
// ---------------------------------------------------------------------------------------------

/** Ações de um cartão; o ViewModel decide o que vai para o host. */
class PendingActions(
    val onAllow: (PermissionRequest, Boolean) -> Unit,
    val onDeny: (PermissionRequest, String?) -> Unit,
    val onAnswer: (PermissionRequest, Map<Int, String>) -> Unit,
    val onDismiss: (PermissionRequest) -> Unit,
    val onOpenChat: (String) -> Unit,
)

/** Um cartão por conversa: cabeçalho (abre o chat) e os pedidos abertos dela. */
@Composable
fun ConversationPendingCard(
    card: PendingCard,
    now: Long,
    actions: PendingActions,
    haptics: AiHaptics,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier = modifier.fillMaxWidth().animateContentSize(AiTheme.motion.spring())) {
        ConversationHeader(card.conversation, onOpen = { actions.onOpenChat(card.conversation.conversationId) })
        Spacer(Modifier.size(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            card.entries.forEach { entry ->
                androidx.compose.runtime.key(entry.request.requestId) {
                    if (entry.request.isAskUserQuestion) QuestionEntry(entry, now, actions, haptics)
                    else PermissionEntry(entry, now, actions, haptics)
                }
            }
        }
    }
}

@Composable
private fun ConversationHeader(conv: PendingConversation, onOpen: () -> Unit) {
    val c = AiTheme.colors
    val title = conv.title.ifBlank { NotificationMapper.DEFAULT_TITLE }
    val openCd = stringResource(R.string.pending_open_chat_cd, title)
    val project = conv.projectPath.trimEnd('/').substringAfterLast('/')
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clip(AiTheme.shapes.md)
            .clickable(role = Role.Button, onClickLabel = openCd, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = openCd; heading() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProviderBadge(providerId = conv.provider.id, compact = true)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AiTheme.typography.heading,
                color = c.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(if (conv.busy) AgentStatus.Busy else AgentStatus.Pending)
                Text(
                    stringResource(if (conv.busy) R.string.pending_busy else R.string.pending_waiting) +
                        if (project.isNotEmpty()) " · $project" else "",
                    style = AiTheme.typography.caption,
                    color = c.fg3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Lucide.ChevronRight, contentDescription = null, tint = c.fg3, modifier = Modifier.size(18.dp))
    }
}

/** Linha "pedido há 3 min" + aviso de prévia cortada + erro da última tentativa. */
@Composable
private fun EntryMeta(entry: PendingEntry, now: Long) {
    val c = AiTheme.colors
    val ago = relativeTime(entry.request.since, now)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (ago.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Lucide.Clock, contentDescription = null, tint = c.fg3, modifier = Modifier.size(12.dp))
                Text(stringResource(R.string.pending_requested_ago, ago), style = AiTheme.typography.caption, color = c.fg3)
            }
        }
        if (entry.request.truncated) {
            Text(stringResource(R.string.pending_preview_truncated), style = AiTheme.typography.caption, color = c.fg3)
        }
        AnimatedVisibility(
            visible = entry.error != null,
            enter = fadeIn(AiTheme.motion.fade()) + expandVertically(AiTheme.motion.spring()),
            exit = fadeOut(AiTheme.motion.exit()) + shrinkVertically(AiTheme.motion.exit()),
        ) {
            Text(
                stringResource(R.string.pending_answer_failed, entry.error.orEmpty()),
                style = AiTheme.typography.caption,
                color = c.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AiTheme.shapes.sm)
                    .background(c.dangerSoft)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun SendingLine() {
    ShimmerText(
        text = stringResource(R.string.pending_sending),
        style = AiTheme.typography.caption,
        color = AiTheme.colors.fg3,
        highlight = AiTheme.colors.fg,
        active = !AiTheme.reducedMotion,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private fun EntryStatus.toPermissionState(): PermissionState = when (this) {
    EntryStatus.Allowed -> PermissionState.Allowed
    EntryStatus.Denied -> PermissionState.Denied
    EntryStatus.Pending, EntryStatus.Sending -> PermissionState.Pending
}

/** Pedido de permissão comum: Permitir / Sempre permitir / Negar + "Responder" (nega e explica). */
@Composable
private fun PermissionEntry(entry: PendingEntry, now: Long, actions: PendingActions, haptics: AiHaptics) {
    val req = entry.request
    val sending = entry.status == EntryStatus.Sending
    val resolved = entry.status == EntryStatus.Allowed || entry.status == EntryStatus.Denied
    var replying by rememberSaveable(req.requestId) { mutableStateOf(false) }
    var reply by rememberSaveable(req.requestId) { mutableStateOf("") }
    val hasSuggestions = req.suggestions != null && req.suggestions !is JsonNull &&
        !(req.suggestions.isJsonArray && req.suggestions.asJsonArray.isEmpty)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PermissionCard(
            toolName = req.tool,
            state = entry.status.toPermissionState(),
            onAllow = { if (!sending) actions.onAllow(req, false) },
            onDeny = { if (!sending) actions.onDeny(req, null) },
            detail = req.inputPreview,
            description = req.reason,
            onAlwaysAllow = if (hasSuggestions) ({ if (!sending) actions.onAllow(req, true) }) else null,
            haptics = haptics,
            modifier = Modifier.alpha(if (sending) 0.6f else 1f),
        )
        if (!resolved) {
            EntryMeta(entry, now)
            val motion = AiTheme.motion
            AnimatedContent(
                targetState = when {
                    sending -> 0
                    replying -> 1
                    else -> 2
                },
                transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.exit()) },
                label = "permissionReply",
            ) { mode ->
                when (mode) {
                    0 -> SendingLine()
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiTextInput(
                            value = reply,
                            onValueChange = { reply = it },
                            placeholder = stringResource(R.string.pending_reply_hint),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            AiButton(
                                text = stringResource(R.string.pending_reply_cancel),
                                onClick = { replying = false },
                                variant = ButtonVariant.Ghost,
                                size = ButtonSize.Large,
                                haptic = HapticKind.Tick,
                                haptics = haptics,
                            )
                            Spacer(Modifier.weight(1f))
                            val sendCd = stringResource(R.string.pending_reply_send_cd)
                            AiButton(
                                text = stringResource(R.string.pending_reply_send),
                                onClick = {
                                    actions.onDeny(req, reply)
                                    replying = false
                                },
                                variant = ButtonVariant.Danger,
                                size = ButtonSize.Large,
                                leadingIcon = Lucide.Send,
                                enabled = reply.isNotBlank(),
                                haptic = HapticKind.Reject,
                                haptics = haptics,
                                modifier = Modifier.semantics { contentDescription = sendCd },
                            )
                        }
                    }
                    else -> AiButton(
                        text = stringResource(R.string.pending_reply),
                        onClick = { replying = true },
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Large,
                        leadingIcon = Lucide.MessageSquare,
                        haptic = HapticKind.Tick,
                        haptics = haptics,
                    )
                }
            }
        }
    }
}

/** Opções do modelo de dados no formato do cartão do design system. */
private fun Question.uiOptions(): List<QuestionOption> = options.map { o ->
    val rec = RECOMMENDED.find(o.label)
    QuestionOption(
        label = if (rec != null) o.label.replace(rec.value, "").trim() else o.label,
        description = o.description,
        recommended = rec != null,
    )
}

private val RECOMMENDED = Regex("""\s*\((?:Recommended|Recomendad[oa])\)\s*""", RegexOption.IGNORE_CASE)

/**
 * `AskUserQuestion`: uma pergunta por vez; respondidas viram resumo (toque refaz). Ao
 * responder a última, envia `allow` com `answers`. "Dispensar" nega com o texto padrão.
 */
@Composable
private fun QuestionEntry(entry: PendingEntry, now: Long, actions: PendingActions, haptics: AiHaptics) {
    val c = AiTheme.colors
    val req = entry.request
    val questions = remember(req.requestId) { req.questions }
    val answers = remember(req.requestId) { mutableStateMapOf<Int, String>() }
    val sending = entry.status == EntryStatus.Sending
    val resolved = entry.status == EntryStatus.Allowed || entry.status == EntryStatus.Denied

    Column(
        Modifier
            .fillMaxWidth()
            .clip(AiTheme.shapes.md)
            .background(c.surface2)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Lucide.Sparkles, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.pending_question_title),
                style = AiTheme.typography.label,
                color = c.fg,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (resolved) {
                Text(
                    stringResource(if (entry.status == EntryStatus.Allowed) R.string.pending_answered_allow else R.string.pending_answered_deny),
                    style = AiTheme.typography.caption,
                    color = if (entry.status == EntryStatus.Allowed) c.ok else c.fg3,
                )
            } else if (questions.size > 1) {
                Text(
                    stringResource(R.string.pending_question_progress, (answers.size + 1).coerceAtMost(questions.size), questions.size),
                    style = AiTheme.typography.caption,
                    color = c.fg3,
                )
            }
        }
        if (resolved) return@Column

        if (questions.isEmpty()) {
            Text(stringResource(R.string.pending_question_unreadable), style = AiTheme.typography.body, color = c.fg2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiButton(
                    text = stringResource(R.string.pending_question_skip),
                    onClick = { actions.onDismiss(req) },
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Large,
                    enabled = !sending,
                    haptic = HapticKind.Reject,
                    haptics = haptics,
                )
                AiButton(
                    text = stringResource(R.string.notif_action_reply),
                    onClick = { actions.onOpenChat(req.conversationId) },
                    size = ButtonSize.Large,
                    trailingIcon = Lucide.ChevronRight,
                    haptics = haptics,
                )
            }
            EntryMeta(entry, now)
            return@Column
        }

        // Respondidas (toque para refazer).
        questions.forEachIndexed { i, q ->
            val a = answers[i] ?: return@forEachIndexed
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch)
                    .clip(AiTheme.shapes.sm)
                    .clickable(enabled = !sending, role = Role.Button) {
                        haptics.perform(HapticKind.Tick)
                        // Refaz esta e as seguintes.
                        answers.keys.filter { it >= i }.forEach { answers.remove(it) }
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Lucide.Check, contentDescription = null, tint = c.ok, modifier = Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.header ?: q.question, style = AiTheme.typography.caption, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a, style = AiTheme.typography.body, color = c.fg, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Lucide.Pencil, contentDescription = null, tint = c.fg3, modifier = Modifier.size(14.dp))
            }
        }

        val current = questions.indices.firstOrNull { it !in answers }
        val motion = AiTheme.motion
        AnimatedContent(
            targetState = if (sending) -2 else current ?: -1,
            transitionSpec = {
                (fadeIn(motion.enter()) + expandVertically(motion.spring())) togetherWith
                    (fadeOut(motion.exit()) + shrinkVertically(motion.exit()))
            },
            label = "question",
        ) { idx ->
            when {
                idx == -2 -> SendingLine()
                idx >= 0 -> {
                    val q = questions[idx]
                    QuestionCard(
                        question = q.question,
                        options = remember(q) { q.uiOptions() },
                        header = q.header,
                        haptics = haptics,
                        onSubmit = { ans ->
                            val text = when (ans) {
                                is QuestionAnswer.Choice -> q.options.getOrNull(ans.number - 1)?.label ?: ans.option.label
                                is QuestionAnswer.Custom -> ans.value.trim()
                            }
                            if (text.isNotEmpty()) {
                                answers[idx] = text
                                if (answers.size >= questions.size) actions.onAnswer(req, answers.toMap())
                            }
                        },
                        onSkip = { actions.onDismiss(req) },
                    )
                }
                else -> AiButton(
                    // Todas respondidas mas o envio falhou: reenviar.
                    text = stringResource(R.string.pending_question_send),
                    onClick = { actions.onAnswer(req, answers.toMap()) },
                    size = ButtonSize.Large,
                    trailingIcon = Lucide.ArrowUp,
                    haptic = HapticKind.Confirm,
                    haptics = haptics,
                )
            }
        }
        EntryMeta(entry, now)
    }
}

// ---------------------------------------------------------------------------------------------
// Esqueleto com shimmer
// ---------------------------------------------------------------------------------------------

/** Brilho que atravessa os blocos do esqueleto (estático com movimento reduzido). */
@Composable
private fun shimmerBrush(): Brush {
    val c = AiTheme.colors
    val base = c.surface2
    val hi = c.line
    if (AiTheme.reducedMotion) return Brush.linearGradient(listOf(base, base))
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    return Brush.linearGradient(listOf(base, hi, base), start = Offset(x, 0f), end = Offset(x + 400f, 0f))
}

/** Cartão-esqueleto do carregamento. */
@Composable
fun PendingSkeletonCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    SurfaceCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(AiTheme.shapes.pill).background(brush))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(AiTheme.shapes.sm).background(brush))
                Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(AiTheme.shapes.sm).background(brush))
            }
        }
        Spacer(Modifier.size(12.dp))
        Box(Modifier.fillMaxWidth().height(96.dp).clip(AiTheme.shapes.md).background(brush))
    }
}

// ---------------------------------------------------------------------------------------------
// Permissão de notificações (explicação no momento certo)
// ---------------------------------------------------------------------------------------------

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun notificationsEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Explica por que ativar as notificações, mostrado na caixa de pendências (onde o valor é
 * óbvio) enquanto estiverem desligadas. Pede a permissão de novo se o sistema ainda deixar
 * (`shouldShowRequestPermissionRationale`); senão abre os ajustes do app.
 */
@Composable
fun NotificationRationale(dismissed: Boolean, onDismiss: () -> Unit, haptics: AiHaptics, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = notificationsEnabled(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = notificationsEnabled(context)
        if (!granted && !enabled) {
            val activity = context.findActivity()
            val canAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
            if (!canAskAgain) openNotificationSettings(context)
        }
    }
    val canRequest = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        context.findActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } == true

    AnimatedVisibility(
        visible = !enabled && !dismissed,
        enter = fadeIn(AiTheme.motion.enter()) + expandVertically(AiTheme.motion.spring()),
        exit = fadeOut(AiTheme.motion.exit()) + shrinkVertically(AiTheme.motion.exit()),
        modifier = modifier,
    ) {
        val c = AiTheme.colors
        SurfaceCard(borderColor = c.accent.copy(alpha = 0.35f)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(36.dp).clip(AiTheme.shapes.pill).background(c.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.Bell, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.pending_notif_rationale_title),
                        style = AiTheme.typography.label,
                        color = c.fg,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.pending_notif_rationale_body), style = AiTheme.typography.body, color = c.fg2)
                }
                AiIconButton(
                    icon = Lucide.X,
                    contentDescription = stringResource(R.string.pending_notif_rationale_dismiss_cd),
                    onClick = onDismiss,
                    size = MinTouch,
                    iconSize = 18.dp,
                    haptics = haptics,
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AiButton(
                    text = stringResource(R.string.pending_notif_rationale_dismiss),
                    onClick = onDismiss,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Large,
                    haptics = haptics,
                )
                Spacer(Modifier.weight(1f))
                AiButton(
                    text = stringResource(
                        if (canRequest) R.string.pending_notif_rationale_allow else R.string.pending_notif_rationale_settings
                    ),
                    onClick = {
                        if (canRequest) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openNotificationSettings(context)
                    },
                    size = ButtonSize.Large,
                    leadingIcon = Lucide.Bell,
                    haptic = HapticKind.Confirm,
                    haptics = haptics,
                )
            }
        }
    }
}
