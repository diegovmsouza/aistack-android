package br.com.amberwrite.aistack.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.amberwrite.aistack.AiStackApplication

/** Sobe o [ConnectionService] no boot se o usuário pediu e o aparelho está pareado. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = AiStackApplication.container(context)
        val settings = container.settingsStore.settings.value
        if (!container.pairingStore.isPaired || !settings.keepConnected) return
        if (action == Intent.ACTION_BOOT_COMPLETED && !settings.startAtBoot) return
        ConnectionService.start(context)
    }
}
