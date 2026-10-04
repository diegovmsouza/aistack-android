package br.com.amberwrite.aistack.feature.chat.composer

import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.EntryKind
import br.com.amberwrite.aistack.data.model.ModelInfo
import br.com.amberwrite.aistack.data.model.SlashCommand
import br.com.amberwrite.aistack.ui.designsystem.components.AttachmentKind
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Lógica pura do composer (sem Android): gatilhos «/» e «@», filtros, inserção de texto,
 * caminhos de menção, ditado, escala de imagem e nível do microfone. Testada na JVM.
 */

/** Um gatilho ativo no texto: o caractere («/» ou «@») está em [start]; [end] é o cursor. */
data class Trigger(val start: Int, val end: Int, val query: String)

/** Texto + cursor resultantes de uma edição feita pelo composer. */
data class TextEdit(val text: String, val cursor: Int)

private val SLASH_TRIGGER = Regex("""(?:^|\s)/([a-zA-Z0-9_-]*)$""")
private val MENTION_TRIGGER = Regex("""(?:^|\s)@([a-zA-Z0-9_\-./\\]*)$""")

/** Máximo de sugestões de arquivo mostradas de uma vez. */
const val MENTION_LIMIT = 60

/** Gatilho «/comando» que termina no cursor (mesma regra do desktop, filtro por prefixo). */
fun findSlashTrigger(text: String, cursor: Int): Trigger? = findTrigger(SLASH_TRIGGER, text, cursor)

/** Gatilho «@caminho» que termina no cursor. */
fun findMentionTrigger(text: String, cursor: Int): Trigger? = findTrigger(MENTION_TRIGGER, text, cursor)

private fun findTrigger(regex: Regex, text: String, cursor: Int): Trigger? {
    if (cursor < 0 || cursor > text.length) return null
    val before = text.substring(0, cursor)
    val m = regex.find(before) ?: return null
    val query = m.groupValues[1]
    return Trigger(start = cursor - query.length - 1, end = cursor, query = query)
}

/**
 * Troca o gatilho por [replacement] (ex.: `/compact `). Se o texto seguinte já começa com espaço,
 * o espaço final da troca não é duplicado.
 */
fun replaceTrigger(text: String, trigger: Trigger, replacement: String): TextEdit {
    val before = text.substring(0, trigger.start.coerceIn(0, text.length))
    var after = text.substring(trigger.end.coerceIn(0, text.length))
    if (replacement.endsWith(' ') && after.startsWith(' ')) after = after.drop(1)
    val out = before + replacement + after
    return TextEdit(out, before.length + replacement.length)
}

/** Insere `@caminho ` no fim do rascunho, separando com espaço quando preciso. */
fun appendMention(draft: String, path: String): TextEdit {
    val clean = path.trim()
    if (clean.isEmpty()) return TextEdit(draft, draft.length)
    val sep = if (draft.isEmpty() || draft.last().isWhitespace()) "" else " "
    val out = "$draft$sep@$clean "
    return TextEdit(out, out.length)
}

/** Caminho como deve aparecer na menção: relativo ao projeto quando estiver dentro dele. */
fun relativizeMention(path: String, projectPath: String?): String {
    val p = path.trim().replace('\\', '/')
    val root = projectPath?.trim()?.replace('\\', '/')?.trimEnd('/')
    if (root.isNullOrEmpty()) return p
    return when {
        p == root -> "."
        p.startsWith("$root/") -> p.removePrefix("$root/")
        else -> p
    }
}

/** Divide a consulta de menção em pasta (relativa ou absoluta) e filtro: `src/ma` → (`src`, `ma`). */
fun splitMentionQuery(query: String): Pair<String, String> {
    val q = query.replace('\\', '/')
    val i = q.lastIndexOf('/')
    if (i < 0) return "" to q
    val dir = if (i == 0) "/" else q.substring(0, i)
    return dir to q.substring(i + 1)
}

/** Pasta pai de uma pasta relativa (`a/b` → `a`, `a` → ``); absoluta sobe até `/`. */
fun parentDir(dir: String): String {
    val d = dir.trimEnd('/')
    if (d.isEmpty()) return if (dir.startsWith("/")) "/" else ""
    val i = d.lastIndexOf('/')
    return when {
        i < 0 -> ""
        i == 0 -> "/"
        else -> d.substring(0, i)
    }
}

/**
 * Caminho absoluto para o `listDir`: [dir] relativo é resolvido contra o projeto; absoluto é
 * usado como está. Sem projeto e sem pasta, devolve `null` (raízes do escopo).
 */
fun resolveListPath(projectPath: String?, dir: String): String? {
    val d = dir.replace('\\', '/')
    if (d.startsWith("/")) return if (d.length > 1) d.trimEnd('/') else "/"
    val root = projectPath?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
    val rel = d.trim('/')
    return when {
        root == null -> rel.ifEmpty { null }
        rel.isEmpty() -> root
        else -> "$root/$rel"
    }
}

/** Texto a inserir ao escolher uma entrada da pasta [dir]: pasta termina em `/` (continua navegando). */
fun mentionInsertPath(dir: String, entry: DirEntry): String {
    val base = when {
        dir.isEmpty() -> ""
        dir.endsWith("/") -> dir
        else -> "$dir/"
    }
    return if (entry.kind == EntryKind.DIR) "$base${entry.name}/" else "$base${entry.name}"
}

/**
 * Filtra a listagem por substring (sem diferenciar caixa). Quem começa com o filtro vem antes;
 * pastas continuam antes dos arquivos. Arquivos ocultos só aparecem se o filtro começar com «.».
 */
fun filterEntries(entries: List<DirEntry>, filter: String, limit: Int = MENTION_LIMIT): List<DirEntry> {
    val f = filter.trim()
    val showHidden = f.startsWith(".")
    return entries.asSequence()
        .filter { it.kind != EntryKind.OTHER }
        .filter { showHidden || !it.name.startsWith(".") }
        .filter { f.isEmpty() || it.name.contains(f, ignoreCase = true) }
        .sortedWith(
            compareBy<DirEntry>(
                { if (it.kind == EntryKind.DIR) 0 else 1 },
                { if (f.isNotEmpty() && it.name.startsWith(f, ignoreCase = true)) 0 else 1 },
            )
        )
        .take(limit)
        .toList()
}

/** Nome do comando sem a barra. */
val SlashCommand.bareName: String get() = name.removePrefix("/")

/** Filtro da paleta «/»: por prefixo (sem caixa); comandos só do desktop ficam no fim. */
fun filterSlash(commands: List<SlashCommand>, query: String): List<SlashCommand> {
    val q = query.trim()
    return commands
        .filter { q.isEmpty() || it.bareName.startsWith(q, ignoreCase = true) }
        .sortedWith(compareBy({ it.desktopOnly }, { !it.bareName.equals(q, ignoreCase = true) }))
}

/**
 * Junta o texto ditado ao rascunho, entre [prefix] (antes do cursor) e [suffix] (depois),
 * com os espaços necessários. O cursor fica logo depois do texto falado.
 */
fun joinDictation(prefix: String, spoken: String, suffix: String): TextEdit {
    val s = spoken.trim()
    if (s.isEmpty()) return TextEdit(prefix + suffix, prefix.length)
    val lead = if (prefix.isNotEmpty() && !prefix.last().isWhitespace()) " " else ""
    val trail = if (suffix.isNotEmpty() && !suffix.first().isWhitespace()) " " else ""
    val head = prefix + lead + s
    return TextEdit(head + trail + suffix, head.length)
}

/** Converte o `rmsdB` do SpeechRecognizer (≈ -2..10) em nível 0..1 para a onda. */
fun rmsToLevel(rmsdB: Float): Float = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)

/** Passo do progresso estimado de envio: aproxima-se de 95% sem chegar (o 100% vem da resposta). */
fun uploadProgressStep(current: Float): Float = (current + (0.95f - current) * 0.12f).coerceIn(0f, 0.95f)

/** Maior `inSampleSize` (potência de 2) que ainda deixa o lado maior ≥ [maxSide]. */
fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    val longest = max(width, height)
    if (longest <= 0 || maxSide <= 0) return 1
    while (longest / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/** Dimensões finais com o lado maior limitado a [maxSide] (nunca amplia). */
fun scaledSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    val longest = max(width, height)
    if (longest <= maxSide || longest <= 0) return width to height
    val f = maxSide.toFloat() / longest
    return max(1, (width * f).roundToInt()) to max(1, (height * f).roundToInt())
}

/** Graus de rotação para a orientação EXIF (espelhamentos são ignorados). */
fun exifRotation(orientation: Int): Int = when (orientation) {
    3, 4 -> 180
    5, 6 -> 90
    7, 8 -> 270
    else -> 0
}

/** Nome do arquivo JPEG gerado a partir do original (`foto.heic` → `foto.jpg`). */
fun jpegName(original: String): String {
    val base = original.substringBeforeLast('.', original).ifBlank { "imagem" }
    return "$base.jpg"
}

private val CODE_EXT = setOf(
    "kt", "kts", "java", "ts", "tsx", "js", "jsx", "mjs", "py", "rs", "go", "rb", "php", "c", "h", "cpp", "hpp",
    "cs", "swift", "m", "scala", "sh", "bash", "zsh", "sql", "html", "css", "scss", "vue", "svelte", "json",
    "yaml", "yml", "toml", "xml", "gradle", "dart", "lua",
)
private val TEXT_EXT = setOf("txt", "md", "markdown", "csv", "log", "rst", "tex")

/** Tipo visual do anexo pelo MIME e pela extensão. */
fun attachmentKindFor(name: String, mime: String): AttachmentKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") -> AttachmentKind.Image
        mime.startsWith("audio/") -> AttachmentKind.Audio
        ext in CODE_EXT -> AttachmentKind.Code
        ext in TEXT_EXT || mime.startsWith("text/") -> AttachmentKind.Text
        else -> AttachmentKind.File
    }
}

/** MIME pelo nome quando o provedor de conteúdo não informa. */
fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "pdf" -> "application/pdf"
    "zip" -> "application/zip"
    "json" -> "application/json"
    "md", "markdown" -> "text/markdown"
    "txt", "log" -> "text/plain"
    "csv" -> "text/csv"
    "html" -> "text/html"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "wav" -> "audio/wav"
    else -> "application/octet-stream"
}

/** Imagens que vale recomprimir (GIF fica como está para não perder a animação). */
fun shouldCompressImage(mime: String): Boolean = mime.startsWith("image/") && mime != "image/gif" && mime != "image/svg+xml"

/** Esforço a manter ao trocar de modelo: o atual se o modelo aceitar; senão o padrão dele. */
fun effortForModel(model: ModelInfo, current: String?): String? = when {
    model.efforts.isEmpty() -> null
    current != null && current in model.efforts -> current
    model.defaultEffort != null && model.defaultEffort in model.efforts -> model.defaultEffort
    else -> model.efforts.first()
}

/** Índice do esforço no slider (o padrão do modelo, ou o primeiro, quando não houver). */
fun effortIndex(levels: List<String>, effort: String?, default: String?): Int {
    if (levels.isEmpty()) return 0
    val i = levels.indexOf(effort)
    if (i >= 0) return i
    val d = levels.indexOf(default)
    return if (d >= 0) d else 0
}

/** Rótulo curto do modelo: nome do catálogo, senão o id sem prefixos longos. */
fun modelLabel(modelId: String?, catalog: List<ModelInfo>): String? {
    if (modelId.isNullOrBlank()) return catalog.firstOrNull { it.isDefault }?.displayName
    return catalog.firstOrNull { it.id == modelId }?.displayName ?: modelId
}

/** Rótulo pt-BR do nível de esforço (valores desconhecidos aparecem como vieram, capitalizados). */
fun effortLabel(effort: String?): String? = when (effort?.lowercase()) {
    null, "" -> null
    "minimal" -> "Mínimo"
    "low" -> "Baixo"
    "medium" -> "Médio"
    "high" -> "Alto"
    "xhigh" -> "Muito alto"
    "max" -> "Máximo"
    else -> effort.replaceFirstChar { it.uppercase() }
}
