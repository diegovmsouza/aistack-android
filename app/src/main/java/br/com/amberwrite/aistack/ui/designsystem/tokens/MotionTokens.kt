package br.com.amberwrite.aistack.ui.designsystem.tokens

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/** Curvas do desktop (tokens.css). */
object Easings {
    /** --ease-spring: cubic-bezier(0.23, 1, 0.32, 1) — entradas com “mola” suave. */
    val Spring: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** --ease-out: cubic-bezier(0.16, 1, 0.3, 1) — saídas rápidas e assentamento longo. */
    val Out: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    /** Simétrica para loops (respiração, shimmer). */
    val InOut: Easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
}

/** Durações em ms (menu-in 140, sheet-up 260, pulse-dot 1200, shimmer 1600). */
object Durations {
    const val Instant = 90
    const val Fast = 140
    const val Base = 200
    const val Slow = 260
    const val Slower = 360
    const val Pulse = 1200
    const val Shimmer = 1600
    const val Breath = 1800
    const val Caret = 530
}

/**
 * Specs de animação prontos. Use sempre via `AiTheme.motion` para que o modo de
 * movimento reduzido (escala de animação = 0) seja respeitado automaticamente.
 */
@Immutable
data class AiStackMotion(
    /** `true` quando o usuário desativou animações (Settings.Global.ANIMATOR_DURATION_SCALE == 0). */
    val reduced: Boolean = false,
) {
    /** Mola padrão (dampingRatio 0.8, stiffness 500 — equivalente ao ease-spring). */
    fun <T> spring(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 500f)

    /** Mola com mais quique, para elementos que “saltam” (cartões que expandem). */
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)

    /** Mola do polegar do EffortSlider (stiffness 500, damping 36 → ratio ≈ 0.8). */
    fun <T> thumb(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 500f)

    fun <T> enter(durationMs: Int = Durations.Slow): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMs, easing = Easings.Spring)

    fun <T> exit(durationMs: Int = Durations.Fast): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMs, easing = Easings.Out)

    fun <T> fade(durationMs: Int = Durations.Base): FiniteAnimationSpec<T> =
        // Fades são essenciais para legibilidade; no modo reduzido ficam curtos, mas existem.
        tween(if (reduced) Durations.Instant else durationMs, easing = Easings.Out)
}

/** Specs tipados úteis em `animate*AsState` sem genéricos explícitos. */
object Specs {
    val springFloat: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 500f)
    val enterFloat: TweenSpec<Float> = tween(Durations.Slow, easing = Easings.Spring)
}

val LocalAiStackMotion = staticCompositionLocalOf { AiStackMotion() }

/** Atalho: `true` quando animações não essenciais devem ser desligadas. */
val LocalReducedMotion = staticCompositionLocalOf { false }

private fun readAnimatorScale(context: Context): Float = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
} catch (_: Throwable) {
    1f
}

/**
 * Observa Settings.Global.ANIMATOR_DURATION_SCALE e devolve `true` quando for 0
 * (opção “Remover animações” da acessibilidade ou escala desligada nas opções de desenvolvedor).
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(readAnimatorScale(context) == 0f) }
    DisposableEffect(context) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readAnimatorScale(context) == 0f
            }
        }
        runCatching { context.contentResolver.registerContentObserver(uri, false, observer) }
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }
    return reduced
}
