package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Tipo de anexo — define o ícone quando não há miniatura. */
enum class AttachmentKind { Image, File, Code, Text, Audio }

/** Ícone Lucide de cada [AttachmentKind]. */
fun AttachmentKind.icon(): ImageVector = when (this) {
    AttachmentKind.Image -> Lucide.Image
    AttachmentKind.File -> Lucide.File
    AttachmentKind.Code -> Lucide.FileCode
    AttachmentKind.Text -> Lucide.FileText
    AttachmentKind.Audio -> Lucide.Mic
}

/**
 * Chip de anexo do composer/mensagem: miniatura (ou ícone do tipo), nome, tamanho e botão de
 * remover. Durante o envio mostra uma barra de progresso na base; com erro, borda `danger`.
 *
 * @param thumbnail miniatura já decodificada (ex.: `BitmapPainter`); `null` usa o ícone de [kind].
 * @param progress 0..1 durante o upload; `null` = concluído/sem upload.
 * @param onRemove `null` esconde o X (anexo já enviado, somente leitura).
 */
@Composable
fun AttachmentChip(
    name: String,
    modifier: Modifier = Modifier,
    kind: AttachmentKind = AttachmentKind.File,
    sizeLabel: String? = null,
    thumbnail: Painter? = null,
    progress: Float? = null,
    error: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    haptics: AiHaptics = AiHaptics.None,
) {
    val c = AiTheme.colors
    val shape = AiTheme.shapes.md
    val animatedProgress by animateFloatAsState(progress ?: 1f, AiTheme.motion.spring(), label = "attachProgress")
    val tone = if (error) c.danger else c.fg2
    Box(
        modifier
            .widthIn(max = 240.dp)
            .clip(shape)
            .background(if (error) c.dangerSoft else c.surface2)
            .border(1.dp, if (error) c.danger.copy(alpha = 0.4f) else c.line, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick() } else Modifier),
    ) {
        Row(
            Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = if (onRemove != null) 4.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(AiTheme.shapes.sm)
                    .background(c.surface3),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(thumbnail, null, Modifier.size(32.dp), contentScale = ContentScale.Crop)
                } else {
                    Icon(if (error) Lucide.TriangleAlert else kind.icon(), null, Modifier.size(16.dp), tint = tone)
                }
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(name, style = AiTheme.typography.label, color = c.fg, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                val sub = when {
                    error -> "Falha no envio"
                    progress != null && progress < 1f -> "Enviando… ${(progress * 100).toInt()}%"
                    else -> sizeLabel
                }
                if (sub != null) Text(sub, style = AiTheme.typography.caption, color = if (error) c.danger else c.fg3, maxLines = 1)
            }
            if (onRemove != null) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(AiTheme.shapes.pill)
                        .clickable(role = Role.Button) {
                            haptics.perform(HapticKind.Tick)
                            onRemove()
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.X, "Remover anexo", Modifier.size(14.dp), tint = c.fg3) }
            }
        }
        if (progress != null && progress < 1f && !error) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(c.line),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(c.accent),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@AiPreviews
@Composable
private fun AttachmentChipPreview() {
    PreviewSurface {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachmentChip("captura-de-tela.png", kind = AttachmentKind.Image, sizeLabel = "412 KB", onRemove = {})
            AttachmentChip("RelayClient.kt", kind = AttachmentKind.Code, progress = 0.42f, onRemove = {})
            AttachmentChip("relatorio-final-versao-2.pdf", sizeLabel = "1,2 MB")
            AttachmentChip("audio.m4a", kind = AttachmentKind.Audio, error = true, onRemove = {})
        }
    }
}
