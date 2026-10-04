package br.com.amberwrite.aistack.feature.fileview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.rpc.userMessage
import br.com.amberwrite.aistack.data.model.FileContent
import br.com.amberwrite.aistack.data.repo.FilesRepo
import br.com.amberwrite.aistack.feature.files.FilesErrorKind
import br.com.amberwrite.aistack.feature.files.FilesLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lê um arquivo do desktop (`readFile`) em etapas: começa com um pedaço que abre rápido e o
 * usuário pode "carregar mais" até o teto do túnel (1 MiB com `frag`, 32 KiB sem). O texto é
 * quebrado em linhas e realçado fora da thread principal.
 */
class FileViewViewModel(
    val path: String,
    private val reader: suspend (path: String, maxBytes: Int) -> FileContent,
    private val fragAvailable: () -> Boolean,
    private val compute: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    constructor(container: AppContainer, path: String) : this(
        path = path,
        reader = { p, max -> container.filesRepo.readFile(p, max) },
        fragAvailable = { container.rpc.hostFrag }
    )

    data class UiState(
        val path: String,
        val fileName: String = FilesLogic.displayName(path),
        val content: FileContent? = null,
        val lines: List<String> = emptyList(),
        val tokens: List<List<Token>> = emptyList(),
        val language: String? = null,
        val maxLineLength: Int = 0,
        /** Bytes de texto recebidos (UTF-8). */
        val shownBytes: Long = 0,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val errorKind: FilesErrorKind? = null,
        /** Bytes pedidos na última leitura. */
        val limit: Int = 0,
        /** Teto de bytes por leitura nesta conexão. */
        val maxLimit: Int = 0,
        val wrap: Boolean = false
    ) {
        val isBinary: Boolean get() = content is FileContent.Binary
        val isText: Boolean get() = content is FileContent.Text
        val truncated: Boolean get() = content?.truncated == true
        val canLoadMore: Boolean get() = isText && truncated && limit < maxLimit
        /** Cortado no teto: o resto só dá para ver no desktop. */
        val hitCeiling: Boolean get() = isText && truncated && limit >= maxLimit
        val isEmptyText: Boolean get() = isText && (content as FileContent.Text).text.isEmpty()
        val showSkeleton: Boolean get() = loading && content == null && error == null
        val showFullError: Boolean get() = error != null && content == null && !loading
    }

    private val _state = MutableStateFlow(UiState(path = path))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load(initialLimit(), more = false)
    }

    private fun ceiling(): Int = if (fragAvailable()) FilesRepo.READ_MAX_FRAG else FilesRepo.READ_MAX_NO_FRAG

    private fun initialLimit(): Int = minOf(INITIAL_BYTES, ceiling())

    /** Lê de novo do começo (puxar para atualizar / tentar de novo). */
    fun reload() = load(initialLimit(), more = false)

    /** Pede um pedaço maior do arquivo (até o teto do túnel). */
    fun loadMore() {
        val s = _state.value
        if (!s.canLoadMore || s.loadingMore) return
        load(nextLimit(s.limit, s.maxLimit), more = true)
    }

    fun toggleWrap() = _state.update { it.copy(wrap = !it.wrap) }

    private fun load(limit: Int, more: Boolean) {
        job?.cancel()
        val max = ceiling()
        _state.update {
            it.copy(
                loading = !more,
                loadingMore = more,
                error = null,
                errorKind = null,
                maxLimit = max
            )
        }
        job = viewModelScope.launch {
            try {
                val content = reader(path, limit)
                val prepared = withContext(compute) { prepare(content) }
                _state.update {
                    it.copy(
                        content = content,
                        lines = prepared.lines,
                        tokens = prepared.tokens,
                        language = prepared.language,
                        maxLineLength = prepared.maxLineLength,
                        shownBytes = prepared.bytes,
                        loading = false,
                        loadingMore = false,
                        limit = limit,
                        maxLimit = max
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, loadingMore = false, error = e.userMessage, errorKind = FilesLogic.classify(e))
                }
            }
        }
    }

    private data class Prepared(
        val lines: List<String>,
        val tokens: List<List<Token>>,
        val language: String?,
        val maxLineLength: Int,
        val bytes: Long
    )

    private fun prepare(content: FileContent): Prepared {
        if (content !is FileContent.Text) return Prepared(emptyList(), emptyList(), null, 0, 0)
        val lines = splitLines(content.text)
        val spec = SyntaxHighlighter.detect(content.path, content.language)
        val tokens = SyntaxHighlighter.highlight(lines, spec)
        val label = if (spec == SyntaxHighlighter.PLAIN) null else spec.label
        return Prepared(lines, tokens, label, lines.maxOfOrNull { it.length } ?: 0, content.text.toByteArray(Charsets.UTF_8).size.toLong())
    }

    companion object {
        /** Primeira leitura: abre rápido mesmo em arquivos grandes. */
        const val INITIAL_BYTES = 256 * 1024

        /** Próximo tamanho de leitura: quadruplica até o teto. */
        fun nextLimit(current: Int, max: Int): Int = minOf(max, maxOf(current * 4, current + 1))

        /** Quebra em linhas aceitando `\n` e `\r\n`; ignora a quebra final. */
        fun splitLines(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            val body = if (text.endsWith("\n")) text.dropLast(1) else text
            return body.split('\n').map { it.removeSuffix("\r") }
        }
    }
}
