package br.com.amberwrite.aistack.feature.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.EntryKind
import br.com.amberwrite.aistack.feature.common.CenteredLoading
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.formatBytes
import br.com.amberwrite.aistack.feature.common.relativeTime
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Navegador de arquivos (somente leitura) do projeto de uma conversa. */
@Composable
fun FilesScreen(
    conversationId: String,
    path: String?,
    onBack: () -> Unit,
    onOpenDir: (String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val vm = containerViewModel(key = "files:$conversationId:$path") { FilesViewModel(it, conversationId, path) }
    val state by vm.state.collectAsStateWithLifecycle()

    FeatureScaffold(
        title = state.path?.substringAfterLast('/')?.ifBlank { state.path } ?: "Arquivos",
        subtitle = state.path,
        onBack = onBack,
        actions = { AiIconButton(icon = Lucide.RefreshCw, contentDescription = "Recarregar", onClick = vm::reload) }
    ) {
        state.error?.let { ErrorStrip(it) }
        if (state.truncated) ErrorStrip("Listagem truncada pelo desktop.")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.entries.isEmpty() -> CenteredLoading(text = "Carregando…")
                state.entries.isEmpty() -> EmptyState(
                    title = if (state.error != null) "Não foi possível listar" else "Pasta vazia",
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.entries, key = { it.name }) { entry ->
                        EntryRow(entry) {
                            val p = vm.childPath(entry)
                            if (entry.kind == EntryKind.DIR) onOpenDir(p) else onOpenFile(p)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: DirEntry, onClick: () -> Unit) {
    val c = AiTheme.colors
    SurfaceCard(
        onClick = if (entry.kind == EntryKind.OTHER) null else onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (entry.kind == EntryKind.DIR) Lucide.Folder else Lucide.File,
                contentDescription = null,
                tint = if (entry.kind == EntryKind.DIR) c.accent else c.fg2,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                entry.name,
                style = AiTheme.typography.body,
                color = c.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val meta = buildList {
                if (entry.kind == EntryKind.FILE && entry.size != null) add(formatBytes(entry.size))
                if (entry.mtime != null) add(relativeTime(entry.mtime))
            }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = AiTheme.typography.caption, color = c.fg3, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
