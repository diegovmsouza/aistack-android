package br.com.amberwrite.aistack.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.amberwrite.aistack.AiStackApplication
import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.repo.ActionResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Botões "Permitir"/"Negar" das notificações de permissão. A notificação só some depois do
 * `rpcResult` ok (ou de "pedido desconhecido", que significa já resolvido); em falha ela é
 * repostada com o erro.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER = "br.com.amberwrite.aistack.action.ANSWER_PERMISSION"
        const val EXTRA_CONVERSATION = "conversationId"
        const val EXTRA_REQUEST = "requestId"
        const val EXTRA_DECISION = "decision"
        const val EXTRA_TOOL = "tool"
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_TITLE = "title"
        const val DECISION_ALLOW = "allow"
        const val DECISION_DENY = "deny"

        /** Prazo total (o sistema mata receivers assíncronos perto de 30 s). */
        private const val TOTAL_TIMEOUT_MS = 25_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ANSWER) return
        val convId = intent.getStringExtra(EXTRA_CONVERSATION) ?: return
        val requestId = intent.getStringExtra(EXTRA_REQUEST) ?: return
        val allow = intent.getStringExtra(EXTRA_DECISION) == DECISION_ALLOW
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Conversa"
        val container = AiStackApplication.container(context)
        val pendingResult = goAsync()
        container.appScope.launch {
            try {
                val request = container.pendingRepo.find(convId, requestId) ?: PermissionRequest(
                    conversationId = convId,
                    requestId = requestId,
                    tool = intent.getStringExtra(EXTRA_TOOL) ?: "ferramenta",
                    toolUseId = null,
                    input = null,
                    inputPreview = intent.getStringExtra(EXTRA_PREVIEW),
                    truncated = false,
                    reason = null,
                    since = null,
                    suggestions = null
                )
                val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                    if (!container.awaitOnline()) {
                        ActionResult.Failed("sem conexão com o desktop")
                    } else {
                        val decision = if (allow) PermissionDecision.Allow() else PermissionDecision.Deny()
                        container.pendingRepo.answer(convId, requestId, decision)
                    }
                } ?: ActionResult.Failed("tempo esgotado")
                when (result) {
                    ActionResult.Ok, ActionResult.AlreadyResolved ->
                        container.notifier.cancelPermission(convId, requestId)
                    is ActionResult.Failed ->
                        container.notifier.showPermission(request, title, result.message)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
