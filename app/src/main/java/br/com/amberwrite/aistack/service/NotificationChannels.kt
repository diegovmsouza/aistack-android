package br.com.amberwrite.aistack.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Canais de notificação: conexão (mínima), pendências (alta), progresso (baixa) e concluído. */
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
                NotificationChannel(PENDING, "Aprovações e perguntas", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Pedidos de permissão e perguntas feitas pela IA."
                    enableVibration(true)
                    setShowBadge(true)
                },
                NotificationChannel(PROGRESS, "Tarefas em andamento", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Conversas em que a IA está trabalhando."
                    setShowBadge(false)
                },
                NotificationChannel(DONE, "Tarefas concluídas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Aviso quando a IA termina um turno."
                    setShowBadge(true)
                }
            )
        )
    }
}
