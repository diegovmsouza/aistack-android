package br.com.amberwrite.aistack.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.amberwrite.aistack.relay.AiStackConnectionManager

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALLOW = "br.com.amberwrite.aistack.ACTION_ALLOW"
        const val ACTION_DENY = "br.com.amberwrite.aistack.ACTION_DENY"
        const val EXTRA_REQUEST_ID = "extra_request_id"
        const val EXTRA_CONV_ID = "extra_conv_id"
        private const val TAG = "NotifActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val convId = intent.getStringExtra(EXTRA_CONV_ID) ?: ""

        Log.i(TAG, "Ação de notificação recebida: $action para request=$requestId")

        val allow = when (action) {
            ACTION_ALLOW -> true
            ACTION_DENY -> false
            else -> return
        }
        // O receiver morre ao retornar de onReceive: goAsync segura o processo até o host responder.
        // A notificação só some se o host confirmou; senão continua lá para nova tentativa.
        val pending = goAsync()
        AiStackConnectionManager.answerPermission(convId, requestId, allow) { ok ->
            if (ok) TaskNotificationManager.dismissPermissionNotification(context, requestId)
            pending.finish()
        }
    }
}
