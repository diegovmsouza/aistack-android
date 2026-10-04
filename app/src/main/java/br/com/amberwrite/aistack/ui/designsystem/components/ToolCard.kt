package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Categoria de ferramenta — define o ícone do [ToolCard]. */
enum class ToolKind { File, Edit, Terminal, Search, Web, Agent, Todo, Tool }

/** Estado de execução de uma ferramenta. */
enum class ToolStatus { Pending, Running, Success, Error }

/** Ícone Lucide de cada [ToolKind]. */
fun ToolKind.icon(): ImageVector = when (this) {
    ToolKind.File -> Lucide.FileText
    ToolKind.Edit -> Lucide.FilePen
    ToolKind.Terminal -> Lucide.SquareTerminal
    ToolKind.Search -> Lucide.Search
    ToolKind.Web -> Lucide.Globe
    ToolKind.Agent -> Lucide.Bot
    ToolKind.Todo -> Lucide.ListTodo
    ToolKind.Tool -> Lucide.Wrench
}

/** Indicador compacto de [ToolStatus] (spinner, check, X ou ponto). */
@Composable
fun ToolStatusIndicator(status: ToolStatus, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val c = AiTheme.colors
    when (status) {
        ToolStatus.Pending -> StatusDot(AgentStatus.Pending, modifier, size = size * 0.6f)
        ToolStatus.Running -> Spinner(modifier, size = size, color = c.accent)
        ToolStatus.Success -> Icon(Lucide.CircleCheck, "Concluída", modifier.size(size), tint = c.ok)
        ToolStatus.Error -> Icon(Lucide.CircleX, "Falhou", modifier.size(size), tint = c.danger)
    }
}

/**
 * Cartão de chamada de ferramenta. O cabeçalho mostra ícone, título, detalhe em mono (ex.:
 * caminho/comando), duração e status; tocar expande o corpo com mola (instantâneo com
 * movimento reduzido).
 *
 * O estado de expansão pode ser controlado ([expanded] + [onExpandedChange]) ou interno
 * (deixe [expanded] `null`).
 */
@Composable
fun ToolCard(
    title: String,
    kind: ToolKind,
    status: ToolStatus,
    modifier: Modifier = Modifier,
    detail: String? = null,
    meta: String? = null,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    haptics: AiHaptics = AiHaptics.None,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = AiTheme.colors
    val motion = AiTheme.motion
    var internal by rememberSaveable { mutableStateOf(false) }
    val isExpanded = expanded ?: internal
    val chevron by animateFloatAsState(if (isExpanded) 180f else 0f, motion.spring(), label = "toolChevron")
    val tone = when (status) {
        ToolStatus.Error -> c.danger
        ToolStatus.Running -> c.accent
        else -> c.fg2
    }
    val shape = AiTheme.shapes.md
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, if (status == ToolStatus.Error) c.danger.copy(alpha = 0.35f) else c.line, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (content != null) {
                        Modifier
                            .clickable(role = Role.Button) {
                                haptics.perform(HapticKind.Tick)
                                val next = !isExpanded
                                internal = next
                                onExpandedChange?.invoke(next)
                            }
                            .semantics { stateDescription = if (isExpanded) "Expandido" else "Recolhido" }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(AiTheme.shapes.xs)
                    .background(c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(kind.icon(), null, Modifier.size(14.dp), tint = tone)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = AiTheme.typography.label, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(detail, style = AiTheme.typography.monoSmall, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (meta != null) Text(meta, style = AiTheme.typography.caption, color = c.fg3)
            ToolStatusIndicator(status)
            if (content != null) {
                Icon(Lucide.ChevronDown, null, Modifier.size(16.dp).rotate(chevron), tint = c.fg3)
            }
        }
        if (content != null) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(motion.spring(), expandFrom = Alignment.Top) + fadeIn(motion.fade()),
                exit = shrinkVertically(motion.spring(), shrinkTowards = Alignment.Top) + fadeOut(motion.fade(120)),
            ) {
                Column {
                    HorizontalDivider(color = c.line, thickness = 1.dp)
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                }
            }
        }
    }
}

/**
 * Bloco de código/saída monoespaçado com rolagem horizontal e altura máxima.
 * @param tone cor do texto (ex.: `danger` para stderr).
 */
@Composable
fun CodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 220.dp,
    tone: Color = AiTheme.colors.fg2,
    background: Color = AiTheme.colors.surface2,
) {
    val shape = AiTheme.shapes.sm
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(shape)
            .background(background)
            .border(1.dp, AiTheme.colors.line, shape)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(10.dp),
    ) {
        Text(text, style = AiTheme.typography.monoSmall, color = tone, softWrap = false)
    }
}

@AiPreviews
@Composable
private fun ToolCardPreview() {
    PreviewSurface {
        ToolCard("Ler arquivo", ToolKind.File, ToolStatus.Success, detail = "src/main/App.kt", meta = "0,2 s")
        ToolCard("Executar comando", ToolKind.Terminal, ToolStatus.Running, detail = "./gradlew test", expanded = true) {
            CodeBlock("> Task :app:test\nBUILD SUCCESSFUL in 12s")
        }
        ToolCard("Buscar na web", ToolKind.Web, ToolStatus.Error, detail = "kotlin coroutines flow") {
            CodeBlock("Tempo esgotado", tone = AiTheme.colors.danger)
        }
    }
}
