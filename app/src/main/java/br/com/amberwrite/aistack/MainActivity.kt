package br.com.amberwrite.aistack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import br.com.amberwrite.aistack.model.AccountParser
import br.com.amberwrite.aistack.model.AccountStatus
import br.com.amberwrite.aistack.model.ChatEffect
import br.com.amberwrite.aistack.model.ChatBlock
import br.com.amberwrite.aistack.model.ChatMessage
import br.com.amberwrite.aistack.model.ChatReducer
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
    /** Link recebido (deep link, QR ou colado) aguardando a confirmação do usuário (N-03). */
    private var pendingLink by mutableStateOf<PairLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tentar restaurar conexão salva ou verificar deep link de entrada
        val deepLink = intent?.data?.toString()
        val initialPairLink = if (deepLink != null) PairLink.parse(deepLink) else null

        // Um link que chega de fora (navegador, mensagem) NUNCA troca o computador pareado sozinho.
        pendingLink = initialPairLink
        val savedLink = AiStackConnectionManager.getSavedPairLink(this)
        if (savedLink != null) {
            activeRelayClient = AiStackConnectionManager.connectWith(this, savedLink)
        }

        setContent {
            AiStackTheme {
                MainContent()
                pendingLink?.let { link ->
                    PairConfirmDialog(
                        link = link,
                        replacing = AiStackConnectionManager.getSavedPairLink(this) != null,
                        onConfirm = {
                            pendingLink = null
                            connectWithLink(link)
                        },
                        onDismiss = { pendingLink = null }
                    )
                }
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
                pendingLink = link
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

    @Composable
    private fun PairConfirmDialog(link: PairLink, replacing: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Parear com este computador?") },
            text = {
                Text(
                    "Relay: ${link.relayHost()}\n" +
                        "Impressão digital do computador:\n${link.fingerprint()}\n\n" +
                        "Confira que ela é a mesma mostrada no AiStack do seu computador. " +
                        (if (replacing) "O computador atualmente pareado será substituído. " else "") +
                        "Se você não pediu este pareamento, toque em Cancelar."
                )
            },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Parear") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        )
    }

    private fun connectWithLink(link: PairLink) {
        AiStackConnectionManager.savePairLink(this, link)
        activeRelayClient = AiStackConnectionManager.connectWith(this, link)
        isScannerOpen = false
    }

    private suspend fun refreshAccounts(client: RelayClient?, accounts: MutableList<AccountStatus>) {
        try {
            val parsed = AccountParser.parse(client?.call("listAccounts")?.takeIf { it.isJsonArray }?.asJsonArray)
            accounts.clear()
            accounts.addAll(parsed)
        } catch (e: Exception) {
            Log.w("MainActivity", "listAccounts falhou: ${e.message}")
        }
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
        // Conversa dona do turno em curso (e do serviço em primeiro plano), mesmo que a tela mostre outra.
        var streamingConvId by remember { mutableStateOf<String?>(null) }

        // Escuta eventos ao vivo do Host. Os eventos de TODAS as conversas chegam aqui: só os da
        // conversa aberta mexem no chat; o pedido de permissão de qualquer uma vira notificação.
        LaunchedEffect(client) {
            client?.events?.collect { event ->
                when (event.event) {
                    "accounts-update", "usage-update" -> refreshAccounts(client, accounts)
                    "conv-event" -> {
                        val p = event.payload?.takeIf { it.isJsonObject }?.asJsonObject ?: return@collect
                        val convId = p.get("conversationId")?.asString
                        val inner = p.get("event")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@collect
                        val mine = convId != null && convId == activeConvId
                        // Fora da conversa aberta aplica-se o evento a uma lista descartável: só o efeito importa.
                        val effect = ChatReducer.apply(if (mine) messages else mutableListOf(), inner) {
                            java.util.UUID.randomUUID().toString()
                        }
                        when (effect) {
                            is ChatEffect.PermissionAsked -> TaskNotificationManager.showPermissionNotification(
                                this@MainActivity, effect.requestId, convId ?: "", effect.tool, effect.input
                            )
                            is ChatEffect.PermissionCancelled ->
                                TaskNotificationManager.dismissPermissionNotification(this@MainActivity, effect.requestId)
                            is ChatEffect.TurnEnded -> {
                                if (convId != null && convId == streamingConvId) {
                                    streamingConvId = null
                                    stopService(Intent(this@MainActivity, AiStackTaskService::class.java))
                                }
                                if (mine) isStreaming = false
                            }
                            ChatEffect.None -> {
                                if (mine && inner.get("type")?.asString == "ToolStart") {
                                    val name = inner.get("name")?.asString ?: "ferramenta"
                                    startService(Intent(this@MainActivity, AiStackTaskService::class.java).apply {
                                        action = AiStackTaskService.ACTION_UPDATE
                                        putExtra(AiStackTaskService.EXTRA_DESCRIPTION, "Executando $name...")
                                    })
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
                        refreshAccounts(client, accounts)
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
                    isScannerOpen = false
                    pendingLink = link
                },
                onBack = { isScannerOpen = false }
            )
        } else if (relayState != RelayState.ONLINE && activeRelayClient == null) {
            PairScreen(
                relayState = relayState,
                errorMessage = lastError,
                onOpenScanner = { isScannerOpen = true },
                onPairWithLink = { link -> pendingLink = link }
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
                            isStreaming = id == streamingConvId
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
                            // O turno em curso não fica órfão: pede ao host para interrompê-lo.
                            val running = streamingConvId
                            if (running != null) {
                                scope.launch {
                                    try {
                                        client?.call("interrupt", mapOf("id" to running))
                                    } catch (e: Exception) {
                                        Log.w("MainActivity", "interrupt ao abrir conversa nova: ${e.message}")
                                    }
                                }
                            }
                            activeConvId = null
                            messages.clear()
                            isStreaming = false
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
                        streamingConvId = activeConvId

                        // Inicia Foreground Service de acompanhamento
                        val sIntent = Intent(this@MainActivity, AiStackTaskService::class.java).apply {
                            action = AiStackTaskService.ACTION_START
                            putExtra(AiStackTaskService.EXTRA_DESCRIPTION, "Turno da IA em andamento")
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
                                    streamingConvId = activeConvId
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
                                streamingConvId = null
                                stopService(Intent(this@MainActivity, AiStackTaskService::class.java))
                                Toast.makeText(this@MainActivity, "Erro ao enviar: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onInterrupt = {
                        activeConvId?.let { cid ->
                            scope.launch {
                                try {
                                    client?.call("interrupt", mapOf("id" to cid))
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "Não foi possível interromper: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onModelChange = { prov, eff ->
                        currentProvider = prov
                        currentEffort = eff
                    },
                    onPermissionDecision = { reqId, dec ->
                        val cid = activeConvId
                        if (cid == null) {
                            Toast.makeText(this@MainActivity, "Nenhuma conversa aberta para responder.", Toast.LENGTH_SHORT).show()
                        } else {
                            AiStackConnectionManager.answerPermission(cid, reqId, dec == "allow") { ok ->
                                // Só marca e descarta depois que o host confirmou: falha deixa o cartão para nova tentativa.
                                if (ok) {
                                    ChatReducer.markPermission(messages, reqId, dec)
                                    TaskNotificationManager.dismissPermissionNotification(this@MainActivity, reqId)
                                } else {
                                    Toast.makeText(this@MainActivity, "O host não recebeu a resposta. Tente de novo.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
