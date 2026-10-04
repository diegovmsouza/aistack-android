package br.com.amberwrite.aistack.feature.pair

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.core.relay.PairLink
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.label
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.icons.Lucide

/**
 * Tela de pareamento. [initialLink] vem de um deep link `aistack://pair?…` (já confirmado
 * pelo usuário quando havia outro desktop pareado). [onPaired] é chamado uma vez quando o
 * host confirma o pareamento.
 */
@Composable
fun PairScreen(
    onPaired: () -> Unit,
    initialLink: PairLink? = null,
    onInitialLinkConsumed: () -> Unit = {}
) {
    val vm = containerViewModel { PairViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialLink) {
        if (initialLink != null) {
            vm.start(initialLink)
            onInitialLinkConsumed()
        }
    }
    LaunchedEffect(state.paired) {
        if (state.paired) onPaired()
    }

    if (state.step == PairViewModel.Step.Scanning) {
        BackHandler { vm.closeScanner() }
        QrScanner(onPairFound = vm::start, onBack = vm::closeScanner)
        return
    }

    FeatureScaffold(title = "Parear com o desktop", onBack = null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (state.step) {
                PairViewModel.Step.Pairing, PairViewModel.Step.Done -> PairingProgress(state, onRetry = vm::reset)
                else -> ChooseMethod(state, vm)
            }
        }
    }
}

@Composable
private fun ChooseMethod(state: PairViewModel.UiState, vm: PairViewModel) {
    val c = AiTheme.colors
    Text(
        "No AiStack do desktop, abra Configurações › Acesso remoto e mostre o QR de pareamento.",
        style = AiTheme.typography.body,
        color = c.fg2
    )
    AiButton(
        text = "Ler QR code",
        onClick = vm::openScanner,
        leadingIcon = Lucide.QrCode,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text("Ou cole o link de pareamento", style = AiTheme.typography.label, color = c.fg3)
    AiTextInput(
        value = state.pasted,
        onValueChange = vm::onPastedChange,
        placeholder = "aistack://pair?relay=…",
        singleLine = true,
        imeAction = ImeAction.Done,
        mono = true,
        modifier = Modifier.fillMaxWidth()
    )
    state.inputError?.let { Text(it, style = AiTheme.typography.caption, color = c.danger) }
    AiButton(
        text = "Parear",
        onClick = vm::submitPasted,
        variant = ButtonVariant.Secondary,
        enabled = state.pasted.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Este aparelho aparecerá no desktop como “${state.deviceName}”.",
        style = AiTheme.typography.caption,
        color = c.fg3
    )
}

@Composable
private fun PairingProgress(state: PairViewModel.UiState, onRetry: () -> Unit) {
    val c = AiTheme.colors
    val failure = state.failure
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (failure == null) {
            Spinner(size = 28.dp)
            Text(
                if (state.paired) "Pareado!" else state.connection.label(),
                style = AiTheme.typography.heading,
                color = c.fg
            )
            Text(
                "Confirme no desktop se ele pedir. O código vale por 10 minutos e só pode ser usado uma vez.",
                style = AiTheme.typography.caption,
                color = c.fg3
            )
        } else {
            Text("Não foi possível parear", style = AiTheme.typography.heading, color = c.danger)
            Text(failure, style = AiTheme.typography.body, color = c.fg2)
            if (state.connection is ConnectionState.AuthRejected) {
                Text(
                    "Gere um novo QR no desktop e tente de novo.",
                    style = AiTheme.typography.caption,
                    color = c.fg3
                )
            }
            AiButton(text = "Tentar outro código", onClick = onRetry, variant = ButtonVariant.Secondary)
        }
    }
}
