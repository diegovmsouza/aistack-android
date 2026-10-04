package br.com.amberwrite.aistack.feature.newsession

import br.com.amberwrite.aistack.data.model.ModelInfo

/*
 * Lógica pura da tela de nova sessão (sem Android): projetos sugeridos, níveis de esforço,
 * validação de caminho e navegação de pastas. Testada em NewSessionLogicTest.
 */

/** Máximo de projetos sugeridos como atalho. */
const val MAX_SUGGESTED_PROJECTS = 12

/**
 * Junta os projetos recentes do desktop com os caminhos das conversas já listadas, sem
 * repetir (barra final não conta) e preservando a ordem: recentes primeiro.
 */
fun mergeProjects(recent: List<String>, conversationPaths: List<String>, max: Int = MAX_SUGGESTED_PROJECTS): List<String> {
    val seen = HashSet<String>()
    val out = ArrayList<String>()
    for (raw in recent + conversationPaths) {
        val path = raw.trim()
        if (path.isEmpty()) continue
        val key = normalizePathKey(path)
        if (seen.add(key)) out += path
        if (out.size >= max) break
    }
    return out
}

private fun normalizePathKey(path: String): String =
    if (path.length > 1) path.trimEnd('/', '\\').ifEmpty { path } else path

/** Rótulo em pt-BR de um nível de esforço do desktop (o id original segue para o host). */
fun effortLabel(id: String): String = when (id.lowercase()) {
    "none" -> "Nenhum"
    "minimal" -> "Mínimo"
    "low" -> "Baixo"
    "medium" -> "Médio"
    "high" -> "Alto"
    "xhigh" -> "Muito alto"
    "max" -> "Máximo"
    else -> id.replaceFirstChar { it.uppercase() }
}

/** Modelo pré-selecionado: o marcado como padrão no catálogo, senão o primeiro. */
fun defaultModelOf(models: List<ModelInfo>): ModelInfo? = models.firstOrNull { it.isDefault } ?: models.firstOrNull()

/**
 * Índice inicial do esforço para [model]: o `defaultEffort` do catálogo, senão "medium",
 * senão o meio da lista. -1 quando o modelo não tem níveis de esforço.
 */
fun defaultEffortIndex(model: ModelInfo?): Int {
    val levels = model?.efforts.orEmpty()
    if (levels.isEmpty()) return -1
    model?.defaultEffort?.let { d -> levels.indexOf(d).takeIf { it >= 0 }?.let { return it } }
    levels.indexOf("medium").takeIf { it >= 0 }?.let { return it }
    return levels.size / 2
}

/** Problema no caminho de projeto digitado. */
enum class ProjectPathError { Missing, NotAbsolute }

private val WINDOWS_ABSOLUTE = Regex("^[A-Za-z]:[\\\\/].*")

/** Valida o caminho do projeto: precisa ser absoluto (Unix, `~` ou Windows). */
fun validateProjectPath(path: String): ProjectPathError? {
    val p = path.trim()
    return when {
        p.isEmpty() -> ProjectPathError.Missing
        p.startsWith("/") || p == "~" || p.startsWith("~/") || p.startsWith("\\\\") || WINDOWS_ABSOLUTE.matches(p) -> null
        else -> ProjectPathError.NotAbsolute
    }
}

/** Pasta-mãe de [path], ou `null` na raiz. Entende caminhos Unix e Windows. */
fun parentPath(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val p = path.trim()
    if (p == "/" || Regex("^[A-Za-z]:[\\\\/]?$").matches(p)) return null
    val trimmed = p.trimEnd('/', '\\')
    val idx = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
    return when {
        idx < 0 -> null
        idx == 0 -> trimmed.substring(0, 1)
        trimmed[idx - 1] == ':' -> trimmed.substring(0, idx + 1)
        else -> trimmed.substring(0, idx)
    }
}

/** Junta pasta e nome usando o separador que a pasta já usa. */
fun joinPath(dir: String, name: String): String {
    if (dir.endsWith("/") || dir.endsWith("\\")) return dir + name
    val sep = if (dir.contains('\\') && !dir.contains('/')) "\\" else "/"
    return dir + sep + name
}

/** Nome curto (última pasta) para chips e títulos. */
fun shortProjectName(path: String): String {
    val t = path.trimEnd('/', '\\')
    return t.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }
}
