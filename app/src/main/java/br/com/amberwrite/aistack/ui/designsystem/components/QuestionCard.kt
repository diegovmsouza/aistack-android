package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Opção de uma pergunta do agente. */
data class QuestionOption(
    val label: String,
    val description: String? = null,
    val recommended: Boolean = false,
)

/** Resposta escolhida num [QuestionCard]. */
sealed interface QuestionAnswer {
    /** Texto no formato do desktop: “2. (Recomendado) Rótulo”. */
    val text: String

    data class Choice(val number: Int, val option: QuestionOption) : QuestionAnswer {
        override val text: String
            get() = "$number. ${if (option.recommended) "(Recomendado) " else ""}${option.label}"
    }

    data class Custom(val value: String) : QuestionAnswer {
        override val text: String get() = value
    }
}

/**
 * Cartão de pergunta do agente com opções numeradas, selo “Recomendado” e a opção “Outro”
 * com texto livre. Somente visual: a resposta sai por [onSubmit] e o descarte por [onSkip].
 * A seleção vive dentro do cartão (sobrevive a rotação).
 */
@Composable
fun QuestionCard(
    question: String,
    options: List<QuestionOption>,
    onSubmit: (QuestionAnswer) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    header: String? = null,
    haptics: AiHaptics = AiHaptics.None,
) {
    val c = AiTheme.colors
    val motion = AiTheme.motion
    val otherNumber = options.size + 1
    var selected by rememberSaveable { mutableIntStateOf(options.indexOfFirst { it.recommended }.let { if (it >= 0) it + 1 else 0 }) }
    var custom by rememberSaveable { mutableStateOf("") }
    val canSubmit = selected in 1..options.size || (selected == otherNumber && custom.isNotBlank())
    val submit = {
        if (canSubmit) {
            haptics.perform(HapticKind.Confirm)
            onSubmit(
                if (selected == otherNumber) QuestionAnswer.Custom(custom.trim())
                else QuestionAnswer.Choice(selected, options[selected - 1]),
            )
        }
    }
    val shape = AiTheme.shapes.xl
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.surface2.copy(alpha = 0.3f))
                .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(AiTheme.shapes.sm)
                    .background(c.accent.copy(alpha = 0.15f))
                    .border(1.dp, c.accent.copy(alpha = 0.25f), AiTheme.shapes.sm),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.MessageSquare, null, Modifier.size(14.dp), tint = c.accent) }
            Column(Modifier.weight(1f)) {
                if (header != null) {
                    Text(header.uppercase(), style = AiTheme.typography.overline.copy(fontFamily = AiTheme.typography.mono.fontFamily), color = c.accent)
                }
                Text(question, style = AiTheme.typography.heading, color = c.fg)
            }
            AiIconButton(Lucide.X, "Pular pergunta", onClick = onSkip, size = 32.dp, iconSize = 16.dp, tint = c.fg3)
        }
        HorizontalDivider(color = c.line, thickness = 1.dp)
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, opt ->
                OptionRow(
                    number = i + 1,
                    selected = selected == i + 1,
                    onSelect = {
                        haptics.perform(HapticKind.Tick)
                        selected = i + 1
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (opt.recommended) {
                            TagBadge("Recomendado", color = c.accent, background = c.accent.copy(alpha = 0.15f))
                        }
                        Text(opt.label, style = AiTheme.typography.label, color = c.fg, modifier = Modifier.weight(1f, fill = false))
                    }
                    if (opt.description != null) {
                        Text(opt.description, style = AiTheme.typography.caption, color = c.fg3)
                    }
                }
            }
            OptionRow(
                number = otherNumber,
                selected = selected == otherNumber,
                onSelect = {
                    haptics.perform(HapticKind.Tick)
                    selected = otherNumber
                },
            ) {
                Text("Outro (escreva sua resposta)", style = AiTheme.typography.label, color = c.fg2)
                AnimatedVisibility(
                    visible = selected == otherNumber,
                    enter = expandVertically(motion.spring()) + fadeIn(motion.fade()),
                    exit = shrinkVertically(motion.spring()) + fadeOut(motion.fade(120)),
                ) {
                    BasicTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        textStyle = AiTheme.typography.bodySmall.copy(color = c.fg),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(AiTheme.shapes.sm)
                                    .background(c.surface)
                                    .border(1.dp, c.line, AiTheme.shapes.sm)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                if (custom.isEmpty()) {
                                    Text("Digite sua resposta…", style = AiTheme.typography.bodySmall, color = c.fg3)
                                }
                                inner()
                            }
                        },
                    )
                }
            }
        }
        HorizontalDivider(color = c.line, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            AiButton("Pular", onClick = onSkip, variant = ButtonVariant.Ghost, size = ButtonSize.Small, haptic = null)
            AiButton("Responder", onClick = submit, enabled = canSubmit, size = ButtonSize.Small, trailingIcon = Lucide.ArrowUp, haptic = null)
        }
    }
}

@Composable
private fun OptionRow(
    number: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = AiTheme.colors
    val motion = AiTheme.motion
    val bg by animateColorAsState(if (selected) c.accent.copy(alpha = 0.10f) else c.surface2.copy(alpha = 0.4f), motion.fade(), label = "optBg")
    val border by animateColorAsState(if (selected) c.accent.copy(alpha = 0.6f) else c.line, motion.fade(), label = "optBorder")
    val shape = AiTheme.shapes.lg
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .clip(AiTheme.shapes.xs)
                .background(if (selected) c.accent else c.surface3)
                .border(1.dp, if (selected) c.accent else c.line, AiTheme.shapes.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = AiTheme.typography.monoSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = if (selected) c.accentFg else c.fg3,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@AiPreviews
@Composable
private fun QuestionCardPreview() {
    PreviewSurface {
        QuestionCard(
            header = "Banco de dados",
            question = "Qual banco usar para o cache local?",
            options = listOf(
                QuestionOption("Room", "SQLite com DAOs tipados e Flow.", recommended = true),
                QuestionOption("DataStore", "Chave-valor; bom para preferências."),
                QuestionOption("Arquivos JSON"),
            ),
            onSubmit = {},
            onSkip = {},
        )
    }
}
