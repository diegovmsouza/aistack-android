package br.com.amberwrite.aistack.feature.newsession

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.data.model.PermissionMode
import br.com.amberwrite.aistack.data.model.Provider
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiChip
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Nova conversa. [onCreated] recebe o id da conversa criada (a tela substitui a si mesma pelo chat). */
@Composable
fun NewSessionScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val vm = containerViewModel { NewSessionViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = AiTheme.colors

    FeatureScaffold(title = "Nova conversa", onBack = onBack) {
        state.error?.let { ErrorStrip(it) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Provedor", style = AiTheme.typography.label, color = c.fg2)
            ChipRow {
                Provider.selectable.forEach { p ->
                    AiChip(text = p.displayName, selected = state.provider == p, onClick = { vm.setProvider(p) })
                }
            }
            if (state.models.isNotEmpty()) {
                Text("Modelo", style = AiTheme.typography.label, color = c.fg2)
                ChipRow {
                    AiChip(text = "Padrão", selected = state.model == null, onClick = { vm.setModel(null) })
                    state.models.forEach { m ->
                        AiChip(text = m.displayName, selected = state.model == m.id, onClick = { vm.setModel(m.id) })
                    }
                }
            }
            Text("Projeto", style = AiTheme.typography.label, color = c.fg2)
            AiTextInput(
                value = state.projectPath,
                onValueChange = vm::setProjectPath,
                placeholder = "/home/usuario/projeto",
                singleLine = true,
                mono = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.recentProjects.isNotEmpty()) {
                ChipRow {
                    state.recentProjects.take(8).forEach { path ->
                        AiChip(
                            text = path.substringAfterLast('/').ifBlank { path },
                            selected = state.projectPath == path,
                            leadingIcon = Lucide.Folder,
                            onClick = { vm.setProjectPath(path) }
                        )
                    }
                }
            }
            Text("Permissões", style = AiTheme.typography.label, color = c.fg2)
            ChipRow {
                PermissionMode.entries.forEach { m ->
                    AiChip(text = m.label, selected = state.permissionMode == m, onClick = { vm.setPermissionMode(m) })
                }
            }
            Text("Primeira mensagem (opcional)", style = AiTheme.typography.label, color = c.fg2)
            AiTextInput(
                value = state.message,
                onValueChange = vm::setMessage,
                placeholder = "O que você quer fazer?",
                modifier = Modifier.fillMaxWidth()
            )
            AiButton(
                text = "Criar conversa",
                onClick = { vm.create(onCreated) },
                enabled = state.canCreate,
                loading = state.creating,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}
