package br.com.amberwrite.aistack.ui.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import br.com.amberwrite.aistack.ui.designsystem.tokens.AiStackColors
import br.com.amberwrite.aistack.ui.designsystem.tokens.AiStackMotion
import br.com.amberwrite.aistack.ui.designsystem.tokens.AiStackShapes
import br.com.amberwrite.aistack.ui.designsystem.tokens.AiStackSpacing
import br.com.amberwrite.aistack.ui.designsystem.tokens.AiStackTypography
import br.com.amberwrite.aistack.ui.designsystem.tokens.DarkAiStackColors
import br.com.amberwrite.aistack.ui.designsystem.tokens.DefaultAiStackTypography
import br.com.amberwrite.aistack.ui.designsystem.tokens.LightAiStackColors
import br.com.amberwrite.aistack.ui.designsystem.tokens.LocalAiStackMotion
import br.com.amberwrite.aistack.ui.designsystem.tokens.LocalReducedMotion
import br.com.amberwrite.aistack.ui.designsystem.tokens.rememberSystemReducedMotion
import br.com.amberwrite.aistack.ui.designsystem.tokens.toMaterial

val LocalAiStackColors = staticCompositionLocalOf { DarkAiStackColors }
val LocalAiStackTypography = staticCompositionLocalOf { DefaultAiStackTypography }
val LocalAiStackShapes = staticCompositionLocalOf { AiStackShapes() }
val LocalAiStackSpacing = staticCompositionLocalOf { AiStackSpacing() }

/** Acesso aos tokens do tema atual: `AiTheme.colors.fg`, `AiTheme.typography.body` etc. */
object AiTheme {
    val colors: AiStackColors
        @Composable @ReadOnlyComposable get() = LocalAiStackColors.current
    val typography: AiStackTypography
        @Composable @ReadOnlyComposable get() = LocalAiStackTypography.current
    val shapes: AiStackShapes
        @Composable @ReadOnlyComposable get() = LocalAiStackShapes.current
    val spacing: AiStackSpacing
        @Composable @ReadOnlyComposable get() = LocalAiStackSpacing.current
    val motion: AiStackMotion
        @Composable @ReadOnlyComposable get() = LocalAiStackMotion.current
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/** ColorScheme do Material 3 derivado dos tokens (para componentes M3 usados nas telas). */
fun AiStackColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = accentFg,
        primaryContainer = accent.copy(alpha = 0.16f).compositeOver(surface),
        onPrimaryContainer = fg,
        secondary = fg2,
        onSecondary = bg,
        secondaryContainer = surface3,
        onSecondaryContainer = fg,
        tertiary = providers.agy,
        onTertiary = accentFg,
        background = bg,
        onBackground = fg,
        surface = surface,
        onSurface = fg,
        surfaceVariant = surface2,
        onSurfaceVariant = fg2,
        surfaceTint = Color.Transparent,
        surfaceBright = surface3,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = sidebar,
        surfaceContainer = surface,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface3,
        inverseSurface = fg,
        inverseOnSurface = bg,
        inversePrimary = accent,
        outline = lineStrong.compositeOver(surface),
        outlineVariant = line.compositeOver(surface),
        error = danger,
        onError = Color.White,
        errorContainer = danger.copy(alpha = 0.14f).compositeOver(surface),
        onErrorContainer = danger,
        scrim = scrim,
    )
}

/**
 * Tema do AiStack.
 *
 * @param darkTheme tema escuro; por padrão segue o sistema.
 * @param dynamicColor usa Material You (Android 12+) apenas no ColorScheme do Material;
 *   os tokens do AiStack ([AiTheme.colors]) continuam fiéis ao desktop. Desligado por padrão.
 * @param accent cor de destaque (ex.: a do provedor ativo). `null` mantém a do Claude.
 * @param reducedMotion força o modo de movimento reduzido; `null` segue o sistema.
 * @param applySystemBars ajusta a aparência das barras de status/navegação ao tema.
 */
@Composable
fun AiStackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accent: Color? = null,
    reducedMotion: Boolean? = null,
    applySystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkAiStackColors else LightAiStackColors
    val colors = remember(base, accent) { if (accent != null) base.copy(accent = accent) else base }
    val systemReduced = rememberSystemReducedMotion()
    val reduced = reducedMotion ?: systemReduced
    val motion = remember(reduced) { AiStackMotion(reduced) }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> colors.toColorScheme()
    }

    val view = LocalView.current
    if (applySystemBars && !view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalAiStackColors provides colors,
        LocalAiStackTypography provides DefaultAiStackTypography,
        LocalAiStackShapes provides AiStackShapes(),
        LocalAiStackSpacing provides AiStackSpacing(),
        LocalAiStackMotion provides motion,
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DefaultAiStackTypography.toMaterial(),
            shapes = AiStackShapes().toMaterial(),
            content = content,
        )
    }
}
