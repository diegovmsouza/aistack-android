package br.com.amberwrite.aistack.feature.chat.markdown

/** Classe de um trecho de código destacado. */
enum class TokenType { Keyword, Type, String, Number, Comment, Annotation, Punctuation }

/** Trecho `[start, end)` do código com a sua classe. Trechos sem classe ficam de fora. */
data class CodeToken(val start: Int, val end: Int, val type: TokenType)

/**
 * Realce de sintaxe leve e puro (sem regex por linha, uma passada só). Não é um parser:
 * reconhece comentários, strings, números, palavras-chave e tipos das linguagens mais
 * comuns no chat. Linguagem desconhecida usa um conjunto genérico de palavras-chave.
 */
object SyntaxHighlighter {

    private data class Lang(
        val keywords: Set<String>,
        val line: List<String> = listOf("//"),
        val block: Pair<String, String>? = "/*" to "*/",
        val quotes: String = "\"'",
        val typesCapitalized: Boolean = true,
        val annotations: Boolean = false,
        val caseInsensitive: Boolean = false,
    )

    private val kotlin = Lang(
        words("package import class interface object fun val var if else when for while do return break continue " +
            "in is as try catch finally throw null true false this super override private public protected internal " +
            "data sealed enum companion suspend inline reified open abstract final lateinit by init const typealias " +
            "operator infix vararg out where get set"),
        quotes = "\"'", annotations = true,
    )
    private val java = Lang(
        words("package import class interface enum extends implements new return if else for while do switch case " +
            "default break continue try catch finally throw throws null true false this super static final public " +
            "private protected abstract void int long short byte char boolean float double synchronized var record"),
        annotations = true,
    )
    private val js = Lang(
        words("const let var function return if else for while do switch case default break continue new delete " +
            "typeof instanceof in of try catch finally throw class extends super this null undefined true false " +
            "import export from as async await yield interface type enum implements readonly public private " +
            "protected static get set void any number string boolean never unknown keyof"),
        quotes = "\"'`",
    )
    private val python = Lang(
        words("def class return if elif else for while in not and or is import from as try except finally raise " +
            "with lambda yield pass break continue global nonlocal None True False async await self match case"),
        line = listOf("#"), block = null, typesCapitalized = true, annotations = true,
    )
    private val shell = Lang(
        words("if then else elif fi for in do done while until case esac function return local export echo cd " +
            "exit set unset source sudo"),
        line = listOf("#"), block = null, typesCapitalized = false,
    )
    private val rust = Lang(
        words("fn let mut const static struct enum impl trait pub use mod crate self Self super match if else for " +
            "while loop return break continue as in ref move async await dyn where type unsafe true false Some None Ok Err"),
    )
    private val go = Lang(
        words("package import func var const type struct interface map chan go defer return if else for range " +
            "switch case default break continue select nil true false"),
        quotes = "\"'`",
    )
    private val swift = Lang(
        words("import func let var class struct enum protocol extension return if else guard for in while switch " +
            "case default break continue nil true false self init throws try catch async await public private"),
    )
    private val c = Lang(
        words("include define if else for while do switch case default break continue return struct union enum " +
            "typedef static const extern void int long short char float double unsigned signed sizeof class " +
            "public private protected namespace using template typename new delete nullptr true false auto"),
    )
    private val sql = Lang(
        words("select from where insert into values update set delete create table index view drop alter add " +
            "join left right inner outer on group by order having limit offset as and or not null is in like " +
            "primary key foreign references distinct union all case when then else end"),
        line = listOf("--"), quotes = "'\"", typesCapitalized = false, caseInsensitive = true,
    )
    private val json = Lang(words("true false null"), line = emptyList(), block = null, quotes = "\"", typesCapitalized = false)
    private val yaml = Lang(words("true false null yes no on off"), line = listOf("#"), block = null, typesCapitalized = false)
    private val markup = Lang(emptySet(), line = emptyList(), block = "<!--" to "-->", quotes = "\"'", typesCapitalized = false)
    private val generic = Lang(
        words("if else for while return function fun def class import export const let var true false null nil " +
            "None True False new this self public private static"),
        line = listOf("//", "#"),
    )

    private fun words(s: String) = s.split(' ').filter { it.isNotBlank() }.toSet()

    private fun langOf(id: String?): Lang = when (id?.lowercase()?.trim()) {
        "kotlin", "kt", "kts" -> kotlin
        "java" -> java
        "js", "javascript", "jsx", "ts", "typescript", "tsx", "mjs", "cjs" -> js
        "py", "python" -> python
        "sh", "bash", "zsh", "shell", "console", "fish" -> shell
        "rs", "rust" -> rust
        "go", "golang" -> go
        "swift" -> swift
        "c", "h", "cpp", "c++", "cc", "hpp", "cs", "csharp", "objc" -> c
        "sql", "sqlite", "postgres", "psql" -> sql
        "json", "jsonc", "json5" -> json
        "yaml", "yml", "toml", "ini" -> yaml
        "xml", "html", "svg", "htm", "vue" -> markup
        else -> generic
    }

    /** Linguagem reconhecida (para o rótulo do bloco); `null` quando cai no genérico. */
    fun isKnown(id: String?): Boolean = langOf(id) !== generic

    fun tokenize(code: String, language: String?): List<CodeToken> {
        val lang = langOf(language)
        val out = ArrayList<CodeToken>()
        val n = code.length
        var i = 0
        while (i < n) {
            val ch = code[i]
            // Comentário de bloco
            val block = lang.block
            if (block != null && code.startsWith(block.first, i)) {
                val end = code.indexOf(block.second, i + block.first.length).let { if (it < 0) n else it + block.second.length }
                out += CodeToken(i, end, TokenType.Comment); i = end; continue
            }
            // Comentário de linha
            val lineStart = lang.line.firstOrNull { code.startsWith(it, i) }
            if (lineStart != null && (lineStart != "#" || i == 0 || !code[i - 1].isLetterOrDigit())) {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                out += CodeToken(i, end, TokenType.Comment); i = end; continue
            }
            // String
            if (ch in lang.quotes) {
                val triple = (ch == '"' || ch == '\'') && code.startsWith("$ch$ch$ch", i)
                val end = if (triple) {
                    code.indexOf("$ch$ch$ch", i + 3).let { if (it < 0) n else it + 3 }
                } else {
                    var j = i + 1
                    while (j < n && code[j] != ch && !(code[j] == '\n' && ch != '`')) {
                        if (code[j] == '\\') j++
                        j++
                    }
                    minOf(n, j + 1)
                }
                out += CodeToken(i, end, TokenType.String); i = end; continue
            }
            // Anotação / decorador
            if (ch == '@' && lang.annotations && i + 1 < n && code[i + 1].isLetter()) {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
                out += CodeToken(i, j, TokenType.Annotation); i = j; continue
            }
            // Número
            if (ch.isDigit() && (i == 0 || !(code[i - 1].isLetterOrDigit() || code[i - 1] == '_'))) {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                out += CodeToken(i, j, TokenType.Number); i = j; continue
            }
            // Identificador
            if (ch.isLetter() || ch == '_' || ch == '$') {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || (lang === shell && code[j] == '-'))) j++
                val word = code.substring(i, j)
                val key = if (lang.caseInsensitive) word.lowercase() else word
                when {
                    key in lang.keywords -> out += CodeToken(i, j, TokenType.Keyword)
                    lang.typesCapitalized && word.length > 1 && word[0].isUpperCase() -> out += CodeToken(i, j, TokenType.Type)
                }
                i = j; continue
            }
            if (lang === markup && (ch == '<' || ch == '>')) {
                // Nome da tag logo após `<` ou `</`.
                var j = i + 1
                if (ch == '<' && j < n && (code[j] == '/' || code[j] == '?' || code[j] == '!')) j++
                val nameStart = j
                while (ch == '<' && j < n && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == ':')) j++
                out += CodeToken(i, nameStart, TokenType.Punctuation)
                if (j > nameStart) out += CodeToken(nameStart, j, TokenType.Keyword)
                i = maxOf(j, i + 1); continue
            }
            i++
        }
        return out
    }
}
