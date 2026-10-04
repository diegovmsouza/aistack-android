package br.com.amberwrite.aistack.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import br.com.amberwrite.aistack.AiStackApplication
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.repo.ActionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ações das notificações de pendência: Permitir, Negar, Dispensar e Responder (RemoteInput).
 *
 * - A notificação é repostada na hora com "Enviando…" (sem ações), para o toque ter retorno.
 * - Só some depois do `rpcResult` ok (ou de "pedido desconhecido", que significa já resolvido);
 *   em falha é repostada com o erro e as ações de volta.
 * - Responder segue [ReplyRouting]: resposta da pergunta (`allow` com `answers`), negação
 *   explicada (`deny` com mensagem) ou, para pergunta de ferramenta, `sendMessage`.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER = "br.com.amberwrite.aistack.action.ANSWER_PERMISSION"
        const val ACTION_TOOL_REPLY = "br.com.amberwrite.aistack.action.TOOL_QUESTION_REPLY"

        const val EXTRA_CONVERSATION = "conversationId"
        const val EXTRA_REQUEST = "requestId"
        const val EXTRA_DECISION = "decision"
        const val EXTRA_TOOL = "tool"
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_TITLE = "title"
        const val EXTRA_REASON = "reason"
        const val EXTRA_IS_QUESTION = "isQuestion"
        const val EXTRA_CHOICES = "choices"
        const val EXTRA_QUESTION_COUNT = "questionCount"
        const val EXTRA_SINCE = "since"
        const val EXTRA_TOOL_QUESTION = "toolQuestion"

        const val DECISION_ALLOW = "allow"
        const val DECISION_DENY = "deny"
        const val DECISION_REPLY = "reply"
        const val DECISION_DISMISS = "dismiss"

        /** Prazo total (o sistema mata receivers assíncronos perto de 30 s). */
        private const val TOTAL_TIMEOUT_MS = 25_000L

        /** Intent explícita de uma ação da notificação de permissão/pergunta. */
        fun permissionIntent(context: Context, notice: PermissionNotice, decision: String): Intent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_ANSWER
                putExtra(EXTRA_CONVERSATION, notice.conversationId)
                putExtra(EXTRA_REQUEST, notice.requestId)
                putExtra(EXTRA_DECISION, decision)
                putExtra(EXTRA_TOOL, notice.tool)
                putExtra(EXTRA_PREVIEW, notice.headline)
                putExtra(EXTRA_TITLE, notice.conversationTitle)
                putExtra(EXTRA_REASON, notice.reason)
                putExtra(EXTRA_IS_QUESTION, notice.isQuestion)
                putStringArrayListExtra(EXTRA_CHOICES, ArrayList(notice.choices))
                putExtra(EXTRA_QUESTION_COUNT, notice.questionCount)
                putExtra(EXTRA_SINCE, notice.since ?: -1L)
            }

        /** Intent explícita do "Responder" de uma pergunta de ferramenta. */
        fun toolQuestionIntent(context: Context, notice: ToolQuestionNotice): Intent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_TOOL_REPLY
                putExtra(EXTRA_CONVERSATION, notice.conversationId)
                putExtra(EXTRA_TITLE, notice.conversationTitle)
                putExtra(EXTRA_TOOL_QUESTION, ToolQuestionJson.encode(notice.question))
            }

        /** Reconstrói o aviso a partir dos extras (para repostar com "Enviando…" ou com o erro). */
        internal fun noticeFrom(intent: Intent): PermissionNotice? {
            val convId = intent.getStringExtra(EXTRA_CONVERSATION) ?: return null
            val requestId = intent.getStringExtra(EXTRA_REQUEST) ?: return null
            val isQuestion = intent.getBooleanExtra(EXTRA_IS_QUESTION, false)
            return PermissionNotice(
                conversationId = convId,
                requestId = requestId,
                conversationTitle = intent.getStringExtra(EXTRA_TITLE) ?: NotificationMapper.DEFAULT_TITLE,
                tool = intent.getStringExtra(EXTRA_TOOL) ?: "ferramenta",
                isQuestion = isQuestion,
                headline = intent.getStringExtra(EXTRA_PREVIEW),
                reason = intent.getStringExtra(EXTRA_REASON),
                choices = intent.getStringArrayListExtra(EXTRA_CHOICES).orEmpty(),
                questionCount = intent.getIntExtra(EXTRA_QUESTION_COUNT, 0),
                since = intent.getLongExtra(EXTRA_SINCE, -1L).takeIf { it > 0 },
                actions = if (isQuestion) listOf(NoticeAction.Reply, NoticeAction.Dismiss)
                else listOf(NoticeAction.Allow, NoticeAction.Deny, NoticeAction.Reply),
                silent = true,
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> onAnswer(context, intent)
            ACTION_TOOL_REPLY -> onToolReply(context, intent)
        }
    }

    private fun replyText(intent: Intent): String? =
        RemoteInput.getResultsFromIntent(intent)?.getCharSequence(AndroidNotifier.KEY_REPLY)?.toString()

    private fun onAnswer(context: Context, intent: Intent) {
        val base = noticeFrom(intent) ?: return
        val decisionKind = intent.getStringExtra(EXTRA_DECISION) ?: return
        val reply = replyText(intent)
        val app = context.applicationContext
        val container = AiStackApplication.container(context)
        val notifier = container.notifier
        val convId = base.conversationId
        val requestId = base.requestId

        if (decisionKind == DECISION_REPLY && reply.isNullOrBlank()) {
            notifier.showPermission(base.copy(error = app.getString(R.string.notif_error_empty)))
            return
        }
        notifier.showPermission(base.copy(sending = true, actions = emptyList()))

        val pendingResult = goAsync()
        container.appScope.launch {
            try {
                val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                    if (!container.awaitOnline()) return@withTimeoutOrNull ActionResult.Failed(app.getString(R.string.notif_error_offline))
                    val decision: PermissionDecision = when (decisionKind) {
                        DECISION_ALLOW -> PermissionDecision.Allow()
                        DECISION_DENY -> PermissionDecision.Deny()
                        DECISION_DISMISS -> PermissionDecision.Deny(PermissionDecision.DISMISS_QUESTION_MESSAGE)
                        else -> {
                            val text = reply.orEmpty()
                            // As perguntas vêm do pedido completo; sem ele (lista não carregada) recarrega uma vez.
                            val request = container.pendingRepo.find(convId, requestId)
                                ?: run { container.pendingRepo.reload(); container.pendingRepo.find(convId, requestId) }
                            when (val route = if (request != null) ReplyRouting.forPermission(request, text)
                            else ReplyRoute.Answer(PermissionDecision.Deny(text.trim()))) {
                                is ReplyRoute.Answer -> route.decision
                                is ReplyRoute.Message -> PermissionDecision.Deny(route.text)
                                is ReplyRoute.Rejected -> return@withTimeoutOrNull ActionResult.Failed(app.getString(R.string.notif_error_empty))
                            }
                        }
                    }
                    container.pendingRepo.answer(convId, requestId, decision)
                } ?: ActionResult.Failed(app.getString(R.string.notif_error_timeout))
                when (result) {
                    ActionResult.Ok, ActionResult.AlreadyResolved -> notifier.cancelPermission(convId, requestId)
                    is ActionResult.Failed -> notifier.showPermission(base.copy(error = result.message))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notifier.showPermission(base.copy(error = e.message ?: e.javaClass.simpleName))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun onToolReply(context: Context, intent: Intent) {
        val convId = intent.getStringExtra(EXTRA_CONVERSATION) ?: return
        val question = ToolQuestionJson.decode(intent.getStringExtra(EXTRA_TOOL_QUESTION)) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: NotificationMapper.DEFAULT_TITLE
        val app = context.applicationContext
        val container = AiStackApplication.container(context)
        val notifier = container.notifier
        val base = NotificationMapper.toolQuestion(convId, title, question)
        val text = replyText(intent).orEmpty()

        val busy = container.pendingRepo.items.value.any { it.conversationId == convId && it.busy }
        val route = ReplyRouting.forToolQuestion(question, text, busy)
        val message = when (route) {
            is ReplyRoute.Message -> route.text
            is ReplyRoute.Rejected -> {
                val err = if (route.reason == ReplyRoute.Reason.Busy) R.string.notif_error_busy else R.string.notif_error_empty
                notifier.showToolQuestion(base.copy(error = app.getString(err)))
                return
            }
            is ReplyRoute.Answer -> return // não acontece para perguntas de ferramenta
        }
        notifier.showToolQuestion(base.copy(sending = true))

        val pendingResult = goAsync()
        container.appScope.launch {
            try {
                // Resultado: ActionResult.Ok ou Failed(motivo); null = prazo esgotado.
                val result: ActionResult = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                    if (!container.awaitOnline()) return@withTimeoutOrNull ActionResult.Failed(app.getString(R.string.notif_error_offline))
                    try {
                        container.chatRepo.send(convId, message)
                        ActionResult.Ok
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ActionResult.Failed(e.message ?: e.javaClass.simpleName)
                    }
                } ?: ActionResult.Failed(app.getString(R.string.notif_error_timeout))
                when (result) {
                    is ActionResult.Failed -> notifier.showToolQuestion(base.copy(error = result.message))
                    else -> notifier.cancelToolQuestion(convId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
