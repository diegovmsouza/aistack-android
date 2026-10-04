package br.com.amberwrite.aistack.ui.designsystem.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.AiStackTheme
import br.com.amberwrite.aistack.ui.designsystem.AiTheme

/** Multipreview padrão do design system: claro e escuro lado a lado. */
@Preview(name = "Claro", showBackground = true, widthDp = 380)
@Preview(name = "Escuro", showBackground = true, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class AiPreviews

/** Superfície de preview: aplica o tema (seguindo o uiMode do preview) e o fundo `bg`. */
@Composable
fun PreviewSurface(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable ColumnScope.() -> Unit,
) {
    AiStackTheme(darkTheme = darkTheme, applySystemBars = false) {
        Column(
            Modifier
                .background(AiTheme.colors.bg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
