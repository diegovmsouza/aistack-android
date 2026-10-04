package br.com.amberwrite.aistack.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import br.com.amberwrite.aistack.R

/**
 * Canais de notificação: conexão (mínima), pendências (alta, com ações), progresso (baixa,
 * Live Updates) e concluídos (padrão). Recriar é idempotente: nome/descrição se atualizam,
 * a importância escolhida pelo usuário é preservada pelo sistema.
 */
object NotificationChannels {
    const val CONNECTION = "aistack_connection"
    const val PENDING = "aistack_pending"
    const val PROGRESS = "aistack_progress"
    const val DONE = "aistack_done"

    /** Canais da versão antiga, removidos na primeira execução. */
    private val LEGACY = listOf("aistack_tasks_channel", "aistack_permission_channel")

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        LEGACY.forEach { runCatching { nm.deleteNotificationChannel(it) } }
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CONNECTION, "Conexão com o desktop", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Mantém o celular ligado ao AiStack do desktop."
                    setShowBadge(false)
                },
                NotificationChannel(PENDING, context.getString(R.string.channel_pending_name), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_pending_desc)
                    enableVibration(true)
                    setShowBadge(true)
                },
                NotificationChannel(PROGRESS, context.getString(R.string.channel_progress_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_progress_desc)
                    enableVibration(false)
                    setSound(null, null)
                    setShowBadge(false)
                },
                NotificationChannel(DONE, context.getString(R.string.channel_done_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_done_desc)
                    setShowBadge(true)
                }
            )
        )
    }
}
