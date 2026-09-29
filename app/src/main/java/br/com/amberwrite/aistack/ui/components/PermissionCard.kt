package br.com.amberwrite.aistack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.amberwrite.aistack.model.ChatBlock
import br.com.amberwrite.aistack.ui.theme.AiStackFg
import br.com.amberwrite.aistack.ui.theme.AiStackFg2
import br.com.amberwrite.aistack.ui.theme.AiStackSurface2
import br.com.amberwrite.aistack.ui.theme.ProviderClaude
import br.com.amberwrite.aistack.ui.theme.StatusDanger
import br.com.amberwrite.aistack.ui.theme.StatusOk
import br.com.amberwrite.aistack.ui.theme.StatusWarn

@Composable
fun PermissionCard(
    permission: ChatBlock.Permission,
    onDecision: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AiStackSurface2)
            .border(1.dp, StatusWarn.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = StatusWarn,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Permissão necessária",
                color = AiStackFg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "A IA precisa de autorização para executar:",
            color = AiStackFg2,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = permission.commandOrFile,
            color = ProviderClaude,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(AiStackSurface2.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (permission.isDecided) {
            Text(
                text = "Decisão registrada: ${permission.decision?.uppercase()}",
                color = if (permission.decision == "allow") StatusOk else StatusDanger,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDecision("allow")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOk),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Permitir", color = AiStackFg, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDecision("deny")
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Negar", fontSize = 13.sp)
                }
            }
        }
    }
}
