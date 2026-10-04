package br.com.amberwrite.aistack.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import br.com.amberwrite.aistack.MainActivity
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.core.relay.ConnectionState

/**
 * [Notifier] real. Cada tipo usa um id fixo e uma `tag` com a chave (conversa/pedido), então
 * os ids são sempre válidos (não zero) e não colidem.
 *
 * Agrupamento: o Android não aninha grupos, então há um grupo de pendências (filhos ordenados
 * por conversa, com a conversa no subtítulo, e um resumo que abre `aistack://pending`) e um
 * grupo de concluídos. Cada filho abre `aistack://chat/{id}`.
 */
class AndroidNotifier(context: Context) : Notifier {
    private val ctx = context.applicationContext
    private val nm = NotificationManagerCompat.from(ctx)
    private val platformNm: NotificationManager? = ctx.getSystemService(NotificationManager::class.java)

    companion object {
        const val ID_CONNECTION = 1001
        const val ID_PERMISSION = 2001
        const val ID_DONE = 2002

        /** Notificação única de progresso da versão anterior (substituída pelos Live Updates). */
        const val ID_PROGRESS = 2003
        const val ID_PENDING_SUMMARY = 2004
        const val ID_DONE_SUMMARY = 2005
        const val ID_LIVE = 2006
        const val ID_TOOL_QUESTION = 2007

        const val GROUP_PENDING = "aistack_group_pending"
        const val GROUP_DONE = "aistack_group_done"

        /** Chave do texto digitado no RemoteInput. */
        const val KEY_REPLY = "reply"

        /** Extra do Live Update que pede a promoção (sem constante pública no SDK 36). */
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

        private const val PENDING_REQUEST_CODE = 0x7E4D

        fun permissionTag(conversationId: String, requestId: String) = "perm:$conversationId:$requestId"
        fun doneTag(conversationId: String) = "done:$conversationId"
        fun liveTag(conversationId: String) = "live:$conversationId"
        fun toolQuestionTag(conversationId: String) = "tq:$conversationId"

        /** Intent que abre o app direto na conversa (deep link `aistack://chat/{id}`). */
        fun chatIntent(context: Context, conversationId: String?): PendingIntent {
            val uri = if (conversationId != null) Uri.parse("aistack://chat/${Uri.encode(conversationId)}") else null
            val intent = Intent(context, MainActivity::class.java).apply {
                action = if (uri != null) Intent.ACTION_VIEW else Intent.ACTION_MAIN
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val code = conversationId?.hashCode() ?: 0
            return PendingIntent.getActivity(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Intent que abre a caixa de pendências (deep link `aistack://pending`). */
        fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("aistack://pending")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context, PENDING_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    init {
        // A notificação agregada de progresso da versão anterior não é mais atualizada.
        runCatching { nm.cancel(ID_PROGRESS) }
    }

    private fun canPost(): Boolean {
        if (!nm.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun post(tag: String?, id: Int, n: Notification) {
        if (!canPost()) return
        try {
            nm.notify(tag, id, n)
        } catch (_: SecurityException) {
            // Permissão revogada entre a checagem e o envio: ignora.
        } catch (_: IllegalArgumentException) {
            // Notificação rejeitada pelo sistema (ex.: estilo inválido num OEM): ignora.
        }
    }

    // ---------------------------------------------------------------- Pendências

    override fun showPermission(notice: PermissionNotice) {
        val title = if (notice.isQuestion) ctx.getString(R.string.notif_question_title)
        else ctx.getString(R.string.notif_permission_title, notice.tool)
        val headline = notice.headline ?: if (notice.isQuestion) ctx.getString(R.string.notif_question_fallback)
        else ctx.getString(R.string.notif_permission_fallback, notice.tool)
        val status = statusLine(notice.error, notice.sending)
        val body = buildString {
            status?.let { append(it).append('\n') }
            append(headline)
            notice.reason?.let { append("\n\n").append(ctx.getString(R.string.notif_reason, it)) }
            if (notice.questionCount > 1) {
                val more = notice.questionCount - 1
                append("\n").append(ctx.resources.getQuantityString(R.plurals.notif_question_more, more, more))
            }
            if (notice.choices.isNotEmpty()) {
                append("\n")
                notice.choices.forEachIndexed { i, c -> append("\n").append(i + 1).append(". ").append(c) }
            }
        }
        val tag = permissionTag(notice.conversationId, notice.requestId)
        val b = pendingBuilder(notice.conversationId, notice.conversationTitle, notice.since, notice.silent)
            .setContentTitle(title)
            .setContentText(status ?: headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        val replyLabel = when {
            !notice.isQuestion -> ctx.getString(R.string.notif_reply_label_permission)
            notice.questionCount > 1 -> ctx.getString(R.string.notif_reply_label_questions)
            else -> ctx.getString(R.string.notif_reply_label_question)
        }
        for (action in notice.actions) {
            val decision = when (action) {
                NoticeAction.Allow -> NotificationActionReceiver.DECISION_ALLOW
                NoticeAction.Deny -> NotificationActionReceiver.DECISION_DENY
                NoticeAction.Reply -> NotificationActionReceiver.DECISION_REPLY
                NoticeAction.Dismiss -> NotificationActionReceiver.DECISION_DISMISS
            }
            val intent = NotificationActionReceiver.permissionIntent(ctx, notice, decision)
            val code = (tag + decision).hashCode()
            when (action) {
                NoticeAction.Allow -> b.addAction(
                    NotificationCompat.Action.Builder(R.drawable.ic_lucide_check, ctx.getString(R.string.notif_action_allow), broadcast(code, intent, mutable = false))
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_NONE)
                        .setShowsUserInterface(false)
                        .build()
                )
                NoticeAction.Deny -> b.addAction(
                    NotificationCompat.Action.Builder(R.drawable.ic_lucide_x, ctx.getString(R.string.notif_action_deny), broadcast(code, intent, mutable = false))
                        .setShowsUserInterface(false)
                        .build()
                )
                NoticeAction.Dismiss -> b.addAction(
                    NotificationCompat.Action.Builder(R.drawable.ic_lucide_x, ctx.getString(R.string.notif_action_dismiss), broadcast(code, intent, mutable = false))
                        .setShowsUserInterface(false)
                        .build()
                )
                NoticeAction.Reply -> b.addAction(replyAction(code, intent, replyLabel, notice.choices))
            }
        }
        post(tag, ID_PERMISSION, b.build())
    }

    override fun cancelPermission(conversationId: String, requestId: String) {
        nm.cancel(permissionTag(conversationId, requestId), ID_PERMISSION)
    }

    override fun showToolQuestion(notice: ToolQuestionNotice) {
        val status = statusLine(notice.error, notice.sending)
        val first = notice.question.questions.first()
        val body = buildString {
            status?.let { append(it).append('\n') }
            append(first.question)
            first.options.forEachIndexed { i, o -> append("\n").append(i + 1).append(". ").append(o.label) }
            if (notice.questionCount > 1) {
                val more = notice.questionCount - 1
                append("\n").append(ctx.resources.getQuantityString(R.plurals.notif_question_more, more, more))
            }
            append("\n\n").append(ctx.getString(R.string.notif_tool_question_hint))
        }
        val tag = toolQuestionTag(notice.conversationId)
        val b = pendingBuilder(notice.conversationId, notice.conversationTitle, null, notice.error != null || notice.sending)
            .setContentTitle(ctx.getString(R.string.notif_question_title))
            .setContentText(status ?: notice.headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        if (!notice.sending) {
            val intent = NotificationActionReceiver.toolQuestionIntent(ctx, notice)
            val label = if (notice.questionCount > 1) ctx.getString(R.string.notif_reply_label_questions)
            else ctx.getString(R.string.notif_reply_label_question)
            b.addAction(replyAction((tag + "reply").hashCode(), intent, label, notice.choices))
        }
        post(tag, ID_TOOL_QUESTION, b.build())
    }

    override fun cancelToolQuestion(conversationId: String) {
        nm.cancel(toolQuestionTag(conversationId), ID_TOOL_QUESTION)
    }

    override fun showPendingSummary(summary: PendingSummary?) {
        if (summary == null) {
            nm.cancel(ID_PENDING_SUMMARY)
            return
        }
        val title = ctx.resources.getQuantityString(R.plurals.notif_summary_title, summary.total, summary.total)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        summary.lines.take(6).forEach { line ->
            style.addLine(ctx.resources.getQuantityString(R.plurals.notif_summary_line, line.count, line.title, line.count))
        }
        val n = NotificationCompat.Builder(ctx, NotificationChannels.PENDING)
            .setSmallIcon(R.drawable.ic_stat_aistack)
            .setContentTitle(title)
            .setContentText(summary.lines.joinToString(", ") { it.title })
            .setStyle(style)
            .setNumber(summary.total)
            .setGroup(GROUP_PENDING)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent(ctx))
            .build()
        post(null, ID_PENDING_SUMMARY, n)
    }

    private fun pendingBuilder(convId: String, convTitle: String, since: Long?, silent: Boolean): NotificationCompat.Builder =
        NotificationCompat.Builder(ctx, NotificationChannels.PENDING)
            .setSmallIcon(R.drawable.ic_stat_aistack)
            .setSubText(convTitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_PENDING)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSortKey("$convId:${since ?: 0}")
            .setWhen(since ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            .setAutoCancel(false)
            .setContentIntent(chatIntent(ctx, convId))

    private fun statusLine(error: String?, sending: Boolean): String? = when {
        sending -> ctx.getString(R.string.notif_sending)
        error != null -> ctx.getString(R.string.notif_failed, error)
        else -> null
    }

    private fun broadcast(code: Int, intent: Intent, mutable: Boolean): PendingIntent {
        // RemoteInput exige PendingIntent mutável (o sistema preenche o texto); a intent é
        // explícita (componente fixo), então isso é permitido e seguro.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, code, intent, flags)
    }

    private fun replyAction(code: Int, intent: Intent, label: String, choices: List<String>): NotificationCompat.Action {
        val input = RemoteInput.Builder(KEY_REPLY)
            .setLabel(label)
            .apply { if (choices.isNotEmpty()) setChoices(choices.take(5).toTypedArray()) }
            .setAllowFreeFormInput(true)
            .build()
        return NotificationCompat.Action.Builder(
            R.drawable.ic_lucide_message_square,
            ctx.getString(R.string.notif_action_reply),
            broadcast(code, intent, mutable = true)
        )
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    // ---------------------------------------------------------------- Live Update

    override fun showLive(notice: LiveNotice) {
        val text = liveText(notice)
        val short = when {
            notice.waiting -> ctx.getString(R.string.notif_live_short_waiting)
            else -> NotificationMapper.shortTool(notice.tool, max = 10)
        }
        val n = if (Build.VERSION.SDK_INT >= 36) liveNotification36(notice, text, short) else liveNotificationCompat(notice, text)
        post(liveTag(notice.conversationId), ID_LIVE, n)
    }

    private fun liveText(n: LiveNotice): String {
        val main = when {
            n.waiting -> ctx.getString(R.string.notif_live_waiting)
            n.rateLimitSeconds != null ->
                ctx.getString(R.string.notif_live_rate_limit, NotificationMapper.formatDuration(n.rateLimitSeconds * 1000))
            n.tool != null -> ctx.getString(R.string.notif_live_tool, NotificationMapper.shortTool(n.tool))
            else -> ctx.getString(R.string.notif_live_working)
        }
        if (n.subagents <= 0) return main
        return main + " · " + ctx.resources.getQuantityString(R.plurals.notif_live_subagents, n.subagents, n.subagents)
    }

    @RequiresApi(36)
    private fun liveNotification36(n: LiveNotice, text: String, short: String?): Notification {
        val style = Notification.ProgressStyle()
            .setStyledByProgress(false)
            .setProgressIndeterminate(true)
        val b = Notification.Builder(ctx, NotificationChannels.PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_aistack)
            .setContentTitle(n.title)
            .setContentText(text)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setWhen(n.startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setContentIntent(chatIntent(ctx, n.conversationId))
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        if (short != null) b.setShortCriticalText(short)
        return b.build()
    }

    private fun liveNotificationCompat(n: LiveNotice, text: String): Notification =
        NotificationCompat.Builder(ctx, NotificationChannels.PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_aistack)
            .setContentTitle(n.title)
            .setContentText(text)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setWhen(n.startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setRequestPromotedOngoing(true)
            .setContentIntent(chatIntent(ctx, n.conversationId))
            .build()

    override fun cancelLive(conversationId: String) {
        nm.cancel(liveTag(conversationId), ID_LIVE)
    }

    // ---------------------------------------------------------------- Concluídos

    override fun showDone(notice: DoneNotice) {
        val title = if (notice.isError) ctx.getString(R.string.notif_done_error_title, notice.title) else notice.title
        val text = when {
            notice.isError -> notice.message ?: ctx.getString(R.string.notif_done_error)
            notice.durationMs != null && notice.durationMs > 0 ->
                ctx.getString(R.string.notif_done_ok_duration, NotificationMapper.formatDuration(notice.durationMs))
            else -> ctx.getString(R.string.notif_done_ok)
        }
        val tag = doneTag(notice.conversationId)
        val n = NotificationCompat.Builder(ctx, NotificationChannels.DONE)
            .setSmallIcon(R.drawable.ic_stat_aistack)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setGroup(GROUP_DONE)
            .setSortKey(notice.conversationId)
            .setCategory(if (notice.isError) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(chatIntent(ctx, notice.conversationId))
            .build()
        post(tag, ID_DONE, n)
        updateDoneSummary(added = tag, removed = null)
    }

    override fun cancelDone(conversationId: String) {
        val tag = doneTag(conversationId)
        nm.cancel(tag, ID_DONE)
        updateDoneSummary(added = null, removed = tag)
    }

    /** O resumo do grupo de concluídos só existe com dois ou mais filhos. */
    private fun updateDoneSummary(added: String?, removed: String?) {
        val active = runCatching {
            platformNm?.activeNotifications
                ?.filter { it.id == ID_DONE && it.tag?.startsWith("done:") == true }
                ?.mapNotNull { it.tag }
                ?.toMutableSet()
        }.getOrNull() ?: return
        added?.let { active += it }
        removed?.let { active -= it }
        if (active.size < 2) {
            nm.cancel(ID_DONE_SUMMARY)
            return
        }
        val title = ctx.resources.getQuantityString(R.plurals.notif_done_summary, active.size, active.size)
        val n = NotificationCompat.Builder(ctx, NotificationChannels.DONE)
            .setSmallIcon(R.drawable.ic_stat_aistack)
            .setContentTitle(title)
            .setStyle(NotificationCompat.InboxStyle().setBigContentTitle(title))
            .setGroup(GROUP_DONE)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(chatIntent(ctx, null))
            .build()
        post(null, ID_DONE_SUMMARY, n)
    }

    // ---------------------------------------------------------------- Conexão

    override fun connectionNotification(state: ConnectionState): Notification {
        val text = when (state) {
            is ConnectionState.Online -> "Conectado ao desktop"
            is ConnectionState.Connecting, ConnectionState.Handshaking -> "Conectando…"
            is ConnectionState.HostOffline -> "Desktop offline, aguardando"
            is ConnectionState.Error -> "Sem conexão, tentando de novo"
            is ConnectionState.AuthRejected -> "Pareamento recusado"
            ConnectionState.Revoked -> "Aparelho revogado"
            ConnectionState.Disconnected -> "Desconectado"
        }
        return NotificationCompat.Builder(ctx, NotificationChannels.CONNECTION)
            .setSmallIcon(R.drawable.ic_stat_aistack)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setContentIntent(chatIntent(ctx, null))
            .build()
    }

    /** Atualiza a notificação do serviço (se o serviço estiver de pé, o sistema a mantém). */
    fun updateConnection(state: ConnectionState) {
        post(null, ID_CONNECTION, connectionNotification(state))
    }

    fun cancelAll() {
        nm.cancelAll()
    }
}
