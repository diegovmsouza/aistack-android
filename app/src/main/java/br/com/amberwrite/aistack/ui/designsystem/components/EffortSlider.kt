package br.com.amberwrite.aistack.ui.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.tokens.EffortPalettes
import kotlin.math.roundToInt

/**
 * Seletor discreto de esforço/raciocínio (ex.: low · medium · high · xhigh · max). A trilha é
 * um degradê da paleta de esforço do provedor ([EffortPalettes.forProvider]); o polegar desliza
 * com mola ([AiStackMotion.thumb]) e cada troca de nível dá um “tick” háptico.
 */
@Composable
fun EffortSlider(
    levels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    providerId: String? = "claude",
    enabled: Boolean = true,
    haptics: AiHaptics = AiHaptics.None,
) {
    if (levels.isEmpty()) return
    val c = AiTheme.colors
    val palette = EffortPalettes.forProvider(providerId, c)
    val last = (levels.size - 1).coerceAtLeast(1)
    val selected = selectedIndex.coerceIn(0, levels.lastIndex)
    val fraction by animateFloatAsState(selected / last.toFloat(), AiTheme.motion.thumb(), label = "effortThumb")
    val thumbColor = EffortPalettes.colorAt(palette, 0.35f + 0.6f * fraction)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var dragIndex by remember { mutableIntStateOf(selected) }
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .semantics {
                    contentDescription = "Esforço"
                    stateDescription = levels[selected]
                    setProgress { v ->
                        val i = (v * last).roundToInt().coerceIn(0, levels.lastIndex)
                        currentOnSelect(i)
                        true
                    }
                }
                .then(
                    if (enabled) {
                        Modifier
                            .pointerInput(levels.size) {
                                detectTapGestures { pos ->
                                    val i = (pos.x / size.width * last).roundToInt().coerceIn(0, levels.lastIndex)
                                    haptics.perform(HapticKind.Tick)
                                    currentOnSelect(i)
                                }
                            }
                            .pointerInput(levels.size) {
                                detectHorizontalDragGestures(onDragStart = { dragIndex = -1 }) { change, _ ->
                                    val i = (change.position.x / size.width * last).roundToInt().coerceIn(0, levels.lastIndex)
                                    if (i != dragIndex) {
                                        dragIndex = i
                                        haptics.perform(HapticKind.Tick)
                                        currentOnSelect(i)
                                    }
                                }
                            }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            val thumb = 20.dp
            val track = maxWidth - thumb
            Box(
                Modifier
                    .padding(horizontal = thumb / 2)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(AiTheme.shapes.pill)
                    .background(Brush.horizontalGradient(palette.drop(2).ifEmpty { palette })),
            )
            // Marcas de cada nível.
            levels.indices.forEach { i ->
                Box(
                    Modifier
                        .offset(x = track * (i / last.toFloat()) + thumb / 2 - 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(c.bg.copy(alpha = 0.7f)),
                )
            }
            Box(
                Modifier
                    .offset(x = track * fraction)
                    .size(thumb)
                    .shadow(4.dp, CircleShape, ambientColor = c.shadow, spotColor = c.shadow)
                    .clip(CircleShape)
                    .background(c.surface)
                    .border(5.dp, thumbColor, CircleShape),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            levels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = AiTheme.typography.caption,
                    color = if (i == selected) c.fg else c.fg3,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        levels.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}

@AiPreviews
@Composable
private fun EffortSliderPreview() {
    PreviewSurface {
        EffortSlider(listOf("low", "medium", "high", "xhigh", "max"), 2, onSelect = {})
        EffortSlider(listOf("minimal", "low", "medium", "high"), 3, onSelect = {}, providerId = "codex")
    }
}
