package br.com.amberwrite.aistack.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.amberwrite.aistack.MainActivity
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.data.model.PendingConversation
import br.com.amberwrite.aistack.data.model.PermissionRequest

/**
 * [Notifier] real. Toda notificação usa um id fixo por tipo e uma `tag` com a chave
 * (conversa/pedido), então os ids são sempre válidos (não zero) e não colidem.
 */
class AndroidNotifier(context: Context) : Notifier {
    private val ctx = context.applicationContext
    private val nm = NotificationManagerCompat.from(ctx)

    companion object {
        const val ID_CONNECTION = 1001
        const val ID_PERMISSION = 2001
        const val ID_DONE = 2002
        const val ID_PROGRESS = 2003

        fun permissionTag(conversationId: String, requestId: String) = "perm:$conversationId:$requestId"
        fun doneTag(conversationId: String) = "done:$conversationId"

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
        }
    }

    override fun showPermission(request: PermissionRequest, conversationTitle: String, error: String?) {
        val isQuestion = request.isAskUserQuestion
        val title = if (isQuestion) "Pergunta da IA" else "Permissão: ${request.tool}"
        val detail = when {
            isQuestion -> request.questions.firstOrNull()?.question ?: "A IA quer te perguntar algo."
            !request.inputPreview.isNullOrBlank() -> request.inputPreview
            !request.reason.isNullOrBlank() -> request.reason
            else -> "A IA pede autorização para usar ${request.tool}."
        }
        val body = buildString {
            if (error != null) append("Falhou: ").append(error).append("\n")
            append(detail)
        }
        val builder = NotificationCompat.Builder(ctx, NotificationChannels.PENDING)
            .setSmallIcon(R.drawable.ic_stat_aistack)
            .setContentTitle(title)
            .setContentText(if (error != null) "Falhou: $error" else detail)
            .setSubText(conversationTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(error != null)
            .setGroup("aistack_pending")
            .setContentIntent(chatIntent(ctx, request.conversationId))
        if (!isQuestion) {
            builder.addAction(0, ctx.getString(R.string.allow), answerIntent(request, conversationTitle, NotificationActionReceiver.DECISION_ALLOW))
            builder.addAction(0, ctx.getString(R.string.deny), answerIntent(request, conversationTitle, NotificationActionReceiver.DECISION_DENY))
        }
        post(permissionTag(request.conversationId, request.requestId), ID_PERMISSION, builder.build())
    }

    private fun answerIntent(request: PermissionRequest, title: String, decision: String): PendingIntent {
        val intent = Intent(ctx, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_ANSWER
            putExtra(NotificationActionReceiver.EXTRA_CONVERSATION, request.conversationId)
            putExtra(NotificationActionReceiver.EXTRA_REQUEST, request.requestId)
            putExtra(NotificationActionReceiver.EXTRA_DECISION, decision)
            putExtra(NotificationActionReceiver.EXTRA_TOOL, request.tool)
            putExtra(NotificationActionReceiver.EXTRA_PREVIEW, request.inputPreview)
            putExtra(NotificationActionReceiver.EXTRA_TITLE, title)
        }
        val code = (permissionTag(request.conversationId, request.requestId) + decision).hashCode()
        return PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun cancelPermission(conversationId: String, requestId: String) {
        nm.cancel(permissionTag(conversationId, requestId), ID_PERMISSION)
    }

    override fun showProgress(busy: List<PendingConversation>) {
        if (busy.isEmpty()) {
            nm.cancel(ID_PROGRESS)
            return
        }
        val text = if (busy.size == 1) busy[0].title.ifBlank { "Conversa" } else "${busy.size} conversas em andamento"
        val n = NotificationCompat.Builder(ctx, NotificationChannels.PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_aistack)
            .setContentTitle("A IA está trabalhando")
            .setContentText(text)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(chatIntent(ctx, busy.singleOrNull()?.conversationId))
            .build()
        post(null, ID_PROGRESS, n)
    }

    override fun showDone(conversationId: String, title: String, text: String, isError: Boolean) {
        val n = NotificationCompat.Builder(ctx, NotificationChannels.DONE)
            .setSmallIcon(R.drawable.ic_stat_aistack)
            .setContentTitle(if (isError) "Erro em $title" else title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(if (isError) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(chatIntent(ctx, conversationId))
            .build()
        post(doneTag(conversationId), ID_DONE, n)
    }

    override fun cancelDone(conversationId: String) {
        nm.cancel(doneTag(conversationId), ID_DONE)
    }

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
