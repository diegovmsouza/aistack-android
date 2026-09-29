package br.com.amberwrite.aistack.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.amberwrite.aistack.model.Provider
import br.com.amberwrite.aistack.relay.PairLink
import br.com.amberwrite.aistack.relay.RelayState
import br.com.amberwrite.aistack.ui.components.BrandHero
import br.com.amberwrite.aistack.ui.theme.AiStackBg
import br.com.amberwrite.aistack.ui.theme.AiStackFg
import br.com.amberwrite.aistack.ui.theme.AiStackFg2
import br.com.amberwrite.aistack.ui.theme.AiStackLine
import br.com.amberwrite.aistack.ui.theme.AiStackSurface2
import br.com.amberwrite.aistack.ui.theme.ProviderClaude
import br.com.amberwrite.aistack.ui.theme.StatusDanger

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun PairScreen(
    relayState: RelayState,
    errorMessage: String?,
    onOpenScanner: () -> Unit,
    onPairWithLink: (PairLink) -> Unit
) {
    val context = LocalContext.current
    var manualText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AiStackBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            BrandHero(provider = Provider.CLAUDE, sizeDp = 80)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Conectar ao AiStack",
                color = AiStackFg,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Controle seus agentes de IA do computador diretamente no celular com criptografia de ponta a ponta.",
                color = AiStackFg2,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botão principal: Escanear QR Code
            Button(
                onClick = onOpenScanner,
                colors = ButtonDefaults.buttonColors(containerColor = ProviderClaude),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = AiStackBg,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Escanear QR Code no PC",
                    color = AiStackBg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "— ou cole o link de pareamento —",
                color = AiStackFg2,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = manualText,
                onValueChange = { manualText = it },
                placeholder = { Text("aistack://pair?relay=...&host=...", color = AiStackFg2.copy(alpha = 0.6f), fontSize = 12.sp) },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ProviderClaude,
                    unfocusedBorderColor = AiStackLine,
                    focusedTextColor = AiStackFg,
                    unfocusedTextColor = AiStackFg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val link = PairLink.parse(manualText)
                    if (link != null) {
                        onPairWithLink(link)
                    } else {
                        Toast.makeText(context, "Link inválido. Deve começar com aistack://pair?...", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = manualText.isNotBlank() && relayState != RelayState.CONNECTING && relayState != RelayState.HANDSHAKING,
                colors = ButtonDefaults.buttonColors(containerColor = AiStackSurface2),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Conectar com Link", color = AiStackFg, fontSize = 14.sp)
            }

            // Status de conexão / Erros
            if (relayState == RelayState.CONNECTING || relayState == RelayState.HANDSHAKING) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = ProviderClaude,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (relayState == RelayState.CONNECTING) "Conectando ao relay..." else "Negociando túnel E2E...",
                        color = AiStackFg2,
                        fontSize = 13.sp
                    )
                }
            }

            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = err,
                    color = StatusDanger,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
