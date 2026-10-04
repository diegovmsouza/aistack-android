package br.com.amberwrite.aistack.feature.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiTheme

/**
 * Campo de texto das telas de feature (o design system ainda não tem um campo próprio).
 * Usa as cores do [AiTheme] por cima do `OutlinedTextField` do Material 3.
 */
@Composable
fun AiTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 6,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    mono: Boolean = false
) {
    val c = AiTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = if (mono) AiTheme.typography.mono else AiTheme.typography.body,
        label = label?.let { { Text(it, style = AiTheme.typography.label) } },
        placeholder = placeholder?.let { { Text(it, style = AiTheme.typography.body, color = c.fg3) } },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        shape = AiTheme.shapes.md,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.fg,
            unfocusedTextColor = c.fg,
            disabledTextColor = c.fg3,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.line,
            cursorColor = c.accent,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            disabledContainerColor = c.surface,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.fg3
        )
    )
}

/** Título de seção em caixa alta. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AiTheme.typography.overline,
        color = AiTheme.colors.fg3,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** Linha com rótulo, descrição opcional e interruptor. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    val c = AiTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = AiTheme.typography.body, color = c.fg)
            if (description != null) {
                Text(description, style = AiTheme.typography.caption, color = c.fg3)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.accentFg,
                uncheckedTrackColor = c.surface2,
                uncheckedThumbColor = c.fg3,
                uncheckedBorderColor = c.line
            )
        )
    }
}

/** Diálogo de confirmação simples (ações destrutivas ou que substituem estado). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancelar",
    destructive: Boolean = false
) {
    val c = AiTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.fg,
        textContentColor = c.fg2,
        title = { Text(title, style = AiTheme.typography.heading) },
        text = { Text(text, style = AiTheme.typography.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) c.danger else c.accent, style = AiTheme.typography.label)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = c.fg2, style = AiTheme.typography.label)
            }
        }
    )
}
