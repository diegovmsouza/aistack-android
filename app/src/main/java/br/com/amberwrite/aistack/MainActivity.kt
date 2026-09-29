package br.com.amberwrite.aistack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.com.amberwrite.aistack.model.AccountStatus
import br.com.amberwrite.aistack.model.ChatBlock
import br.com.amberwrite.aistack.model.ChatMessage
import br.com.amberwrite.aistack.model.ConversationItem
import br.com.amberwrite.aistack.model.Provider
import br.com.amberwrite.aistack.relay.AiStackConnectionManager
import br.com.amberwrite.aistack.relay.PairLink
import br.com.amberwrite.aistack.relay.RelayClient
import br.com.amberwrite.aistack.relay.RelayState
import br.com.amberwrite.aistack.service.AiStackTaskService
import br.com.amberwrite.aistack.service.TaskNotificationManager
import br.com.amberwrite.aistack.ui.scanner.QrScannerScreen
import br.com.amberwrite.aistack.ui.screens.ChatScreen
import br.com.amberwrite.aistack.ui.screens.DrawerContent
import br.com.amberwrite.aistack.ui.screens.PairScreen
import br.com.amberwrite.aistack.ui.theme.AiStackTheme
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var activeRelayClient by mutableStateOf<RelayClient?>(null)
    private var isScannerOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tentar restaurar conexão salva ou verificar deep link de entrada
        val deepLink = intent?.data?.toString()
        val initialPairLink = if (deepLink != null) PairLink.parse(deepLink) else null

        if (initialPairLink != null) {
            connectWithLink(initialPairLink)
        } else {
            val savedLink = AiStackConnectionManager.getSavedPairLink(this)
            if (savedLink != null) {
                activeRelayClient = AiStackConnectionManager.connectWith(this, savedLink)
            }
        }

        setContent {
            AiStackTheme {
                MainContent()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLink = intent.data?.toString()
        if (deepLink != null) {
            val link = PairLink.parse(deepLink)
            if (link != null) {
                Toast.makeText(this, "Pareamento recebido via link!", Toast.LENGTH_SHORT).show()
                connectWithLink(link)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val client = activeRelayClient
        if (client != null && client.state.value != RelayState.ONLINE && client.state.value != RelayState.CONNECTING) {
            client.connect()
        }
    }

    private fun connectWithLink(link: PairLink) {
        AiStackConnectionManager.savePairLink(this, link)
        activeRelayClient = AiStackConnectionManager.connectWith(this, link)
        isScannerOpen = false
    }

    @Composable
    private fun MainContent() {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val client = activeRelayClient
        val relayState by client?.state?.collectAsState() ?: remember { mutableStateOf(RelayState.DISCONNECTED) }
        val lastError by client?.lastError?.collectAsState() ?: remember { mutableStateOf<String?>(null) }

        val conversations = remember { mutableStateListOf<ConversationItem>() }
        val accounts = remember { mutableStateListOf<AccountStatus>() }
        val messages = remember { mutableStateListOf<ChatMessage>() }

        var activeConvId by remember { mutableStateOf<String?>(null) }
        var currentProvider by remember { mutableStateOf(Provider.CLAUDE) }
        var currentEffort by remember { mutableStateOf("high") }
        var isStreaming by remember { mutableStateOf(false) }

        // Escuta eventos ao vivo do Host
        LaunchedEffect(client) {
            client?.events?.collect { event ->
                when (event.event) {
                    "conv-event" -> {
                        event.payload?.asJsonObject?.let { p ->
                            val convId = p.get("conversationId")?.asString
                            val inner = p.get("event")?.asJsonObject ?: return@let
                            val type = inner.get("type")?.asString

                            when (type) {
                                "TextDelta" -> {
                                    val text = inner.get("text")?.asString ?: ""
                                    val lastMsg = messages.lastOrNull()
                                    if (lastMsg != null && lastMsg.role == "assistant") {
                                        val lastBlock = lastMsg.blocks.lastOrNull()
                                        if (lastBlock is ChatBlock.Text) {
                                            val updated = lastBlock.copy(text = lastBlock.text + text, isStreaming = true)
                                            val updatedBlocks = lastMsg.blocks.toMutableList().also { it[it.size - 1] = updated }
                                            messages[messages.size - 1] = lastMsg.copy(blocks = updatedBlocks)
                                        } else {
                                            val newBlock = ChatBlock.Text(id = System.currentTimeMillis().toString(), text = text, isStreaming = true)
                                            messages[messages.size - 1] = lastMsg.copy(blocks = lastMsg.blocks + newBlock)
                                        }
                                    } else {
                                        val newBlock = ChatBlock.Text(id = System.currentTimeMillis().toString(), text = text, isStreaming = true)
                                        messages.add(ChatMessage(id = System.currentTimeMillis().toString(), role = "assistant", blocks = listOf(newBlock)))
                                    }
                                }
                                "ThinkingDelta" -> {
                                    val text = inner.get("text")?.asString ?: ""
                                    val lastMsg = messages.lastOrNull()
                                    if (lastMsg != null && lastMsg.role == "assistant") {
                                        val lastBlock = lastMsg.blocks.lastOrNull()
                                        if (lastBlock is ChatBlock.Thinking) {
                                            val updated = lastBlock.copy(text = lastBlock.text + text, isStreaming = true)
                                            val updatedBlocks = lastMsg.blocks.toMutableList().also { it[it.size - 1] = updated }
                                            messages[messages.size - 1] = lastMsg.copy(blocks = updatedBlocks)
                                        } else {
                                            val newBlock = ChatBlock.Thinking(id = System.currentTimeMillis().toString(), text = text, isStreaming = true)
                                            messages[messages.size - 1] = lastMsg.copy(blocks = lastMsg.blocks + newBlock)
                                        }
                                    }
                                }
                                "ToolStart" -> {
                                    val id = inner.get("id")?.asString ?: ""
                                    val name = inner.get("name")?.asString ?: "ferramenta"
                                    val input = inner.get("input")?.toString() ?: ""
                                    val toolBlock = ChatBlock.ToolCall(id = id, toolName = name, input = input, isRunning = true)
                                    val lastMsg = messages.lastOrNull()
                                    if (lastMsg != null && lastMsg.role == "assistant") {
                                        messages[messages.size - 1] = lastMsg.copy(blocks = lastMsg.blocks + toolBlock)
                                    }

                                    // Atualiza Foreground Service
                                    val sIntent = Intent(this@MainActivity, AiStackTaskService::class.java).apply {
                                        action = AiStackTaskService.ACTION_UPDATE
                                        putExtra(AiStackTaskService.EXTRA_DESCRIPTION, "Executando $name...")
                                    }
                                    startService(sIntent)
                                }
                                "PermissionRequest" -> {
                                    val reqId = inner.get("id")?.asString ?: ""
                                    val tool = inner.get("tool")?.asString ?: "Comando"
                                    val input = inner.get("input")?.toString() ?: ""
                                    val permBlock = ChatBlock.Permission(requestId = reqId, toolName = tool, commandOrFile = input)
                                    val lastMsg = messages.lastOrNull()
                                    if (lastMsg != null && lastMsg.role == "assistant") {
                                        messages[messages.size - 1] = lastMsg.copy(blocks = lastMsg.blocks + permBlock)
                                    }

                                    // Dispara notificação Heads-up interativa
                                    TaskNotificationManager.showPermissionNotification(
                                        this@MainActivity,
                                        reqId,
                                        convId ?: activeConvId ?: "",
                                        tool,
                                        input
                                    )
                                }
                                "TurnComplete" -> {
                                    isStreaming = false
                                    // Para o serviço de background
                                    stopService(Intent(this@MainActivity, AiStackTaskService::class.java))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Carrega dados iniciais quando o túnel fica ONLINE
        LaunchedEffect(relayState) {
            if (relayState == RelayState.ONLINE) {
                scope.launch {
                    try {
                        val result = client?.call("listConversations")
                        conversations.clear()
                        result?.asJsonArray?.forEach { elem ->
                            val obj = elem.asJsonObject
                            val id = obj.get("id")?.asString ?: ""
                            val title = obj.get("title")?.asString ?: ""
                            val provStr = obj.get("provider")?.asString
                            val prov = Provider.fromId(provStr)
                            val upd = try {
                                obj.get("updatedAt")?.asLong ?: 0L
                            } catch (_: Exception) {
                                try {
                                    val s = obj.get("updatedAt")?.asString
                                    if (s != null) {
                                        java.time.Instant.parse(s).toEpochMilli()
                                    } else 0L
                                } catch (_: Exception) {
                                    0L
                                }
                            }
                            conversations.add(
                                ConversationItem(
                                    id = id,
                                    provider = prov,
                                    title = title,
                                    projectPath = obj.get("projectPath")?.asString,
                                    model = obj.get("model")?.asString,
                                    effort = obj.get("effort")?.asString,
                                    permissionMode = obj.get("permissionMode")?.asString ?: "default",
                                    updatedAt = upd
                                )
                            )
                        }
                        if (conversations.isNotEmpty() && activeConvId == null) {
                            val first = conversations.first()
                            activeConvId = first.id
                            currentProvider = first.provider
                            try {
                                val convData = client?.call("getConversation", mapOf("id" to first.id))?.asJsonObject
                                val blocks = convData?.getAsJsonArray("blocks")
                                blocks?.forEach { blockElem ->
                                    val bObj = blockElem.asJsonObject
                                    val kind = bObj.get("kind")?.asString ?: "text"
                                    val content = bObj.get("content")?.asJsonObject
                                    val blockId = bObj.get("id")?.asString ?: System.currentTimeMillis().toString()
                                    when (kind) {
                                        "user" -> {
                                            val txt = content?.get("text")?.asString ?: ""
                                            messages.add(ChatMessage(id = blockId, role = "user", blocks = listOf(ChatBlock.Text(id = blockId, text = txt))))
                                        }
                                        "text" -> {
                                            val txt = content?.get("text")?.asString ?: ""
                                            messages.add(ChatMessage(id = blockId, role = "assistant", blocks = listOf(ChatBlock.Text(id = blockId, text = txt))))
                                        }
                                        "thought" -> {
                                            val txt = content?.get("text")?.asString ?: ""
                                            messages.add(ChatMessage(id = blockId, role = "assistant", blocks = listOf(ChatBlock.Thinking(id = blockId, text = txt))))
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Erro listando conversas: ${e.message}", e)
                    }
                }
            }
        }

        if (isScannerOpen) {
            QrScannerScreen(
                onPairFound = { link ->
                    connectWithLink(link)
                },
                onBack = { isScannerOpen = false }
            )
        } else if (relayState != RelayState.ONLINE && activeRelayClient == null) {
            PairScreen(
                relayState = relayState,
                errorMessage = lastError,
                onOpenScanner = { isScannerOpen = true },
                onPairWithLink = { link -> connectWithLink(link) }
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    DrawerContent(
                        conversations = conversations,
                        accounts = accounts,
                        activeConversationId = activeConvId,
                        onSelectConversation = { id ->
                            activeConvId = id
                            messages.clear()
                            val selectedConv = conversations.find { it.id == id }
                            if (selectedConv != null) {
                                currentProvider = selectedConv.provider
                            }
                            scope.launch {
                                try {
                                    val convData = client?.call("getConversation", mapOf("id" to id))?.asJsonObject
                                    val blocks = convData?.getAsJsonArray("blocks")
                                    blocks?.forEach { blockElem ->
                                        val bObj = blockElem.asJsonObject
                                        val kind = bObj.get("kind")?.asString ?: "text"
                                        val content = bObj.get("content")?.asJsonObject
                                        val blockId = bObj.get("id")?.asString ?: System.currentTimeMillis().toString()
                                        when (kind) {
                                            "user" -> {
                                                val txt = content?.get("text")?.asString ?: ""
                                                messages.add(ChatMessage(id = blockId, role = "user", blocks = listOf(ChatBlock.Text(id = blockId, text = txt))))
                                            }
                                            "text" -> {
                                                val txt = content?.get("text")?.asString ?: ""
                                                messages.add(ChatMessage(id = blockId, role = "assistant", blocks = listOf(ChatBlock.Text(id = blockId, text = txt))))
                                            }
                                            "thought" -> {
                                                val txt = content?.get("text")?.asString ?: ""
                                                messages.add(ChatMessage(id = blockId, role = "assistant", blocks = listOf(ChatBlock.Thinking(id = blockId, text = txt))))
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                                drawerState.close()
                            }
                        },
                        onNewConversation = {
                            activeConvId = null
                            messages.clear()
                            scope.launch { drawerState.close() }
                        },
                        onDisconnectHost = {
                            AiStackConnectionManager.clearSavedLink(this@MainActivity)
                            activeRelayClient = null
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            ) {
                ChatScreen(
                    conversationTitle = conversations.find { it.id == activeConvId }?.title ?: "Nova Conversa",
                    messages = messages,
                    currentProvider = currentProvider,
                    currentEffort = currentEffort,
                    relayState = relayState,
                    isStreaming = isStreaming,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSendMessage = { text ->
                        val userMsg = ChatMessage(
                            id = System.currentTimeMillis().toString(),
                            role = "user",
                            blocks = listOf(ChatBlock.Text(id = System.currentTimeMillis().toString(), text = text))
                        )
                        messages.add(userMsg)
                        isStreaming = true

                        // Inicia Foreground Service de acompanhamento
                        val sIntent = Intent(this@MainActivity, AiStackTaskService::class.java).apply {
                            action = AiStackTaskService.ACTION_START
                            putExtra(AiStackTaskService.EXTRA_DESCRIPTION, "Executando $text...")
                        }
                        startService(sIntent)

                        // Envia para o Host via RPC
                        scope.launch {
                            try {
                                if (activeConvId == null) {
                                    val createParams = mapOf(
                                        "provider" to currentProvider.id,
                                        "projectPath" to "",
                                        "effort" to currentEffort,
                                        "permissionMode" to "default"
                                    )
                                    val newConv = client?.call("createConversation", createParams)?.asJsonObject
                                    activeConvId = newConv?.get("id")?.asString
                                }

                                activeConvId?.let { cid ->
                                    val sendParams = mapOf(
                                        "id" to cid,
                                        "text" to text
                                    )
                                    client?.call("sendMessage", sendParams)
                                }
                            } catch (e: Exception) {
                                isStreaming = false
                                stopService(Intent(this@MainActivity, AiStackTaskService::class.java))
                                Toast.makeText(this@MainActivity, "Erro ao enviar: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onInterrupt = {
                        activeConvId?.let { cid ->
                            scope.launch {
                                try {
                                    client?.call("interruptConversation", mapOf("id" to cid))
                                } catch (e: Exception) {
                                    // ignorar
                                }
                            }
                        }
                    },
                    onModelChange = { prov, eff ->
                        currentProvider = prov
                        currentEffort = eff
                    },
                    onPermissionDecision = { reqId, dec ->
                        AiStackConnectionManager.answerPermission(activeConvId ?: "", reqId, dec == "allow")
                        TaskNotificationManager.dismissPermissionNotification(this@MainActivity, reqId)
                    }
                )
            }
        }
    }
}
