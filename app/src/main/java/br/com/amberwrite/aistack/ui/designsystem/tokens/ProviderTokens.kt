package br.com.amberwrite.aistack.ui.designsystem.tokens

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import br.com.amberwrite.aistack.R

/**
 * Metadados visuais de um provedor.
 *
 * @property id identificador canônico usado no protocolo (claude, codex, agy, kimi, deepseek, glm, qwen).
 * @property logo drawable vetorial do logotipo oficial.
 * @property tintable `true` quando o logo é monocromático e deve ser tingido com a cor do provedor;
 *   `false` para logos coloridos (Gemini, Kimi, DeepSeek), desenhados sem tint.
 */
data class ProviderSpec(
    val id: String,
    val displayName: String,
    @DrawableRes val logo: Int,
    val tintable: Boolean,
)

object Providers {
    val Claude = ProviderSpec("claude", "Claude", R.drawable.ic_provider_claude, tintable = true)
    val Codex = ProviderSpec("codex", "Codex", R.drawable.ic_provider_codex, tintable = true)
    val Agy = ProviderSpec("agy", "Gemini", R.drawable.ic_provider_gemini, tintable = false)
    val Kimi = ProviderSpec("kimi", "Kimi", R.drawable.ic_provider_kimi, tintable = false)
    val DeepSeek = ProviderSpec("deepseek", "DeepSeek", R.drawable.ic_provider_deepseek, tintable = false)
    val Glm = ProviderSpec("glm", "GLM", R.drawable.ic_provider_glm, tintable = true)
    val Qwen = ProviderSpec("qwen", "Qwen", R.drawable.ic_provider_qwen, tintable = true)

    val all: List<ProviderSpec> = listOf(Claude, Codex, Agy, Kimi, DeepSeek, Glm, Qwen)

    /** Resolve um id (aceita apelidos como "gemini", "openai", "zai"); cai no Claude se desconhecido. */
    fun byId(id: String?): ProviderSpec = when (id?.lowercase()) {
        "claude", "anthropic" -> Claude
        "codex", "openai", "gpt" -> Codex
        "agy", "gemini", "antigravity", "google" -> Agy
        "kimi", "moonshot" -> Kimi
        "deepseek" -> DeepSeek
        "glm", "zai", "z.ai", "zhipu" -> Glm
        "qwen", "alibaba" -> Qwen
        else -> Claude
    }
}

/**
 * Paletas do EffortSlider do desktop (do esforço mínimo ao máximo). Provedores sem paleta
 * própria no desktop recebem uma rampa gerada a partir da cor do provedor.
 */
object EffortPalettes {
    private fun hex(vararg v: Long) = v.map { Color(0xFF000000 or it) }

    val Claude = hex(0x7c2d12, 0x9a3412, 0xb45309, 0xc2410c, 0xda7756, 0xd97706, 0xea580c, 0xf97316, 0xfb923c, 0xffffff)
    val Codex = hex(0x171717, 0x262626, 0x383838, 0x525252, 0x737373, 0xa3a3a3, 0xd4d4d4, 0xf5f5f5, 0xffffff)
    val Gemini = hex(0x1e3a8a, 0x1d4ed8, 0x2563eb, 0x0284c7, 0x38bdf8, 0x60a5fa, 0x06b6d4, 0x22d3ee, 0xa5f3fc, 0xffffff)
    val Kimi = hex(0x090d16, 0x0f172a, 0x172554, 0x1e3a8a, 0x2563eb, 0x3b82f6, 0x60a5fa, 0x93c5fd, 0xffffff)

    /** Brilho do polegar do slider do Claude: rgba(234, 88, 12, 0.65). */
    val ClaudeGlow = Color(0xA6EA580C)

    /** Rampa de 9 tons: quase preto → cor do provedor → branco. */
    fun ramp(base: Color, steps: Int = 9): List<Color> {
        val dark = lerp(Color(0xFF05070C), base, 0.15f)
        return List(steps) { i ->
            val t = i / (steps - 1f)
            if (t <= 0.6f) lerp(dark, base, t / 0.6f) else lerp(base, Color.White, (t - 0.6f) / 0.4f)
        }
    }

    fun forProvider(id: String?, colors: AiStackColors): List<Color> = when (Providers.byId(id)) {
        Providers.Claude -> Claude
        Providers.Codex -> Codex
        Providers.Agy -> Gemini
        Providers.Kimi -> Kimi
        else -> ramp(colors.providers.byId(id))
    }

    /** Cor contínua da paleta para uma fração 0..1 do esforço. */
    fun colorAt(palette: List<Color>, fraction: Float): Color {
        if (palette.isEmpty()) return Color.Unspecified
        val f = fraction.coerceIn(0f, 1f) * (palette.size - 1)
        val i = f.toInt().coerceAtMost(palette.size - 2).coerceAtLeast(0)
        return if (palette.size == 1) palette[0] else lerp(palette[i], palette[i + 1], f - i)
    }
}
