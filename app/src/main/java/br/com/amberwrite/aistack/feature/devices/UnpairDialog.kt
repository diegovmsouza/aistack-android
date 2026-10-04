package br.com.amberwrite.aistack.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide

/**
 * Confirmação forte do desparear: o botão só habilita depois que o usuário digita
 * [DevicesLogic.CONFIRM_WORD]. Usado pela tela de aparelhos e pelas configurações.
 */
@Composable
fun UnpairConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    working: Boolean = false
) {
    val c = AiTheme.colors
    val haptics = rememberAiHaptics()
    var typed by rememberSaveable { mutableStateOf("") }
    val matches = DevicesLogic.confirmMatches(typed)

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !working, dismissOnBackPress = !working),
        containerColor = c.surface,
        titleContentColor = c.fg,
        textContentColor = c.fg2,
        icon = { Icon(Lucide.Unplug, contentDescription = null, tint = c.danger) },
        title = { Text(stringResource(R.string.unpair_title), style = AiTheme.typography.heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.unpair_body), style = AiTheme.typography.body)
                AiTextInput(
                    value = typed,
                    onValueChange = { typed = it },
                    label = stringResource(R.string.unpair_type_hint, DevicesLogic.CONFIRM_WORD),
                    placeholder = DevicesLogic.CONFIRM_WORD,
                    singleLine = true,
                    enabled = !working,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AiButton(
                text = stringResource(if (working) R.string.unpair_working else R.string.unpair_confirm),
                onClick = onConfirm,
                variant = ButtonVariant.Danger,
                enabled = matches && !working,
                loading = working,
                haptic = HapticKind.Confirm,
                haptics = haptics
            )
        },
        dismissButton = {
            AiButton(
                text = stringResource(R.string.unpair_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                enabled = !working
            )
        }
    )
}
