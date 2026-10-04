package br.com.amberwrite.aistack.feature.settings

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.BuildConfig
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.ConfirmDialog
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.SectionTitle
import br.com.amberwrite.aistack.feature.common.SwitchRow
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.label
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiChip
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.icons.Lucide

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenDesignCatalog: () -> Unit,
    onUnpaired: () -> Unit
) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = AiTheme.colors
    var confirmUnpair by remember { mutableStateOf(false) }

    FeatureScaffold(title = "Configurações", onBack = onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Conexão")
            Text(
                listOfNotNull(state.connection.label(), state.link?.relay).joinToString(" · "),
                style = AiTheme.typography.caption,
                color = c.fg3
            )
            SwitchRow(
                title = "Manter conectado",
                description = "Mantém a conexão em segundo plano com uma notificação fixa.",
                checked = state.settings.keepConnected,
                onCheckedChange = vm::setKeepConnected
            )
            SwitchRow(
                title = "Iniciar com o aparelho",
                checked = state.settings.startAtBoot,
                onCheckedChange = vm::setStartAtBoot,
                enabled = state.settings.keepConnected
            )
            SwitchRow(
                title = "Avisar quando um turno terminar",
                description = "Só com o app em segundo plano.",
                checked = state.settings.notifyDone,
                onCheckedChange = vm::setNotifyDone
            )

            SectionTitle("Aparência")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Sistema", "dark" to "Escuro", "light" to "Claro").forEach { (id, text) ->
                    AiChip(text = text, selected = state.settings.theme == id, onClick = { vm.setTheme(id) })
                }
            }

            SectionTitle("Este aparelho")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiTextInput(
                    value = state.deviceNameDraft,
                    onValueChange = vm::setNameDraft,
                    label = "Nome mostrado no desktop",
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                AiButton(text = "Salvar", onClick = vm::saveName, enabled = state.nameChanged, size = ButtonSize.Small)
            }
            Text(
                "O desktop vê o nome novo no próximo pareamento.",
                style = AiTheme.typography.caption,
                color = c.fg3
            )

            SectionTitle("Desktop")
            AiButton(text = "Aparelhos pareados", onClick = onOpenDevices, variant = ButtonVariant.Secondary, leadingIcon = Lucide.Smartphone, modifier = Modifier.fillMaxWidth())
            AiButton(text = "Contas e cotas", onClick = onOpenAccounts, variant = ButtonVariant.Secondary, leadingIcon = Lucide.User, modifier = Modifier.fillMaxWidth())
            if (BuildConfig.DEBUG) {
                AiButton(text = "Catálogo do design system", onClick = onOpenDesignCatalog, variant = ButtonVariant.Ghost, leadingIcon = Lucide.Layers, modifier = Modifier.fillMaxWidth())
            }
            AiButton(
                text = "Desparear",
                onClick = { confirmUnpair = true },
                variant = ButtonVariant.Danger,
                leadingIcon = Lucide.Unplug,
                loading = state.unpairing,
                enabled = state.link != null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Text(
                "AiStack Android ${BuildConfig.VERSION_NAME}",
                style = AiTheme.typography.caption,
                color = c.fg3,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    if (confirmUnpair) {
        ConfirmDialog(
            title = "Desparear este aparelho?",
            text = "O desktop deixa de aceitar este celular e as credenciais locais são apagadas. Para voltar, será preciso um novo código de pareamento.",
            confirmLabel = "Desparear",
            destructive = true,
            onConfirm = {
                confirmUnpair = false
                vm.unpair(onUnpaired)
            },
            onDismiss = { confirmUnpair = false }
        )
    }
}
