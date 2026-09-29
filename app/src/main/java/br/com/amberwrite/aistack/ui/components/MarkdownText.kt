package br.com.amberwrite.aistack.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.amberwrite.aistack.ui.theme.AiStackFg
import br.com.amberwrite.aistack.ui.theme.AiStackFg2
import br.com.amberwrite.aistack.ui.theme.AiStackLine
import br.com.amberwrite.aistack.ui.theme.AiStackSurface2
import br.com.amberwrite.aistack.ui.theme.ProviderClaude

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val blocks = parseMarkdownBlocks(text)

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> {
                    Text(
                        text = block.annotated,
                        color = AiStackFg,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                is MdBlock.CodeFence -> {
                    CodeBlockView(block = block, context = context)
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: MdBlock.CodeFence, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AiStackSurface2)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AiStackLine.copy(alpha = 0.3f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = block.language.ifEmpty { "código" },
                color = AiStackFg2,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("code", block.code)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Código copiado!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copiar código",
                    tint = AiStackFg2,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Box(modifier = Modifier.padding(10.dp)) {
            Text(
                text = block.code,
                color = AiStackFg,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp
            )
        }
    }
}

sealed class MdBlock {
    data class Paragraph(val annotated: androidx.compose.ui.text.AnnotatedString) : MdBlock()
    data class CodeFence(val language: String, val code: String) : MdBlock()
}

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val result = mutableListOf<MdBlock>()
    val lines = text.lines()
    var inCodeFence = false
    var codeLang = ""
    val codeBuffer = StringBuilder()
    val paraBuffer = StringBuilder()

    fun flushPara() {
        if (paraBuffer.isNotEmpty()) {
            result.add(MdBlock.Paragraph(parseInlineMarkdown(paraBuffer.toString())))
            paraBuffer.clear()
        }
    }

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeFence) {
                // Fim do bloco de código
                result.add(MdBlock.CodeFence(codeLang, codeBuffer.toString().trimEnd()))
                codeBuffer.clear()
                inCodeFence = false
            } else {
                flushPara()
                inCodeFence = true
                codeLang = line.trim().removePrefix("```").trim()
            }
        } else if (inCodeFence) {
            if (codeBuffer.isNotEmpty()) codeBuffer.append("\n")
            codeBuffer.append(line)
        } else {
            if (paraBuffer.isNotEmpty()) paraBuffer.append("\n")
            paraBuffer.append(line)
        }
    }

    if (inCodeFence) {
        result.add(MdBlock.CodeFence(codeLang, codeBuffer.toString().trimEnd()))
    }
    flushPara()

    return result
}

private fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            } else if (text.startsWith("*", i) && !text.startsWith("**", i)) {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            } else if (text.startsWith("`", i) && !text.startsWith("```", i)) {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = AiStackSurface2,
                            color = ProviderClaude
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}
