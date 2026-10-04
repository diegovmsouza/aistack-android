package br.com.amberwrite.aistack.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide

/**
 * Explica por que o app quer notificar antes de abrir o pedido do sistema (Android 13+).
 * Só aparece depois do pareamento: antes dele não há pendências nem turnos para avisar.
 */
@Composable
fun NotificationRationaleDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    val c = AiTheme.colors
    val haptics = rememberAiHaptics()
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = c.surface,
        titleContentColor = c.fg,
        textContentColor = c.fg2,
        icon = { Icon(Lucide.Bell, contentDescription = null, tint = c.accent) },
        title = { Text(stringResource(R.string.notif_rationale_title), style = AiTheme.typography.heading) },
        text = { Text(stringResource(R.string.notif_rationale_body), style = AiTheme.typography.body) },
        confirmButton = {
            AiButton(
                text = stringResource(R.string.notif_rationale_allow),
                onClick = onAllow,
                variant = ButtonVariant.Primary,
                haptic = HapticKind.Confirm,
                haptics = haptics
            )
        },
        dismissButton = {
            AiButton(
                text = stringResource(R.string.notif_rationale_later),
                onClick = onLater,
                variant = ButtonVariant.Ghost,
                haptics = haptics
            )
        }
    )
}
