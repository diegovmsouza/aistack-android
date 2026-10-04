package br.com.amberwrite.aistack.feature.chat.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.ChatItem
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.feature.chat.ChatViewModel
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.PermissionCard
import br.com.amberwrite.aistack.ui.designsystem.components.PermissionState
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionAnswer
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionCard
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.icons.Lucide
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionOption as DsOption

/** Pedido pendente inline: permissão de ferramenta ou `AskUserQuestion`. */
@Composable
internal fun PendingView(
    request: PermissionRequest,
    local: ChatViewModel.Local,
    actions: ThreadActions,
    haptics: AiHaptics,
    modifier: Modifier = Modifier,
) {
    val decision = local.answering[request.requestId]
    val failure = local.failures[request.requestId]
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (request.isAskUserQuestion && request.questions.isNotEmpty()) {
            AskUserQuestionView(request, decision != null, failure, actions, haptics)
        } else {
            val state = when (decision) {
                ChatViewModel.Decision.Allow, ChatViewModel.Decision.Answer -> PermissionState.Allowed
                ChatViewModel.Decision.Deny -> PermissionState.Denied
                null -> PermissionState.Pending
            }
            val hasSuggestions = request.suggestions?.let { !it.isJsonNull && !(it.isJsonArray && it.asJsonArray.isEmpty) } == true
            PermissionCard(
                toolName = request.tool,
                state = state,
                onAllow = { actions.onAllow(request, false) },
                onDeny = { actions.onDeny(request) },
                detail = ThreadModel.toolDetailOf(request.input) ?: request.inputPreview?.lineSequence()?.firstOrNull()?.take(160),
                description = request.reason,
                onAlwaysAllow = if (hasSuggestions) ({ actions.onAllow(request, true) }) else null,
                haptics = haptics,
            )
            if (decision != null) SendingRow(stringResource(R.string.chat_permission_sending))
        }
        FailureRow(failure)
    }
}

/** Perguntas do `AskUserQuestion`, uma de cada vez; envia todas juntas no fim. */
@Composable
private fun AskUserQuestionView(
    request: PermissionRequest,
    sending: Boolean,
    failure: String?,
    actions: ThreadActions,
    haptics: AiHaptics,
) {
    val questions = request.questions
    val answers = remember(request.requestId) { mutableStateMapOf<Question, List<String>>() }
    // Uma falha devolve o pedido ao início para o usuário responder de novo.
    LaunchedEffect(failure) { if (failure != null) answers.clear() }
    if (sending) {
        SendingRow(stringResource(R.string.chat_question_sending))
        return
    }
    val index = questions.indexOfFirst { it !in answers }
    if (index < 0) return
    val q = questions[index]
    val record: (List<String>) -> Unit = { picked ->
        answers[q] = picked
        if (questions.all { it in answers }) actions.onAnswerQuestion(request, answers.toMap())
    }
    val header = buildString {
        q.header?.takeIf { it.isNotBlank() }?.let { append(it) }
        if (questions.size > 1) {
            if (isNotEmpty()) append(" · ")
            append("${index + 1}/${questions.size}")
        }
    }.ifBlank { null }
    key(request.requestId, index) {
        if (q.multiSelect) {
            MultiSelectQuestionCard(
                question = q,
                header = header,
                onSubmit = record,
                onSkip = { actions.onDismissQuestion(request) },
                haptics = haptics,
            )
        } else {
            QuestionCard(
                question = q.question,
                options = q.options.map { DsOption(it.label, it.description) },
                onSubmit = { a ->
                    record(
                        when (a) {
                            is QuestionAnswer.Choice -> listOf(a.option.label)
                            is QuestionAnswer.Custom -> listOf(a.value)
                        },
                    )
                },
                onSkip = { actions.onDismissQuestion(request) },
                header = header,
                haptics = haptics,
            )
        }
    }
}

/** Pergunta de ferramenta (`ask_question`): a resposta vira uma mensagem comum. */
@Composable
internal fun ToolAskView(
    tool: ChatItem.Tool,
    question: Question,
    local: ChatViewModel.Local,
    actions: ThreadActions,
    haptics: AiHaptics,
    modifier: Modifier = Modifier,
) {
    var skipped by rememberSaveable(tool.key) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedVisibility(
            visible = !skipped,
            enter = expandVertically(AiTheme.motion.spring()) + fadeIn(AiTheme.motion.fade()),
            exit = shrinkVertically(AiTheme.motion.spring()) + fadeOut(AiTheme.motion.fade()),
        ) {
            if (local.toolAnswering) {
                SendingRow(stringResource(R.string.chat_question_sending))
            } else if (question.multiSelect) {
                MultiSelectQuestionCard(
                    question = question,
                    header = question.header,
                    onSubmit = { picked -> actions.onAnswerTool(question, null, picked.joinToString(", ")) },
                    onSkip = { skipped = true },
                    haptics = haptics,
                )
            } else {
                QuestionCard(
                    question = question.question,
                    options = question.options.map { DsOption(it.label, it.description) },
                    onSubmit = { a ->
                        when (a) {
                            is QuestionAnswer.Choice -> actions.onAnswerTool(question, a.number - 1, null)
                            is QuestionAnswer.Custom -> actions.onAnswerTool(question, null, a.value)
                        }
                    },
                    onSkip = { skipped = true },
                    header = question.header,
                    haptics = haptics,
                )
            }
        }
        if (!skipped) FailureRow(local.toolAnswerError)
    }
}

/**
 * Pergunta de múltipla escolha: caixas de seleção (alvo de 48 dp) e "Outro" com texto livre.
 * A seleção sobrevive à rotação (índices unidos por vírgula).
 */
@Composable
internal fun MultiSelectQuestionCard(
    question: Question,
    header: String?,
    onSubmit: (List<String>) -> Unit,
    onSkip: () -> Unit,
    haptics: AiHaptics,
    modifier: Modifier = Modifier,
) {
    val c = AiTheme.colors
    val t = AiTheme.typography
    var selectedRaw by rememberSaveable { mutableStateOf("") }
    var otherOn by rememberSaveable { mutableStateOf(false) }
    var other by rememberSaveable { mutableStateOf("") }
    val selected = selectedRaw.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    val picked = question.options.filterIndexed { i, _ -> i in selected }.map { it.label } +
        listOfNotNull(other.trim().takeIf { otherOn && it.isNotEmpty() })
    val checkColors = CheckboxDefaults.colors(
        checkedColor = c.accent,
        uncheckedColor = c.lineStrong,
        checkmarkColor = c.accentFg,
    )

    SurfaceCard(modifier.fillMaxWidth(), borderColor = c.accent.copy(alpha = 0.35f)) {
        if (header != null) {
            Text(header.uppercase(), style = t.overline, color = c.accent)
            Spacer(Modifier.size(4.dp))
        }
        Text(question.question, style = t.heading, color = c.fg, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.chat_question_multi_hint), style = t.caption, color = c.fg3, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        question.options.forEachIndexed { i, opt ->
            val checked = i in selected
            CheckRow(
                checked = checked,
                onToggle = {
                    haptics.perform(HapticKind.Tick)
                    val next = if (checked) selected - i else selected + i
                    selectedRaw = next.sorted().joinToString(",")
                },
                label = opt.label,
                description = opt.description,
                colors = checkColors,
            )
        }
        CheckRow(
            checked = otherOn,
            onToggle = {
                haptics.perform(HapticKind.Tick)
                otherOn = !otherOn
            },
            label = stringResource(R.string.chat_question_other),
            description = null,
            colors = checkColors,
        )
        AnimatedVisibility(
            visible = otherOn,
            enter = expandVertically(AiTheme.motion.spring()) + fadeIn(AiTheme.motion.fade()),
            exit = shrinkVertically(AiTheme.motion.spring()) + fadeOut(AiTheme.motion.fade()),
        ) {
            AiTextInput(
                value = other,
                onValueChange = { other = it },
                placeholder = stringResource(R.string.chat_question_other_placeholder),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            AiButton(
                text = stringResource(R.string.chat_question_skip),
                onClick = onSkip,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Large,
            )
            AiButton(
                text = stringResource(R.string.chat_question_submit),
                onClick = {
                    haptics.perform(HapticKind.Confirm)
                    onSubmit(picked)
                },
                enabled = picked.isNotEmpty(),
                size = ButtonSize.Large,
                leadingIcon = Lucide.Check,
            )
        }
    }
}

@Composable
private fun CheckRow(
    checked: Boolean,
    onToggle: () -> Unit,
    label: String,
    description: String?,
    colors: androidx.compose.material3.CheckboxColors,
) {
    val c = AiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A linha inteira é o alvo; a caixa é só visual.
        Checkbox(checked = checked, onCheckedChange = null, colors = colors)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, style = AiTheme.typography.body, color = c.fg)
            if (!description.isNullOrBlank()) Text(description, style = AiTheme.typography.caption, color = c.fg3)
        }
    }
}

@Composable
internal fun SendingRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 32.dp)
            .padding(horizontal = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spinner(size = 12.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = AiTheme.typography.caption, color = AiTheme.colors.fg3)
    }
}

@Composable
internal fun FailureRow(message: String?, modifier: Modifier = Modifier) {
    // Mantém o último texto durante a animação de saída.
    var last by remember { mutableStateOf(message.orEmpty()) }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(AiTheme.motion.fade()) + expandVertically(AiTheme.motion.spring()),
        exit = fadeOut(AiTheme.motion.fade()) + shrinkVertically(AiTheme.motion.spring()),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .padding(horizontal = 4.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.TriangleAlert, contentDescription = null, tint = AiTheme.colors.danger, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.chat_answer_failed, last),
                style = AiTheme.typography.caption,
                color = AiTheme.colors.danger,
            )
        }
    }
}
