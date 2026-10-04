package br.com.amberwrite.aistack.data.repo

import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.core.rpc.RpcCaller
import br.com.amberwrite.aistack.core.rpc.RpcException
import br.com.amberwrite.aistack.core.rpc.asArr
import br.com.amberwrite.aistack.core.rpc.asObj
import br.com.amberwrite.aistack.core.rpc.asStr
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.Conversation
import br.com.amberwrite.aistack.data.model.ModelInfo
import br.com.amberwrite.aistack.data.model.PermissionMode
import br.com.amberwrite.aistack.data.model.Provider
import br.com.amberwrite.aistack.data.model.SlashCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Lista de conversas e as ações que mexem nelas (criar, renomear, arquivar, bifurcar, opções). */
class SessionsRepo(
    private val rpc: RpcCaller,
    private val scope: CoroutineScope
) {
    data class State(
        val conversations: List<Conversation> = emptyList(),
        val includeArchived: Boolean = false,
        val loading: Boolean = false,
        val loaded: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var reloadJob: Job? = null
    private val catalogCache = ConcurrentHashMap<Provider, List<ModelInfo>>()

    fun find(id: String): Conversation? = _state.value.conversations.firstOrNull { it.id == id }

    suspend fun reload() {
        _state.update { it.copy(loading = true) }
        try {
            val list = Conversation.parseList(
                rpc.call("listConversations", params("includeArchived" to _state.value.includeArchived))
            ).sortedByDescending { it.updatedAt }
            _state.update { it.copy(conversations = list, loading = false, loaded = true, error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.userMessage) }
        }
    }

    fun setIncludeArchived(value: Boolean) {
        if (_state.value.includeArchived == value) return
        _state.update { it.copy(includeArchived = value) }
        scope.launch { reload() }
    }

    fun clear() {
        _state.value = State(includeArchived = _state.value.includeArchived)
    }

    fun onEvent(ev: HostEvent) {
        if (ev is HostEvent.ConversationsChanged) scheduleReload()
    }

    /** Agrupa rajadas de `conversations-changed` num só `listConversations`. */
    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(RELOAD_DEBOUNCE_MS)
            reload()
        }
    }

    /** Atualiza (ou insere) uma conversa devolvida por uma ação, sem esperar o evento. */
    private fun upsert(c: Conversation) {
        _state.update { s ->
            val others = s.conversations.filterNot { it.id == c.id }
            val list = if (c.archived && !s.includeArchived) others else (others + c)
            s.copy(conversations = list.sortedByDescending { it.updatedAt })
        }
    }

    suspend fun recentProjects(): List<String> =
        rpc.call("recentProjects").asArr()?.mapNotNull { it.asStr() } ?: emptyList()

    suspend fun listSlashCommands(provider: Provider, projectPath: String?): List<SlashCommand> = try {
        SlashCommand.parseList(rpc.call("listSlashCommands", params("provider" to provider.id, "projectPath" to projectPath)))
    } catch (e: RpcException) {
        if (e.kind == RpcException.Kind.UNKNOWN_METHOD) emptyList() else throw e
    }

    suspend fun getCatalog(provider: Provider, refresh: Boolean = false): List<ModelInfo> {
        if (!refresh) catalogCache[provider]?.let { return it }
        val list = ModelInfo.parseList(rpc.call("getCatalog", params("provider" to provider.id, "refresh" to refresh)))
        catalogCache[provider] = list
        return list
    }

    /**
     * Cria uma conversa. [permissionMode] é obrigatório no remoto (`bypass` é recusado).
     * A conversa devolvida pode trazer `warning` (ex.: modo rebaixado).
     */
    suspend fun createConversation(
        provider: Provider,
        projectPath: String,
        permissionMode: PermissionMode,
        model: String? = null,
        effort: String? = null,
        extraDirs: List<String> = emptyList()
    ): Conversation {
        val res = rpc.call(
            "createConversation",
            params(
                "provider" to provider.id,
                "projectPath" to projectPath,
                "model" to model,
                "effort" to effort,
                "permissionMode" to permissionMode.id,
                "extraDirs" to extraDirs
            )
        )
        val c = res.asObj()?.let(Conversation::parse) ?: throw RpcException(RpcException.Kind.REMOTE, "Resposta inválida do desktop.")
        upsert(c)
        return c
    }

    suspend fun rename(id: String, title: String) {
        rpc.call("renameConversation", params("id" to id, "title" to title))
        _state.update { s -> s.copy(conversations = s.conversations.map { if (it.id == id) it.copy(title = title) else it }) }
    }

    suspend fun archive(id: String, archived: Boolean) {
        rpc.call("archiveConversation", params("id" to id, "archived" to archived))
        find(id)?.let { upsert(it.copy(archived = archived)) }
    }

    suspend fun fork(id: String, upToTurn: Long): Conversation {
        val c = rpc.call("forkConversation", params("id" to id, "upToTurn" to upToTurn)).asObj()?.let(Conversation::parse)
            ?: throw RpcException(RpcException.Kind.REMOTE, "Resposta inválida do desktop.")
        upsert(c)
        return c
    }

    suspend fun setConversationOptions(
        id: String,
        model: String? = null,
        effort: String? = null,
        permissionMode: PermissionMode? = null,
        projectPath: String? = null,
        extraDirs: List<String>? = null
    ): Conversation? {
        val res = rpc.call(
            "setConversationOptions",
            params(
                "id" to id,
                "model" to model,
                "effort" to effort,
                "permissionMode" to permissionMode?.id,
                "projectPath" to projectPath,
                "extraDirs" to extraDirs
            )
        )
        val c = res.asObj()?.let(Conversation::parse)
        if (c != null) upsert(c)
        return c
    }

    private companion object {
        const val RELOAD_DEBOUNCE_MS = 300L
    }
}
