package br.com.amberwrite.aistack.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import br.com.amberwrite.aistack.AiStackApplication
import br.com.amberwrite.aistack.MainActivity
import br.com.amberwrite.aistack.R

object TaskNotificationManager {

    const val TASK_NOTIFICATION_ID = 1001
    const val PERMISSION_NOTIFICATION_BASE_ID = 2000

    fun createOngoingTaskNotification(
        context: Context,
        taskDescription: String,
        elapsedTimeSeconds: Int
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minutes = elapsedTimeSeconds / 60
        val seconds = elapsedTimeSeconds % 60
        val timeFormatted = "%02d:%02d".format(minutes, seconds)

        return NotificationCompat.Builder(context, AiStackApplication.TASK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AiStack — Tarefa em execução ($timeFormatted)")
            .setContentText(taskDescription)
            .setProgress(0, 0, true) // Indeterminado
            .setContentIntent(pendingOpen)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun showPermissionNotification(
        context: Context,
        requestId: String,
        conversationId: String,
        toolName: String,
        commandOrFile: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent para abrir o app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("conversation_id", conversationId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            requestId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação Permitir
        val allowIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_ALLOW
            putExtra(NotificationActionReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(NotificationActionReceiver.EXTRA_CONV_ID, conversationId)
        }
        val pendingAllow = PendingIntent.getBroadcast(
            context,
            (requestId + "_allow").hashCode(),
            allowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação Negar
        val denyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DENY
            putExtra(NotificationActionReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(NotificationActionReceiver.EXTRA_CONV_ID, conversationId)
        }
        val pendingDeny = PendingIntent.getBroadcast(
            context,
            (requestId + "_deny").hashCode(),
            denyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifId = PERMISSION_NOTIFICATION_BASE_ID + (Math.floorMod(requestId.hashCode(), 1000))

        val notification = NotificationCompat.Builder(context, AiStackApplication.PERMISSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AiStack — Permissão necessária")
            .setContentText("A IA solicita permissão para $toolName: $commandOrFile")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("A IA deseja executar uma ação no seu computador:\n\n• Ferramenta: $toolName\n• Alvo: $commandOrFile\n\nDeseja autorizar agora?")
            )
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            // Na tela de bloqueio aparece só o aviso genérico: o comando pode conter caminhos e segredos.
            .setPublicVersion(
                NotificationCompat.Builder(context, AiStackApplication.PERMISSION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("AiStack — Permissão necessária")
                    .setContentText("Desbloqueie o aparelho para ver o pedido.")
                    .build()
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .addAction(R.drawable.ic_launcher_foreground, "Permitir", pendingAllow)
            .addAction(R.drawable.ic_launcher_foreground, "Negar", pendingDeny)
            .build()

        manager.notify(notifId, notification)
    }

    fun dismissPermissionNotification(context: Context, requestId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = PERMISSION_NOTIFICATION_BASE_ID + (Math.floorMod(requestId.hashCode(), 1000))
        manager.cancel(notifId)
    }
}
