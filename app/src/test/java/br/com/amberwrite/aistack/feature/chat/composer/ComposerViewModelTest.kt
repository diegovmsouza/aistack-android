package br.com.amberwrite.aistack.feature.chat.composer

import br.com.amberwrite.aistack.data.model.Attachment
import br.com.amberwrite.aistack.data.model.Conversation
import br.com.amberwrite.aistack.data.model.ConversationOrigin
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.DirListing
import br.com.amberwrite.aistack.data.model.EntryKind
import br.com.amberwrite.aistack.data.model.ModelInfo
import br.com.amberwrite.aistack.data.model.Provider
import br.com.amberwrite.aistack.data.model.SlashCommand
import br.com.amberwrite.aistack.data.model.UploadedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun conversation(
    projectPath: String = "/proj",
    model: String? = "sonnet",
    effort: String? = "medium",
    provider: Provider = Provider.CLAUDE,
) = Conversation(
    id = "c1", provider = provider, providerId = provider.id, nativeSessionId = null, projectPath = projectPath,
    title = "Teste", model = model, effort = effort, permissionMode = null, activeSlot = null,
    extraDirs = emptyList(), archived = false, createdAt = 0L, updatedAt = 0L,
    origin = ConversationOrigin.MOBILE, originDevice = null, warning = null,
)

/** Backend falso: registra as chamadas e falha quando configurado. */
private class FakeComposerBackend(initial: Conversation? = conversation()) : ComposerBackend {
    val conv = MutableStateFlow(initial)

    data class Sent(val mode: SendMode, val text: String, val attachments: List<Attachment>)
    val sent = mutableListOf<Sent>()
    var sendError: Throwable? = null

    var slashCommands = listOf(
        SlashCommand("compact", "Compacta", null, null, false),
        SlashCommand("clear", "Limpa", null, null, false),
        SlashCommand("config", "Configura", null, null, true),
    )
    var slashCalls = 0
    var slashError: Throwable? = null

    val dirs = mutableMapOf<String?, DirListing>()
    val listDirCalls = mutableListOf<String?>()

    var catalog = listOf(
        ModelInfo("sonnet", "Sonnet", null, listOf("low", "medium", "high"), "medium", true),
        ModelInfo("opus", "Opus", null, listOf("low", "high", "max"), "high", false),
        ModelInfo("haiku", "Haiku", null, emptyList(), null, false),
    )
    var catalogCalls = 0
    var catalogError: Throwable? = null

    val setOptionsCalls = mutableListOf<Pair<String?, String?>>()
    var setOptionsError: Throwable? = null

    val uploads = mutableListOf<String>()
    var uploadError: Throwable? = null

    override var uploadMaxBytes: Int = 100

    override fun conversation(id: String): Flow<Conversation?> = conv

    private fun record(mode: SendMode, text: String, attachments: List<Attachment>) {
        sendError?.let { throw it }
        sent += Sent(mode, text, attachments)
    }

    override suspend fun send(id: String, text: String, attachments: List<Attachment>) = record(SendMode.Send, text, attachments)
    override suspend fun queue(id: String, text: String, attachments: List<Attachment>) = record(SendMode.Queue, text, attachments)
    override suspend fun sendNow(id: String, text: String, attachments: List<Attachment>) = record(SendMode.Now, text, attachments)

    override suspend fun listSlashCommands(provider: Provider, projectPath: String?): List<SlashCommand> {
        slashCalls++
        slashError?.let { throw it }
        return slashCommands
    }

    override suspend fun listDir(path: String?): DirListing {
        listDirCalls += path
        return dirs[path] ?: throw IllegalStateException("Pasta não encontrada.")
    }

    override suspend fun getCatalog(provider: Provider, refresh: Boolean): List<ModelInfo> {
        catalogCalls++
        catalogError?.let { throw it }
        return catalog
    }

    override suspend fun setOptions(id: String, model: String?, effort: String?): Conversation? {
        setOptionsCalls += model to effort
        setOptionsError?.let { throw it }
        val next = conv.value?.let { it.copy(model = model ?: it.model, effort = effort ?: it.effort) }
        conv.value = next
        return next
    }

    override suspend fun saveUpload(bytes: ByteArray, name: String, mime: String): UploadedFile {
        uploads += name
        uploadError?.let { throw it }
        return UploadedFile("/uploads/$name", mime)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newVm(backend: FakeComposerBackend): ComposerViewModel {
        val vm = ComposerViewModel(backend, "c1")
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.collectEvents(vm: ComposerViewModel): MutableList<ComposerEvent> {
        val out = mutableListOf<ComposerEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(out) }
        return out
    }

    private fun ComposerViewModel.type(text: String) = onTextChange(text, text.length)

    private fun bytes(n: Int) = PreparedUpload(ByteArray(n), "x", "application/octet-stream")

    // ---------------------------------------------------------------- envio

    @Test fun sendClearsDraftAndCallsBackend() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)
        val events = collectEvents(vm)

        vm.type("  olá mundo  ")
        val rev = vm.state.value.revision
        vm.send(SendMode.Send)
        assertEquals("", vm.state.value.draft)
        assertTrue(vm.state.value.sending)
        assertEquals(rev + 1, vm.state.value.revision)

        advanceUntilIdle()
        assertEquals(listOf(FakeComposerBackend.Sent(SendMode.Send, "olá mundo", emptyList())), backend.sent)
        assertFalse(vm.state.value.sending)
        assertEquals(listOf<ComposerEvent>(ComposerEvent.Sent), events)
    }

    @Test fun blankDraftDoesNotSend() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)
        vm.type("   ")
        assertFalse(vm.state.value.canSend)
        vm.send(SendMode.Send)
        advanceUntilIdle()
        assertTrue(backend.sent.isEmpty())
    }

    @Test fun failedSendRestoresDraftAndShowsError() = runTest(dispatcher) {
        val backend = FakeComposerBackend().apply { sendError = IllegalStateException("Falhou o envio.") }
        val vm = newVm(backend)
        val events = collectEvents(vm)

        vm.type("mensagem")
        vm.send(SendMode.Send)
        vm.type("extra")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("mensagem\nextra", s.draft)
        assertEquals(s.draft.length, s.cursor)
        assertEquals("Falhou o envio.", s.error)
        assertFalse(s.sending)
        assertEquals(listOf<ComposerEvent>(ComposerEvent.Failed), events)
    }

    @Test fun queueAndSendNowUseTheirBackendCalls() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)
        val events = collectEvents(vm)

        vm.type("na fila")
        vm.send(SendMode.Queue)
        advanceUntilIdle()
        vm.type("agora")
        vm.send(SendMode.Now)
        advanceUntilIdle()

        assertEquals(listOf(SendMode.Queue, SendMode.Now), backend.sent.map { it.mode })
        assertEquals(listOf(ComposerEvent.Queued, ComposerEvent.Sent), events)
    }

    @Test fun desktopOnlyCommandIsNotSent() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)
        vm.type("/")
        advanceUntilIdle() // carrega a paleta (cache dos comandos)

        vm.type("/config tema")
        vm.send(SendMode.Send)
        advanceUntilIdle()

        assertTrue(backend.sent.isEmpty())
        assertEquals(ComposerNotice.DesktopOnly("/config"), vm.state.value.notice)
        assertEquals("/config tema", vm.state.value.draft)
    }

    // ---------------------------------------------------------------- menção inicial

    @Test fun initialMentionIsInsertedOncePerValue() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)

        vm.applyInitialMention("/proj/src/App.kt")
        advanceUntilIdle()
        assertEquals("@src/App.kt ", vm.state.value.draft)

        vm.applyInitialMention("/proj/src/App.kt")
        advanceUntilIdle()
        assertEquals("@src/App.kt ", vm.state.value.draft)

        vm.applyInitialMention("/outro/README.md")
        advanceUntilIdle()
        assertEquals("@src/App.kt @/outro/README.md ", vm.state.value.draft)

        vm.applyInitialMention(null)
        advanceUntilIdle()
        assertEquals("@src/App.kt @/outro/README.md ", vm.state.value.draft)
    }

    @Test fun initialMentionWaitsForConversation() = runTest(dispatcher) {
        val backend = FakeComposerBackend(initial = null)
        val vm = newVm(backend)

        vm.applyInitialMention("/proj/a.kt")
        advanceTimeBy(500)
        assertEquals("", vm.state.value.draft)

        backend.conv.value = conversation()
        advanceUntilIdle()
        assertEquals("@a.kt ", vm.state.value.draft)
    }

    // ---------------------------------------------------------------- paleta «/»

    @Test fun slashPopupLoadsFiltersAndPicks() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)

        vm.type("/c")
        val loading = vm.state.value.popup as ComposerPopup.Slash
        assertEquals(ComposerLoad.Loading, loading.items)

        advanceUntilIdle()
        val ready = (vm.state.value.popup as ComposerPopup.Slash).items as ComposerLoad.Ready
        assertEquals(listOf("compact", "clear", "config"), ready.value.map { it.name })

        vm.type("/co")
        advanceUntilIdle()
        assertEquals(1, backend.slashCalls) // cache

        vm.pickSlash(backend.slashCommands[0])
        assertEquals("/compact ", vm.state.value.draft)
        assertEquals(ComposerPopup.None, vm.state.value.popup)
    }

    @Test fun pickingDesktopOnlyCommandShowsNotice() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)
        vm.type("/con")
        advanceUntilIdle()

        vm.pickSlash(backend.slashCommands[2])
        assertEquals("/con", vm.state.value.draft)
        assertEquals(ComposerNotice.DesktopOnly("/config"), vm.state.value.notice)
    }

    @Test fun slashErrorIsShownAndRetried() = runTest(dispatcher) {
        val backend = FakeComposerBackend().apply { slashError = IllegalStateException("Sem comandos.") }
        val vm = newVm(backend)
        vm.type("/")
        advanceUntilIdle()
        assertEquals(ComposerLoad.Failed("Sem comandos."), (vm.state.value.popup as ComposerPopup.Slash).items)

        backend.slashError = null
        vm.retryPopup()
        advanceUntilIdle()
        assertTrue((vm.state.value.popup as ComposerPopup.Slash).items is ComposerLoad.Ready)
    }

    @Test fun dismissedPopupStaysClosedForSameTrigger() = runTest(dispatcher) {
        val vm = newVm(FakeComposerBackend())
        vm.type("/c")
        advanceUntilIdle()
        vm.dismissPopup()
        vm.type("/co")
        assertEquals(ComposerPopup.None, vm.state.value.popup)
        vm.type("/co /")
        assertTrue(vm.state.value.popup is ComposerPopup.Slash)
    }

    // ---------------------------------------------------------------- menção «@»

    @Test fun mentionPopupListsAndNavigates() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        backend.dirs["/proj"] = DirListing(
            "/proj",
            listOf(DirEntry("src", EntryKind.DIR, null, null), DirEntry("README.md", EntryKind.FILE, 5, null)),
            truncated = false,
        )
        backend.dirs["/proj/src"] = DirListing("/proj/src", listOf(DirEntry("App.kt", EntryKind.FILE, 9, null)), false)
        val vm = newVm(backend)

        vm.type("veja @sr")
        advanceUntilIdle()
        val root = vm.state.value.popup as ComposerPopup.Mention
        assertEquals("", root.dir)
        assertEquals("sr", root.filter)
        assertEquals(listOf("src"), (root.items as ComposerLoad.Ready).value.map { it.name })

        vm.pickMention(DirEntry("src", EntryKind.DIR, null, null))
        assertEquals("veja @src/", vm.state.value.draft)
        advanceUntilIdle()
        val inSrc = vm.state.value.popup as ComposerPopup.Mention
        assertEquals("src", inSrc.dir)
        assertEquals(listOf("/proj", "/proj/src"), backend.listDirCalls)

        vm.pickMention(DirEntry("App.kt", EntryKind.FILE, 9, null))
        assertEquals("veja @src/App.kt ", vm.state.value.draft)
        assertEquals(ComposerPopup.None, vm.state.value.popup)
    }

    @Test fun mentionUpGoesToParent() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        backend.dirs["/proj"] = DirListing("/proj", emptyList(), false)
        backend.dirs["/proj/src"] = DirListing("/proj/src", emptyList(), false)
        val vm = newVm(backend)
        vm.type("@src/")
        advanceUntilIdle()
        vm.mentionUp()
        assertEquals("@", vm.state.value.draft)
        advanceUntilIdle()
        assertEquals("", (vm.state.value.popup as ComposerPopup.Mention).dir)
    }

    @Test fun mentionListingErrorIsReported() = runTest(dispatcher) {
        val vm = newVm(FakeComposerBackend())
        vm.type("@nada/")
        advanceUntilIdle()
        val items = (vm.state.value.popup as ComposerPopup.Mention).items
        assertEquals(ComposerLoad.Failed("Pasta não encontrada."), items)
    }

    // ---------------------------------------------------------------- anexos

    @Test fun attachmentUploadsAndIsSent() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)

        vm.addAttachment("notas.txt", "text/plain", 10) { PreparedUpload(ByteArray(10), "notas.txt", "text/plain") }
        assertTrue(vm.state.value.uploading)
        assertFalse(vm.state.value.canSend)

        advanceUntilIdle()
        val item = vm.state.value.attachments.single()
        assertEquals(AttachmentPhase.Ready, item.phase)
        assertEquals(Attachment("/uploads/notas.txt", "text/plain"), item.uploaded)
        assertTrue(vm.state.value.canSend) // só anexo, sem texto

        vm.send(SendMode.Send)
        advanceUntilIdle()
        assertEquals(listOf(Attachment("/uploads/notas.txt", "text/plain")), backend.sent.single().attachments)
        assertTrue(vm.state.value.attachments.isEmpty())
    }

    @Test fun oversizedFileIsRefusedLocally() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)
        var loaderCalled = false
        vm.addAttachment("grande.zip", "application/zip", 1_000) { loaderCalled = true; bytes(1_000) }
        advanceUntilIdle()

        assertTrue(vm.state.value.attachments.isEmpty())
        assertEquals(ComposerNotice.FileTooLarge("grande.zip"), vm.state.value.notice)
        assertFalse(loaderCalled)
        assertTrue(backend.uploads.isEmpty())
    }

    @Test fun preparedBytesOverLimitAreRefused() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)
        vm.addAttachment("foto.jpg", "image/jpeg", 5_000) { bytes(500) }
        advanceUntilIdle()
        assertTrue(vm.state.value.attachments.isEmpty())
        assertEquals(ComposerNotice.FileTooLarge("foto.jpg"), vm.state.value.notice)

        vm.addAttachment("outra.jpg", "image/jpeg", null) { throw AttachmentTooLargeException(9_999) }
        advanceUntilIdle()
        assertTrue(vm.state.value.attachments.isEmpty())
        assertEquals(ComposerNotice.FileTooLarge("outra.jpg"), vm.state.value.notice)
        assertTrue(backend.uploads.isEmpty())
    }

    @Test fun tooManyAttachments() = runTest(dispatcher) {
        val vm = newVm(FakeComposerBackend())
        repeat(ComposerViewModel.MAX_ATTACHMENTS) { i -> vm.addAttachment("a$i.txt", "text/plain", 1) { bytes(1) } }
        advanceUntilIdle()
        vm.addAttachment("extra.txt", "text/plain", 1) { bytes(1) }
        assertEquals(ComposerViewModel.MAX_ATTACHMENTS, vm.state.value.attachments.size)
        assertEquals(ComposerNotice.TooManyAttachments, vm.state.value.notice)
    }

    @Test fun failedUploadBlocksSendAndCanBeRetried() = runTest(dispatcher) {
        val backend = FakeComposerBackend().apply { uploadError = IllegalStateException("Disco cheio.") }
        val vm = newVm(backend)
        vm.type("com anexo")
        vm.addAttachment("a.txt", "text/plain", 3) { bytes(3) }
        advanceUntilIdle()

        val failed = vm.state.value.attachments.single()
        assertEquals(AttachmentPhase.Failed, failed.phase)
        assertEquals("Disco cheio.", failed.error)
        assertFalse(vm.state.value.canSend)

        backend.uploadError = null
        vm.retryAttachment(failed.id)
        advanceUntilIdle()
        assertEquals(AttachmentPhase.Ready, vm.state.value.attachments.single().phase)
        assertTrue(vm.state.value.canSend)

        vm.removeAttachment(failed.id)
        assertTrue(vm.state.value.attachments.isEmpty())
    }

    @Test fun failedSendRestoresAttachments() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)
        vm.addAttachment("a.txt", "text/plain", 3) { bytes(3) }
        advanceUntilIdle()
        backend.sendError = IllegalStateException("Sem rede.")
        vm.send(SendMode.Send)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.attachments.size)
        assertEquals("Sem rede.", vm.state.value.error)
    }

    // ---------------------------------------------------------------- ditado

    @Test fun dictationInsertsAtCursor() = runTest(dispatcher) {
        val vm = newVm(FakeComposerBackend())
        vm.onTextChange("abc def", 3)
        vm.onDictationStarting()
        assertEquals(DictationPhase.Starting, vm.state.value.dictation)

        vm.onDictationPartial("olá")
        assertEquals("abc olá def", vm.state.value.draft)
        assertEquals(DictationPhase.Listening, vm.state.value.dictation)

        vm.onDictationLevel(4f)
        assertEquals(0.5f, vm.dictationLevel.value, 1e-6f)

        vm.onDictationResult("olá mundo")
        assertEquals("abc olá mundo def", vm.state.value.draft)
        assertEquals(7 + " mundo".length, vm.state.value.cursor)
        assertEquals(DictationPhase.Idle, vm.state.value.dictation)
        assertEquals(0f, vm.dictationLevel.value, 0f)
    }

    @Test fun userEditStopsDictation() = runTest(dispatcher) {
        val vm = newVm(FakeComposerBackend())
        val events = collectEvents(vm)
        vm.onDictationStarting()
        vm.onDictationPartial("oi")
        vm.onTextChange("oi!", 3)
        assertEquals(DictationPhase.Idle, vm.state.value.dictation)
        assertEquals(listOf<ComposerEvent>(ComposerEvent.StopDictation), events)

        vm.onDictationPartial("depois") // ignorado: o ditado acabou
        assertEquals("oi!", vm.state.value.draft)
    }

    @Test fun dictationErrorShowsNotice() = runTest(dispatcher) {
        val vm = newVm(FakeComposerBackend())
        vm.onDictationStarting()
        vm.onDictationError(ComposerNotice.NoRecognizer)
        assertEquals(DictationPhase.Idle, vm.state.value.dictation)
        assertEquals(ComposerNotice.NoRecognizer, vm.state.value.notice)
    }

    // ---------------------------------------------------------------- modelo e esforço

    @Test fun catalogLoadsOnceUnlessRefresh() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)
        vm.loadCatalog()
        assertEquals(ComposerLoad.Loading, vm.state.value.catalog)
        advanceUntilIdle()
        assertEquals(3, (vm.state.value.catalog as ComposerLoad.Ready).value.size)

        vm.loadCatalog()
        advanceUntilIdle()
        assertEquals(1, backend.catalogCalls)

        vm.loadCatalog(refresh = true)
        advanceUntilIdle()
        assertEquals(2, backend.catalogCalls)
    }

    @Test fun catalogErrorIsReported() = runTest(dispatcher) {
        val backend = FakeComposerBackend().apply { catalogError = IllegalStateException("Sem catálogo.") }
        val vm = newVm(backend)
        vm.loadCatalog()
        advanceUntilIdle()
        assertEquals(ComposerLoad.Failed("Sem catálogo."), vm.state.value.catalog)
    }

    @Test fun selectModelKeepsCompatibleEffortAndSaves() = runTest(dispatcher) {
        val backend = FakeComposerBackend()
        val vm = newVm(backend)
        val opus = backend.catalog[1]

        vm.selectModel(opus) // "medium" não existe no opus → padrão "high"
        assertEquals("opus", vm.state.value.model)
        assertEquals("high", vm.state.value.effort)
        assertTrue(vm.state.value.optionsSaving)

        advanceUntilIdle()
        assertEquals(listOf<Pair<String?, String?>>("opus" to "high"), backend.setOptionsCalls)
        val s = vm.state.value
        assertFalse(s.optionsSaving)
        assertNull(s.pendingModel)
        assertNull(s.pendingEffort)
        assertEquals("opus", s.conversation?.model)

        vm.selectEffort("max")
        advanceUntilIdle()
        assertEquals(null to "max", backend.setOptionsCalls.last())
        assertEquals("max", vm.state.value.effort)

        vm.selectEffort("max") // sem mudança: nada a salvar
        advanceUntilIdle()
        assertEquals(2, backend.setOptionsCalls.size)
    }

    @Test fun selectModelFailureRevertsPending() = runTest(dispatcher) {
        val backend = FakeComposerBackend().apply { setOptionsError = IllegalStateException("Não salvou.") }
        val vm = newVm(backend)
        vm.selectModel(backend.catalog[2])
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals("Não salvou.", s.optionsError)
        assertFalse(s.optionsSaving)
        assertEquals("sonnet", s.model)
        assertEquals("medium", s.effort)
    }
}
