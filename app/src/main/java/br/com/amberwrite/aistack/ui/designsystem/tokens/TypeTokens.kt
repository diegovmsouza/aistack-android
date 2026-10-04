package br.com.amberwrite.aistack.ui.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import br.com.amberwrite.aistack.R

/*
 * Tipografia do AiStack: Inter (interface), Source Serif 4 (títulos editoriais e respostas
 * longas) e JetBrains Mono (código, caminhos, ferramentas). As três são fontes variáveis
 * (OFL, ver assets/licenses); cada peso é uma instância do eixo "wght".
 */

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val weights = listOf(300, 400, 500, 600, 700)

val InterFamily: FontFamily = FontFamily(weights.map { variable(R.font.inter, it) })
val SourceSerifFamily: FontFamily = FontFamily(weights.map { variable(R.font.source_serif_4, it) })
val JetBrainsMonoFamily: FontFamily = FontFamily(listOf(400, 500, 600, 700).map { variable(R.font.jetbrains_mono, it) })

/**
 * Escala tipográfica (tamanhos do desktop: base 15, composer 15, barra lateral 13 com
 * subtítulo 11,5, títulos de modal 14 seminegrito).
 */
@Immutable
data class AiStackTypography(
    /** Título editorial grande (serifada) — telas vazias, herói. */
    val display: TextStyle,
    /** Título de tela (serifada). */
    val title: TextStyle,
    /** Título de seção/modal: 14 seminegrito. */
    val heading: TextStyle,
    /** Corpo padrão do chat: 15. */
    val body: TextStyle,
    /** Corpo das respostas em serifada (leitura longa). */
    val bodySerif: TextStyle,
    /** Corpo compacto: 13,5. */
    val bodySmall: TextStyle,
    /** Rótulos de botões e chips: 13 médio. */
    val label: TextStyle,
    /** Legendas, metadados: 11,5. */
    val caption: TextStyle,
    /** Microtexto em caixa alta (seções): 10,5 com espaçamento. */
    val overline: TextStyle,
    /** Código e caminhos: 13. */
    val mono: TextStyle,
    /** Código compacto: 12. */
    val monoSmall: TextStyle,
)

val DefaultAiStackTypography = AiStackTypography(
    display = TextStyle(fontFamily = SourceSerifFamily, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.01).em),
    title = TextStyle(fontFamily = SourceSerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.005).em),
    heading = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    body = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodySerif = TextStyle(fontFamily = SourceSerifFamily, fontWeight = FontWeight.Normal, fontSize = 16.5.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
    label = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    caption = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 15.sp),
    overline = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.06.em),
    mono = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    monoSmall = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
)

/** Mapeia a escala do AiStack para a Typography do Material 3 (para componentes M3 de terceiros). */
fun AiStackTypography.toMaterial(): Typography = Typography(
    displayLarge = display.copy(fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = display.copy(fontSize = 34.sp, lineHeight = 40.sp),
    displaySmall = display,
    headlineLarge = title.copy(fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = title.copy(fontSize = 23.sp, lineHeight = 29.sp),
    headlineSmall = title,
    titleLarge = heading.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = heading.copy(fontSize = 15.sp, lineHeight = 21.sp),
    titleSmall = heading,
    bodyLarge = body,
    bodyMedium = bodySmall,
    bodySmall = caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = label.copy(fontSize = 14.sp),
    labelMedium = label,
    labelSmall = caption.copy(fontWeight = FontWeight.Medium),
)
