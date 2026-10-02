package br.com.amberwrite.aistack.relay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import br.com.amberwrite.aistack.crypto.DeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object AiStackConnectionManager {

    private const val TAG = "AiStackConnManager"
    private const val PREFS = "aistack_remote_config"
    private const val KEY_RELAY = "remote_relay"
    private const val KEY_HOST = "remote_host"
    private const val KEY_PK = "remote_pk"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeClient: RelayClient? = null
    private val _currentClient = MutableStateFlow<RelayClient?>(null)
    val currentClient: StateFlow<RelayClient?> = _currentClient.asStateFlow()

    fun getSavedPairLink(context: Context): PairLink? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val relay = sp.getString(KEY_RELAY, null) ?: return null
        val host = sp.getString(KEY_HOST, null) ?: return null
        val pk = sp.getString(KEY_PK, null) ?: return null
        return PairLink(relay, host, pk, null)
    }

    fun savePairLink(context: Context, link: PairLink) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_RELAY, link.relay)
            .putString(KEY_HOST, link.host)
            .putString(KEY_PK, link.pk)
            .apply()
    }

    fun clearSavedLink(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        disconnect()
    }

    fun connectWith(context: Context, link: PairLink): RelayClient {
        activeClient?.disconnect()
        val identity = DeviceIdentity.getOrCreate(context)
        val client = RelayClient(context, link, identity)
        activeClient = client
        _currentClient.value = client
        client.connect()
        return client
    }

    fun disconnect() {
        activeClient?.disconnect()
        activeClient = null
        _currentClient.value = null
    }

    /** `onResult(true)` só depois de o host confirmar o RPC; o chamador descarta o pedido nesse caso. */
    fun answerPermission(conversationId: String, requestId: String, allow: Boolean, onResult: (Boolean) -> Unit = {}) {
        val client = activeClient
        if (client == null || conversationId.isBlank() || requestId.isBlank()) {
            Log.w(TAG, "Resposta de permissão sem cliente ou sem ids: não enviada")
            onResult(false)
            return
        }
        scope.launch {
            var ok = false
            try {
                val decision = mapOf(
                    "behavior" to if (allow) "allow" else "deny"
                )
                val params = mapOf(
                    "id" to conversationId,
                    "requestId" to requestId,
                    "decision" to decision
                )
                client.call("answerPermission", params)
                ok = true
                Log.i(TAG, "Permissão respondida com sucesso: $requestId -> allow=$allow")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao responder permissão: ${e.message}", e)
            }
            onResult(ok)
        }
    }
}
