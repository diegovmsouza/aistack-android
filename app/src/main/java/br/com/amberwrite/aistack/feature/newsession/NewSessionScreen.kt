package br.com.amberwrite.aistack.feature.newsession

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.data.model.ModelInfo
import br.com.amberwrite.aistack.data.model.PermissionMode
import br.com.amberwrite.aistack.data.model.Provider
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.ErrorStrip
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.sessions.SkeletonBlock
import br.com.amberwrite.aistack.feature.sessions.skeletonShimmer
import br.com.amberwrite.aistack.navigation.SharedKeys
import br.com.amberwrite.aistack.navigation.aiSharedBounds
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.brand.ProviderLogo
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiChip
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiStackTopBar
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.EffortSlider
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.TagBadge
import br.com.amberwrite.aistack.ui.designsystem.illustrations.Illustration
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide
import br.com.amberwrite.aistack.feature.newsession.NewSessionViewModel.Phase

/**
 * Nova sessão (§4.3). [onCreated] recebe o id da conversa criada; quem chama decide se
 * substitui esta tela pelo chat (telefone) ou seleciona o chat no painel ao lado (tablet).
 */
@Composable
fun NewSessionScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val vm = containerViewModel { NewSessionViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAiHaptics()
    val c = AiTheme.colors

    LaunchedEffect(state.navigateTo) {
        val id = state.navigateTo ?: return@LaunchedEffect
        haptics.perform(HapticKind.Confirm)
        onCreated(id)
        vm.onNavigated()
    }
    LaunchedEffect(state.error) {
        if (state.error != null) haptics.perform(HapticKind.Reject)
    }

    Column(
        Modifier
            .fillMaxSize()
            .aiSharedBounds(SharedKeys.NEW_SESSION)
            .background(c.bg)
    ) {
        val scroll = rememberScrollState()
        AiStackTopBar(
            title = stringResource(R.string.newsession_title),
            navigationIcon = Lucide.ArrowLeft,
            navigationContentDescription = stringResource(R.string.newsession_back_cd),
            onNavigationClick = onBack,
            providerId = state.provider.id,
            scrolled = scroll.value > 0,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ProjectSection(state, vm, haptics)
            ModelSection(state, vm, haptics)
            EffortSection(state, vm, haptics)
            PermissionSection(state.permissionMode, enabled = !state.busy, onSelect = vm::setPermissionMode, haptics = haptics)
            Section(stringResource(R.string.newsession_section_message)) {
                AiTextInput(
                    value = state.message,
                    onValueChange = vm::setMessage,
                    placeholder = stringResource(R.string.newsession_message_placeholder),
                    enabled = !state.busy,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                )
            }
            Spacer(Modifier.size(8.dp))
        }
        BottomBar(state, onCreate = vm::create)
    }

    when (val phase = state.phase) {
        is Phase.Warning -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Lucide.TriangleAlert, contentDescription = null, tint = c.warn) },
            title = { Text(stringResource(R.string.newsession_warning_title)) },
            text = { Text(phase.message, style = AiTheme.typography.body) },
            confirmButton = {
                AiButton(stringResource(R.string.newsession_continue), onClick = vm::continueAfterWarning, haptics = haptics)
            },
            dismissButton = {
                AiButton(
                    stringResource(R.string.newsession_open_without_sending),
                    onClick = vm::openWithoutSending,
                    variant = ButtonVariant.Ghost,
                    haptics = haptics
                )
            },
            containerColor = c.surface
        )
        is Phase.SendFailed -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Lucide.CircleX, contentDescription = null, tint = c.danger) },
            title = { Text(stringResource(R.string.newsession_send_failed_title)) },
            text = { Text(phase.message, style = AiTheme.typography.body) },
            confirmButton = {
                AiButton(
                    stringResource(R.string.newsession_retry),
                    onClick = vm::retrySend,
                    leadingIcon = Lucide.RefreshCw,
                    haptics = haptics
                )
            },
            dismissButton = {
                AiButton(
                    stringResource(R.string.newsession_open_without_sending),
                    onClick = vm::openWithoutSending,
                    variant = ButtonVariant.Ghost,
                    haptics = haptics
                )
            },
            containerColor = c.surface
        )
        else -> Unit
    }

    state.browser?.let { browser ->
        DirBrowserSheet(
            browser = browser,
            onDismiss = vm::closeBrowser,
            onOpen = vm::browseInto,
            onUp = vm::browseUp,
            onHome = { vm.browse(null) },
            onRetry = vm::retryBrowse,
            onPick = vm::pickBrowsed,
            haptics = haptics
        )
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = AiTheme.typography.overline,
            color = AiTheme.colors.fg3,
            modifier = Modifier.semantics { heading() }
        )
        content()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

// ---------------------------------------------------------------- Projeto

@Composable
private fun ProjectSection(state: NewSessionViewModel.UiState, vm: NewSessionViewModel, haptics: AiHaptics) {
    val motion = AiTheme.motion
    Section(stringResource(R.string.newsession_section_project)) {
        AnimatedContent(
            targetState = state.projectsLoading,
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
            label = "projects"
        ) { loading ->
            if (loading) {
                val cd = stringResource(R.string.newsession_projects_loading_cd)
                Row(
                    Modifier.fillMaxWidth().semantics { contentDescription = cd },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { SkeletonBlock(width = (72 + it * 16).dp, height = 32.dp) }
                }
            } else {
                ChipRow {
                    state.projects.forEach { path ->
                        val selectCd = stringResource(R.string.newsession_project_select_cd, path)
                        AiChip(
                            text = shortProjectName(path),
                            selected = state.projectPath.trim() == path,
                            leadingIcon = Lucide.Folder,
                            onClick = if (state.busy) null else ({ vm.setProjectPath(path) }),
                            haptics = haptics,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics { contentDescription = selectCd }
                        )
                    }
                    val browseCd = stringResource(R.string.newsession_project_browse_cd)
                    AiChip(
                        text = stringResource(R.string.newsession_project_browse),
                        leadingIcon = Lucide.FolderOpen,
                        onClick = if (state.busy) null else vm::openBrowser,
                        color = AiTheme.colors.fg2,
                        haptics = haptics,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics { contentDescription = browseCd }
                    )
                }
            }
        }
        if (!state.projectsLoading && state.projects.isEmpty()) {
            Text(stringResource(R.string.newsession_project_none), style = AiTheme.typography.caption, color = AiTheme.colors.fg3)
        }
        AiTextInput(
            value = state.projectPath,
            onValueChange = vm::setProjectPath,
            placeholder = stringResource(R.string.newsession_project_placeholder),
            singleLine = true,
            mono = true,
            enabled = !state.busy,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        // Só mostra "falta o projeto" depois que o usuário digitou algo errado ou não há sugestão.
        val err = state.pathError
        val show = err == ProjectPathError.NotAbsolute || (err == ProjectPathError.Missing && !state.projectsLoading && state.projects.isEmpty())
        AnimatedVisibility(show, enter = fadeIn(motion.fade()) + expandVertically(), exit = fadeOut(motion.fade()) + shrinkVertically()) {
            Text(
                stringResource(
                    if (err == ProjectPathError.NotAbsolute) R.string.newsession_project_not_absolute
                    else R.string.newsession_project_missing
                ),
                style = AiTheme.typography.caption,
                color = if (err == ProjectPathError.NotAbsolute) AiTheme.colors.danger else AiTheme.colors.fg3
            )
        }
    }
}

// ---------------------------------------------------------------- Provedor e modelo

private enum class ModelsView { Loading, Error, Empty, List }

@Composable
private fun ModelSection(state: NewSessionViewModel.UiState, vm: NewSessionViewModel, haptics: AiHaptics) {
    val motion = AiTheme.motion
    Section(stringResource(R.string.newsession_section_model)) {
        ChipRow {
            Provider.selectable.forEach { p ->
                val cd = stringResource(R.string.newsession_provider_cd, p.displayName)
                AiChip(
                    text = p.displayName,
                    selected = state.provider == p,
                    leading = { ProviderLogo(p.id, size = 16.dp) },
                    color = AiTheme.colors.providers.byId(p.id),
                    onClick = if (state.busy) null else ({ vm.setProvider(p) }),
                    haptics = haptics,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics { contentDescription = cd }
                )
            }
        }
        val view = when {
            state.modelsLoading -> ModelsView.Loading
            state.modelsError != null -> ModelsView.Error
            state.models.isEmpty() -> ModelsView.Empty
            else -> ModelsView.List
        }
        AnimatedContent(
            targetState = view,
            transitionSpec = { (fadeIn(motion.fade()) togetherWith fadeOut(motion.fade())) },
            label = "models"
        ) { v ->
            when (v) {
                ModelsView.Loading -> {
                    val cd = stringResource(R.string.newsession_models_loading_cd)
                    Column(
                        Modifier.fillMaxWidth().semantics { contentDescription = cd },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(3) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).skeletonShimmer(AiTheme.shapes.md))
                        }
                    }
                }
                ModelsView.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ErrorStrip(state.modelsError ?: stringResource(R.string.newsession_models_error))
                    AiButton(
                        stringResource(R.string.newsession_retry),
                        onClick = vm::retryCatalog,
                        variant = ButtonVariant.Secondary,
                        leadingIcon = Lucide.RefreshCw,
                        size = ButtonSize.Large,
                        haptics = haptics
                    )
                }
                ModelsView.Empty -> Text(
                    stringResource(R.string.newsession_models_empty),
                    style = AiTheme.typography.caption,
                    color = AiTheme.colors.fg3
                )
                ModelsView.List -> ModelPicker(
                    models = state.models,
                    selectedId = state.modelId,
                    providerId = state.provider.id,
                    enabled = !state.busy,
                    onSelect = vm::setModel,
                    haptics = haptics
                )
            }
        }
    }
}

/** Lista de modelos do catálogo do desktop, com seleção única. */
@Composable
private fun ModelPicker(
    models: List<ModelInfo>,
    selectedId: String?,
    providerId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    haptics: AiHaptics,
) {
    val c = AiTheme.colors
    val tone = c.providers.byId(providerId)
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { m ->
            val selected = m.id == selectedId
            val bg by animateColorAsState(if (selected) tone.copy(alpha = 0.10f) else c.surface, AiTheme.motion.fade(), label = "modelBg")
            val border by animateColorAsState(if (selected) tone.copy(alpha = 0.55f) else c.line, AiTheme.motion.fade(), label = "modelBorder")
            val cd = stringResource(R.string.newsession_model_select_cd, m.displayName)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(bg, AiTheme.shapes.md)
                    .border(1.dp, border, AiTheme.shapes.md)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = {
                            if (!selected) haptics.perform(HapticKind.Tick)
                            onSelect(m.id)
                        }
                    )
                    .semantics { contentDescription = cd }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProviderLogo(providerId, size = 20.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            m.displayName,
                            style = AiTheme.typography.label,
                            fontWeight = FontWeight.SemiBold,
                            color = c.fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (m.isDefault) TagBadge(stringResource(R.string.newsession_model_default))
                    }
                    m.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = AiTheme.typography.caption, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                AnimatedVisibility(selected, enter = fadeIn(AiTheme.motion.fade()), exit = fadeOut(AiTheme.motion.fade())) {
                    Icon(Lucide.Check, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Esforço

@Composable
private fun EffortSection(state: NewSessionViewModel.UiState, vm: NewSessionViewModel, haptics: AiHaptics) {
    if (state.modelsLoading || state.models.isEmpty()) return
    Section(stringResource(R.string.newsession_section_effort)) {
        val levels = state.effortLevels
        if (levels.isEmpty()) {
            Text(stringResource(R.string.newsession_effort_none), style = AiTheme.typography.caption, color = AiTheme.colors.fg3)
        } else {
            EffortSlider(
                levels = levels.map(::effortLabel),
                selectedIndex = state.effortIndex.coerceIn(0, levels.lastIndex),
                onSelect = vm::setEffortIndex,
                providerId = state.provider.id,
                enabled = !state.busy,
                haptics = haptics,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------------------------------------------------------- Permissões

private data class PermOption(val mode: PermissionMode, val icon: ImageVector, val title: Int, val desc: Int)

private val PERM_OPTIONS = listOf(
    PermOption(PermissionMode.ASK, Lucide.ShieldCheck, R.string.newsession_perm_ask, R.string.newsession_perm_ask_desc),
    PermOption(PermissionMode.ACCEPT_EDITS, Lucide.FilePen, R.string.newsession_perm_accept, R.string.newsession_perm_accept_desc),
    PermOption(PermissionMode.PLAN, Lucide.Map, R.string.newsession_perm_plan, R.string.newsession_perm_plan_desc),
)

@Composable
private fun PermissionSection(selected: PermissionMode, enabled: Boolean, onSelect: (PermissionMode) -> Unit, haptics: AiHaptics) {
    val c = AiTheme.colors
    Section(stringResource(R.string.newsession_section_permission)) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PERM_OPTIONS.forEach { opt ->
                val isSel = opt.mode == selected
                val bg by animateColorAsState(if (isSel) c.accentSoft else c.surface, AiTheme.motion.fade(), label = "permBg")
                val border by animateColorAsState(if (isSel) c.accent.copy(alpha = 0.5f) else c.line, AiTheme.motion.fade(), label = "permBorder")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .background(bg, AiTheme.shapes.md)
                        .border(1.dp, border, AiTheme.shapes.md)
                        .selectable(
                            selected = isSel,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = {
                                if (!isSel) haptics.perform(HapticKind.Tick)
                                onSelect(opt.mode)
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(opt.icon, contentDescription = null, tint = if (isSel) c.accent else c.fg2, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(opt.title), style = AiTheme.typography.label, fontWeight = FontWeight.SemiBold, color = c.fg)
                        Text(stringResource(opt.desc), style = AiTheme.typography.caption, color = c.fg3)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Rodapé

@Composable
private fun BottomBar(state: NewSessionViewModel.UiState, onCreate: () -> Unit) {
    val c = AiTheme.colors
    val motion = AiTheme.motion
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(
            state.error != null,
            enter = fadeIn(motion.fade()) + expandVertically(),
            exit = fadeOut(motion.fade()) + shrinkVertically()
        ) {
            ErrorStrip(state.error.orEmpty())
        }
        val label = when (state.phase) {
            Phase.Creating -> stringResource(R.string.newsession_creating)
            is Phase.Sending -> stringResource(R.string.newsession_sending)
            else -> if (state.message.isBlank()) stringResource(R.string.newsession_create)
            else stringResource(R.string.newsession_create_and_send)
        }
        AiButton(
            text = label,
            onClick = onCreate,
            size = ButtonSize.Large,
            leadingIcon = if (state.message.isBlank()) Lucide.Plus else Lucide.Send,
            enabled = state.canCreate,
            loading = state.phase == Phase.Creating || state.phase is Phase.Sending,
            haptic = HapticKind.Confirm,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------------------------------------------------------- Navegador de pastas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirBrowserSheet(
    browser: NewSessionViewModel.Browser,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onUp: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    onPick: () -> Unit,
    haptics: AiHaptics,
) {
    val c = AiTheme.colors
    val motion = AiTheme.motion
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = AiTheme.shapes.sheet
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AiIconButton(
                    icon = Lucide.ArrowUp,
                    contentDescription = stringResource(R.string.newsession_browser_up_cd),
                    onClick = onUp,
                    enabled = browser.canGoUp && !browser.loading,
                    size = 48.dp,
                    haptic = HapticKind.Tick,
                    haptics = haptics
                )
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        stringResource(R.string.newsession_browser_title),
                        style = AiTheme.typography.heading,
                        color = c.fg,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        browser.path ?: stringResource(R.string.newsession_browser_home),
                        style = AiTheme.typography.monoSmall,
                        color = c.fg3,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis
                    )
                }
                AiIconButton(
                    icon = Lucide.X,
                    contentDescription = stringResource(R.string.newsession_browser_close),
                    onClick = onDismiss,
                    size = 48.dp
                )
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 420.dp)) {
                val view = when {
                    browser.loading -> 0
                    browser.error != null -> 1
                    browser.dirs.isEmpty() -> 2
                    else -> 3
                }
                AnimatedContent(
                    targetState = view to browser.path,
                    transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
                    label = "dirBrowser"
                ) { (v, _) ->
                    when (v) {
                        0 -> {
                            val cd = stringResource(R.string.newsession_browser_loading_cd)
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp).clearAndSetSemantics { contentDescription = cd },
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                repeat(6) { i ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(20.dp).skeletonShimmer(AiTheme.shapes.xs))
                                        Spacer(Modifier.width(12.dp))
                                        SkeletonBlock(width = (90 + (i * 37) % 120).dp, height = 14.dp)
                                    }
                                }
                            }
                        }
                        1 -> EmptyState(
                            title = stringResource(R.string.newsession_browser_error_title),
                            body = browser.error,
                            illustration = Illustration.Offline,
                            accent = c.warn,
                            illustrationWidth = 112.dp,
                            primaryAction = {
                                AiButton(
                                    stringResource(R.string.newsession_retry),
                                    onClick = onRetry,
                                    variant = ButtonVariant.Secondary,
                                    leadingIcon = Lucide.RefreshCw,
                                    size = ButtonSize.Large
                                )
                            },
                            secondaryAction = {
                                AiButton(
                                    stringResource(R.string.newsession_browser_home),
                                    onClick = onHome,
                                    variant = ButtonVariant.Ghost,
                                    size = ButtonSize.Large
                                )
                            },
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                        2 -> EmptyState(
                            title = stringResource(R.string.newsession_browser_empty_title),
                            body = stringResource(R.string.newsession_browser_empty_body),
                            illustration = Illustration.NoSessions,
                            illustrationWidth = 112.dp,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                        else -> LazyColumn(Modifier.fillMaxWidth()) {
                            if (browser.truncated) {
                                item {
                                    Text(
                                        stringResource(R.string.newsession_browser_truncated),
                                        style = AiTheme.typography.caption,
                                        color = c.warn,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            items(browser.dirs, key = { it.name }) { entry ->
                                val cd = stringResource(R.string.newsession_browser_open_cd, entry.name)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable(role = Role.Button, onClickLabel = cd) {
                                            haptics.perform(HapticKind.Tick)
                                            onOpen(entry.name)
                                        }
                                        .semantics { contentDescription = cd }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Lucide.Folder, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                                    Text(
                                        entry.name,
                                        style = AiTheme.typography.body,
                                        color = c.fg,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Lucide.ChevronRight, contentDescription = null, tint = c.fg3, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            AiButton(
                text = stringResource(R.string.newsession_browser_use),
                onClick = onPick,
                enabled = browser.path != null && !browser.loading,
                size = ButtonSize.Large,
                leadingIcon = Lucide.Check,
                haptic = HapticKind.Confirm,
                haptics = haptics,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
