package br.com.amberwrite.aistack.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import br.com.amberwrite.aistack.ui.designsystem.AiTheme

/**
 * Transições de elemento compartilhado entre destinos (item da lista → chat, FAB → nova
 * sessão). As telas não dependem do NavHost: usam [aiSharedBounds], que vira no-op quando
 * não há escopo (prévia, painel de detalhe) ou com movimento reduzido.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Escopo de animação do destino atual do NavHost. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

object SharedKeys {
    fun conversation(id: String) = "conv-$id"
    const val NEW_SESSION = "new-session"
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val SharedBoundsTransform = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.86f, stiffness = 380f)
}

/** Limites compartilhados (container transform) com a chave [key]. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.aiSharedBounds(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    if (AiTheme.reducedMotion) return this
    return with(shared) {
        this@aiSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            enter = fadeIn(),
            exit = fadeOut(),
            boundsTransform = SharedBoundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
}
