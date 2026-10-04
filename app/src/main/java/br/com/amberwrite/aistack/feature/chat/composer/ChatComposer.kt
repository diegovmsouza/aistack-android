package br.com.amberwrite.aistack.feature.chat.composer

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.formatBytes
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiChip
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.AttachmentChip
import br.com.amberwrite.aistack.ui.designsystem.components.AttachmentKind
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.MicButton
import br.com.amberwrite.aistack.ui.designsystem.components.MicState
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.designsystem.components.VoiceWave
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Composer do chat (contrato fixo da Onda 2: docs/spec/CONTRATO-ONDA2.md §2).
 *
 * Dono do rascunho, dos anexos (câmera, galeria e arquivo), das paletas «/» e «@», do ditado,
 * do seletor de modelo/esforço e do envio: com [busy] a mensagem vai para a fila do host (ou
 * «Enviar agora»); [onInterrupt] interrompe o turno. [initialMention] é inserido uma vez por valor.
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
    val haptics = rememberAiHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictation = rememberDictationController(vm)
    val reader = remember(context) { AttachmentReader(context, br.com.amberwrite.aistack.data.repo.FilesRepo.UPLOAD_MAX_BYTES) }
    val c = AiTheme.colors

    LaunchedEffect(initialMention) { vm.applyInitialMention(initialMention) }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                ComposerEvent.Sent, ComposerEvent.Queued -> haptics.perform(HapticKind.Confirm)
                ComposerEvent.Failed -> haptics.perform(HapticKind.Reject)
                ComposerEvent.StopDictation -> dictation.cancel()
            }
        }
    }

    // --------------------------------------------------------------- anexos
    fun addUri(uri: Uri, fallbackName: String) {
        scope.launch {
            val meta = try {
                reader.meta(uri, fallbackName)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                AttachmentMeta(fallbackName, guessMime(fallbackName), null)
            }
            vm.addAttachment(
                name = meta.name,
                mime = meta.mime,
                sizeHint = meta.size,
                previewUri = if (meta.mime.startsWith("image/")) uri.toString() else null,
                loader = { reader.prepare(uri, meta) },
            )
        }
    }

    var attachOpen by rememberSaveable { mutableStateOf(false) }
    var cameraOpen by rememberSaveable { mutableStateOf(false) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }

    val imageName = stringResource(R.string.composer_default_image_name)
    val fileName = stringResource(R.string.composer_default_file_name)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        uris.forEach { addUri(it, imageName) }
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.take(MAX_PICK).forEach { addUri(it, fileName) }
    }

    val micGate = rememberPermissionGate(
        permission = Manifest.permission.RECORD_AUDIO,
        rationaleTitle = stringResource(R.string.composer_mic_permission_title),
        rationaleText = stringResource(R.string.composer_mic_permission_text),
        settingsText = stringResource(R.string.composer_mic_permission_settings),
        onDenied = { vm.showNotice(ComposerNotice.MicDenied) },
    )
    val cameraGate = rememberPermissionGate(
        permission = Manifest.permission.CAMERA,
        rationaleTitle = stringResource(R.string.composer_camera_permission_title),
        rationaleText = stringResource(R.string.composer_camera_permission_text),
        settingsText = stringResource(R.string.composer_camera_permission_settings),
        onDenied = { vm.showNotice(ComposerNotice.CameraDenied) },
    )

    // ---------------------------------------------------------------- texto
    // Valor local (cursor e composição do IME); o ViewModel só o substitui quando `revision` muda.
    var field by remember { mutableStateOf(TextFieldValue(state.draft, TextRange(state.cursor.coerceIn(0, state.draft.length)))) }
    val seenRevision = remember { intArrayOf(state.revision) }
    if (seenRevision[0] != state.revision) {
        seenRevision[0] = state.revision
        field = TextFieldValue(state.draft, TextRange(state.cursor.coerceIn(0, state.draft.length)))
    }

    val currentBusy by rememberUpdatedState(busy)
    val send: (SendMode) -> Unit = { mode ->
        if (state.dictation != DictationPhase.Idle) dictation.stop()
        vm.send(mode)
    }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 840.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ComposerPopupPanel(
                popup = state.popup,
                onPickSlash = vm::pickSlash,
                onPickMention = vm::pickMention,
                onUp = vm::mentionUp,
                onRetry = vm::retryPopup,
                onDismiss = vm::dismissPopup,
                haptics = haptics,
            )

            NoticeArea(
                error = state.error,
                notice = state.notice,
                onDismissError = vm::dismissError,
                onDismissNotice = vm::dismissNotice,
            )

            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(AiTheme.shapes.composer)
                    .background(c.surface)
                    .border(1.dp, if (focused) c.lineStrong else c.line, AiTheme.shapes.composer)
                    .animateContentSize(AiTheme.motion.spring()),
            ) {
                AttachmentStrip(
                    items = state.attachments,
                    onRetry = vm::retryAttachment,
                    onRemove = vm::removeAttachment,
                    haptics = haptics,
                )

                val placeholder = when {
                    !online -> stringResource(R.string.composer_placeholder_offline)
                    state.dictation != DictationPhase.Idle -> stringResource(R.string.composer_placeholder_listening)
                    busy -> stringResource(R.string.composer_placeholder_busy)
                    else -> stringResource(R.string.composer_placeholder)
                }
                val fieldDesc = stringResource(R.string.composer_field_desc)
                BasicTextField(
                    value = field,
                    onValueChange = { v ->
                        field = v
                        vm.onTextChange(v.text, v.selection.end)
                    },
                    textStyle = AiTheme.typography.body.copy(color = c.fg),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 1,
                    maxLines = 8,
                    interactionSource = interaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics { contentDescription = fieldDesc },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (field.text.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = AiTheme.typography.body,
                                    color = c.fg3,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )

                AnimatedVisibility(
                    visible = attachOpen,
                    enter = fadeIn(AiTheme.motion.fade()) + expandVertically(AiTheme.motion.spring()),
                    exit = fadeOut(AiTheme.motion.exit()) + shrinkVertically(AiTheme.motion.exit()),
                ) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AttachOption(Lucide.Camera, stringResource(R.string.composer_attach_camera), haptics) {
                            attachOpen = false
                            cameraGate.run { cameraOpen = true }
                        }
                        AttachOption(Lucide.Image, stringResource(R.string.composer_attach_gallery), haptics) {
                            attachOpen = false
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        AttachOption(Lucide.Paperclip, stringResource(R.string.composer_attach_file), haptics) {
                            attachOpen = false
                            runCatching { documents.launch(arrayOf("*/*")) }
                                .onFailure { vm.showNotice(ComposerNotice.Message(context.getString(R.string.composer_no_file_picker))) }
                        }
                    }
                }

                Toolbar(
                    state = state,
                    busy = busy,
                    online = online,
                    attachOpen = attachOpen,
                    onToggleAttach = { attachOpen = !attachOpen },
                    onOpenOptions = { optionsOpen = true },
                    levelProvider = vm.dictationLevel,
                    onMic = {
                        if (state.dictation == DictationPhase.Idle) {
                            if (!dictation.available) vm.showNotice(ComposerNotice.NoRecognizer)
                            else micGate.run { dictation.start() }
                        } else {
                            dictation.stop()
                        }
                    },
                    onSend = { send(if (currentBusy) SendMode.Queue else SendMode.Send) },
                    onSendNow = { send(SendMode.Now) },
                    onInterrupt = onInterrupt,
                    haptics = haptics,
                )
            }
        }
    }

    if (cameraOpen) {
        CameraCaptureScreen(
            onCaptured = { uri ->
                cameraOpen = false
                addUri(uri, imageName)
            },
            onDismiss = { cameraOpen = false },
            haptics = haptics,
        )
    }

    if (optionsOpen) {
        LoadCatalogOnOpen(true) { vm.loadCatalog() }
        ModelEffortSheet(
            state = state,
            onSelectModel = vm::selectModel,
            onSelectEffort = vm::selectEffort,
            onRetry = { vm.loadCatalog(refresh = true) },
            onDismiss = {
                optionsOpen = false
                vm.dismissOptionsError()
            },
            haptics = haptics,
        )
    }
}

private const val MAX_PICK = 5

// ------------------------------------------------------------------- partes

@Composable
private fun Toolbar(
    state: ComposerUiState,
    busy: Boolean,
    online: Boolean,
    attachOpen: Boolean,
    onToggleAttach: () -> Unit,
    onOpenOptions: () -> Unit,
    levelProvider: kotlinx.coroutines.flow.StateFlow<Float>,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onSendNow: () -> Unit,
    onInterrupt: () -> Unit,
    haptics: AiHaptics,
) {
    val c = AiTheme.colors
    val listening = state.dictation != DictationPhase.Idle
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rotation by animateFloatAsState(if (attachOpen) 45f else 0f, AiTheme.motion.spring(), label = "attach-rot")
        AiIconButton(
            icon = Lucide.Plus,
            contentDescription = stringResource(if (attachOpen) R.string.composer_attach_close else R.string.composer_attach_open),
            onClick = onToggleAttach,
            size = 48.dp,
            haptic = HapticKind.Tick,
            haptics = haptics,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )

        // Modelo e esforço (rolagem horizontal em telas estreitas).
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val conv = state.conversation
            if (conv != null) {
                val catalog = (state.catalog as? ComposerLoad.Ready)?.value.orEmpty()
                val modelText = modelLabel(state.model, catalog) ?: stringResource(R.string.composer_model_default)
                val modelDesc = stringResource(R.string.composer_model_chip_desc, modelText)
                AiChip(
                    text = modelText,
                    leadingIcon = Lucide.Cpu,
                    onClick = onOpenOptions,
                    haptics = haptics,
                    modifier = Modifier.minimumInteractiveComponentSize()
                        .semantics { contentDescription = modelDesc },
                )
                effortLabel(state.effort)?.let { eff ->
                    val effortDesc = stringResource(R.string.composer_effort_chip_desc, eff)
                    AiChip(
                        text = eff,
                        leadingIcon = Lucide.Brain,
                        onClick = onOpenOptions,
                        haptics = haptics,
                        modifier = Modifier.minimumInteractiveComponentSize()
                            .semantics { contentDescription = effortDesc },
                    )
                }
            }
        }

        // Ditado: onda de voz + microfone.
        AnimatedVisibility(
            visible = listening,
            enter = fadeIn(AiTheme.motion.fade()) + expandHorizontally(AiTheme.motion.spring()),
            exit = fadeOut(AiTheme.motion.exit()) + shrinkHorizontally(AiTheme.motion.exit()),
        ) {
            val level by levelProvider.collectAsStateWithLifecycle()
            VoiceWave(level = level, modifier = Modifier.width(64.dp).padding(end = 4.dp), bars = 14, active = listening, height = 24.dp)
        }
        val level by levelProvider.collectAsStateWithLifecycle()
        val micDesc = stringResource(if (listening) R.string.composer_mic_stop else R.string.composer_mic_start)
        Box(Modifier.size(48.dp).semantics { contentDescription = micDesc }, contentAlignment = Alignment.Center) {
            MicButton(
                state = when (state.dictation) {
                    DictationPhase.Idle -> MicState.Idle
                    DictationPhase.Starting, DictationPhase.Listening -> MicState.Listening
                    DictationPhase.Processing -> MicState.Processing
                },
                onClick = onMic,
                level = level,
                size = 44.dp,
                haptics = haptics,
            )
        }

        // Envio / fila / interrupção.
        val mode = when {
            state.sending -> SendButtonMode.Sending
            busy -> SendButtonMode.Busy
            else -> SendButtonMode.Idle
        }
        val motion = AiTheme.motion
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(motion.fade()) + scaleIn(motion.bouncy(), initialScale = 0.8f)) togetherWith
                    (fadeOut(motion.exit()) + scaleOut(motion.exit(), targetScale = 0.8f))
            },
            label = "send-mode",
        ) { m ->
            when (m) {
                SendButtonMode.Sending -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Spinner(size = 20.dp, color = c.accent)
                }
                SendButtonMode.Idle -> AiIconButton(
                    icon = Lucide.ArrowUp,
                    contentDescription = stringResource(R.string.composer_send),
                    onClick = onSend,
                    variant = ButtonVariant.Primary,
                    size = 48.dp,
                    enabled = state.canSend && online,
                    haptics = haptics,
                )
                SendButtonMode.Busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(
                        visible = state.canSend,
                        enter = fadeIn(AiTheme.motion.fade()) + expandHorizontally(AiTheme.motion.spring()),
                        exit = fadeOut(AiTheme.motion.exit()) + shrinkHorizontally(AiTheme.motion.exit()),
                    ) {
                        Row {
                            AiIconButton(
                                icon = Lucide.Zap,
                                contentDescription = stringResource(R.string.composer_send_now),
                                onClick = onSendNow,
                                size = 48.dp,
                                enabled = online,
                                tint = c.warn,
                                haptics = haptics,
                            )
                            AiIconButton(
                                icon = Lucide.Layers,
                                contentDescription = stringResource(R.string.composer_queue),
                                onClick = onSend,
                                variant = ButtonVariant.Primary,
                                size = 48.dp,
                                enabled = online,
                                haptics = haptics,
                            )
                        }
                    }
                    AiIconButton(
                        icon = Lucide.CircleStop,
                        contentDescription = stringResource(R.string.composer_interrupt),
                        onClick = onInterrupt,
                        size = 48.dp,
                        iconSize = 22.dp,
                        enabled = online,
                        tint = c.danger,
                        haptic = HapticKind.LongPress,
                        haptics = haptics,
                    )
                }
            }
        }
    }
}

private enum class SendButtonMode { Idle, Busy, Sending }

@Composable
private fun AttachOption(icon: ImageVector, label: String, haptics: AiHaptics, onClick: () -> Unit) {
    val c = AiTheme.colors
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(AiTheme.shapes.pill)
            .background(c.surface2)
            .border(1.dp, c.line, AiTheme.shapes.pill)
            .clickable(role = Role.Button) {
                haptics.perform(HapticKind.Tick)
                onClick()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = c.accent)
        Text(label, style = AiTheme.typography.label, color = c.fg)
    }
}

@Composable
private fun AttachmentStrip(
    items: List<AttachmentItem>,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    haptics: AiHaptics,
) {
    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = fadeIn(AiTheme.motion.fade()) + expandVertically(AiTheme.motion.spring()),
        exit = fadeOut(AiTheme.motion.exit()) + shrinkVertically(AiTheme.motion.exit()),
    ) {
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { item ->
                val thumb = if (item.kind == AttachmentKind.Image) rememberThumbnail(item.previewUri) else null
                val failed = item.phase == AttachmentPhase.Failed
                val sizeLabel = when (item.phase) {
                    AttachmentPhase.Preparing -> stringResource(R.string.composer_attachment_preparing)
                    AttachmentPhase.Uploading -> stringResource(R.string.composer_attachment_uploading)
                    AttachmentPhase.Failed -> stringResource(R.string.composer_attachment_failed)
                    AttachmentPhase.Ready -> item.sizeBytes?.let { formatBytes(it) }
                }
                val desc = stringResource(
                    R.string.composer_attachment_desc, item.name, sizeLabel ?: "",
                )
                AttachmentChip(
                    name = item.name,
                    kind = item.kind,
                    sizeLabel = sizeLabel,
                    thumbnail = thumb,
                    progress = when (item.phase) {
                        AttachmentPhase.Preparing -> 0.02f
                        AttachmentPhase.Uploading -> item.progress
                        else -> null
                    },
                    error = failed,
                    onClick = if (failed) ({ onRetry(item.id) }) else null,
                    onRemove = { onRemove(item.id) },
                    haptics = haptics,
                    modifier = Modifier
                        .animateItem()
                        .widthIn(max = 220.dp)
                        .semantics { contentDescription = desc; liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun NoticeArea(
    error: String?,
    notice: ComposerNotice?,
    onDismissError: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val msg: Pair<String, Boolean>? = when {
        error != null -> error to true
        notice != null -> noticeText(notice) to false
        else -> null
    }
    var last by remember { mutableStateOf(msg) }
    if (msg != null) last = msg
    AnimatedVisibility(
        visible = msg != null,
        enter = fadeIn(AiTheme.motion.fade()) + expandVertically(AiTheme.motion.spring()),
        exit = fadeOut(AiTheme.motion.exit()) + shrinkVertically(AiTheme.motion.exit()),
    ) {
        val (text, isError) = last ?: return@AnimatedVisibility
        val c = AiTheme.colors
        val tone = if (isError) c.danger else c.accent
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AiTheme.shapes.md)
                .background(tone.copy(alpha = 0.10f))
                .border(1.dp, tone.copy(alpha = 0.30f), AiTheme.shapes.md)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(if (isError) Lucide.TriangleAlert else Lucide.Info, null, Modifier.size(16.dp), tint = tone)
            Text(text, style = AiTheme.typography.bodySmall, color = c.fg, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            AiIconButton(
                icon = Lucide.X,
                contentDescription = stringResource(R.string.composer_notice_close),
                onClick = if (isError) onDismissError else onDismissNotice,
                size = 48.dp,
                iconSize = 16.dp,
            )
        }
    }
}

@Composable
private fun noticeText(n: ComposerNotice): String = when (n) {
    is ComposerNotice.DesktopOnly -> stringResource(R.string.composer_notice_desktop_only, n.command)
    is ComposerNotice.FileTooLarge -> stringResource(R.string.composer_notice_file_too_large, n.name)
    ComposerNotice.TooManyAttachments -> stringResource(R.string.composer_notice_too_many, ComposerViewModel.MAX_ATTACHMENTS)
    ComposerNotice.NoRecognizer -> stringResource(R.string.composer_notice_no_recognizer)
    ComposerNotice.MicDenied -> stringResource(R.string.composer_notice_mic_denied)
    ComposerNotice.CameraDenied -> stringResource(R.string.composer_notice_camera_denied)
    ComposerNotice.DictationNetwork -> stringResource(R.string.composer_notice_dictation_network)
    ComposerNotice.DictationBusy -> stringResource(R.string.composer_notice_dictation_busy)
    ComposerNotice.DictationFailed -> stringResource(R.string.composer_notice_dictation_failed)
    is ComposerNotice.Message -> n.text
}
