package br.com.amberwrite.aistack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.amberwrite.aistack.model.AccountStatus
import br.com.amberwrite.aistack.model.ConversationItem
import br.com.amberwrite.aistack.ui.components.BrandHero
import br.com.amberwrite.aistack.ui.theme.AiStackBg
import br.com.amberwrite.aistack.ui.theme.AiStackFg
import br.com.amberwrite.aistack.ui.theme.AiStackFg2
import br.com.amberwrite.aistack.ui.theme.AiStackFg3
import br.com.amberwrite.aistack.ui.theme.AiStackLine
import br.com.amberwrite.aistack.ui.theme.AiStackSurface
import br.com.amberwrite.aistack.ui.theme.AiStackSurface2
import br.com.amberwrite.aistack.ui.theme.ProviderClaude
import br.com.amberwrite.aistack.ui.theme.StatusDanger
import br.com.amberwrite.aistack.ui.theme.StatusOk
import br.com.amberwrite.aistack.ui.theme.StatusWarn

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun DrawerContent(
    conversations: List<ConversationItem>,
    accounts: List<AccountStatus>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDisconnectHost: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(AiStackSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // Cabeçalho da Gaveta
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(StatusOk)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AiStack Remote",
                color = AiStackFg,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Botão Nova Conversa
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AiStackSurface2)
                .clickable { onNewConversation() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = ProviderClaude,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Nova Conversa",
                color = AiStackFg,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Conversas Recentes",
            color = AiStackFg3,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Lista de conversas
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(conversations) { conv ->
                val isSelected = conv.id == activeConversationId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AiStackLine else Color.Transparent)
                        .clickable { onSelectConversation(conv.id) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color(conv.provider.colorHex),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = conv.title.ifEmpty { "Sem título" },
                        color = if (isSelected) AiStackFg else AiStackFg2,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Divider(color = AiStackLine, modifier = Modifier.padding(vertical = 12.dp))

        // Monitor de Uso das Contas
        if (accounts.isNotEmpty()) {
            Text(
                text = "Uso de Cotas (CLIs)",
                color = AiStackFg3,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            accounts.take(3).forEach { acc ->
                val firstWindow = acc.windows.firstOrNull()
                val pct = firstWindow?.usedPct ?: 0.0
                val statusColor = when {
                    pct >= 95.0 -> StatusDanger
                    pct >= 80.0 -> StatusWarn
                    else -> StatusOk
                }

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${acc.provider.displayName} (${acc.slot.uppercase()})",
                            color = Color(acc.provider.colorHex),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${pct.toInt()}%",
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = statusColor,
                        trackColor = AiStackSurface2,
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Botão Desconectar Host
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDisconnectHost() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LinkOff,
                contentDescription = null,
                tint = AiStackFg3,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Desconectar Host",
                color = AiStackFg3,
                fontSize = 12.sp
            )
        }
    }
}
