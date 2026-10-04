package br.com.amberwrite.aistack.service

import br.com.amberwrite.aistack.core.rpc.EngineEvent
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.ToolQuestion

/*
 * Modelos semânticos das notificações. A lógica pura ([NotificationMapper],
 * [NotificationCoordinator], [LiveTurnTracker], [ReplyRouting]) produz estes valores; só o
 * [AndroidNotifier] os transforma em texto (strings_pending.xml) e em `Notification`.
 * Assim o mapeamento evento → notificação é testável na JVM, sem o framework Android.
 */

/** Ações possíveis numa notificação de pendência. */
enum class NoticeAction {
    /** `answerPermission` allow. */
    Allow,

    /** `answerPermission` deny. */
    Deny,

    /** RemoteInput: vira resposta da pergunta, negação explicada ou mensagem ([ReplyRouting]). */
    Reply,

    /** Dispensar uma pergunta (`deny` com [br.com.amberwrite.aistack.data.model.PermissionDecision.DISMISS_QUESTION_MESSAGE]). */
    Dismiss,
}

/** Pedido de permissão ou pergunta (`AskUserQuestion`) de uma conversa. */
data class PermissionNotice(
    val conversationId: String,
    val requestId: String,
    val conversationTitle: String,
    val tool: String,
    val isQuestion: Boolean,
    /** Texto principal: a pergunta (perguntas) ou o `inputPreview` (permissões). */
    val headline: String?,
    /** Motivo do motor (`reason`), quando houver. */
    val reason: String?,
    /** Rótulos das opções (só perguntas de uma única questão), viram respostas rápidas. */
    val choices: List<String>,
    /** Quantas perguntas o pedido tem (0 para permissões). */
    val questionCount: Int,
    /** Epoch ms do pedido. */
    val since: Long?,
    val actions: List<NoticeAction>,
    /** Falha da última tentativa de resposta (a notificação é repostada com ela). */
    val error: String? = null,
    /** Resposta a caminho: sem ações, texto "Enviando…". */
    val sending: Boolean = false,
    /** Já alertou antes (repostagem): posta sem som/vibração. */
    val silent: Boolean = false,
)

/** Pergunta de ferramenta (`ask_question`/`AskFollowupQuestion`): a resposta vira mensagem. */
data class ToolQuestionNotice(
    val conversationId: String,
    val conversationTitle: String,
    val question: ToolQuestion,
    val choices: List<String>,
    val error: String? = null,
    val sending: Boolean = false,
) {
    val headline: String get() = question.questions.first().question
    val questionCount: Int get() = question.questions.size
}

/** Uma linha do resumo do grupo de pendências. */
data class PendingSummaryLine(val conversationId: String, val title: String, val count: Int)

/** Resumo do grupo de pendências (abre `aistack://pending`). */
data class PendingSummary(val total: Int, val lines: List<PendingSummaryLine>)

/** Turno em andamento (Live Update). */
data class LiveNotice(
    val conversationId: String,
    val title: String,
    /** Início do turno (epoch ms), base do cronômetro. */
    val startedAt: Long,
    /** Ferramenta atual (do agente principal ou de um sub-agente). */
    val tool: String?,
    /** Sub-agentes ativos agora. */
    val subagents: Int,
    /** A conversa espera uma permissão ou resposta. */
    val waiting: Boolean,
    /** Espera de limite de uso (segundos), quando houver. */
    val rateLimitSeconds: Long?,
    /** Recebemos eventos de ferramenta (assinatura `full`); sem isso só há cronômetro. */
    val detailed: Boolean,
)

/** Turno concluído ou com erro. */
data class DoneNotice(
    val conversationId: String,
    val title: String,
    val isError: Boolean,
    /** Mensagem de erro do host (só em erro). */
    val message: String?,
    val durationMs: Long?,
)

/** Conversão pura de eventos/pedidos em modelos de notificação. */
object NotificationMapper {
    const val DEFAULT_TITLE = "Conversa"

    fun permission(
        request: PermissionRequest,
        conversationTitle: String,
        error: String? = null,
        sending: Boolean = false,
        silent: Boolean = error != null || sending,
    ): PermissionNotice {
        val isQuestion = request.isAskUserQuestion
        val questions = if (isQuestion) request.questions else emptyList()
        val headline = when {
            isQuestion -> questions.firstOrNull()?.question
            !request.inputPreview.isNullOrBlank() -> request.inputPreview
            else -> null
        }
        val single = questions.singleOrNull()
        val choices = if (single != null && !single.multiSelect) single.options.map { it.label }.filter { it.isNotBlank() } else emptyList()
        val actions = when {
            sending -> emptyList()
            isQuestion -> listOf(NoticeAction.Reply, NoticeAction.Dismiss)
            else -> listOf(NoticeAction.Allow, NoticeAction.Deny, NoticeAction.Reply)
        }
        return PermissionNotice(
            conversationId = request.conversationId,
            requestId = request.requestId,
            conversationTitle = conversationTitle.ifBlank { DEFAULT_TITLE },
            tool = request.tool,
            isQuestion = isQuestion,
            headline = headline?.trim()?.takeIf { it.isNotEmpty() },
            reason = request.reason?.trim()?.takeIf { it.isNotEmpty() },
            choices = choices,
            questionCount = questions.size,
            since = request.since,
            actions = actions,
            error = error,
            sending = sending,
            silent = silent,
        )
    }

    fun toolQuestion(
        conversationId: String,
        conversationTitle: String,
        question: ToolQuestion,
        error: String? = null,
        sending: Boolean = false,
    ): ToolQuestionNotice {
        val single = question.questions.singleOrNull()
        val choices = if (single != null && !single.multiSelect) single.options.map { it.label }.filter { it.isNotBlank() } else emptyList()
        return ToolQuestionNotice(conversationId, conversationTitle.ifBlank { DEFAULT_TITLE }, question, choices, error, sending)
    }

    /**
     * Fim de turno → [DoneNotice]. `null` para eventos que não encerram turno e para
     * `turnError` do tipo `interrupted` (o próprio usuário parou).
     */
    fun done(conversationId: String, title: String, event: EngineEvent): DoneNotice? = when (event) {
        is EngineEvent.TurnComplete -> DoneNotice(conversationId, title, isError = false, message = null, durationMs = event.durationMs)
        is EngineEvent.TurnError ->
            if (event.kind == "interrupted") null
            else DoneNotice(conversationId, title, isError = true, message = event.message.trim().ifEmpty { null }, durationMs = null)
        is EngineEvent.Exited ->
            if (event.code == null || event.code == 0L) null
            else DoneNotice(conversationId, title, isError = true, message = event.detail.trim().ifEmpty { null }, durationMs = null)
        else -> null
    }

    /** Resumo do grupo; `null` quando não há nada pendente. */
    fun summary(permissions: Collection<PermissionNotice>, toolQuestions: Collection<ToolQuestionNotice>): PendingSummary? {
        val byConv = LinkedHashMap<String, PendingSummaryLine>()
        fun add(id: String, title: String) {
            val cur = byConv[id]
            byConv[id] = cur?.copy(count = cur.count + 1) ?: PendingSummaryLine(id, title, 1)
        }
        permissions.forEach { add(it.conversationId, it.conversationTitle) }
        toolQuestions.forEach { add(it.conversationId, it.conversationTitle) }
        if (byConv.isEmpty()) return null
        return PendingSummary(byConv.values.sumOf { it.count }, byConv.values.toList())
    }

    /**
     * Nome curto de ferramenta para a notificação: `mcp__servidor__ferramenta` vira
     * `ferramenta`; corta em [max] caracteres com reticências.
     */
    fun shortTool(name: String?, max: Int = 24): String? {
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val base = if (n.startsWith("mcp__")) n.substringAfterLast("__").ifEmpty { n } else n
        return if (base.length <= max) base else base.take((max - 1).coerceAtLeast(1)) + "…"
    }

    /** Duração legível: "45 s", "3 min 05 s", "1 h 02 min". */
    fun formatDuration(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 500) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "%d h %02d min".format(h, m)
            m > 0 -> "%d min %02d s".format(m, s)
            else -> "$s s"
        }
    }

    fun live(turn: LiveTurn, title: String, waiting: Boolean): LiveNotice = LiveNotice(
        conversationId = turn.conversationId,
        title = title.ifBlank { DEFAULT_TITLE },
        startedAt = turn.startedAt,
        tool = turn.tool,
        subagents = turn.activeSubagents.size,
        waiting = waiting,
        rateLimitSeconds = turn.rateLimitSeconds,
        detailed = turn.detailed,
    )
}
