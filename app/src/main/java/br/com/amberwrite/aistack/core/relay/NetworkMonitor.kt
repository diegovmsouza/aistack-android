package br.com.amberwrite.aistack.core.relay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Observa a rede padrão: quando ela volta (ou muda), zera o backoff e reconecta na hora
 * em vez de esperar até 60 s.
 */
class NetworkMonitor(context: Context, private val onAvailable: () -> Unit) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onAvailable()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) onAvailable()
        }
    }

    fun start() {
        if (registered || cm == null) return
        try {
            cm.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (e: Exception) {
            Log.w("NetworkMonitor", "Não foi possível observar a rede: ${e.message}")
        }
    }

    fun stop() {
        if (!registered) return
        try { cm?.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        registered = false
    }
}
