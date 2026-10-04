package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Item genérico de paleta/menção. */
data class PaletteItem(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val badge: String? = null,
)

/**
 * Popover genérico de sugestões (base de [SlashPalette] e [MentionPopup]): cartão de vidro,
 * lista rolável com item destacado e entrada `menu-in` (escala .96 + deslocamento de 2 dp,
 * ancorado embaixo, pois abre acima do composer).
 */
@Composable
fun <T> SuggestionPopup(
    visible: Boolean,
    items: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    highlightedIndex: Int = 0,
    title: String? = null,
    emptyText: String = "Nada encontrado",
    maxHeight: Dp = 280.dp,
    key: ((T) -> Any)? = null,
    haptics: AiHaptics = AiHaptics.None,
    itemContent: @Composable (item: T, highlighted: Boolean) -> Unit,
) {
    val motion = AiTheme.motion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(motion.fade(Durations_Fast)) +
            scaleIn(motion.enter(), initialScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 1f)) +
            slideInVertically(motion.enter()) { it / 60 },
        exit = fadeOut(motion.fade(Durations_Instant)) + scaleOut(motion.exit(), targetScale = 0.98f, transformOrigin = TransformOrigin(0.5f, 1f)),
    ) {
        GlassCard(contentPadding = PaddingValues(4.dp), shape = AiTheme.shapes.lg) {
            if (title != null) {
                Text(
                    title.uppercase(),
                    style = AiTheme.typography.overline,
                    color = AiTheme.colors.fg3,
                    modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 4.dp),
                )
            }
            if (items.isEmpty()) {
                Text(emptyText, style = AiTheme.typography.bodySmall, color = AiTheme.colors.fg3, modifier = Modifier.padding(12.dp))
            } else {
                val state = rememberLazyListState()
                LaunchedEffect(highlightedIndex) {
                    if (highlightedIndex in items.indices) state.animateScrollToItem(highlightedIndex)
                }
                LazyColumn(Modifier.heightIn(max = maxHeight), state = state) {
                    itemsIndexed(items, key = key?.let { k -> { _, item -> k(item) } }) { index, item ->
                        val highlighted = index == highlightedIndex
                        val bg by animateColorAsState(
                            if (highlighted) AiTheme.colors.surface3 else Color.Transparent,
                            motion.fade(Durations_Instant),
                            label = "suggestionBg",
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(AiTheme.shapes.md)
                                .background(bg)
                                .clickable {
                                    haptics.perform(HapticKind.Tick)
                                    onSelect(item)
                                },
                        ) { itemContent(item, highlighted) }
                    }
                }
            }
        }
    }
}

private const val Durations_Fast = 140
private const val Durations_Instant = 90

/** Realça (negrito + `fg`) a primeira ocorrência de [query] em [text], sem diferenciar maiúsculas. */
@Composable
fun highlightMatch(text: String, query: String): AnnotatedString {
    val color = AiTheme.colors.fg
    return remember(text, query, color) {
        val i = if (query.isBlank()) -1 else text.indexOf(query, ignoreCase = true)
        if (i < 0) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text.substring(0, i))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = color)) { append(text.substring(i, i + query.length)) }
                append(text.substring(i + query.length))
            }
        }
    }
}

/** Linha padrão de [PaletteItem] (ícone, título com realce, subtítulo e selo). */
@Composable
fun PaletteItemRow(
    item: PaletteItem,
    highlighted: Boolean,
    query: String = "",
    prefix: String = "",
    monoTitle: Boolean = false,
) {
    val c = AiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (item.icon != null) {
            Icon(item.icon, null, Modifier.size(16.dp), tint = if (highlighted) c.accent else c.fg3)
        }
        Column(Modifier.weight(1f)) {
            Text(
                highlightMatch(prefix + item.title, query),
                style = if (monoTitle) AiTheme.typography.mono else AiTheme.typography.label,
                color = c.fg2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle != null) {
                Text(item.subtitle, style = AiTheme.typography.caption, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.badge != null) TagBadge(item.badge)
    }
}

/**
 * Paleta de comandos “/” acima do composer.
 * @param query texto após a barra (usado para realçar o casamento).
 */
@Composable
fun SlashPalette(
    visible: Boolean,
    items: List<PaletteItem>,
    onSelect: (PaletteItem) -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    highlightedIndex: Int = 0,
    haptics: AiHaptics = AiHaptics.None,
) {
    SuggestionPopup(
        visible = visible,
        items = items,
        onSelect = onSelect,
        modifier = modifier,
        highlightedIndex = highlightedIndex,
        title = "Comandos",
        emptyText = "Nenhum comando",
        key = { it.key },
        haptics = haptics,
    ) { item, highlighted ->
        PaletteItemRow(item.copy(icon = item.icon ?: Lucide.Slash), highlighted, query = query, prefix = "/", monoTitle = true)
    }
}

/**
 * Popup de menções “@” (arquivos, agentes, sessões). Genérico em [T]: forneça [itemContent]
 * ou use a sobrecarga com [PaletteItem].
 */
@Composable
fun <T> MentionPopup(
    visible: Boolean,
    items: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    highlightedIndex: Int = 0,
    title: String? = "Mencionar",
    key: ((T) -> Any)? = null,
    haptics: AiHaptics = AiHaptics.None,
    itemContent: @Composable (item: T, highlighted: Boolean) -> Unit,
) {
    SuggestionPopup(
        visible = visible,
        items = items,
        onSelect = onSelect,
        modifier = modifier,
        highlightedIndex = highlightedIndex,
        title = title,
        emptyText = "Nenhum resultado",
        key = key,
        haptics = haptics,
        itemContent = itemContent,
    )
}

/** Sobrecarga de [MentionPopup] para [PaletteItem] (ícone padrão: arquivo). */
@Composable
fun MentionPopup(
    visible: Boolean,
    items: List<PaletteItem>,
    onSelect: (PaletteItem) -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    highlightedIndex: Int = 0,
    haptics: AiHaptics = AiHaptics.None,
) {
    MentionPopup(
        visible = visible,
        items = items,
        onSelect = onSelect,
        modifier = modifier,
        highlightedIndex = highlightedIndex,
        key = { it.key },
        haptics = haptics,
    ) { item, highlighted ->
        PaletteItemRow(item.copy(icon = item.icon ?: Lucide.File), highlighted, query = query, prefix = "@")
    }
}

@AiPreviews
@Composable
private fun PopupsPreview() {
    PreviewSurface {
        SlashPalette(
            visible = true,
            query = "co",
            items = listOf(
                PaletteItem("compact", "compact", "Resume o contexto da conversa"),
                PaletteItem("commit", "commit", "Cria um commit com as mudanças", badge = "git"),
                PaletteItem("cost", "cost", "Mostra o custo da sessão"),
            ),
            onSelect = {},
            highlightedIndex = 1,
        )
        MentionPopup(
            visible = true,
            query = "main",
            items = listOf(
                PaletteItem("a", "MainActivity.kt", "app/src/main/java/…"),
                PaletteItem("b", "main.rs", "src-tauri/src", icon = Lucide.FileCode),
            ),
            onSelect = {},
        )
    }
}
