package br.com.amberwrite.aistack.ui.theme

import androidx.compose.runtime.Composable

/**
 * Tema legado, mantido por compatibilidade com as telas antigas: delega para
 * [br.com.amberwrite.aistack.ui.designsystem.AiStackTheme]. O padrão continua escuro
 * porque as telas antigas usam as constantes escuras de Color.kt diretamente.
 */
@Composable
fun AiStackTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    br.com.amberwrite.aistack.ui.designsystem.AiStackTheme(
        darkTheme = darkTheme,
        dynamicColor = false,
        content = content,
    )
}
