package br.com.amberwrite.aistack.ui.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Tipos de retorno tátil usados pelo design system. */
enum class HapticKind { Confirm, Reject, Tick, LongPress }

/**
 * Retorno tátil injetável. Componentes que confirmam/negam ações (PermissionCard,
 * QuestionCard, botões) recebem um [AiHaptics] por parâmetro; passe [AiHaptics.None]
 * para silenciar (ex.: quando o usuário desligou vibração nas preferências do app).
 */
fun interface AiHaptics {
    fun perform(kind: HapticKind)

    companion object {
        val None = AiHaptics { }
    }
}

/** Implementação padrão baseada em View.performHapticFeedback (CONFIRM/REJECT no Android 11+). */
@Composable
fun rememberAiHaptics(): AiHaptics {
    val view = LocalView.current
    return remember(view) { ViewHaptics(view) }
}

private class ViewHaptics(private val view: View) : AiHaptics {
    override fun perform(kind: HapticKind) {
        val constant = when (kind) {
            HapticKind.Confirm ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
            HapticKind.Reject ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            HapticKind.Tick -> HapticFeedbackConstants.CLOCK_TICK
            HapticKind.LongPress -> HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}
