package br.com.amberwrite.aistack.ui.designsystem.illustrations

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.ui.designsystem.AiTheme

/**
 * Ilustrações vetoriais de estados vazios. Cada uma tem duas camadas monocromáticas
 * (preto + alfa) que são tingidas em tempo de execução: [neutral] com a cor de texto e
 * [accent] com a cor de destaque — assim funcionam no claro, no escuro e com acento trocado.
 * Proporção 4:3 (160×120 dp).
 */
enum class Illustration(
    @DrawableRes val neutral: Int,
    @DrawableRes val accent: Int,
    val description: String,
) {
    /** Nenhuma sessão ainda. */
    NoSessions(R.drawable.ill_empty_sessions, R.drawable.ill_empty_sessions_accent, "Nenhuma sessão"),

    /** Nada pendente (permissões/perguntas/notificações). */
    NoPending(R.drawable.ill_empty_pending, R.drawable.ill_empty_pending_accent, "Nada pendente"),

    /** Sem conexão com o desktop. */
    Offline(R.drawable.ill_offline, R.drawable.ill_offline_accent, "Sem conexão"),

    /** Parear com o desktop via QR code. */
    Pairing(R.drawable.ill_pairing, R.drawable.ill_pairing_accent, "Parear com o desktop"),
}

/**
 * Desenha uma [Illustration] tingida pelo tema.
 *
 * @param width largura; a altura segue a proporção 4:3.
 * @param tint cor da camada neutra (padrão `fg`).
 * @param accent cor da camada de destaque (padrão `accent`; use `warn`/`danger` em erros).
 */
@Composable
fun IllustrationImage(
    illustration: Illustration,
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    tint: Color = AiTheme.colors.fg,
    accent: Color = AiTheme.colors.accent,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(width = width, height = width * 0.75f)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        Image(
            painterResource(illustration.neutral),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            colorFilter = ColorFilter.tint(tint),
        )
        Image(
            painterResource(illustration.accent),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            colorFilter = ColorFilter.tint(accent),
        )
    }
}
