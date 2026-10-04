package br.com.amberwrite.aistack.ui.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Tokens de cor do AiStack, convertidos de src/styles/tokens.css (OKLCH) para sRGB com
 * script (matriz OKLab → LMS → sRGB linear + curva de transferência sRGB). Cores fora do
 * gamut sRGB tiveram o croma reduzido até caber, preservando luminosidade e matiz, como o
 * navegador faz ao exibir em telas sRGB.
 */

/** Cores dos provedores (logo, acento, barra de cota). */
@Immutable
data class ProviderColors(
    val claude: Color,
    val codex: Color,
    val agy: Color,
    val kimi: Color,
    val deepseek: Color,
    val glm: Color,
    val qwen: Color,
) {
    /** Cor pelo id do provedor (claude, codex, agy/gemini, kimi, deepseek, glm, qwen). */
    fun byId(id: String?): Color = when (id?.lowercase()) {
        "claude", "anthropic" -> claude
        "codex", "openai", "gpt" -> codex
        "agy", "gemini", "antigravity", "google" -> agy
        "kimi", "moonshot" -> kimi
        "deepseek" -> deepseek
        "glm", "zai", "z.ai", "zhipu" -> glm
        "qwen", "alibaba" -> qwen
        else -> claude
    }
}

/** Paleta semântica completa do app; acesse via `AiTheme.colors`. */
@Immutable
data class AiStackColors(
    val isDark: Boolean,
    val bg: Color,
    val sidebar: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val userBubble: Color,
    val userBubbleFg: Color,
    val line: Color,
    val lineStrong: Color,
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val glass: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    /** Cor de destaque (por padrão, a do Claude). Troque com `AiStackTheme(accent = …)`. */
    val accent: Color,
    /** Texto/ícone sobre [accent]. */
    val accentFg: Color,
    val providers: ProviderColors,
    /** Sombra de elementos flutuantes (menus, folhas, cartões de vidro). */
    val shadow: Color,
    /** Véu atrás de modais e folhas. */
    val scrim: Color,
    /** Realce de busca (warn a 45%, como no desktop). */
    val highlight: Color,
    val diffAddBg: Color,
    val diffAddFg: Color,
    val diffDelBg: Color,
    val diffDelFg: Color,
) {
    /** Acento a 12% — fundos suaves de seleção e badges. */
    val accentSoft: Color get() = accent.copy(alpha = 0.12f)
    val okSoft: Color get() = ok.copy(alpha = 0.12f)
    val warnSoft: Color get() = warn.copy(alpha = 0.07f)
    val dangerSoft: Color get() = danger.copy(alpha = 0.10f)
}

/** Tokens crus do tema claro (oklch convertido). */
object LightTokens {
    val Bg = Color(0xFFFBFAF7)
    val Sidebar = Color(0xFFF5F2EE)
    val Surface = Color(0xFFFFFFFF)
    val Surface2 = Color(0xFFF6F4F0)
    val Surface3 = Color(0xFFECE9E4)
    val UserBubble = Color(0xFF024E9A)
    val UserBubbleFg = Color(0xFFFAFCFE)
    val Line = Color(0xFFE2DFDA)
    val LineStrong = Color(0xFFCDCAC4)
    val Fg = Color(0xFF1A1510)
    val Fg2 = Color(0xFF544F49)
    val Fg3 = Color(0xFF7E7974)
    val Glass = Color(0xC7FFFFFF)
    val Claude = Color(0xFFD6673F)
    val Codex = Color(0xFF1E1F22)
    val Agy = Color(0xFF017DD6)
    val Kimi = Color(0xFF0278E7)
    val DeepSeek = Color(0xFF0382C4)
    val Glm = Color(0xFF068FA7)
    val Qwen = Color(0xFF685EF7)
    val Ok = Color(0xFF329D5A)
    val Warn = Color(0xFFD68C05)
    val Danger = Color(0xFFD73337)
    val AccentFg = Color(0xFFFCFCFC)
    /** oklch(0.3 0.02 70 / 0.18) */
    val Shadow = Color(0x2E342C23)
}

/** Tokens crus do tema escuro (oklch convertido). */
object DarkTokens {
    val Bg = Color(0xFF0D0E11)
    val Sidebar = Color(0xFF08090C)
    val Surface = Color(0xFF16171A)
    val Surface2 = Color(0xFF1D1E22)
    val Surface3 = Color(0xFF25262B)
    val UserBubble = Color(0xFF10427B)
    val UserBubbleFg = Color(0xFFF6F9FC)
    val Line = Color(0x13FFFFFF)
    val LineStrong = Color(0x24FFFFFF)
    val Fg = Color(0xFFEFF0F3)
    val Fg2 = Color(0xFFA9ABB0)
    val Fg3 = Color(0xFF72747B)
    val Glass = Color(0xB817181C)
    val Claude = Color(0xFFED845B)
    val Codex = Color(0xFFD6D7DA)
    val Agy = Color(0xFF35A4FF)
    val Kimi = Color(0xFF4599FF)
    val DeepSeek = Color(0xFF01A8FB)
    val Glm = Color(0xFF02B8D6)
    val Qwen = Color(0xFF8688FF)
    val Ok = Color(0xFF54C57A)
    val Warn = Color(0xFFF2B036)
    val Danger = Color(0xFFF75D59)
    val AccentFg = Color(0xFF0C0D12)
    val Shadow = Color(0xB3000000)
}

/** Cores fixas da marca (iguais nos dois temas, como os SVGs do desktop). */
object BrandColors {
    val Claude = Color(0xFFE0845A)
    val Codex = Color(0xFFE8E9EC)
    val Gemini = Color(0xFF6E9BF5)
    /** Interseções com multiply pré-calculado. */
    val ClaudeCodex = Color(0xFFCC7953)
    val CodexGemini = Color(0xFF648EE3)
    val AllThree = Color(0xFF584950)
    /** Ladrilho do ícone. */
    val TileTop = Color(0xFF26262C)
    val TileBottom = Color(0xFF141417)
}

val LightAiStackColors = AiStackColors(
    isDark = false,
    bg = LightTokens.Bg,
    sidebar = LightTokens.Sidebar,
    surface = LightTokens.Surface,
    surface2 = LightTokens.Surface2,
    surface3 = LightTokens.Surface3,
    userBubble = LightTokens.UserBubble,
    userBubbleFg = LightTokens.UserBubbleFg,
    line = LightTokens.Line,
    lineStrong = LightTokens.LineStrong,
    fg = LightTokens.Fg,
    fg2 = LightTokens.Fg2,
    fg3 = LightTokens.Fg3,
    glass = LightTokens.Glass,
    ok = LightTokens.Ok,
    warn = LightTokens.Warn,
    danger = LightTokens.Danger,
    accent = LightTokens.Claude,
    accentFg = LightTokens.AccentFg,
    providers = ProviderColors(
        claude = LightTokens.Claude,
        codex = LightTokens.Codex,
        agy = LightTokens.Agy,
        kimi = LightTokens.Kimi,
        deepseek = LightTokens.DeepSeek,
        glm = LightTokens.Glm,
        qwen = LightTokens.Qwen,
    ),
    shadow = LightTokens.Shadow,
    scrim = Color(0x52251F18),
    highlight = LightTokens.Warn.copy(alpha = 0.45f),
    diffAddBg = LightTokens.Ok.copy(alpha = 0.12f),
    diffAddFg = Color(0xFF1E7A43),
    diffDelBg = LightTokens.Danger.copy(alpha = 0.10f),
    diffDelFg = Color(0xFFB4262B),
)

val DarkAiStackColors = AiStackColors(
    isDark = true,
    bg = DarkTokens.Bg,
    sidebar = DarkTokens.Sidebar,
    surface = DarkTokens.Surface,
    surface2 = DarkTokens.Surface2,
    surface3 = DarkTokens.Surface3,
    userBubble = DarkTokens.UserBubble,
    userBubbleFg = DarkTokens.UserBubbleFg,
    line = DarkTokens.Line,
    lineStrong = DarkTokens.LineStrong,
    fg = DarkTokens.Fg,
    fg2 = DarkTokens.Fg2,
    fg3 = DarkTokens.Fg3,
    glass = DarkTokens.Glass,
    ok = DarkTokens.Ok,
    warn = DarkTokens.Warn,
    danger = DarkTokens.Danger,
    accent = DarkTokens.Claude,
    accentFg = DarkTokens.AccentFg,
    providers = ProviderColors(
        claude = DarkTokens.Claude,
        codex = DarkTokens.Codex,
        agy = DarkTokens.Agy,
        kimi = DarkTokens.Kimi,
        deepseek = DarkTokens.DeepSeek,
        glm = DarkTokens.Glm,
        qwen = DarkTokens.Qwen,
    ),
    shadow = DarkTokens.Shadow,
    scrim = Color(0x99000000),
    highlight = DarkTokens.Warn.copy(alpha = 0.45f),
    diffAddBg = DarkTokens.Ok.copy(alpha = 0.14f),
    diffAddFg = Color(0xFF86E3A5),
    diffDelBg = DarkTokens.Danger.copy(alpha = 0.14f),
    diffDelFg = Color(0xFFFFA8A3),
)
