package br.com.amberwrite.aistack.ui.theme

import androidx.compose.ui.graphics.Color
import br.com.amberwrite.aistack.ui.designsystem.tokens.DarkTokens

/*
 * Constantes legadas (tema escuro fixo) mantidas para as telas antigas. Os valores agora
 * vêm dos tokens do design system (oklch do desktop convertido). Código novo deve usar
 * `AiTheme.colors` de br.com.amberwrite.aistack.ui.designsystem, que segue claro/escuro.
 */

val AiStackBg = DarkTokens.Bg
val AiStackSurface = DarkTokens.Surface
val AiStackSurface2 = DarkTokens.Surface2
val AiStackSurface3 = DarkTokens.Surface3
val AiStackLine = DarkTokens.Line
val AiStackLineStrong = DarkTokens.LineStrong

val AiStackFg = DarkTokens.Fg
val AiStackFg2 = DarkTokens.Fg2
val AiStackFg3 = DarkTokens.Fg3

// Cores dos provedores (variante escura dos tokens do desktop).
val ProviderClaude = DarkTokens.Claude
val ProviderCodex = DarkTokens.Codex
val ProviderAgy = DarkTokens.Agy
val ProviderKimi = DarkTokens.Kimi
val ProviderDeepSeek = DarkTokens.DeepSeek
val ProviderGlm = DarkTokens.Glm
val ProviderQwen = DarkTokens.Qwen

// Status
val StatusOk = DarkTokens.Ok
val StatusWarn = DarkTokens.Warn
val StatusDanger = DarkTokens.Danger
val DiffAdd = Color(0xFF166534)
val DiffAddFg = Color(0xFF86EFAC)
val DiffDelete = Color(0xFF7F1D1D)
val DiffDeleteFg = Color(0xFFFCA5A5)
