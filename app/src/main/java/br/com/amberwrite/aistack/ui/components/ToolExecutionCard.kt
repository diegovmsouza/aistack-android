package br.com.amberwrite.aistack.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.amberwrite.aistack.model.ChatBlock
import br.com.amberwrite.aistack.ui.theme.AiStackFg
import br.com.amberwrite.aistack.ui.theme.AiStackFg2
import br.com.amberwrite.aistack.ui.theme.AiStackLine
import br.com.amberwrite.aistack.ui.theme.AiStackSurface2
import br.com.amberwrite.aistack.ui.theme.DiffAdd
import br.com.amberwrite.aistack.ui.theme.DiffAddFg
import br.com.amberwrite.aistack.ui.theme.DiffDelete
import br.com.amberwrite.aistack.ui.theme.DiffDeleteFg
import br.com.amberwrite.aistack.ui.theme.StatusDanger
import br.com.amberwrite.aistack.ui.theme.StatusOk

@Composable
fun ToolExecutionCard(
    tool: ChatBlock.ToolCall,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AiStackSurface2)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (tool.toolName.contains("bash", true)) Icons.Default.Terminal else Icons.Default.Code,
                contentDescription = null,
                tint = AiStackFg2,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tool.toolName,
                color = AiStackFg,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))

            // Status icon
            when {
                tool.isRunning -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = AiStackFg2
                    )
                }
                tool.isError -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Erro",
                        tint = StatusDanger,
                        modifier = Modifier.size(14.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Sucesso",
                        tint = StatusOk,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = AiStackFg2,
                modifier = Modifier.size(16.dp)
            )
        }

        // Resumo do input
        Text(
            text = tool.input.lines().firstOrNull() ?: "",
            color = AiStackFg2,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            modifier = Modifier.padding(top = 4.dp)
        )

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                // Se houver Diffs
                if (tool.diffAdditions.isNotEmpty() || tool.diffDeletions.isNotEmpty()) {
                    Text(
                        text = "Diff de Alterações:",
                        color = AiStackFg2,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AiStackLine)
                            .padding(6.dp)
                    ) {
                        tool.diffDeletions.forEach { line ->
                            Text(
                                text = "- $line",
                                color = DiffDeleteFg,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DiffDelete.copy(alpha = 0.4f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        tool.diffAdditions.forEach { line ->
                            Text(
                                text = "+ $line",
                                color = DiffAddFg,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DiffAdd.copy(alpha = 0.4f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Saída completa
                tool.output?.let { out ->
                    Spacer(modifier = Modifier.padding(top = 6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AiStackLine.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = out,
                            color = if (tool.isError) DiffDeleteFg else AiStackFg2,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
