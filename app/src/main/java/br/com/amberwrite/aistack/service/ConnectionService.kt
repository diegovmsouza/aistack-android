package br.com.amberwrite.aistack.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import br.com.amberwrite.aistack.AiStackApplication
import br.com.amberwrite.aistack.core.relay.ConnectionState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano (tipo `remoteMessaging`) que mantém o processo vivo para a
 * conexão com o desktop. A conexão em si pertence ao [br.com.amberwrite.aistack.AppContainer];
 * aqui só há a notificação discreta (canal de prioridade mínima) refletindo o estado.
 */
class ConnectionService : LifecycleService() {

    companion object {
        private const val TAG = "ConnectionService"

        /** Sobe o serviço. Se o sistema proibir (app em segundo plano), liga direto. */
        fun start(context: Context) {
            val app = context.applicationContext
            try {
                ContextCompat.startForegroundService(app, Intent(app, ConnectionService::class.java))
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException (Android 12+) ou IllegalStateException.
                Log.w(TAG, "Não foi possível subir o serviço: ${e.message}")
                AiStackApplication.container(app).connectSaved()
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, ConnectionService::class.java))
        }
    }

    private var started = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val container = AiStackApplication.container(this)
        val notifier = container.notifier
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        try {
            ServiceCompat.startForeground(
                this, AndroidNotifier.ID_CONNECTION,
                notifier.connectionNotification(container.connectionState.value), type
            )
        } catch (e: Exception) {
            Log.w(TAG, "startForeground falhou: ${e.message}")
            container.connectSaved()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!container.pairingStore.isPaired || !container.settingsStore.settings.value.keepConnected) {
            stopSelf()
            return START_NOT_STICKY
        }
        container.connectSaved()
        if (!started) {
            started = true
            // Live Updates detalhados (ferramenta/sub-agentes) enquanto o serviço está de pé.
            LiveDetailSubscriber.forContainer(container).start(lifecycleScope)
            lifecycleScope.launch {
                container.connectionState
                    .map { label(it) to it }
                    .distinctUntilChanged { a, b -> a.first == b.first }
                    .collect { (_, st) ->
                        notifier.updateConnection(st)
                        if (st is ConnectionState.Revoked || st is ConnectionState.AuthRejected) stopSelf()
                    }
            }
        }
        return START_STICKY
    }

    /** Agrupa estados equivalentes para não reemitir a notificação a cada tentativa. */
    private fun label(st: ConnectionState): String = when (st) {
        is ConnectionState.Online -> "online"
        is ConnectionState.Connecting, ConnectionState.Handshaking -> "connecting"
        is ConnectionState.HostOffline -> "offline"
        is ConnectionState.Error -> "error"
        is ConnectionState.AuthRejected -> "rejected"
        ConnectionState.Revoked -> "revoked"
        ConnectionState.Disconnected -> "disconnected"
    }

    override fun onDestroy() {
        started = false
        super.onDestroy()
    }
}
