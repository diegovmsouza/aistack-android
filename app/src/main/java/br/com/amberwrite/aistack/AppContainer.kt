package br.com.amberwrite.aistack

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import br.com.amberwrite.aistack.core.crypto.DeviceIdentity
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.relay.NetworkMonitor
import br.com.amberwrite.aistack.core.relay.PairLink
import br.com.amberwrite.aistack.core.relay.RelayConnection
import br.com.amberwrite.aistack.core.rpc.RpcClient
import br.com.amberwrite.aistack.data.repo.AccountsRepo
import br.com.amberwrite.aistack.data.repo.ChatRepo
import br.com.amberwrite.aistack.data.repo.DevicesRepo
import br.com.amberwrite.aistack.data.repo.FilesRepo
import br.com.amberwrite.aistack.data.repo.PendingRepo
import br.com.amberwrite.aistack.data.repo.SessionsRepo
import br.com.amberwrite.aistack.data.repo.SyncCoordinator
import br.com.amberwrite.aistack.data.store.PairingStore
import br.com.amberwrite.aistack.data.store.SettingsStore
import br.com.amberwrite.aistack.service.AndroidNotifier
import br.com.amberwrite.aistack.service.ConnectionService
import br.com.amberwrite.aistack.service.NotificationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Injeção manual de dependências: um único grafo por processo.
 *
 * Dono da conexão: a [RelayConnection] vive aqui; o [ConnectionService] só mantém o processo
 * vivo em segundo plano. Regras:
 * - "Manter conectado" ligado: o serviço sobe quando o app abre (ou no boot, se pedido) e
 *   segura a conexão mesmo com o app fechado.
 * - Desligado: conecta quando o app vem para frente e desliga quando vai para o fundo.
 * - O link só é salvo depois do pareamento confirmado ([RelayConnection.Listener.onPaired]).
 */
class AppContainer(context: Context) {
    private val tag = "AppContainer"
    val appContext: Context = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val pairingStore = PairingStore(appContext)
    val settingsStore = SettingsStore(appContext)

    @Volatile
    private var cachedIdentity: DeviceIdentity? = null

    /** Identidade do aparelho (chave privada cifrada pelo Keystore). */
    val identity: DeviceIdentity
        get() = cachedIdentity ?: synchronized(this) {
            cachedIdentity ?: DeviceIdentity.getOrCreate(appContext).also { cachedIdentity = it }
        }

    fun renameDevice(name: String) {
        DeviceIdentity.rename(appContext, name)
        synchronized(this) { cachedIdentity = null }
    }

    val rpc = RpcClient(appScope)

    val connection = RelayConnection(
        identityProvider = { identity },
        rpc = rpc,
        scope = appScope,
        listener = object : RelayConnection.Listener {
            override fun onPaired(link: PairLink) = handlePaired(link)
            override fun onRevoked() = handleRevoked()
        }
    )

    val connectionState: StateFlow<ConnectionState> get() = connection.state

    val sessionsRepo = SessionsRepo(rpc, appScope)
    val pendingRepo = PendingRepo(rpc) { id ->
        sessionsRepo.find(id)?.let { Triple(it.displayTitle, it.projectPath, it.provider) }
    }
    val chatRepo = ChatRepo(rpc, appScope, pendingRepo)
    val filesRepo = FilesRepo(rpc)
    val accountsRepo = AccountsRepo(rpc, appScope)
    val devicesRepo = DevicesRepo(rpc) { identity.id }
    private val sync = SyncCoordinator(rpc, appScope, sessionsRepo, chatRepo, pendingRepo, accountsRepo, devicesRepo)

    val notifier = AndroidNotifier(appContext)

    private val _foreground = MutableStateFlow(false)
    /** O app tem alguma tela visível. */
    val isForeground: StateFlow<Boolean> = _foreground.asStateFlow()

    private val _visibleChat = MutableStateFlow<String?>(null)
    /** Conversa aberta na tela agora (para não notificar o que o usuário já está vendo). */
    val visibleChat: StateFlow<String?> = _visibleChat.asStateFlow()

    private val notifications = NotificationCoordinator(
        notifier = notifier,
        scope = appScope,
        pending = pendingRepo.items,
        events = rpc.events,
        isForeground = isForeground,
        visibleChat = visibleChat,
        notifyDone = { settingsStore.settings.value.notifyDone },
        titleOf = { id -> sessionsRepo.find(id)?.displayTitle }
    )

    private val network = NetworkMonitor(appContext) { connection.reconnectNow() }

    init {
        sync.start()
        notifications.start()
        network.start()
        // Aquece a identidade fora da thread principal (Keystore pode ser lento).
        appScope.launch(Dispatchers.IO) { runCatching { identity } }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = onAppForeground()
            override fun onStop(owner: LifecycleOwner) = onAppBackground()
        })
    }

    fun setVisibleChat(id: String?) {
        _visibleChat.value = id
    }

    /** Clareia a conversa visível só se ainda for [id] (evita corrida entre telas). */
    fun clearVisibleChat(id: String) {
        _visibleChat.compareAndSet(id, null)
    }

    private fun onAppForeground() {
        _foreground.value = true
        if (!pairingStore.isPaired) return
        if (settingsStore.settings.value.keepConnected) {
            ConnectionService.start(appContext)
        }
        connectSaved()
    }

    private fun onAppBackground() {
        _foreground.value = false
        if (!settingsStore.settings.value.keepConnected && pairingStore.isPaired) {
            connection.stop()
        }
    }

    /** Liga com o link salvo (serviço, boot, ação de notificação). Falso se não está pareado. */
    fun connectSaved(): Boolean {
        val link = pairingStore.link.value ?: return false
        val st = connection.state.value
        if (st is ConnectionState.AuthRejected || st is ConnectionState.Revoked) return false
        connection.start(link)
        return true
    }

    /** Tenta de novo mesmo após recusa (ação explícita do usuário). */
    fun retry() {
        val link = pairingStore.link.value ?: return
        connection.start(link)
        connection.reconnectNow()
    }

    /** Garante a conexão e espera ficar online por até [timeoutMs]. */
    suspend fun awaitOnline(timeoutMs: Long = 15_000): Boolean {
        if (rpc.connected.value) return true
        if (!connectSaved()) return false
        return withTimeoutOrNull(timeoutMs) { rpc.connected.first { it } } ?: false
    }

    /** Começa o pareamento com um link que traz o código de uso único. */
    fun pair(link: PairLink) {
        connection.start(link)
    }

    /** Liga/desliga "manter conectado" e ajusta o serviço. */
    fun setKeepConnected(value: Boolean) {
        settingsStore.update { it.copy(keepConnected = value) }
        if (value) {
            if (pairingStore.isPaired) ConnectionService.start(appContext)
        } else {
            ConnectionService.stop(appContext)
            if (!_foreground.value) connection.stop()
        }
    }

    /** Esquece o desktop: avisa o host (melhor esforço), apaga credenciais e para tudo. */
    suspend fun unpair() {
        if (rpc.connected.value) {
            withTimeoutOrNull(5_000) { runCatching { devicesRepo.revokeSelf() } }
        }
        clearLocal()
    }

    private fun handlePaired(link: PairLink) {
        Log.i(tag, "Pareamento confirmado com o host ${link.host.take(8)}…")
        pairingStore.save(link)
        if (settingsStore.settings.value.keepConnected) ConnectionService.start(appContext)
    }

    private fun handleRevoked() {
        Log.w(tag, "Aparelho revogado pelo desktop")
        pairingStore.clear()
        ConnectionService.stop(appContext)
        clearRepos()
    }

    private fun clearLocal() {
        connection.stop()
        pairingStore.clear()
        ConnectionService.stop(appContext)
        clearRepos()
    }

    private fun clearRepos() {
        sessionsRepo.clear()
        pendingRepo.clear()
        accountsRepo.clear()
        devicesRepo.clear()
        notifications.clearAll()
        notifier.cancelAll()
    }
}
