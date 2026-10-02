package br.com.amberwrite.aistack.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.amberwrite.aistack.model.ChatBlock
import br.com.amberwrite.aistack.model.ChatMessage
import br.com.amberwrite.aistack.model.Provider
import br.com.amberwrite.aistack.relay.RelayState
import br.com.amberwrite.aistack.ui.components.BrandHero
import br.com.amberwrite.aistack.ui.components.MarkdownText
import br.com.amberwrite.aistack.ui.components.ModelPickerSheet
import br.com.amberwrite.aistack.ui.components.PermissionCard
import br.com.amberwrite.aistack.ui.components.ThinkingCard
import br.com.amberwrite.aistack.ui.components.ToolExecutionCard
import br.com.amberwrite.aistack.ui.theme.AiStackBg
import br.com.amberwrite.aistack.ui.theme.AiStackFg
import br.com.amberwrite.aistack.ui.theme.AiStackFg2
import br.com.amberwrite.aistack.ui.theme.AiStackFg3
import br.com.amberwrite.aistack.ui.theme.AiStackLine
import br.com.amberwrite.aistack.ui.theme.AiStackSurface
import br.com.amberwrite.aistack.ui.theme.AiStackSurface2
import br.com.amberwrite.aistack.ui.theme.AiStackSurface3
import br.com.amberwrite.aistack.ui.theme.ProviderClaude
import br.com.amberwrite.aistack.ui.theme.StatusDanger
import br.com.amberwrite.aistack.ui.theme.StatusOk
import br.com.amberwrite.aistack.ui.theme.StatusWarn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationTitle: String,
    messages: List<ChatMessage>,
    currentProvider: Provider,
    currentEffort: String,
    relayState: RelayState,
    isStreaming: Boolean,
    onOpenDrawer: () -> Unit,
    onSendMessage: (String) -> Unit,
    onInterrupt: () -> Unit,
    onModelChange: (Provider, String) -> Unit,
    onPermissionDecision: (requestId: String, decision: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var promptInput by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }

    // Speech-to-Text Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                promptInput = if (promptInput.isBlank()) spokenText else "$promptInput $spokenText"
            }
        }
    }

    // Auto-scroll para a última mensagem
    LaunchedEffect(messages.size, messages.lastOrNull()?.blocks?.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AiStackSurface2)
                            .clickable { showModelPicker = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        BrandHero(provider = currentProvider, sizeDp = 22)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${currentProvider.displayName} • ${currentEffort.uppercase()}",
                            color = Color(currentProvider.colorHex),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = AiStackFg2,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Abrir Menu",
                            tint = AiStackFg
                        )
                    }
                },
                actions = {
                    // Pílula de status do Host
                    val (statusColor, statusText) = when (relayState) {
                        RelayState.ONLINE -> Pair(StatusOk, "Host Online")
                        RelayState.CONNECTING, RelayState.HANDSHAKING -> Pair(StatusWarn, "Conectando...")
                        else -> Pair(StatusDanger, "Offline")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AiStackSurface)
            )
        },
        containerColor = AiStackBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Feed de mensagens
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BrandHero(provider = currentProvider, sizeDp = 72)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Como posso ajudar você hoje?",
                                color = AiStackFg,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "O AiStack executa comandos locais reais no seu PC.",
                                color = AiStackFg3,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        provider = currentProvider,
                        onPermissionDecision = onPermissionDecision
                    )
                }
            }

            // Composer Flutuante Mobile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AiStackSurface)
                    .border(width = 1.dp, color = AiStackLine, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Botão Microfone (Speech to text)
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com o AiStack...")
                            }
                            speechLauncher.launch(intent)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Falar",
                            tint = AiStackFg2
                        )
                    }

                    // Campo de Texto do Prompt
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = { Text("Comande o AiStack...", color = AiStackFg3, fontSize = 14.sp) },
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = AiStackFg,
                            unfocusedTextColor = AiStackFg,
                            focusedContainerColor = AiStackSurface2,
                            unfocusedContainerColor = AiStackSurface2
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )

                    // Botão Enviar ou Interromper
                    if (isStreaming) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onInterrupt()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(StatusDanger)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Interromper",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (promptInput.isNotBlank()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val text = promptInput.trim()
                                    promptInput = ""
                                    onSendMessage(text)
                                }
                            },
                            enabled = promptInput.isNotBlank() && relayState == RelayState.ONLINE,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (promptInput.isNotBlank() && relayState == RelayState.ONLINE)
                                        Color(currentProvider.colorHex)
                                    else
                                        AiStackSurface3
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Enviar",
                                tint = if (promptInput.isNotBlank() && relayState == RelayState.ONLINE) AiStackBg else AiStackFg3,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            currentProvider = currentProvider,
            currentEffort = currentEffort,
            onSelect = onModelChange,
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    provider: Provider,
    onPermissionDecision: (requestId: String, decision: String) -> Unit
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            BrandHero(provider = provider, sizeDp = 28, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isUser) AiStackSurface2 else Color.Transparent)
                .padding(if (isUser) 12.dp else 0.dp)
        ) {
            message.blocks.forEach { block ->
                when (block) {
                    is ChatBlock.Text -> {
                        MarkdownText(text = block.text)
                    }
                    is ChatBlock.Thinking -> {
                        ThinkingCard(thinking = block, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    is ChatBlock.ToolCall -> {
                        ToolExecutionCard(tool = block, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    is ChatBlock.Permission -> {
                        PermissionCard(
                            permission = block,
                            onDecision = { dec -> onPermissionDecision(block.requestId, dec) },
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
