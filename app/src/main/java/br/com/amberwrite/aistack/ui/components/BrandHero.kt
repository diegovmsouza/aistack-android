package br.com.amberwrite.aistack.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.model.Provider
import br.com.amberwrite.aistack.ui.theme.ProviderAgy
import br.com.amberwrite.aistack.ui.theme.ProviderClaude
import br.com.amberwrite.aistack.ui.theme.ProviderCodex
import br.com.amberwrite.aistack.ui.theme.ProviderDeepSeek
import br.com.amberwrite.aistack.ui.theme.ProviderGlm
import br.com.amberwrite.aistack.ui.theme.ProviderKimi
import br.com.amberwrite.aistack.ui.theme.ProviderQwen
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * BrandHero nativo para Android:
 * Reproduz as 4 coreografias de marca (Salto Parabólico, Vórtice Orbital, Ejeção Gravidade Zero,
 * e Salto Gelatina) com física de mola elástica ao toque.
 */
@Composable
fun BrandHero(
    provider: Provider,
    modifier: Modifier = Modifier,
    sizeDp: Int = 96
) {
    val scope = rememberCoroutineScope()

    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val transY = remember { Animatable(0f) }
    val transX = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }

    // Dispara animação coreografada ao trocar de provedor
    LaunchedEffect(provider) {
        val variant = Random.nextInt(4)
        when (variant) {
            0 -> {
                // Salto Parabólico & Ricochete
                scaleX.snapTo(0.7f)
                scaleY.snapTo(1.3f)
                transY.snapTo(-140f)
                rotation.snapTo(-25f)

                launch {
                    transY.animateTo(
                        0f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
                launch {
                    scaleX.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                }
                launch {
                    scaleY.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                }
                launch {
                    rotation.animateTo(
                        0f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
            }
            1 -> {
                // Vórtice Orbital Ascendente
                scaleX.snapTo(0.4f)
                scaleY.snapTo(0.4f)
                rotation.snapTo(360f)
                transY.snapTo(50f)

                launch {
                    rotation.animateTo(
                        0f,
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
                launch {
                    scaleX.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
                launch {
                    scaleY.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
                launch {
                    transY.animateTo(
                        0f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                }
            }
            2 -> {
                // Ejeção Gravidade Zero com Parada Suave
                scaleX.snapTo(1.2f)
                scaleY.snapTo(0.8f)
                transY.snapTo(100f)

                launch {
                    transY.animateTo(
                        0f,
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                }
                launch {
                    scaleX.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
                launch {
                    scaleY.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
            }
            else -> {
                // Salto Gelatina do Caderno
                scaleX.snapTo(1.4f)
                scaleY.snapTo(0.6f)
                rotation.snapTo(15f)

                launch {
                    scaleX.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
                    )
                }
                launch {
                    scaleY.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
                    )
                }
                launch {
                    rotation.animateTo(
                        0f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scope.launch {
                            scaleX.animateTo(0.85f, spring(stiffness = Spring.StiffnessHigh))
                            scaleY.animateTo(0.85f, spring(stiffness = Spring.StiffnessHigh))
                        }
                        tryAwaitRelease()
                        scope.launch {
                            scaleX.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy))
                            scaleY.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy))
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            translate(left = transX.value, top = transY.value) {
                scale(scaleX = scaleX.value, scaleY = scaleY.value, pivot = center) {
                    rotate(degrees = rotation.value, pivot = center) {
                        drawProviderLogo(provider)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawProviderLogo(provider: Provider) {
    val radius = size.minDimension / 2
    when (provider) {
        Provider.CLAUDE -> {
            // Emblema geométrico Claude (terracota)
            drawCircle(color = ProviderClaude, radius = radius * 0.9f)
            drawRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                size = Size(radius * 0.7f, radius * 0.7f)
            )
        }
        Provider.AGY -> {
            // Emblema estrela Gemini / Agy (azul)
            drawCircle(color = ProviderAgy, radius = radius * 0.9f)
            drawCircle(color = Color.White, radius = radius * 0.45f)
        }
        Provider.CODEX -> {
            // Emblema espiral Codex / GPT (grafite / branco)
            drawCircle(color = ProviderCodex, radius = radius * 0.9f)
            drawRect(
                color = Color(0xFF1E2024),
                topLeft = Offset(center.x - radius * 0.4f, center.y - radius * 0.4f),
                size = Size(radius * 0.8f, radius * 0.8f)
            )
        }
        Provider.KIMI -> {
            drawCircle(color = ProviderKimi, radius = radius * 0.9f)
            drawCircle(color = Color.White, radius = radius * 0.4f)
        }
        Provider.DEEPSEEK -> {
            drawCircle(color = ProviderDeepSeek, radius = radius * 0.9f)
            drawRect(
                color = Color.White,
                topLeft = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                size = Size(radius * 0.6f, radius * 0.6f)
            )
        }
        Provider.GLM -> {
            drawCircle(color = ProviderGlm, radius = radius * 0.9f)
            drawCircle(color = Color.White, radius = radius * 0.45f)
        }
        Provider.QWEN -> {
            drawCircle(color = ProviderQwen, radius = radius * 0.9f)
            drawCircle(color = Color.White, radius = radius * 0.4f)
        }
    }
}
