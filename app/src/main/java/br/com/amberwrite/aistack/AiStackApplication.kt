package br.com.amberwrite.aistack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class AiStackApplication : Application() {

    companion object {
        const val TASK_CHANNEL_ID = "aistack_tasks_channel"
        const val PERMISSION_CHANNEL_ID = "aistack_permission_channel"
    }

    override fun onCreate() {
        super.onCreate()
        setupBouncyCastle()
        createNotificationChannels()
    }

    private fun setupBouncyCastle() {
        // Registra o provedor Bouncy Castle para suporte a X25519 e Ed25519
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal para tarefas em andamento (Foreground Service)
            val taskChannel = NotificationChannel(
                TASK_CHANNEL_ID,
                getString(R.string.task_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.task_channel_desc)
                setShowBadge(false)
            }

            // Canal para pedidos de autorização de ferramentas (Alta prioridade / Heads-up)
            val permissionChannel = NotificationChannel(
                PERMISSION_CHANNEL_ID,
                "Aprovações de IA (AiStack)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Solicitações da IA para executar comandos ou editar arquivos"
                enableVibration(true)
                setShowBadge(true)
            }

            manager.createNotificationChannel(taskChannel)
            manager.createNotificationChannel(permissionChannel)
        }
    }
}
