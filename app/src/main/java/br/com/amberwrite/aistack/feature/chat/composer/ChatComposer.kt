package br.com.amberwrite.aistack.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.icons.Lucide

/**
 * Composer do chat (contrato fixo da Onda 2: docs/spec/CONTRATO-ONDA2.md §2).
 *
 * Dono do rascunho, dos anexos, das paletas «/» e «@», do ditado e do envio: com [busy] a mensagem
 * vai para a fila do host; [onInterrupt] interrompe o turno. [initialMention] é inserido uma vez.
 */
@Composable
fun ChatComposer(
    conversationId: String,
    busy: Boolean,
    online: Boolean,
    onInterrupt: () -> Unit,
    modifier: Modifier = Modifier,
    initialMention: String? = null,
) {
    val vm = containerViewModel(key = "composer:$conversationId") { ComposerViewModel(it, conversationId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = AiTheme.colors

    LaunchedEffect(initialMention) { initialMention?.let(vm::insertMention) }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            AiTextInput(
                value = state.draft,
                onValueChange = vm::setDraft,
                placeholder = if (busy) "Mensagem (vai para a fila)" else "Mensagem",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            if (busy) {
                AiIconButton(icon = Lucide.CircleStop, contentDescription = "Interromper", onClick = onInterrupt, tint = c.danger)
            }
            AiIconButton(
                icon = Lucide.Send,
                contentDescription = if (busy) "Enfileirar" else "Enviar",
                onClick = { vm.send(busy) },
                variant = ButtonVariant.Primary,
                enabled = state.draft.isNotBlank() && !state.sending && online
            )
        }
    }
}
