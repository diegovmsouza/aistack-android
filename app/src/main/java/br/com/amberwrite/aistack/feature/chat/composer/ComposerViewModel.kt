package br.com.amberwrite.aistack.feature.chat.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.Attachment
import br.com.amberwrite.aistack.data.model.Conversation
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.DirListing
import br.com.amberwrite.aistack.data.model.EntryKind
import br.com.amberwrite.aistack.data.model.ModelInfo
import br.com.amberwrite.aistack.data.model.Provider
import br.com.amberwrite.aistack.data.model.SlashCommand
import br.com.amberwrite.aistack.ui.designsystem.components.AttachmentKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Estado de um carregamento assíncrono (paletas, catálogo). */
sealed interface ComposerLoad<out T> {
    data object Loading : ComposerLoad<Nothing>
    data class Ready<T>(val value: T) : ComposerLoad<T>
    data class Failed(val message: String) : ComposerLoad<Nothing>
}

/** Popup acima do campo: nenhum, paleta «/» ou menção «@». */
sealed interface ComposerPopup {
    data object None : ComposerPopup

    data class Slash(val trigger: Trigger, val items: ComposerLoad<List<SlashCommand>>) : ComposerPopup

    /** [dir] é a pasta da consulta (relativa ao projeto ou absoluta); [filter] o trecho após a última «/». */
    data class Mention(
        val trigger: Trigger,
        val dir: String,
        val filter: String,
        val items: ComposerLoad<List<DirEntry>>,
        val truncated: Boolean = false,
    ) : ComposerPopup
}

/** Avisos curtos (não são erros de envio); o texto vem de `strings_composer.xml`. */
sealed interface ComposerNotice {
    data class DesktopOnly(val command: String) : ComposerNotice
    data class FileTooLarge(val name: String) : ComposerNotice
    data object TooManyAttachments : ComposerNotice
    data object NoRecognizer : ComposerNotice
    data object MicDenied : ComposerNotice
    data object CameraDenied : ComposerNotice
    data object DictationNetwork : ComposerNotice
    data object DictationBusy : ComposerNotice
    data object DictationFailed : ComposerNotice
    data class Message(val text: String) : ComposerNotice
}

enum class AttachmentPhase { Preparing, Uploading, Ready, Failed }

/** Anexo no composer, do preparo local até o caminho devolvido pelo `saveUpload`. */
data class AttachmentItem(
    val id: String,
    val name: String,
    val mime: String,
    val kind: AttachmentKind,
    val sizeBytes: Long?,
    val phase: AttachmentPhase,
    val progress: Float? = null,
    val uploaded: Attachment? = null,
    val error: String? = null,
    /** URI local (content:// ou file://) para a miniatura. */
    val previewUri: String? = null,
)

/** Bytes prontos para envio (já comprimidos, no caso de imagem). */
class PreparedUpload(val bytes: ByteArray, val name: String, val mime: String)

/** Lançada pelo leitor quando o arquivo passa do teto do host. */
class AttachmentTooLargeException(val size: Long?) : Exception("Arquivo grande demais")

enum class DictationPhase { Idle, Starting, Listening, Processing }

/** Modo do envio: normal, para a fila (turno em curso) ou interrompendo o turno. */
enum class SendMode { Send, Queue, Now }

sealed interface ComposerEvent {
    data object Sent : ComposerEvent
    data object Queued : ComposerEvent
    data object Failed : ComposerEvent
    /** O usuário editou o texto durante o ditado: a UI deve parar o reconhecedor. */
    data object StopDictation : ComposerEvent
}

data class ComposerUiState(
    val draft: String = "",
    val cursor: Int = 0,
    /** Muda só quando o ViewModel altera o texto; a UI então ressincroniza o campo. */
    val revision: Int = 0,
    val sending: Boolean = false,
    val error: String? = null,
    val notice: ComposerNotice? = null,
    val attachments: List<AttachmentItem> = emptyList(),
    val popup: ComposerPopup = ComposerPopup.None,
    val dictation: DictationPhase = DictationPhase.Idle,
    val conversation: Conversation? = null,
    val catalog: ComposerLoad<List<ModelInfo>>? = null,
    val pendingModel: String? = null,
    val pendingEffort: String? = null,
    val optionsSaving: Boolean = false,
    val optionsError: String? = null,
) {
    val uploading: Boolean get() = attachments.any { it.phase == AttachmentPhase.Preparing || it.phase == AttachmentPhase.Uploading }
    val hasFailedAttachment: Boolean get() = attachments.any { it.phase == AttachmentPhase.Failed }
    val canSend: Boolean
        get() = !sending && !uploading && !hasFailedAttachment &&
            (draft.isNotBlank() || attachments.any { it.phase == AttachmentPhase.Ready })
    val model: String? get() = pendingModel ?: conversation?.model
    val effort: String? get() = pendingEffort ?: conversation?.effort
    val provider: Provider get() = conversation?.provider ?: Provider.UNKNOWN
}

/**
 * Estado do composer de uma conversa: rascunho, anexos, paletas, ditado, modelo/esforço e envio.
 * Toda E/S passa pelo [ComposerBackend]; nada bloqueia a main thread.
 */
class ComposerViewModel(
    private val backend: ComposerBackend,
    private val conversationId: String,
) : ViewModel() {

    constructor(container: AppContainer, conversationId: String) :
        this(ContainerComposerBackend(container), conversationId)

    private val _state = MutableStateFlow(ComposerUiState())
    val state: StateFlow<ComposerUiState> = _state.asStateFlow()

    private val _level = MutableStateFlow(0f)
    /** Nível do microfone (0..1) — fluxo separado para não recompor o composer inteiro. */
    val dictationLevel: StateFlow<Float> = _level.asStateFlow()

    private val _events = MutableSharedFlow<ComposerEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<ComposerEvent> = _events.asSharedFlow()

    private var lastInitialMention: String? = null

    private var slashCache: List<SlashCommand>? = null
    private var slashError: String? = null
    private var slashJob: Job? = null

    private val dirCache = HashMap<String, DirListing>()
    private val dirErrors = HashMap<String, String>()
    private var dirJob: Job? = null
    private var dirJobKey: String? = null

    /** Início do gatilho que o usuário dispensou (o popup não reabre para ele). */
    private var dismissedTrigger: Int? = null

    private var dictationPrefix: String? = null
    private var dictationSuffix: String = ""

    private val uploadJobs = HashMap<String, Job>()
    private val loaders = HashMap<String, suspend () -> PreparedUpload>()
    private var nextAttachmentId = 0

    init {
        viewModelScope.launch {
            backend.conversation(conversationId).collect { conv ->
                val before = _state.value.conversation
                _state.update { s ->
                    s.copy(
                        conversation = conv,
                        pendingModel = s.pendingModel?.takeIf { conv?.model != it },
                        pendingEffort = s.pendingEffort?.takeIf { conv?.effort != it },
                    )
                }
                if (conv != null && (before == null || before.projectPath != conv.projectPath || before.provider != conv.provider)) {
                    if (before != null) {
                        slashCache = null; slashError = null; dirCache.clear(); dirErrors.clear()
                    }
                    refreshPopup()
                }
            }
        }
    }

    // ---------------------------------------------------------------- texto

    /** Edição feita pelo usuário no campo. */
    fun onTextChange(text: String, cursor: Int) {
        val prev = _state.value
        if (prev.dictation != DictationPhase.Idle && text != prev.draft) {
            dictationPrefix = null
            _state.update { it.copy(dictation = DictationPhase.Idle) }
            _events.tryEmit(ComposerEvent.StopDictation)
        }
        _state.update {
            it.copy(
                draft = text,
                cursor = cursor.coerceIn(0, text.length),
                error = if (text != prev.draft && text.isNotEmpty()) null else it.error,
            )
        }
        refreshPopup()
    }

    private fun setText(edit: TextEdit) {
        _state.update { it.copy(draft = edit.text, cursor = edit.cursor.coerceIn(0, edit.text.length), revision = it.revision + 1) }
        refreshPopup()
    }

    /** Insere `@caminho ` uma única vez por valor (o mesmo valor não é reinserido). */
    fun applyInitialMention(path: String?) {
        if (path.isNullOrBlank() || path == lastInitialMention) return
        lastInitialMention = path
        viewModelScope.launch {
            val conv = _state.value.conversation
                ?: withTimeoutOrNull(1_500) { _state.map { it.conversation }.filterNotNull().first() }
            insertMention(relativizeMention(path, conv?.projectPath))
        }
    }

    /** Acrescenta uma menção ao rascunho (`@caminho `). */
    fun insertMention(path: String) {
        setText(appendMention(_state.value.draft, path))
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
    fun showNotice(notice: ComposerNotice) = _state.update { it.copy(notice = notice) }

    // --------------------------------------------------------------- popups

    private fun refreshPopup() {
        val s = _state.value
        val mention = findMentionTrigger(s.draft, s.cursor)
        val slash = if (mention == null) findSlashTrigger(s.draft, s.cursor) else null
        val trigger = mention ?: slash
        if (trigger == null) {
            dismissedTrigger = null
            _state.update { it.copy(popup = ComposerPopup.None) }
            return
        }
        if (trigger.start == dismissedTrigger) {
            _state.update { it.copy(popup = ComposerPopup.None) }
            return
        }
        dismissedTrigger = null
        val conv = s.conversation
        val popup = if (mention != null) mentionPopup(mention, conv) else slashPopup(slash!!, conv)
        _state.update { it.copy(popup = popup) }
    }

    private fun slashPopup(trigger: Trigger, conv: Conversation?): ComposerPopup.Slash {
        val cached = slashCache
        val items: ComposerLoad<List<SlashCommand>> = when {
            cached != null -> ComposerLoad.Ready(filterSlash(cached, trigger.query))
            slashError != null -> ComposerLoad.Failed(slashError!!)
            else -> {
                if (conv != null) loadSlash(conv)
                ComposerLoad.Loading
            }
        }
        return ComposerPopup.Slash(trigger, items)
    }

    private fun loadSlash(conv: Conversation) {
        if (slashJob?.isActive == true) return
        slashJob = viewModelScope.launch {
            try {
                slashCache = backend.listSlashCommands(conv.provider, conv.projectPath.ifBlank { null })
                slashError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                slashError = e.userMessage
            }
            refreshPopup()
        }
    }

    private fun mentionPopup(trigger: Trigger, conv: Conversation?): ComposerPopup.Mention {
        val (dir, filter) = splitMentionQuery(trigger.query)
        if (conv == null) return ComposerPopup.Mention(trigger, dir, filter, ComposerLoad.Loading)
        val path = resolveListPath(conv.projectPath, dir)
        val key = path ?: ""
        val listing = dirCache[key]
        val error = dirErrors[key]
        val items: ComposerLoad<List<DirEntry>> = when {
            listing != null -> ComposerLoad.Ready(filterEntries(listing.entries, filter))
            error != null -> ComposerLoad.Failed(error)
            else -> {
                loadDir(key, path)
                ComposerLoad.Loading
            }
        }
        return ComposerPopup.Mention(trigger, dir, filter, items, truncated = listing?.truncated == true)
    }

    private fun loadDir(key: String, path: String?) {
        if (dirJob?.isActive == true && dirJobKey == key) return
        dirJob?.cancel()
        dirJobKey = key
        dirJob = viewModelScope.launch {
            try {
                dirCache[key] = backend.listDir(path)
                dirErrors.remove(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                dirErrors[key] = e.userMessage
            }
            refreshPopup()
        }
    }

    /** Tenta de novo o carregamento do popup atual. */
    fun retryPopup() {
        when (val p = _state.value.popup) {
            is ComposerPopup.Slash -> { slashError = null; slashCache = null }
            is ComposerPopup.Mention -> {
                val key = resolveListPath(_state.value.conversation?.projectPath, p.dir) ?: ""
                dirErrors.remove(key); dirCache.remove(key)
            }
            ComposerPopup.None -> return
        }
        refreshPopup()
    }

    /** Fecha o popup até o usuário começar outro gatilho. */
    fun dismissPopup() {
        val p = _state.value.popup
        dismissedTrigger = when (p) {
            is ComposerPopup.Slash -> p.trigger.start
            is ComposerPopup.Mention -> p.trigger.start
            ComposerPopup.None -> null
        }
        _state.update { it.copy(popup = ComposerPopup.None) }
    }

    /** Escolha na paleta «/». Comandos só do desktop não são inseridos. */
    fun pickSlash(command: SlashCommand) {
        val p = _state.value.popup as? ComposerPopup.Slash ?: return
        if (command.desktopOnly) {
            _state.update { it.copy(notice = ComposerNotice.DesktopOnly("/" + command.bareName)) }
            return
        }
        setText(replaceTrigger(_state.value.draft, p.trigger, "/${command.bareName} "))
    }

    /** Escolha no popup «@»: pasta continua a navegação; arquivo fecha com espaço. */
    fun pickMention(entry: DirEntry) {
        val p = _state.value.popup as? ComposerPopup.Mention ?: return
        val path = mentionInsertPath(p.dir, entry)
        val replacement = if (entry.kind == EntryKind.DIR) "@$path" else "@$path "
        setText(replaceTrigger(_state.value.draft, p.trigger, replacement))
    }

    /** Sobe uma pasta no popup «@». */
    fun mentionUp() {
        val p = _state.value.popup as? ComposerPopup.Mention ?: return
        val parent = parentDir(p.dir)
        val q = when {
            parent.isEmpty() -> ""
            parent == "/" -> "/"
            else -> "$parent/"
        }
        setText(replaceTrigger(_state.value.draft, p.trigger, "@$q"))
    }

    // ---------------------------------------------------------------- envio

    fun send(mode: SendMode) {
        val s = _state.value
        if (!s.canSend) return
        val text = s.draft.trim()
        val desktopOnly = desktopOnlyCommand(text)
        if (desktopOnly != null) {
            _state.update { it.copy(notice = ComposerNotice.DesktopOnly(desktopOnly)) }
            return
        }
        val sentAttachments = s.attachments
        val payload = sentAttachments.mapNotNull { it.uploaded }
        sentAttachments.forEach { loaders.remove(it.id) }
        _state.update {
            it.copy(
                draft = "", cursor = 0, revision = it.revision + 1, attachments = emptyList(),
                sending = true, error = null, popup = ComposerPopup.None,
            )
        }
        dismissedTrigger = null
        viewModelScope.launch {
            try {
                when (mode) {
                    SendMode.Send -> backend.send(conversationId, text, payload)
                    SendMode.Queue -> backend.queue(conversationId, text, payload)
                    SendMode.Now -> backend.sendNow(conversationId, text, payload)
                }
                _state.update { it.copy(sending = false) }
                _events.tryEmit(if (mode == SendMode.Queue) ComposerEvent.Queued else ComposerEvent.Sent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { cur ->
                    val restored = if (cur.draft.isBlank()) text else text + "\n" + cur.draft
                    cur.copy(
                        draft = restored, cursor = restored.length, revision = cur.revision + 1,
                        attachments = sentAttachments + cur.attachments,
                        sending = false, error = e.userMessage,
                    )
                }
                _events.tryEmit(ComposerEvent.Failed)
            }
        }
    }

    private fun desktopOnlyCommand(text: String): String? {
        if (!text.startsWith("/")) return null
        val name = text.drop(1).takeWhile { !it.isWhitespace() }
        if (name.isEmpty()) return null
        val cmd = slashCache?.firstOrNull { it.bareName.equals(name, ignoreCase = true) } ?: return null
        return if (cmd.desktopOnly) "/" + cmd.bareName else null
    }

    // ---------------------------------------------------------------- anexos

    /**
     * Adiciona um anexo. [loader] lê (e comprime, se for imagem) fora da main thread; o envio
     * para o host começa logo depois. Arquivos acima do teto são recusados localmente.
     */
    fun addAttachment(
        name: String,
        mime: String,
        sizeHint: Long?,
        previewUri: String? = null,
        loader: suspend () -> PreparedUpload,
    ) {
        if (_state.value.attachments.size >= MAX_ATTACHMENTS) {
            _state.update { it.copy(notice = ComposerNotice.TooManyAttachments) }
            return
        }
        if (sizeHint != null && sizeHint > backend.uploadMaxBytes && !shouldCompressImage(mime)) {
            _state.update { it.copy(notice = ComposerNotice.FileTooLarge(name)) }
            return
        }
        val id = "a${nextAttachmentId++}"
        val item = AttachmentItem(
            id = id, name = name, mime = mime, kind = attachmentKindFor(name, mime), sizeBytes = sizeHint,
            phase = AttachmentPhase.Preparing, previewUri = previewUri,
        )
        loaders[id] = loader
        _state.update { it.copy(attachments = it.attachments + item, error = null) }
        startUpload(id)
    }

    fun retryAttachment(id: String) {
        if (loaders[id] == null) return
        updateAttachment(id) { it.copy(phase = AttachmentPhase.Preparing, error = null, progress = null) }
        startUpload(id)
    }

    fun removeAttachment(id: String) {
        uploadJobs.remove(id)?.cancel()
        loaders.remove(id)
        _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == id }) }
    }

    private fun startUpload(id: String) {
        val loader = loaders[id] ?: return
        uploadJobs[id]?.cancel()
        uploadJobs[id] = viewModelScope.launch {
            val prepared = try {
                loader()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AttachmentTooLargeException) {
                refuseTooLarge(id)
                return@launch
            } catch (e: Throwable) {
                updateAttachment(id) { it.copy(phase = AttachmentPhase.Failed, error = e.userMessage) }
                return@launch
            }
            if (prepared.bytes.size > backend.uploadMaxBytes) {
                refuseTooLarge(id)
                return@launch
            }
            updateAttachment(id) {
                it.copy(
                    name = prepared.name, mime = prepared.mime, kind = attachmentKindFor(prepared.name, prepared.mime),
                    sizeBytes = prepared.bytes.size.toLong(), phase = AttachmentPhase.Uploading, progress = 0.04f,
                )
            }
            val ticker = launch {
                repeat(PROGRESS_TICKS) {
                    delay(PROGRESS_TICK_MS)
                    updateAttachment(id) { a -> a.copy(progress = uploadProgressStep(a.progress ?: 0f)) }
                }
            }
            try {
                val uploaded = backend.saveUpload(prepared.bytes, prepared.name, prepared.mime)
                ticker.cancel()
                updateAttachment(id) {
                    it.copy(phase = AttachmentPhase.Ready, progress = 1f, uploaded = Attachment(uploaded.path, uploaded.mime))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ticker.cancel()
                updateAttachment(id) { it.copy(phase = AttachmentPhase.Failed, error = e.userMessage, progress = null) }
            }
        }
    }

    private fun refuseTooLarge(id: String) {
        val name = _state.value.attachments.firstOrNull { it.id == id }?.name ?: return
        loaders.remove(id)
        _state.update { s ->
            s.copy(attachments = s.attachments.filterNot { it.id == id }, notice = ComposerNotice.FileTooLarge(name))
        }
    }

    private inline fun updateAttachment(id: String, crossinline f: (AttachmentItem) -> AttachmentItem) {
        _state.update { s -> s.copy(attachments = s.attachments.map { if (it.id == id) f(it) else it }) }
    }

    // ---------------------------------------------------------------- ditado

    /** O reconhecedor começou: guarda onde o texto ditado será inserido (no cursor). */
    fun onDictationStarting() {
        val s = _state.value
        val c = s.cursor.coerceIn(0, s.draft.length)
        dictationPrefix = s.draft.substring(0, c)
        dictationSuffix = s.draft.substring(c)
        _level.value = 0f
        _state.update { it.copy(dictation = DictationPhase.Starting, notice = null) }
    }

    fun onDictationReady() = _state.update { if (it.dictation == DictationPhase.Idle) it else it.copy(dictation = DictationPhase.Listening) }

    fun onDictationLevel(rmsdB: Float) {
        _level.value = rmsToLevel(rmsdB)
    }

    fun onDictationEndOfSpeech() {
        _level.value = 0f
        _state.update { if (it.dictation == DictationPhase.Idle) it else it.copy(dictation = DictationPhase.Processing) }
    }

    /** Resultado parcial: aparece ao vivo no campo. */
    fun onDictationPartial(text: String) {
        val prefix = dictationPrefix ?: return
        if (text.isBlank()) return
        setText(joinDictation(prefix, text, dictationSuffix))
        _state.update { if (it.dictation == DictationPhase.Starting) it.copy(dictation = DictationPhase.Listening) else it }
    }

    /** Resultado final: fixa o texto e encerra o ditado. */
    fun onDictationResult(text: String) {
        val prefix = dictationPrefix
        if (prefix != null && text.isNotBlank()) setText(joinDictation(prefix, text, dictationSuffix))
        finishDictation(null)
    }

    /** Erro do reconhecedor; [notice] nulo = encerra em silêncio (ex.: nada foi dito). */
    fun onDictationError(notice: ComposerNotice?) = finishDictation(notice)

    /** Encerrado pelo usuário ou pelo ciclo de vida. */
    fun onDictationStopped() = finishDictation(null)

    private fun finishDictation(notice: ComposerNotice?) {
        dictationPrefix = null
        dictationSuffix = ""
        _level.value = 0f
        _state.update { it.copy(dictation = DictationPhase.Idle, notice = notice ?: it.notice) }
    }

    // ----------------------------------------------------- modelo e esforço

    /** Abre a folha de modelo/esforço: carrega o catálogo do provedor (cache do repo). */
    fun loadCatalog(refresh: Boolean = false) {
        val conv = _state.value.conversation ?: return
        val current = _state.value.catalog
        if (!refresh && (current is ComposerLoad.Ready || current is ComposerLoad.Loading)) return
        _state.update { it.copy(catalog = ComposerLoad.Loading) }
        viewModelScope.launch {
            val result = try {
                ComposerLoad.Ready(backend.getCatalog(conv.provider, refresh))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ComposerLoad.Failed(e.userMessage)
            }
            _state.update { it.copy(catalog = result) }
        }
    }

    fun selectModel(model: ModelInfo) {
        val s = _state.value
        if (s.model == model.id && s.optionsError == null) return
        val effort = effortForModel(model, s.effort)
        saveOptions(model.id, effort)
    }

    fun selectEffort(effort: String) {
        if (_state.value.effort == effort) return
        saveOptions(null, effort)
    }

    fun dismissOptionsError() = _state.update { it.copy(optionsError = null) }

    private fun saveOptions(model: String?, effort: String?) {
        _state.update {
            it.copy(
                pendingModel = model ?: it.pendingModel,
                pendingEffort = effort ?: it.pendingEffort,
                optionsSaving = true,
                optionsError = null,
            )
        }
        viewModelScope.launch {
            try {
                val conv = backend.setOptions(conversationId, model, effort)
                _state.update { s ->
                    val c = conv ?: s.conversation
                    s.copy(
                        conversation = c,
                        optionsSaving = false,
                        pendingModel = s.pendingModel?.takeIf { conv == null && it != c?.model },
                        pendingEffort = s.pendingEffort?.takeIf { conv == null && it != c?.effort },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(optionsSaving = false, pendingModel = null, pendingEffort = null, optionsError = e.userMessage) }
            }
        }
    }

    override fun onCleared() {
        uploadJobs.values.forEach { it.cancel() }
        uploadJobs.clear()
        loaders.clear()
    }

    companion object {
        const val MAX_ATTACHMENTS = 10
        private const val PROGRESS_TICKS = 80
        private const val PROGRESS_TICK_MS = 250L
    }
}
