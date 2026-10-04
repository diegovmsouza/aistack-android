package br.com.amberwrite.aistack.feature.files

import br.com.amberwrite.aistack.core.rpc.RpcException
import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.EntryKind

/** Um item da trilha (breadcrumb). `path == null` é a lista de raízes do escopo. */
data class Crumb(val label: String, val path: String?, val isRoots: Boolean = false)

/** Família do arquivo, usada para escolher o ícone e a cor. */
enum class FileType { Folder, Code, Markup, Config, Text, Image, Archive, Data, Lock, Other }

/** Por que a listagem falhou (muda a ilustração e o texto do estado de erro). */
enum class FilesErrorKind { NoPermission, NotFound, NotDirectory, Offline, Generic }

/** Lógica pura do explorador: ordenação, filtro, trilha, caminhos e classificação. */
object FilesLogic {

    /** Pastas primeiro, depois por nome sem diferenciar maiúsculas (igual ao contrato §9.3). */
    fun sort(entries: List<DirEntry>): List<DirEntry> = entries.sortedWith(
        compareBy<DirEntry> { if (it.kind == EntryKind.DIR) 0 else 1 }
            .thenBy { it.name.lowercase() }
            .thenBy { it.name }
    )

    /** Busca local por trecho do nome, sem diferenciar maiúsculas. Consulta vazia = tudo. */
    fun filter(entries: List<DirEntry>, query: String): List<DirEntry> {
        val q = query.trim()
        if (q.isEmpty()) return entries
        return entries.filter { it.name.contains(q, ignoreCase = true) }
    }

    /** Separador do caminho (o desktop pode ser Windows). */
    fun separatorOf(path: String): Char = if ('/' !in path && '\\' in path) '\\' else '/'

    /** Caminho absoluto de um filho. Nas raízes (`base == null`) o nome já é absoluto. */
    fun childPath(base: String?, name: String): String {
        if (base.isNullOrEmpty()) return name
        val sep = separatorOf(base)
        return if (base.endsWith(sep)) "$base$name" else "$base$sep$name"
    }

    /** Raiz do escopo que contém [path] (a mais longa, para raízes aninhadas). */
    fun rootOf(path: String, roots: List<String>): String? = roots
        .filter { r -> isInside(path, r) }
        .maxByOrNull { it.length }

    private fun isInside(path: String, root: String): Boolean {
        val r = root.trimEnd('/', '\\')
        if (r.isEmpty()) return path.startsWith(root)
        return path == r || path == root || path.startsWith("$r/") || path.startsWith("$r\\")
    }

    /**
     * Pasta-mãe dentro do escopo. Numa raiz (ou fora de qualquer raiz conhecida, no topo)
     * devolve `null`, que significa "voltar para a lista de raízes".
     */
    fun parentOf(path: String?, roots: List<String>): String? {
        if (path == null) return null
        val root = rootOf(path, roots)
        if (root != null && path.trimEnd('/', '\\') == root.trimEnd('/', '\\')) return null
        val sep = separatorOf(path)
        val trimmed = path.trimEnd(sep)
        val idx = trimmed.lastIndexOf(sep)
        if (idx < 0) return null
        val parent = if (idx == 0) sep.toString() else trimmed.substring(0, idx)
        if (root == null && parent == sep.toString() && roots.isNotEmpty()) return null
        return parent
    }

    /**
     * Trilha a partir da raiz do escopo: [Raízes] › projeto › src › main. Se o caminho não estiver
     * em nenhuma raiz conhecida (ex.: raízes ainda não carregadas), quebra o caminho inteiro.
     */
    fun crumbs(path: String?, roots: List<String>): List<Crumb> {
        val rootsCrumb = Crumb(label = "", path = null, isRoots = true)
        if (path.isNullOrEmpty()) return listOf(rootsCrumb)
        val sep = separatorOf(path)
        val root = rootOf(path, roots)
        val out = mutableListOf(rootsCrumb)
        if (root != null) {
            val rootClean = root.trimEnd('/', '\\').ifEmpty { root }
            out += Crumb(label = displayName(rootClean), path = rootClean)
            val rest = path.removePrefix(rootClean).trim('/', '\\')
            var acc = rootClean
            if (rest.isNotEmpty()) {
                rest.split(sep).filter { it.isNotEmpty() }.forEach { seg ->
                    acc = childPath(acc, seg)
                    out += Crumb(label = seg, path = acc)
                }
            }
        } else {
            val parts = path.split(sep).filter { it.isNotEmpty() }
            var acc = if (path.startsWith(sep)) sep.toString() else ""
            parts.forEach { seg ->
                acc = if (acc.isEmpty()) seg else childPath(acc, seg)
                out += Crumb(label = seg, path = acc)
            }
        }
        return out
    }

    /** Último segmento do caminho (ou o próprio caminho se for a raiz do disco). */
    fun displayName(path: String): String {
        val trimmed = path.trimEnd('/', '\\')
        if (trimmed.isEmpty()) return path
        val idx = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        return if (idx < 0) trimmed else trimmed.substring(idx + 1).ifEmpty { trimmed }
    }

    /** Extensão em minúsculas, sem o ponto ("" se não houver). */
    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase()
    }

    private val CODE = setOf(
        "kt", "kts", "java", "scala", "groovy", "gradle", "js", "mjs", "cjs", "jsx", "ts", "tsx", "py", "rb", "go",
        "rs", "c", "h", "cc", "cpp", "hpp", "cs", "swift", "m", "php", "sh", "bash", "zsh", "fish", "ps1", "lua",
        "dart", "r", "sql", "vue", "svelte", "ex", "exs", "erl", "hs", "clj", "zig", "nim", "pl"
    )
    private val MARKUP = setOf("html", "htm", "xml", "svg", "css", "scss", "sass", "less", "md", "mdx", "rst", "tex")
    private val CONFIG = setOf("json", "jsonc", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "env", "editorconfig")
    private val TEXT = setOf("txt", "log", "csv", "tsv", "rtf")
    private val IMAGE = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "heic", "avif", "tiff")
    private val ARCHIVE = setOf("zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar", "aar", "apk", "deb", "rpm", "dmg", "iso")
    private val DATA = setOf("db", "sqlite", "sqlite3", "parquet", "bin", "dat", "pdf", "mp3", "mp4", "wav", "ogg", "mov", "woff", "woff2", "ttf", "otf")
    private val SPECIAL_TEXT = setOf(
        "dockerfile", "makefile", "readme", "license", "changelog", "gemfile", "procfile", ".gitignore",
        ".gitattributes", ".dockerignore", ".npmrc", ".prettierrc", ".eslintrc"
    )

    /** Família do arquivo pelo nome/extensão. */
    fun fileType(name: String, kind: EntryKind): FileType {
        if (kind == EntryKind.DIR) return FileType.Folder
        val lower = name.lowercase()
        if (lower.endsWith(".lock") || lower == "package-lock.json") return FileType.Lock
        val ext = extension(name)
        return when {
            ext in CODE -> FileType.Code
            ext in MARKUP -> FileType.Markup
            ext in CONFIG || lower.startsWith(".env") -> FileType.Config
            ext in TEXT -> FileType.Text
            ext in IMAGE -> FileType.Image
            ext in ARCHIVE -> FileType.Archive
            ext in DATA -> FileType.Data
            lower in SPECIAL_TEXT || lower.substringBefore('.') in SPECIAL_TEXT -> FileType.Text
            else -> FileType.Other
        }
    }

    /** Classifica a falha de `listDir` (texto do host em português, §9.3). */
    fun classify(error: Throwable): FilesErrorKind {
        if (error is RpcException) {
            when (error.kind) {
                RpcException.Kind.NOT_ALLOWED -> return FilesErrorKind.NoPermission
                RpcException.Kind.DISCONNECTED, RpcException.Kind.TIMEOUT -> return FilesErrorKind.Offline
                else -> Unit
            }
        }
        val msg = error.message.orEmpty().lowercase()
        return when {
            "fora do escopo" in msg || "não permitido" in msg || "permission" in msg || "permissão" in msg ->
                FilesErrorKind.NoPermission
            "não encontrado" in msg || "not found" in msg -> FilesErrorKind.NotFound
            "não é uma pasta" in msg -> FilesErrorKind.NotDirectory
            else -> FilesErrorKind.Generic
        }
    }
}
