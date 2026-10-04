# 06 — Design system (Android)

Pacote raiz: `br.com.amberwrite.aistack.ui.designsystem`. Ícones em `br.com.amberwrite.aistack.ui.icons`.
Tudo é Compose + Material3 (BOM 2025.10.01), **sem** `material-icons`. Ícones, logos e ilustrações são
vetores (SVG convertido em VectorDrawable ou `ImageVector`); não há PNG, JPG nem WebP.

Para ver tudo funcionando: `DesignCatalogScreen()` (rota de debug `designCatalog`, criada por A1).

---

## 1. Tema

```kotlin
@Composable
fun AiStackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,      // reservado; o app usa sempre a paleta própria
    accent: Color? = null,              // sobrescreve AiTheme.colors.accent (ex.: cor do provedor)
    reducedMotion: Boolean? = null,     // null = segue ANIMATOR_DURATION_SCALE == 0 do sistema
    applySystemBars: Boolean = true,    // ícones claros/escuros nas barras do sistema
    content: @Composable () -> Unit,
)
```

- Também popula o `MaterialTheme` (colorScheme, typography, shapes), então componentes M3 herdam a paleta.
- `ui.theme.AiStackTheme(darkTheme = true, content)` continua existindo por compatibilidade e delega
  para o tema novo.
- Acesso aos tokens: `AiTheme.colors`, `AiTheme.typography`, `AiTheme.shapes`, `AiTheme.spacing`,
  `AiTheme.motion`, `AiTheme.reducedMotion`.

### 1.1 Cores (`tokens/ColorTokens.kt`)

Os valores foram definidos em OKLCH (mesmos do desktop) e convertidos para sRGB. `LightAiStackColors` e
`DarkAiStackColors` são do tipo `AiStackColors`, com estes campos:

| Grupo | Campos |
|---|---|
| Fundos | `bg`, `sidebar`, `surface`, `surface2`, `surface3`, `glass`, `scrim`, `shadow` |
| Texto | `fg`, `fg2`, `fg3` |
| Linhas | `line`, `lineStrong` |
| Bolha do usuário | `userBubble`, `userBubbleFg` |
| Semânticas | `ok`, `warn`, `danger`, `accent`, `accentFg`, `highlight` |
| Diff | `diffAddBg/Fg`, `diffDelBg/Fg` |
| Derivadas | `accentSoft`, `okSoft`, `warnSoft`, `dangerSoft` |
| Provedores | `providers: ProviderColors` (claude, codex, agy, kimi, deepseek, glm, qwen) e `providers.byId(id)` |

Também há `isDark`. Referência: no tema claro, `bg` é FBFAF7, `fg` é 1A1510 e o acento é D6673F; no
escuro, `bg` é 0D0E11, `fg` é EFF0F3 e o acento é ED845B.

### 1.2 Provedores e esforço (`tokens/ProviderTokens.kt`)

- `Providers.all: List<ProviderSpec(id, displayName, logo, tintable)>`, além de `Providers.byId(id)`.
  O `byId` aceita apelidos como `openai`/`gpt` → codex e `gemini`/`google` → agy.
- `EffortPalettes.forProvider(id, colors): List<Color>`: rampa de cores de esforço do provedor.
- `EffortPalettes.colorAt(palette, fraction)` interpola a rampa; `EffortPalettes.ramp(base, steps)`
  gera uma rampa nova.

### 1.3 Tipografia (`tokens/TypeTokens.kt`)

Fontes (licença OFL, textos em `assets/licenses/`): Inter (UI), Source Serif 4 (títulos e leitura) e
JetBrains Mono (código).

Estilos: `display`, `title`, `heading`, `body`, `bodySerif`, `bodySmall`, `label`, `caption`,
`overline`, `mono`, `monoSmall`.

### 1.4 Formas e espaçamento (`tokens/ShapeTokens.kt`)

- **Formas:** `xs` 6, `sm` 8, `md` 10, `lg` 14, `xl` 18, `composer` 22. Além delas, `sheet` (28, só nos
  cantos de cima), `userBubble` e `pill`.
- **Espaçamento:** `xxs` 2, `xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 20, `xxl` 24, `xxxl` 32, `gutter` 16.

### 1.5 Movimento (`tokens/MotionTokens.kt`)

- **Durações (ms):** `Instant` 90, `Fast` 140, `Base` 200, `Slow` 260, `Slower` 360, `Pulse` 1200,
  `Shimmer` 1600, `Breath` 1800, `Caret` 530.
- **Curvas (`Easings`):** `Spring`, `Out`, `InOut`.
- **Specs:** `AiTheme.motion.spring()`, `bouncy()`, `thumb()`, `enter(ms)`, `exit(ms)` e `fade(ms)`.
  Todas são genéricas (`FiniteAnimationSpec<T>`). Com movimento reduzido, viram `snap()` ou fades curtos.
- `rememberSystemReducedMotion()` observa `Settings.Global.ANIMATOR_DURATION_SCALE` ao vivo.

**Convenção:** todo componente animado consulta `AiTheme.reducedMotion`. Loops infinitos (pulso,
shimmer, cursor, respiração) param, e transições ficam instantâneas. A informação nunca depende só da
animação: o spinner vira um arco estático e o shimmer vira texto simples.

### 1.6 Hápticos (`Haptics.kt`)

```kotlin
enum class HapticKind { Confirm, Reject, Tick, LongPress }
fun interface AiHaptics { fun perform(kind: HapticKind); companion object { val None: AiHaptics } }
@Composable fun rememberAiHaptics(): AiHaptics   // usa LocalView.performHapticFeedback
```

Componentes interativos recebem `haptics: AiHaptics` como **parâmetro**, com padrão `AiHaptics.None`.
Quem chama decide se há vibração (por exemplo, conforme uma preferência do usuário) passando
`rememberAiHaptics()`.

### 1.7 Previews

- `@AiPreviews` gera as variantes claro e escuro, com 380 dp de largura.
- `PreviewSurface { }` aplica o tema e empilha o conteúdo com 12 dp de espaço.

Todo componente tem preview.

---

## 2. Marca, provedores e ícones

- `BrandMark(modifier, size = 48.dp, animation: BrandAnimation = None, contentDescription?)`:
  - desenhado em Canvas;
  - `BrandAnimation.Assemble` faz a montagem uma vez, e `Pulse` faz uma respiração contínua.
- `BrandMarkAvd(avd: BrandAvd, modifier, size, playing = true)`:
  - toca `avd_brand_assemble` ou `avd_brand_pulse` via ImageView;
  - com movimento reduzido, mostra `ic_brand_mark` estático.
- `ProviderLogo(providerId, modifier, size = 16.dp, tint: Color? = null, contentDescription?)`:
  - usa os logos `ic_provider_*`;
  - os monocromáticos (codex, kimi) seguem `fg` quando `tint` é nulo.
- `Lucide` (`ui.icons`):
  - `ImageVector`s lazy (Lucide, ISC), por exemplo `Lucide.Send` e `Lucide.Mic`;
  - `Lucide.all: List<Pair<String, ImageVector>>` lista todos com o nome kebab-case.
  - Há também cópias em drawable (`ic_lucide_*`) para notificações e Views.

---

## 3. Componentes (`designsystem.components`)

### Status e badges

- `StatusDot(status: AgentStatus, modifier, size = 8.dp, pulse: Boolean? = null, color: Color? = null)`
  - `AgentStatus { Online, Busy, Pending, Error, Offline }` e `AgentStatus.color()`.
  - Busy e Pending pulsam.
- `ProviderBadge(providerId, modifier, model: String? = null, compact = false)`
- `OriginBadge(origin: Origin, modifier, label = origin.label, showLabel = true)`
  - `Origin { Desktop, Mobile, Cli, Scheduled }`.
- `TagBadge(text, modifier, icon?, color = fg2, background = surface2)`

### Botões e chips

- `AiButton(text, onClick, modifier, variant = Primary, size = Medium, leadingIcon?, trailingIcon?, enabled, loading, shape, haptic: HapticKind? = Tick, haptics)`
  - `ButtonVariant { Primary, Secondary, Ghost, Danger }` e `ButtonSize { Small, Medium, Large }`.
  - A escala de pressão usa mola.
- `AiIconButton(icon, contentDescription, onClick, modifier, variant = Ghost, size = 40.dp, iconSize = 20.dp, enabled, tint?, haptic?, haptics)`
- `AiChip(text, modifier, selected, onClick?, leadingIcon?, leading?, onRemove?, color?, haptics)`

### Cartões e texto

- `GlassCard(modifier, shape = lg, elevation = 12.dp, contentPadding, borderColor, onClick?, content: ColumnScope)`:
  para menus e popovers.
- `SurfaceCard(modifier, shape = lg, contentPadding, color = surface, borderColor = line, onClick?, content)`
- `ShimmerText(text, modifier, style = label, color = fg3, highlight = fg, active = true, maxLines = 1)`:
  "Pensando…".
- `TypingCaret(modifier, color = accent, height = 16.dp, block = false, blinking = true)`
- `Spinner(modifier, size = 16.dp, color = fg2, strokeWidth = 2.dp)`

### Ferramentas, permissões e perguntas

- `ToolCard(title, kind: ToolKind, status: ToolStatus, modifier, detail?, meta?, expanded: Boolean? = null, onExpandedChange?, haptics, content: (ColumnScope)?)`
  - `ToolKind { File, Edit, Terminal, Search, Web, Agent, Todo, Tool }` (`ToolKind.icon()`).
  - `ToolStatus { Pending, Running, Success, Error }`.
  - Abre e fecha com mola.
  - O estado de expansão é interno quando `expanded == null`, e controlado de fora caso contrário.
- `ToolStatusIndicator(status, modifier, size = 14.dp)`
- `CodeBlock(text, modifier, maxHeight = 220.dp, tone = fg2, background = surface2)`: monoespaçado e
  com rolagem.
- `PermissionCard(toolName, state: PermissionState, onAllow, onDeny, modifier, detail?, description?, onAlwaysAllow?, haptics)`
  - `PermissionState { Pending, Allowed, Denied, Cancelled }`.
  - O componente é só visual: quem chama atualiza `state` a partir dos callbacks.
- `QuestionCard(question, options: List<QuestionOption>, onSubmit: (QuestionAnswer) -> Unit, onSkip, modifier, header?, haptics)`
  - `QuestionOption(label, description?, recommended = false)`.
  - `QuestionAnswer.Choice(number, option)` e `QuestionAnswer.Custom(value)` têm `.text`.

### Subagentes e sugestões

- `SubagentTimeline(steps: List<TimelineStep>, modifier, onStepClick?)`
  - `TimelineStep(id, title, state: StepState, subtitle?, providerId?, meta?)`.
  - `StepState { Pending, Running, Done, Failed, Skipped }`.
- `SlashPalette(visible, items: List<PaletteItem>, onSelect, modifier, query = "", highlightedIndex = 0, haptics)`
- `MentionPopup(visible, items: List<PaletteItem>, onSelect, modifier, query, …)` tem uma sobrecarga
  genérica `MentionPopup<T>(…, itemContent)`.
- `PaletteItem(key, title, subtitle?, icon?, badge?)`
- Base genérica: `SuggestionPopup<T>(visible, items, onSelect, modifier, highlightedIndex, title?, emptyText, maxHeight = 280.dp, key?, haptics, itemContent)`.
  - Usa `highlightMatch(text, query)` e `PaletteItemRow`.

### Composer

- `AttachmentChip(name, modifier, kind = File, sizeLabel?, thumbnail: Painter?, progress: Float?, error = false, onClick?, onRemove?, haptics)`
  - `AttachmentKind { Image, File, Code, Text, Audio }` (`icon()`).
  - O nome é cortado no meio, e o progresso aparece como uma barra de 2 dp na base.
- `MicButton(state: MicState, onClick, modifier, level = 0f, size = 44.dp, onLongPress?, haptics)`
  - `MicState { Idle, Listening, Processing, Disabled }`.
  - `level` (0..1) controla o halo, que é desenhado fora dos limites do botão e não ocupa layout.
  - O anel de "respiração" é desligado com movimento reduzido.
- `VoiceWave(level, modifier, color = accent, bars = 32, active = true, height = 28.dp)`
  - Mostra um histórico rolante amostrado a cada 60 ms.
  - Com movimento reduzido, vira um perfil estático escalado pelo nível.
- `EffortSlider(levels: List<String>, selectedIndex, onSelect, modifier, providerId = "claude", enabled = true, haptics)`
  - Trilha em gradiente da paleta de esforço; o polegar anda com mola.
  - Aceita toque e arraste, com um `Tick` por nível.

### Cota

- `QuotaBar(fraction, modifier, label?, valueText?, resetText?, providerId?, color?, height = 6.dp)`
  - Anima com mola e expõe semântica de progresso.
- `QuotaMeter(fraction, modifier, width = 40.dp, color?)`: versão compacta.
- `quotaColor(fraction)` devolve ok abaixo de 0,6, warn abaixo de 0,85 e danger acima disso.

### Estrutura de tela

- `AiStackTopBar(title, modifier, subtitle?, navigationIcon = Lucide.Menu, navigationContentDescription, onNavigationClick?, providerId?, status: AgentStatus?, scrolled = false, windowInsets = statusBars, haptics, titleContent?, actions: RowScope.() -> Unit)`
  - Tem 56 dp de altura.
  - Com `scrolled = true`, ganha fundo `sidebar` e linha inferior.
- `ConnectionBanner(state: ConnectionState, modifier, detail?, onRetry?, onRepair?, showWhenConnected = false)`
  - `ConnectionState { Connected, Connecting, Reconnecting, Offline, HostOffline, AuthRejected }`.
  - Expande e recolhe, e é uma live region para acessibilidade.
  - O botão "Parear de novo" aparece em `AuthRejected`; "Tentar agora" aparece em `Reconnecting`,
    `Offline` e `HostOffline`.
- `EmptyState(title, modifier, illustration: Illustration? = null, body?, accent = accent, illustrationWidth = 160.dp, art?, primaryAction?, secondaryAction?)`

---

## 4. Ilustrações (`designsystem.illustrations`)

`enum Illustration { NoSessions, NoPending, Offline, Pairing }` e
`IllustrationImage(illustration, modifier, width = 160.dp, tint = fg, accent = accent, contentDescription?)`.

Cada ilustração tem **duas camadas** monocromáticas (preto com alpha):

- `ill_*` é a camada neutra;
- `ill_*_accent` é a camada de destaque.

As duas são tingidas com `ColorFilter.tint` (SrcIn), uma com `fg` e a outra com `accent`. Assim a mesma
arte funciona nos temas claro e escuro e com a cor de qualquer provedor. Proporção 4:3, viewport 160×120.

---

## 5. Recursos Android

| Recurso | Conteúdo |
|---|---|
| `drawable/ic_brand_mark`, `avd_brand_assemble`, `avd_brand_pulse` | marca estática e AVDs |
| `drawable/ic_launcher_{background,foreground,monochrome}` + `mipmap-anydpi-v26/ic_launcher(_round)` | ícone adaptativo, com camada monocromática para ícones temáticos (Android 13+) |
| `drawable/ic_stat_aistack` | ícone de notificação (branco) |
| `drawable/ic_provider_*` | claude, codex, gemini, kimi, deepseek, glm, qwen |
| `drawable/ic_lucide_*` | subconjunto Lucide para notificações e Views |
| `drawable/ill_*` | ilustrações (seção 4) |
| `font/` | inter, source_serif_4, jetbrains_mono (variáveis) |
| `values*/colors.xml` | `aistack_bg`, `aistack_surface`, `aistack_fg`, `aistack_accent`, `aistack_splash_bg`, `aistack_notification` |
| `values*/themes.xml` | `Theme.AiStack` (platform `Theme.Material.*.NoActionBar`) |
| `assets/licenses/` | OFL das fontes e ISC do Lucide |

Sobre os temas de janela:

- `Theme.AiStack` define `windowBackground` igual ao `bg` do tema, o que evita flash na abertura.
- As barras de status e navegação são transparentes, com ícones claros ou escuros em `values-v27` e
  `values-night(-v27)`.
- A splash do Android 12+ usa `windowBackground` e o ícone do launcher, sem override em v31.

---

## 6. Catálogo

`br.com.amberwrite.aistack.ui.designsystem.DesignCatalogScreen(onBack: (() -> Unit)? = null)`

Tela autônoma, que aplica o próprio `AiStackTheme`, com:

- **Na barra superior:** alternância claro/escuro e de movimento reduzido.
- **Seções:** chips de cor de destaque por provedor, marca e AVDs, cores, tipografia, formas,
  provedores, status e badges, botões e chips, cartões, streaming, ferramentas, permissão e pergunta,
  subagentes, sugestões, anexos e voz (com o microfone simulando nível), cota e esforço, conexão,
  estados vazios e a grade com todos os ícones Lucide.

A1 registra a rota de debug `designCatalog`:

```kotlin
composable("designCatalog") { DesignCatalogScreen(onBack = navController::popBackStack) }
```
