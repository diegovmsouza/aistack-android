package br.com.amberwrite.aistack.feature.fileview

/** Categoria de um trecho realçado (mapeada para cor na tela). */
enum class TokenKind { Keyword, Type, Str, Comment, Number, Annotation, Tag, Attr, Key, Heading, Punct }

/** Trecho `[start, end)` de uma linha. */
data class Token(val start: Int, val end: Int, val kind: TokenKind)

/**
 * Realce de sintaxe leve e puro (sem Android), feito linha a linha com o mínimo de estado entre
 * linhas (comentários de bloco, tags abertas, blocos de código no Markdown). Não pretende ser um
 * parser: o objetivo é leitura confortável no celular sem custo de CPU perceptível.
 */
object SyntaxHighlighter {

    enum class Mode { Code, Json, Config, Markup, Markdown, Plain }

    data class Spec(
        val id: String,
        val label: String,
        val mode: Mode = Mode.Code,
        val keywords: Set<String> = emptySet(),
        val lineComments: List<String> = emptyList(),
        val block: Pair<String, String>? = null,
        val quotes: String = "\"'",
        val caseInsensitive: Boolean = false,
        val capsAreTypes: Boolean = true,
        val annotations: Boolean = false
    )

    /** Linhas maiores que isto só são realçadas no começo (mantém o custo previsível). */
    const val MAX_LINE_SCAN = 2_000

    private fun kw(vararg words: String) = words.toSet()

    private val C_LIKE_COMMON = kw(
        "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return",
        "true", "false", "null", "new", "this", "try", "catch", "finally", "throw", "class", "interface",
        "enum", "public", "private", "protected", "static", "final", "void", "import", "package", "const"
    )

    val KOTLIN = Spec(
        "kotlin", "Kotlin",
        keywords = C_LIKE_COMMON + kw(
            "fun", "val", "var", "object", "when", "is", "in", "as", "data", "sealed", "override", "open",
            "abstract", "internal", "companion", "suspend", "inline", "reified", "lateinit", "by", "init",
            "typealias", "out", "where", "constructor", "operator", "infix", "tailrec", "vararg", "crossinline",
            "noinline", "annotation", "inner", "value", "it", "super", "get", "set"
        ),
        lineComments = listOf("//"), block = "/*" to "*/", annotations = true
    )
    val JAVA = Spec(
        "java", "Java",
        keywords = C_LIKE_COMMON + kw(
            "extends", "implements", "abstract", "synchronized", "volatile", "transient", "native", "instanceof",
            "throws", "super", "int", "long", "short", "byte", "char", "boolean", "float", "double", "var",
            "record", "yield", "assert"
        ),
        lineComments = listOf("//"), block = "/*" to "*/", annotations = true
    )
    val JS = Spec(
        "javascript", "JavaScript",
        keywords = C_LIKE_COMMON + kw(
            "function", "let", "var", "of", "in", "typeof", "instanceof", "async", "await", "yield", "export",
            "from", "extends", "super", "undefined", "delete", "void", "static", "get", "set", "as", "type",
            "implements", "readonly", "declare", "namespace", "keyof", "never", "unknown", "any", "string",
            "number", "boolean", "satisfies"
        ),
        lineComments = listOf("//"), block = "/*" to "*/", quotes = "\"'`", annotations = true
    )
    val TS = JS.copy(id = "typescript", label = "TypeScript")
    val PYTHON = Spec(
        "python", "Python",
        keywords = kw(
            "def", "class", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "is", "return",
            "import", "from", "as", "with", "try", "except", "finally", "raise", "pass", "break", "continue",
            "lambda", "yield", "global", "nonlocal", "assert", "del", "True", "False", "None", "async", "await",
            "self", "match", "case"
        ),
        lineComments = listOf("#"), block = "\"\"\"" to "\"\"\"", annotations = true
    )
    val GO = Spec(
        "go", "Go",
        keywords = kw(
            "func", "package", "import", "var", "const", "type", "struct", "interface", "map", "chan", "go",
            "defer", "select", "if", "else", "for", "range", "switch", "case", "default", "return", "break",
            "continue", "fallthrough", "goto", "nil", "true", "false", "string", "int", "int64", "bool", "error",
            "byte", "rune", "float64", "any"
        ),
        lineComments = listOf("//"), block = "/*" to "*/", quotes = "\"'`"
    )
    val RUST = Spec(
        "rust", "Rust",
        keywords = kw(
            "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "for", "in", "if", "else",
            "match", "loop", "while", "return", "break", "continue", "use", "mod", "pub", "crate", "self",
            "Self", "super", "as", "where", "async", "await", "move", "ref", "dyn", "unsafe", "true", "false",
            "type", "extern"
        ),
        lineComments = listOf("//"), block = "/*" to "*/", quotes = "\""
    )
    val C = Spec(
        "c", "C/C++",
        keywords = C_LIKE_COMMON + kw(
            "int", "long", "short", "char", "unsigned", "signed", "float", "double", "bool", "struct", "union",
            "typedef", "sizeof", "extern", "inline", "auto", "register", "volatile", "goto", "namespace",
            "using", "template", "typename", "virtual", "override", "nullptr", "delete", "operator", "#include",
            "#define", "#ifdef", "#ifndef", "#endif", "#pragma"
        ),
        lineComments = listOf("//"), block = "/*" to "*/"
    )
    val CSHARP = JAVA.copy(
        id = "csharp", label = "C#",
        keywords = JAVA.keywords + kw("namespace", "using", "var", "async", "await", "readonly", "string", "get", "set", "is", "as", "out", "ref", "override", "virtual", "internal")
    )
    val SWIFT = Spec(
        "swift", "Swift",
        keywords = C_LIKE_COMMON + kw("func", "let", "var", "struct", "protocol", "extension", "guard", "in", "nil", "self", "Self", "init", "deinit", "inout", "async", "await", "some", "any", "where", "override", "mutating", "import"),
        lineComments = listOf("//"), block = "/*" to "*/", annotations = true
    )
    val DART = JAVA.copy(id = "dart", label = "Dart", keywords = JAVA.keywords + kw("var", "late", "required", "async", "await", "dynamic", "mixin", "with", "factory", "is", "in"))
    val PHP = Spec(
        "php", "PHP",
        keywords = C_LIKE_COMMON + kw("function", "echo", "foreach", "as", "use", "namespace", "extends", "implements", "fn", "match", "array", "isset", "unset", "require", "include"),
        lineComments = listOf("//", "#"), block = "/*" to "*/"
    )
    val SHELL = Spec(
        "shell", "Shell",
        keywords = kw("if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until", "case", "esac", "function", "return", "export", "local", "readonly", "echo", "exit", "set", "unset", "source"),
        lineComments = listOf("#"), capsAreTypes = false
    )
    val RUBY = Spec(
        "ruby", "Ruby",
        keywords = kw("def", "end", "class", "module", "if", "elsif", "else", "unless", "while", "until", "for", "in", "do", "return", "yield", "begin", "rescue", "ensure", "raise", "self", "nil", "true", "false", "require", "attr_accessor", "then", "case", "when"),
        lineComments = listOf("#")
    )
    val LUA = Spec(
        "lua", "Lua",
        keywords = kw("function", "local", "end", "if", "then", "else", "elseif", "for", "in", "do", "while", "repeat", "until", "return", "nil", "true", "false", "and", "or", "not", "break"),
        lineComments = listOf("--"), block = "--[[" to "]]"
    )
    val SQL = Spec(
        "sql", "SQL",
        keywords = kw("select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create", "table", "drop", "alter", "index", "join", "left", "right", "inner", "outer", "on", "and", "or", "not", "null", "is", "as", "order", "by", "group", "having", "limit", "offset", "primary", "key", "foreign", "references", "default", "unique", "integer", "text", "real", "begin", "commit", "with", "case", "when", "then", "else", "end", "distinct", "union", "exists", "in"),
        lineComments = listOf("--"), block = "/*" to "*/", caseInsensitive = true, capsAreTypes = false
    )
    val CSS = Spec("css", "CSS", lineComments = emptyList(), block = "/*" to "*/", capsAreTypes = false, annotations = true)
    val JSON = Spec("json", "JSON", mode = Mode.Json, keywords = kw("true", "false", "null"), quotes = "\"", capsAreTypes = false)
    val YAML = Spec("yaml", "YAML", mode = Mode.Config, keywords = kw("true", "false", "null", "yes", "no", "on", "off", "~"), lineComments = listOf("#"), capsAreTypes = false)
    val TOML = YAML.copy(id = "toml", label = "TOML")
    val INI = YAML.copy(id = "ini", label = "Config", lineComments = listOf("#", ";"))
    val MARKUP = Spec("xml", "XML", mode = Mode.Markup, quotes = "\"'")
    val HTML = MARKUP.copy(id = "html", label = "HTML")
    val MARKDOWN = Spec("markdown", "Markdown", mode = Mode.Markdown)
    val PLAIN = Spec("text", "Texto", mode = Mode.Plain)

    private val BY_EXT: Map<String, Spec> = buildMap {
        listOf("kt", "kts").forEach { put(it, KOTLIN) }
        listOf("java", "groovy", "gradle", "scala").forEach { put(it, JAVA) }
        listOf("js", "mjs", "cjs", "jsx").forEach { put(it, JS) }
        listOf("ts", "tsx", "vue", "svelte").forEach { put(it, TS) }
        put("py", PYTHON); put("go", GO); put("rs", RUST)
        listOf("c", "h", "cc", "cpp", "hpp", "cxx", "m", "mm").forEach { put(it, C) }
        put("cs", CSHARP); put("swift", SWIFT); put("dart", DART); put("php", PHP)
        listOf("sh", "bash", "zsh", "fish", "ps1").forEach { put(it, SHELL) }
        put("rb", RUBY); put("lua", LUA); put("sql", SQL)
        listOf("css", "scss", "sass", "less").forEach { put(it, CSS) }
        listOf("json", "jsonc", "json5").forEach { put(it, JSON) }
        listOf("yaml", "yml").forEach { put(it, YAML) }
        put("toml", TOML)
        listOf("ini", "cfg", "conf", "properties", "env").forEach { put(it, INI) }
        listOf("xml", "svg", "plist", "xaml").forEach { put(it, MARKUP) }
        listOf("html", "htm").forEach { put(it, HTML) }
        listOf("md", "mdx", "markdown").forEach { put(it, MARKDOWN) }
        listOf("txt", "log", "csv", "tsv").forEach { put(it, PLAIN) }
    }

    private val BY_ID: Map<String, Spec> = buildMap {
        BY_EXT.values.forEach { put(it.id, it) }
        put("js", JS); put("ts", TS); put("py", PYTHON); put("sh", SHELL); put("bash", SHELL)
        put("cpp", C); put("c++", C); put("yml", YAML); put("md", MARKDOWN); put("rs", RUST)
        put("plaintext", PLAIN); put("plain", PLAIN)
    }

    /** Escolhe a linguagem pela dica do host (`language`) ou pelo nome do arquivo. */
    fun detect(fileName: String, hint: String? = null): Spec {
        hint?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { h -> BY_ID[h]?.let { return it } }
        val name = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
        when (name) {
            "dockerfile", "makefile", "procfile", ".bashrc", ".zshrc", ".profile" -> return SHELL
            ".gitignore", ".dockerignore", ".npmrc", ".editorconfig" -> return INI
        }
        if (name.startsWith(".env")) return INI
        val dot = name.lastIndexOf('.')
        val ext = if (dot <= 0) "" else name.substring(dot + 1)
        return BY_EXT[ext] ?: PLAIN
    }

    /** Realça todas as linhas, propagando o estado de comentários/tags entre elas. */
    fun highlight(lines: List<String>, spec: Spec): List<List<Token>> {
        if (spec.mode == Mode.Plain) return List(lines.size) { emptyList() }
        var state = 0
        return lines.map { raw ->
            val line = if (raw.length > MAX_LINE_SCAN) raw.substring(0, MAX_LINE_SCAN) else raw
            val out = ArrayList<Token>()
            state = when (spec.mode) {
                Mode.Code, Mode.Json -> lexCode(line, 0, spec, state, out)
                Mode.Config -> lexConfig(line, spec, out)
                Mode.Markup -> lexMarkup(line, spec, state, out)
                Mode.Markdown -> lexMarkdown(line, state, out)
                Mode.Plain -> 0
            }
            out
        }
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    /** Estado: 0 normal, 1 dentro de comentário de bloco. */
    private fun lexCode(line: String, from: Int, spec: Spec, startState: Int, out: MutableList<Token>): Int {
        val n = line.length
        var i = from
        if (startState == 1 && spec.block != null) {
            val end = line.indexOf(spec.block.second, i)
            if (end < 0) {
                if (n > i) out += Token(i, n, TokenKind.Comment)
                return 1
            }
            out += Token(i, end + spec.block.second.length, TokenKind.Comment)
            i = end + spec.block.second.length
        }
        while (i < n) {
            val c = line[i]
            if (spec.lineComments.any { line.startsWith(it, i) }) {
                out += Token(i, n, TokenKind.Comment)
                return 0
            }
            val block = spec.block
            if (block != null && line.startsWith(block.first, i)) {
                val end = line.indexOf(block.second, i + block.first.length)
                if (end < 0) {
                    out += Token(i, n, TokenKind.Comment)
                    return 1
                }
                out += Token(i, end + block.second.length, TokenKind.Comment)
                i = end + block.second.length
                continue
            }
            if (c in spec.quotes) {
                val end = scanString(line, i, c)
                val kind = if (spec.mode == Mode.Json && nextNonSpace(line, end) == ':') TokenKind.Key else TokenKind.Str
                out += Token(i, end, kind)
                i = end
                continue
            }
            if (c.isDigit() && (i == 0 || !isIdentPart(line[i - 1]))) {
                var j = i + 1
                while (j < n && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) {
                    if (line[j] == '.' && (j + 1 >= n || !line[j + 1].isDigit())) break
                    j++
                }
                out += Token(i, j, TokenKind.Number)
                i = j
                continue
            }
            if (c == '-' && spec.mode == Mode.Json && i + 1 < n && line[i + 1].isDigit()) {
                var j = i + 1
                while (j < n && (line[j].isDigit() || line[j] in ".eE+-")) j++
                out += Token(i, j, TokenKind.Number)
                i = j
                continue
            }
            if (spec.annotations && c == '@' && i + 1 < n && isIdentStart(line[i + 1])) {
                var j = i + 1
                while (j < n && (isIdentPart(line[j]) || line[j] == '.')) j++
                out += Token(i, j, TokenKind.Annotation)
                i = j
                continue
            }
            if (isIdentStart(c) || (c == '#' && spec.keywords.any { it.startsWith("#") })) {
                var j = i + 1
                while (j < n && isIdentPart(line[j])) j++
                val word = line.substring(i, j)
                val isKw = if (spec.caseInsensitive) word.lowercase() in spec.keywords else word in spec.keywords
                when {
                    isKw -> out += Token(i, j, TokenKind.Keyword)
                    spec.capsAreTypes && word[0].isUpperCase() && word.length > 1 -> out += Token(i, j, TokenKind.Type)
                }
                i = j
                continue
            }
            i++
        }
        return 0
    }

    /** Fim (exclusivo) de uma string iniciada em [start] com [quote], respeitando escapes. */
    private fun scanString(line: String, start: Int, quote: Char): Int {
        var j = start + 1
        while (j < line.length) {
            val ch = line[j]
            if (ch == '\\') { j += 2; continue }
            if (ch == quote) return j + 1
            j++
        }
        return line.length
    }

    private fun nextNonSpace(line: String, from: Int): Char? {
        var j = from
        while (j < line.length && line[j].isWhitespace()) j++
        return line.getOrNull(j)
    }

    /** YAML/TOML/INI: seção, chave antes de `:`/`=`, comentário e valor. */
    private fun lexConfig(line: String, spec: Spec, out: MutableList<Token>): Int {
        val trimmedStart = line.indexOfFirst { !it.isWhitespace() }
        if (trimmedStart < 0) return 0
        if (spec.lineComments.any { line.startsWith(it, trimmedStart) }) {
            out += Token(trimmedStart, line.length, TokenKind.Comment)
            return 0
        }
        if (line[trimmedStart] == '[') {
            val end = line.indexOf(']', trimmedStart).let { if (it < 0) line.length else it + 1 }
            out += Token(trimmedStart, end, TokenKind.Type)
            return lexCode(line, end, spec, 0, out)
        }
        var keyStart = trimmedStart
        if (line[keyStart] == '-') {
            out += Token(keyStart, keyStart + 1, TokenKind.Punct)
            keyStart++
            while (keyStart < line.length && line[keyStart] == ' ') keyStart++
        }
        var sep = -1
        var j = keyStart
        while (j < line.length) {
            val ch = line[j]
            if (ch == '"' || ch == '\'' || ch == '#') break
            if (ch == '=' || (ch == ':' && (j + 1 == line.length || line[j + 1] == ' '))) { sep = j; break }
            j++
        }
        return if (sep > keyStart) {
            out += Token(keyStart, sep, TokenKind.Key)
            lexCode(line, sep + 1, spec, 0, out)
        } else {
            lexCode(line, keyStart, spec, 0, out)
        }
    }

    /** Estado: 0 texto, 1 comentário `<!-- -->`, 2 dentro de uma tag. */
    private fun lexMarkup(line: String, spec: Spec, startState: Int, out: MutableList<Token>): Int {
        val n = line.length
        var i = 0
        var state = startState
        while (i < n) {
            when (state) {
                1 -> {
                    val end = line.indexOf("-->", i)
                    if (end < 0) { out += Token(i, n, TokenKind.Comment); return 1 }
                    out += Token(i, end + 3, TokenKind.Comment)
                    i = end + 3
                    state = 0
                }
                2 -> {
                    val c = line[i]
                    when {
                        c == '>' || (c == '/' && i + 1 < n && line[i + 1] == '>') || (c == '?' && i + 1 < n && line[i + 1] == '>') -> {
                            val len = if (c == '>') 1 else 2
                            out += Token(i, i + len, TokenKind.Tag)
                            i += len
                            state = 0
                        }
                        c in spec.quotes -> {
                            val end = scanString(line, i, c)
                            out += Token(i, end, TokenKind.Str)
                            i = end
                        }
                        isIdentStart(c) -> {
                            var j = i + 1
                            while (j < n && (isIdentPart(line[j]) || line[j] == '-' || line[j] == ':' || line[j] == '.')) j++
                            out += Token(i, j, TokenKind.Attr)
                            i = j
                        }
                        else -> i++
                    }
                }
                else -> {
                    val lt = line.indexOf('<', i)
                    if (lt < 0) return 0
                    if (line.startsWith("<!--", lt)) {
                        state = 1
                        i = lt
                        val end = line.indexOf("-->", lt + 4)
                        if (end < 0) { out += Token(lt, n, TokenKind.Comment); return 1 }
                        out += Token(lt, end + 3, TokenKind.Comment)
                        i = end + 3
                        state = 0
                        continue
                    }
                    var j = lt + 1
                    if (j < n && (line[j] == '/' || line[j] == '?' || line[j] == '!')) j++
                    if (j < n && (isIdentStart(line[j]))) {
                        while (j < n && (isIdentPart(line[j]) || line[j] == '-' || line[j] == ':' || line[j] == '.')) j++
                        out += Token(lt, j, TokenKind.Tag)
                        state = 2
                    }
                    i = j
                }
            }
        }
        return state
    }

    private val BULLET = Regex("^([-*+]|\\d+[.)])\\s")

    /** Estado: 0 texto, 1 dentro de bloco de código cercado. */
    private fun lexMarkdown(line: String, startState: Int, out: MutableList<Token>): Int {
        val trimmed = line.trimStart()
        val lead = line.length - trimmed.length
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            out += Token(0, line.length, TokenKind.Comment)
            return if (startState == 1) 0 else 1
        }
        if (startState == 1) {
            if (line.isNotEmpty()) out += Token(0, line.length, TokenKind.Str)
            return 1
        }
        if (trimmed.startsWith("#")) {
            out += Token(0, line.length, TokenKind.Heading)
            return 0
        }
        if (trimmed.startsWith(">")) {
            out += Token(0, line.length, TokenKind.Comment)
            return 0
        }
        val bullet = BULLET.find(trimmed)
        if (bullet != null) out += Token(lead, lead + bullet.groupValues[1].length, TokenKind.Keyword)
        var i = 0
        while (i < line.length) {
            if (line[i] == '`') {
                val end = line.indexOf('`', i + 1)
                if (end < 0) break
                out += Token(i, end + 1, TokenKind.Str)
                i = end + 1
                continue
            }
            if (line[i] == '[') {
                val close = line.indexOf("](", i)
                val paren = if (close >= 0) line.indexOf(')', close) else -1
                if (close > i && paren > close) {
                    out += Token(i, close + 1, TokenKind.Attr)
                    out += Token(close + 1, paren + 1, TokenKind.Comment)
                    i = paren + 1
                    continue
                }
            }
            i++
        }
        return 0
    }
}
