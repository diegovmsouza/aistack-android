package br.com.amberwrite.aistack.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AiStackTaskService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null
    private var elapsedSeconds = 0
    private var currentTaskDescription = "Processando turno da IA..."

    companion object {
        const val ACTION_START = "br.com.amberwrite.aistack.START_TASK"
        const val ACTION_UPDATE = "br.com.amberwrite.aistack.UPDATE_TASK"
        const val ACTION_STOP = "br.com.amberwrite.aistack.STOP_TASK"
        const val EXTRA_DESCRIPTION = "extra_description"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentTaskDescription = intent.getStringExtra(EXTRA_DESCRIPTION) ?: "Executando tarefa..."
                elapsedSeconds = 0
                startForegroundService()
                startTimer()
            }
            ACTION_UPDATE -> {
                currentTaskDescription = intent.getStringExtra(EXTRA_DESCRIPTION) ?: currentTaskDescription
                updateNotification()
            }
            ACTION_STOP -> {
                stopTimer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 limita o tempo do serviço `dataSync`: ao estourar, encerra com a notificação removida. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopTimer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundService() {
        val notification = TaskNotificationManager.createOngoingTaskNotification(
            this,
            currentTaskDescription,
            elapsedSeconds
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TaskNotificationManager.TASK_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(TaskNotificationManager.TASK_NOTIFICATION_ID, notification)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                elapsedSeconds++
                updateNotification()
            }
        }
    }

    private fun updateNotification() {
        val notification = TaskNotificationManager.createOngoingTaskNotification(
            this,
            currentTaskDescription,
            elapsedSeconds
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(TaskNotificationManager.TASK_NOTIFICATION_ID, notification)
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onDestroy() {
        stopTimer()
        super.onDestroy()
    }
}
