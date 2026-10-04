package br.com.amberwrite.aistack.data.model

/** Provedores conhecidos do desktop. Valores desconhecidos caem em [UNKNOWN] sem quebrar. */
enum class Provider(val id: String, val displayName: String) {
    CLAUDE("claude", "Claude"),
    CODEX("codex", "Codex"),
    AGY("agy", "Gemini"),
    KIMI("kimi", "Kimi"),
    DEEPSEEK("deepseek", "DeepSeek"),
    GLM("glm", "GLM"),
    QWEN("qwen", "Qwen"),
    UNKNOWN("", "Desconhecido");

    companion object {
        /** Provedores que podem ser escolhidos numa nova sessão. */
        val selectable: List<Provider> = entries.filter { it != UNKNOWN }

        fun fromId(id: String?): Provider =
            entries.firstOrNull { it != UNKNOWN && it.id.equals(id?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** Modos de permissão aceitos pelo app (o desktop recusa `bypass`/`default` vindos do remoto). */
enum class PermissionMode(val id: String, val label: String) {
    ASK("ask", "Perguntar"),
    ACCEPT_EDITS("acceptEdits", "Aceitar edições"),
    PLAN("plan", "Planejar");

    companion object {
        fun fromId(id: String?): PermissionMode? = entries.firstOrNull { it.id == id }
    }
}
