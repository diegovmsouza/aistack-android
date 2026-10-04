package br.com.amberwrite.aistack.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.amberwrite.aistack.ui.designsystem.brand.BrandAnimation
import br.com.amberwrite.aistack.ui.designsystem.brand.BrandAvd
import br.com.amberwrite.aistack.ui.designsystem.brand.BrandMark
import br.com.amberwrite.aistack.ui.designsystem.brand.BrandMarkAvd
import br.com.amberwrite.aistack.ui.designsystem.brand.ProviderLogo
import br.com.amberwrite.aistack.ui.designsystem.components.AgentStatus
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiChip
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiStackTopBar
import br.com.amberwrite.aistack.ui.designsystem.components.AttachmentChip
import br.com.amberwrite.aistack.ui.designsystem.components.AttachmentKind
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.CodeBlock
import br.com.amberwrite.aistack.ui.designsystem.components.ConnectionBanner
import br.com.amberwrite.aistack.ui.designsystem.components.ConnectionState
import br.com.amberwrite.aistack.ui.designsystem.components.EffortSlider
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.components.GlassCard
import br.com.amberwrite.aistack.ui.designsystem.components.MentionPopup
import br.com.amberwrite.aistack.ui.designsystem.components.MicButton
import br.com.amberwrite.aistack.ui.designsystem.components.MicState
import br.com.amberwrite.aistack.ui.designsystem.components.Origin
import br.com.amberwrite.aistack.ui.designsystem.components.OriginBadge
import br.com.amberwrite.aistack.ui.designsystem.components.PaletteItem
import br.com.amberwrite.aistack.ui.designsystem.components.PermissionCard
import br.com.amberwrite.aistack.ui.designsystem.components.PermissionState
import br.com.amberwrite.aistack.ui.designsystem.components.ProviderBadge
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionCard
import br.com.amberwrite.aistack.ui.designsystem.components.QuestionOption
import br.com.amberwrite.aistack.ui.designsystem.components.QuotaBar
import br.com.amberwrite.aistack.ui.designsystem.components.ShimmerText
import br.com.amberwrite.aistack.ui.designsystem.components.SlashPalette
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.designsystem.components.StatusDot
import br.com.amberwrite.aistack.ui.designsystem.components.StepState
import br.com.amberwrite.aistack.ui.designsystem.components.SubagentTimeline
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.designsystem.components.TagBadge
import br.com.amberwrite.aistack.ui.designsystem.components.TimelineStep
import br.com.amberwrite.aistack.ui.designsystem.components.ToolCard
import br.com.amberwrite.aistack.ui.designsystem.components.ToolKind
import br.com.amberwrite.aistack.ui.designsystem.components.ToolStatus
import br.com.amberwrite.aistack.ui.designsystem.components.TypingCaret
import br.com.amberwrite.aistack.ui.designsystem.components.VoiceWave
import br.com.amberwrite.aistack.ui.designsystem.illustrations.Illustration
import br.com.amberwrite.aistack.ui.designsystem.tokens.DarkAiStackColors
import br.com.amberwrite.aistack.ui.designsystem.tokens.LightAiStackColors
import br.com.amberwrite.aistack.ui.designsystem.tokens.Providers
import br.com.amberwrite.aistack.ui.designsystem.tokens.rememberSystemReducedMotion
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Catálogo do design system (rota de debug "designCatalog"): mostra tokens, marca, ícones,
 * ilustrações e todos os componentes, com alternância claro/escuro, movimento reduzido e cor
 * de destaque. Os componentes interativos funcionam de verdade (estado local), para testar
 * animações e hápticos no aparelho.
 *
 * @param onBack `null` esconde o botão de voltar.
 */
@Composable
fun DesignCatalogScreen(onBack: (() -> Unit)? = null) {
    val systemDark = isSystemInDarkTheme()
    val systemReduced = rememberSystemReducedMotion()
    var dark by rememberSaveable { mutableStateOf(systemDark) }
    var reduced by rememberSaveable { mutableStateOf(systemReduced) }
    var accentId by rememberSaveable { mutableStateOf<String?>(null) }
    val base = if (dark) DarkAiStackColors else LightAiStackColors
    val accent = accentId?.let { base.providers.byId(it) }

    AiStackTheme(darkTheme = dark, accent = accent, reducedMotion = reduced) {
        val haptics = rememberAiHaptics()
        val listState = rememberLazyListState()
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
        Column(
            Modifier
                .fillMaxSize()
                .background(AiTheme.colors.bg),
        ) {
            AiStackTopBar(
                title = "Design system",
                subtitle = "Catálogo de componentes",
                navigationIcon = Lucide.ArrowLeft,
                navigationContentDescription = "Voltar",
                onNavigationClick = onBack,
                scrolled = scrolled,
                haptics = haptics,
            ) {
                AiIconButton(
                    if (reduced) Lucide.CircleStop else Lucide.Play,
                    if (reduced) "Movimento reduzido ligado" else "Movimento reduzido desligado",
                    onClick = { reduced = !reduced },
                    tint = if (reduced) AiTheme.colors.warn else null,
                    haptic = HapticKind.Tick,
                    haptics = haptics,
                )
                AiIconButton(
                    if (dark) Lucide.Sun else Lucide.Moon,
                    if (dark) "Usar tema claro" else "Usar tema escuro",
                    onClick = { dark = !dark },
                    haptic = HapticKind.Tick,
                    haptics = haptics,
                )
            }
            LazyColumn(
                Modifier.fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                item("accent") { AccentSection(accentId) { accentId = it } }
                item("brand") { BrandSection() }
                item("colors") { ColorsSection() }
                item("type") { TypeSection() }
                item("shapes") { ShapesSection() }
                item("providers") { ProvidersSection() }
                item("status") { StatusSection() }
                item("buttons") { ButtonsSection(haptics) }
                item("cards") { CardsSection() }
                item("streaming") { StreamingSection() }
                item("tools") { ToolsSection(haptics) }
                item("permission") { PermissionSection(haptics) }
                item("timeline") { TimelineSection() }
                item("suggestions") { SuggestionsSection(haptics) }
                item("attachments") { AttachmentsSection(haptics) }
                item("quota") { QuotaSection(haptics) }
                item("connection") { ConnectionSection(haptics) }
                item("empty") { EmptySection() }
                item("icons") { IconsSection() }
                item("bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, caption: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(title.uppercase(), style = AiTheme.typography.overline, color = AiTheme.colors.fg3)
            if (caption != null) Text(caption, style = AiTheme.typography.caption, color = AiTheme.colors.fg3)
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Wrap(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun AccentSection(selected: String?, onSelect: (String?) -> Unit) {
    Section("Destaque", "AiStackTheme(accent = …) — padrão: Claude") {
        Wrap {
            AiChip("Padrão", selected = selected == null, onClick = { onSelect(null) })
            Providers.all.forEach { p ->
                AiChip(
                    p.displayName,
                    selected = selected == p.id,
                    onClick = { onSelect(p.id) },
                    leading = { ProviderLogo(p.id, size = 14.dp) },
                    color = AiTheme.colors.providers.byId(p.id),
                )
            }
        }
    }
}

@Composable
private fun BrandSection() {
    var replay by remember { mutableIntStateOf(0) }
    Section("Marca", "BrandMark (Canvas) e BrandMarkAvd (AnimatedVectorDrawable)") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Labeled("Estática") { BrandMark(size = 56.dp) }
            Labeled("Montagem") { key(replay) { BrandMark(size = 56.dp, animation = BrandAnimation.Assemble) } }
            Labeled("Pulso") { BrandMark(size = 56.dp, animation = BrandAnimation.Pulse) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Labeled("AVD montagem") { key(replay) { BrandMarkAvd(BrandAvd.Assemble, size = 56.dp) } }
            Labeled("AVD pulso") { BrandMarkAvd(BrandAvd.Pulse, size = 56.dp) }
            AiButton("Repetir", onClick = { replay++ }, variant = ButtonVariant.Secondary, size = ButtonSize.Small, leadingIcon = Lucide.RefreshCw)
        }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content()
        Text(label, style = AiTheme.typography.caption, color = AiTheme.colors.fg3)
    }
}

@Composable
private fun ColorsSection() {
    val c = AiTheme.colors
    val swatches = listOf(
        "bg" to c.bg, "sidebar" to c.sidebar, "surface" to c.surface, "surface2" to c.surface2,
        "surface3" to c.surface3, "userBubble" to c.userBubble, "line" to c.line, "lineStrong" to c.lineStrong,
        "fg" to c.fg, "fg2" to c.fg2, "fg3" to c.fg3, "glass" to c.glass,
        "accent" to c.accent, "accentSoft" to c.accentSoft, "ok" to c.ok, "warn" to c.warn,
        "danger" to c.danger, "highlight" to c.highlight, "diffAdd" to c.diffAddBg, "diffDel" to c.diffDelBg,
    )
    val providers = Providers.all.map { it.displayName to c.providers.byId(it.id) }
    Section("Cores", "AiTheme.colors (OKLCH convertido para sRGB)") {
        Wrap { swatches.forEach { (n, col) -> Swatch(n, col) } }
        Wrap { providers.forEach { (n, col) -> Swatch(n, col) } }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(AiTheme.shapes.sm)
                .background(color)
                .border(1.dp, AiTheme.colors.line, AiTheme.shapes.sm),
        )
        Text(name, style = AiTheme.typography.caption, color = AiTheme.colors.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TypeSection() {
    val t = AiTheme.typography
    val styles: List<Pair<String, TextStyle>> = listOf(
        "display" to t.display, "title" to t.title, "heading" to t.heading, "body" to t.body,
        "bodySerif" to t.bodySerif, "bodySmall" to t.bodySmall, "label" to t.label, "caption" to t.caption,
        "overline" to t.overline, "mono" to t.mono, "monoSmall" to t.monoSmall,
    )
    Section("Tipografia", "Inter · Source Serif 4 · JetBrains Mono (OFL)") {
        styles.forEach { (name, style) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = t.monoSmall, color = AiTheme.colors.fg3, modifier = Modifier.width(84.dp))
                Text(
                    if (name == "overline") "SESSÕES RECENTES" else "Ação rápida — ãéç 0123",
                    style = style,
                    color = AiTheme.colors.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ShapesSection() {
    val s = AiTheme.shapes
    Section("Formas", "AiTheme.shapes e AiTheme.spacing (gutter 16 dp)") {
        Wrap {
            listOf("xs" to s.xs, "sm" to s.sm, "md" to s.md, "lg" to s.lg, "xl" to s.xl, "composer" to s.composer, "userBubble" to s.userBubble, "pill" to s.pill)
                .forEach { (n, shape) ->
                    Labeled(n) {
                        Box(
                            Modifier
                                .size(width = 56.dp, height = 40.dp)
                                .clip(shape)
                                .background(AiTheme.colors.surface2)
                                .border(1.dp, AiTheme.colors.lineStrong, shape),
                        )
                    }
                }
        }
    }
}

@Composable
private fun ProvidersSection() {
    Section("Provedores", "ProviderLogo · ProviderBadge") {
        Wrap { Providers.all.forEach { ProviderLogo(it.id, size = 28.dp, contentDescription = it.displayName) } }
        Wrap {
            ProviderBadge("claude", model = "opus-4.7")
            ProviderBadge("codex", model = "gpt-5.5")
            ProviderBadge("agy", model = "gemini-3-pro")
            ProviderBadge("kimi")
            ProviderBadge("deepseek", compact = true)
            ProviderBadge("glm", compact = true)
            ProviderBadge("qwen", compact = true)
        }
    }
}

@Composable
private fun StatusSection() {
    Section("Status e badges", "StatusDot · OriginBadge · TagBadge") {
        Wrap {
            AgentStatus.entries.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(s)
                    Text(s.name, style = AiTheme.typography.caption, color = AiTheme.colors.fg2)
                }
            }
        }
        Wrap {
            Origin.entries.forEach { OriginBadge(it) }
            TagBadge("main", icon = Lucide.GitBranch)
            TagBadge("3 subagentes", icon = Lucide.Bot, color = AiTheme.colors.accent, background = AiTheme.colors.accentSoft)
        }
    }
}

@Composable
private fun ButtonsSection(haptics: AiHaptics) {
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf("Ativas")) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(1500)
            loading = false
        }
    }
    Section("Botões e chips", "AiButton · AiIconButton · AiChip") {
        Wrap {
            AiButton("Enviar", onClick = { loading = true }, loading = loading, leadingIcon = Lucide.Send, haptics = haptics)
            AiButton("Secundário", onClick = {}, variant = ButtonVariant.Secondary, haptics = haptics)
            AiButton("Fantasma", onClick = {}, variant = ButtonVariant.Ghost, haptics = haptics)
            AiButton("Excluir", onClick = {}, variant = ButtonVariant.Danger, leadingIcon = Lucide.Trash2, haptics = haptics)
            AiButton("Pequeno", onClick = {}, size = ButtonSize.Small, haptics = haptics)
            AiButton("Grande", onClick = {}, size = ButtonSize.Large, haptics = haptics)
            AiButton("Desativado", onClick = {}, enabled = false)
        }
        Wrap {
            AiIconButton(Lucide.Plus, "Adicionar", onClick = {}, variant = ButtonVariant.Primary, haptics = haptics, haptic = HapticKind.Tick)
            AiIconButton(Lucide.Settings, "Configurações", onClick = {}, variant = ButtonVariant.Secondary)
            AiIconButton(Lucide.Ellipsis, "Mais", onClick = {})
            AiIconButton(Lucide.Trash2, "Excluir", onClick = {}, variant = ButtonVariant.Danger)
        }
        Wrap {
            listOf("Ativas", "Arquivadas", "Agendadas").forEach { f ->
                AiChip(
                    f,
                    selected = f in selected,
                    onClick = { selected = if (f in selected) selected - f else selected + f },
                    haptics = haptics,
                )
            }
            AiChip("src/relay", leadingIcon = Lucide.Folder, onRemove = {})
        }
    }
}

@Composable
private fun CardsSection() {
    Section("Cartões", "GlassCard (menus/popovers) · SurfaceCard") {
        GlassCard {
            Text("Cartão de vidro", style = AiTheme.typography.heading, color = AiTheme.colors.fg)
            Text("Fundo translúcido, borda fina e sombra suave.", style = AiTheme.typography.bodySmall, color = AiTheme.colors.fg2)
        }
        SurfaceCard(onClick = {}) {
            Text("Cartão de superfície (clicável)", style = AiTheme.typography.heading, color = AiTheme.colors.fg)
            Text("Usado em listas de sessões e configurações.", style = AiTheme.typography.bodySmall, color = AiTheme.colors.fg2)
        }
    }
}

@Composable
private fun StreamingSection() {
    Section("Streaming", "ShimmerText · TypingCaret · Spinner") {
        ShimmerText("Pensando…")
        ShimmerText("Lendo 14 arquivos", style = AiTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A resposta está chegando", style = AiTheme.typography.body, color = AiTheme.colors.fg)
            Spacer(Modifier.width(2.dp))
            TypingCaret()
            Spacer(Modifier.width(16.dp))
            TypingCaret(block = true, color = AiTheme.colors.fg2)
            Spacer(Modifier.width(16.dp))
            Spinner()
        }
    }
}

@Composable
private fun ToolsSection(haptics: AiHaptics) {
    Section("Ferramentas", "ToolCard expande com mola · CodeBlock") {
        ToolCard("Read", ToolKind.File, ToolStatus.Success, detail = "src/relay/RelayClient.kt", meta = "0,2 s", haptics = haptics) {
            CodeBlock("class RelayClient(\n    private val url: String,\n) { … }")
        }
        ToolCard("Bash", ToolKind.Terminal, ToolStatus.Running, detail = "./gradlew :app:compileDebugKotlin", haptics = haptics) {
            CodeBlock("> Task :app:compileDebugKotlin\nBUILD SUCCESSFUL in 41s")
        }
        ToolCard("Edit", ToolKind.Edit, ToolStatus.Error, detail = "app/build.gradle.kts", meta = "falhou", haptics = haptics) {
            CodeBlock("old_string não encontrado", tone = AiTheme.colors.danger)
        }
        ToolCard("WebSearch", ToolKind.Web, ToolStatus.Pending, detail = "compose animation-graphics", haptics = haptics)
    }
}

@Composable
private fun PermissionSection(haptics: AiHaptics) {
    var state by remember { mutableStateOf(PermissionState.Pending) }
    var answer by remember { mutableStateOf<String?>(null) }
    Section("Permissão e pergunta", "Somente visual; decisões saem pelos callbacks") {
        PermissionCard(
            toolName = "Bash",
            state = state,
            onAllow = { state = PermissionState.Allowed },
            onDeny = { state = PermissionState.Denied },
            onAlwaysAllow = { state = PermissionState.Allowed },
            detail = "rm -rf build/ && ./gradlew assembleDebug",
            description = "Limpar a pasta de build e recompilar",
            haptics = haptics,
        )
        if (state != PermissionState.Pending) {
            AiButton("Reiniciar", onClick = { state = PermissionState.Pending }, variant = ButtonVariant.Ghost, size = ButtonSize.Small)
        }
        QuestionCard(
            question = "Qual biblioteca de QR code devo usar?",
            header = "Pergunta do agente",
            options = listOf(
                QuestionOption("ML Kit", "Leitura rápida, já usada no app", recommended = true),
                QuestionOption("ZXing", "Sem Google Play Services"),
            ),
            onSubmit = { answer = it.text },
            onSkip = { answer = "(pulada)" },
            haptics = haptics,
        )
        answer?.let { Text("Resposta: $it", style = AiTheme.typography.monoSmall, color = AiTheme.colors.fg2) }
    }
}

@Composable
private fun TimelineSection() {
    Section("Subagentes", "SubagentTimeline") {
        SubagentTimeline(
            listOf(
                TimelineStep("1", "Explorar o código", StepState.Done, "12 arquivos lidos", providerId = "claude", meta = "38 s"),
                TimelineStep("2", "Revisar o relay", StepState.Running, "Verificando reconexão", providerId = "codex"),
                TimelineStep("3", "Escrever testes", StepState.Pending, providerId = "agy"),
                TimelineStep("4", "Migrar ícones", StepState.Failed, "Dependência ausente"),
                TimelineStep("5", "Atualizar docs", StepState.Skipped),
            ),
        )
    }
}

@Composable
private fun SuggestionsSection(haptics: AiHaptics) {
    var highlighted by remember { mutableIntStateOf(0) }
    val commands = listOf(
        PaletteItem("compact", "compact", "Resumir a conversa"),
        PaletteItem("model", "model", "Trocar o modelo", badge = "opus"),
        PaletteItem("review", "review", "Revisar as mudanças"),
    )
    val files = listOf(
        PaletteItem("a", "RelayClient.kt", "app/src/main/java/…/relay", icon = Lucide.FileCode),
        PaletteItem("b", "README.md", "raiz", icon = Lucide.FileText),
    )
    Section("Sugestões", "SlashPalette · MentionPopup") {
        SlashPalette(true, commands, onSelect = { highlighted = commands.indexOf(it) }, query = "re", highlightedIndex = highlighted, haptics = haptics)
        MentionPopup(true, files, onSelect = {}, query = "rel", haptics = haptics)
    }
}

@Composable
private fun AttachmentsSection(haptics: AiHaptics) {
    var mic by remember { mutableStateOf(MicState.Idle) }
    var level by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(mic) {
        // Simula o nível do microfone enquanto “ouve”.
        while (mic == MicState.Listening) {
            level = (level * 0.5f + Random.nextFloat() * 0.6f).coerceIn(0f, 1f)
            delay(80)
        }
        if (mic == MicState.Processing) {
            delay(1200)
            mic = MicState.Idle
        }
        level = 0f
    }
    LaunchedEffect(Unit) {
        while (true) {
            progress = 0f
            while (progress < 1f) {
                delay(120)
                progress += 0.05f
            }
            delay(1500)
        }
    }
    Section("Anexos e voz", "AttachmentChip · MicButton · VoiceWave") {
        Wrap {
            AttachmentChip("captura.png", kind = AttachmentKind.Image, sizeLabel = "412 KB", onRemove = {}, haptics = haptics)
            AttachmentChip("RelayClient.kt", kind = AttachmentKind.Code, progress = progress.coerceAtMost(1f), onRemove = {})
            AttachmentChip("nota.m4a", kind = AttachmentKind.Audio, error = true, onRemove = {})
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MicButton(
                state = mic,
                level = level,
                onClick = {
                    mic = when (mic) {
                        MicState.Idle -> MicState.Listening
                        MicState.Listening -> MicState.Processing
                        else -> mic
                    }
                },
                haptics = haptics,
            )
            VoiceWave(level = level, active = mic == MicState.Listening, modifier = Modifier.weight(1f))
        }
        Text("Toque no microfone para simular o ditado.", style = AiTheme.typography.caption, color = AiTheme.colors.fg3)
    }
}

@Composable
private fun QuotaSection(haptics: AiHaptics) {
    var a by remember { mutableFloatStateOf(0.32f) }
    var b by remember { mutableFloatStateOf(0.71f) }
    var effort by remember { mutableIntStateOf(2) }
    var effortCodex by remember { mutableIntStateOf(1) }
    Section("Cota e esforço", "QuotaBar (animada) · EffortSlider") {
        QuotaBar(a, label = "Sessão de 5 h", providerId = "claude", resetText = "Renova em 2 h 14 min")
        QuotaBar(b, label = "Semanal", providerId = "codex")
        AiButton(
            "Sortear valores",
            onClick = {
                a = Random.nextFloat()
                b = Random.nextFloat()
            },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Small,
            leadingIcon = Lucide.RefreshCw,
        )
        EffortSlider(listOf("low", "medium", "high", "xhigh", "max"), effort, onSelect = { effort = it }, haptics = haptics)
        EffortSlider(listOf("minimal", "low", "medium", "high"), effortCodex, onSelect = { effortCodex = it }, providerId = "codex", haptics = haptics)
        EffortSlider(listOf("low", "high"), 1, onSelect = {}, providerId = "agy")
    }
}

@Composable
private fun ConnectionSection(haptics: AiHaptics) {
    var state by remember { mutableStateOf(ConnectionState.Reconnecting) }
    Section("Conexão", "ConnectionBanner") {
        Wrap {
            ConnectionState.entries.forEach { s ->
                AiChip(s.name, selected = s == state, onClick = { state = s }, haptics = haptics)
            }
        }
        Box(Modifier.clip(AiTheme.shapes.md).border(1.dp, AiTheme.colors.line, AiTheme.shapes.md)) {
            Column(Modifier.fillMaxWidth()) {
                ConnectionBanner(
                    state,
                    detail = when (state) {
                        ConnectionState.Reconnecting -> "Tentando de novo em 8 s"
                        ConnectionState.HostOffline -> "Abra o aistack no computador"
                        ConnectionState.AuthRejected -> "O desktop não reconhece este celular"
                        else -> null
                    },
                    onRetry = { state = ConnectionState.Connecting },
                    onRepair = { state = ConnectionState.Connecting },
                )
                Text(
                    "Conteúdo da tela",
                    style = AiTheme.typography.bodySmall,
                    color = AiTheme.colors.fg3,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptySection() {
    Section("Estados vazios", "EmptyState + ilustrações SVG tingidas pelo tema") {
        EmptyState(
            "Nenhuma sessão ainda",
            illustration = Illustration.NoSessions,
            body = "Comece uma conversa aqui ou no desktop.",
            primaryAction = { AiButton("Nova sessão", onClick = {}, leadingIcon = Lucide.Plus) },
        )
        EmptyState("Nada pendente", illustration = Illustration.NoPending, body = "Permissões e perguntas aparecem aqui.")
        EmptyState(
            "Sem conexão com o desktop",
            illustration = Illustration.Offline,
            accent = AiTheme.colors.warn,
            body = "Verifique se o aistack está aberto no computador.",
            primaryAction = { AiButton("Tentar de novo", onClick = {}, variant = ButtonVariant.Secondary, leadingIcon = Lucide.RefreshCw) },
        )
        EmptyState(
            "Parear com o desktop",
            illustration = Illustration.Pairing,
            body = "No desktop, abra Configurações › Celular e escaneie o QR code.",
            primaryAction = { AiButton("Escanear QR code", onClick = {}, leadingIcon = Lucide.QrCode) },
            secondaryAction = { AiButton("Colar link", onClick = {}, variant = ButtonVariant.Ghost, leadingIcon = Lucide.Link) },
        )
        EmptyState("aistack", body = "Seus agentes, no bolso.", art = { BrandMark(size = 72.dp, animation = BrandAnimation.Pulse) })
    }
}

@Composable
private fun IconsSection() {
    Section("Ícones", "Lucide (ISC) — ${Lucide.all.size} ícones em ui.icons.Lucide") {
        Wrap {
            Lucide.all.forEach { (name, icon) ->
                Column(
                    Modifier
                        .width(76.dp)
                        .clip(AiTheme.shapes.sm)
                        .background(AiTheme.colors.surface)
                        .border(1.dp, AiTheme.colors.line, AiTheme.shapes.sm)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(icon, name, Modifier.size(20.dp), tint = AiTheme.colors.fg)
                    Text(name, style = AiTheme.typography.caption, color = AiTheme.colors.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
