package br.com.amberwrite.aistack.feature.chat.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.ModelInfo
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.EffortSlider
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.ProviderBadge
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.designsystem.components.TagBadge
import br.com.amberwrite.aistack.ui.designsystem.illustrations.Illustration
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Folha de escolha de modelo e esforço da conversa (salva via `setConversationOptions`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEffortSheet(
    state: ComposerUiState,
    onSelectModel: (ModelInfo) -> Unit,
    onSelectEffort: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    haptics: AiHaptics = AiHaptics.None,
) {
    val c = AiTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AiTheme.shapes.sheet,
        containerColor = c.surface,
        contentColor = c.fg,
        scrimColor = c.scrim,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.composer_options_title),
                    style = AiTheme.typography.heading,
                    color = c.fg,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(state.optionsSaving, enter = fadeIn(AiTheme.motion.fade()), exit = fadeOut(AiTheme.motion.exit())) {
                    Spinner(size = 16.dp)
                }
                ProviderBadge(providerId = state.provider.id.ifEmpty { null })
            }
            state.optionsError?.let { ErrorStrip(it, Modifier.clip(AiTheme.shapes.sm)) }

            Text(stringResource(R.string.composer_options_model), style = AiTheme.typography.overline, color = c.fg3)
            val catalog = state.catalog ?: ComposerLoad.Loading
            val motion = AiTheme.motion
            AnimatedContent(
                targetState = catalog,
                contentKey = { it::class },
                transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.exit()) },
                label = "catalog",
            ) { load ->
                when (load) {
                    ComposerLoad.Loading -> Column { repeat(3) { SkeletonRow(titleFraction = 0.4f + it * 0.12f) } }
                    is ComposerLoad.Failed -> RetryPanel(load.message, onRetry)
                    is ComposerLoad.Ready -> if (load.value.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.composer_options_empty_title),
                            body = stringResource(R.string.composer_options_empty_body),
                            illustration = Illustration.NoPending,
                            illustrationWidth = 96.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(load.value, key = { it.id }) { model ->
                                ModelRow(
                                    model = model,
                                    selected = model.id == state.model || (state.model == null && model.isDefault),
                                    onClick = {
                                        haptics.perform(HapticKind.Tick)
                                        onSelectModel(model)
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            val models = (catalog as? ComposerLoad.Ready)?.value.orEmpty()
            val current = models.firstOrNull { it.id == state.model } ?: models.firstOrNull { state.model == null && it.isDefault }
            val levels = current?.efforts.orEmpty()
            Text(stringResource(R.string.composer_options_effort), style = AiTheme.typography.overline, color = c.fg3)
            if (levels.isNotEmpty()) {
                EffortSlider(
                    levels = levels,
                    selectedIndex = effortIndex(levels, state.effort, current?.defaultEffort),
                    onSelect = { i -> levels.getOrNull(i)?.let(onSelectEffort) },
                    providerId = state.provider.id.ifEmpty { null },
                    enabled = !state.optionsSaving,
                    haptics = haptics,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    if (current == null) stringResource(R.string.composer_options_effort_pick_model)
                    else stringResource(R.string.composer_options_effort_none),
                    style = AiTheme.typography.bodySmall,
                    color = c.fg3,
                )
            }
        }
    }
}

@Composable
private fun ModelRow(model: ModelInfo, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    val shape = AiTheme.shapes.md
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.10f) else c.surface2.copy(alpha = 0f))
            .border(1.dp, if (selected) c.accent.copy(alpha = 0.4f) else c.line, shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Lucide.Check, null, Modifier.size(18.dp), tint = c.accent)
            else Icon(Lucide.Cpu, null, Modifier.size(16.dp), tint = c.fg3)
        }
        Column(Modifier.weight(1f)) {
            Text(
                model.displayName.ifBlank { model.id },
                style = AiTheme.typography.body,
                color = c.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            model.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = AiTheme.typography.caption, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (model.isDefault) TagBadge(stringResource(R.string.composer_options_default))
    }
}

/** Dispara o carregamento do catálogo quando a folha abre. */
@Composable
internal fun LoadCatalogOnOpen(open: Boolean, load: () -> Unit) {
    LaunchedEffect(open) { if (open) load() }
}
