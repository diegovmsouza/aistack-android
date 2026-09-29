package br.com.amberwrite.aistack.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ProviderClaude,
    onPrimary = AiStackBg,
    background = AiStackBg,
    onBackground = AiStackFg,
    surface = AiStackSurface,
    onSurface = AiStackFg,
    surfaceVariant = AiStackSurface2,
    onSurfaceVariant = AiStackFg2,
    outline = AiStackLine,
    error = StatusDanger
)

@Composable
fun AiStackTheme(
    darkTheme: Boolean = true, // Padrão AiStack Dark
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AiStackSurface.toArgb()
            window.navigationBarColor = AiStackBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
