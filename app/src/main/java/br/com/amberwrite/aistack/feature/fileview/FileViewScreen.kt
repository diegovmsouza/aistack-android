package br.com.amberwrite.aistack.feature.fileview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.data.model.FileContent
import br.com.amberwrite.aistack.feature.common.CenteredLoading
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.formatBytes
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Visualizador somente leitura de um arquivo do desktop (texto em fonte mono). */
@Composable
fun FileViewScreen(conversationId: String, path: String, onBack: () -> Unit) {
    val vm = containerViewModel(key = "fileView:$conversationId:$path") { FileViewViewModel(it, path) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = AiTheme.colors
    val content = state.content

    FeatureScaffold(
        title = path.substringAfterLast('/'),
        subtitle = listOfNotNull(
            path.substringBeforeLast('/', "").ifBlank { null },
            content?.size?.let { formatBytes(it) }
        ).joinToString(" · "),
        onBack = onBack,
        actions = { AiIconButton(icon = Lucide.RefreshCw, contentDescription = "Recarregar", onClick = vm::reload) }
    ) {
        state.error?.let { ErrorStrip(it) }
        if (content?.truncated == true) ErrorStrip("Arquivo grande: mostrando só o início.")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (content) {
                null -> if (state.loading) CenteredLoading(text = "Abrindo arquivo…")
                else EmptyState(title = "Não foi possível abrir", modifier = Modifier.fillMaxSize())
                is FileContent.Binary -> EmptyState(
                    title = "Arquivo binário",
                    body = content.warning ?: listOfNotNull(content.mime, content.size?.let { formatBytes(it) }).joinToString(" · "),
                    modifier = Modifier.fillMaxSize()
                )
                is FileContent.Text -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        Text(content.text, style = AiTheme.typography.mono, color = c.fg, softWrap = false)
                    }
                }
            }
        }
    }
}
