# Contrato da Onda 2 — interfaces fixas entre F1 a F5

Vale para os cinco agentes da Onda 2. O que está aqui não muda sem o coordenador. A API do núcleo está em `07-core-api.md`; o fio, em `05-contrato-v2.md`.

## 1. Rotas e deep links (dono: F1; já aplicados)

- `Routes.chat(id, mention = null)` → `chat/{id}?mention={mention}`. O `mention` é um caminho absoluto ou relativo ao projeto; o `ChatScreen` o recebe como `initialMention` e repassa ao `ChatComposer`.
- `Routes.files(convId, path)`, `Routes.fileView(convId, path)`, `Routes.PENDING`, `Routes.ACCOUNTS`, `Routes.SETTINGS`, `Routes.DEVICES`, `Routes.NEW_SESSION`, `Routes.SESSIONS`, `Routes.PAIR` e `Routes.DESIGN_CATALOG` (só debug) não mudam de assinatura.
- Deep links aceitos (`DeepLink.parse`, manifest com intent-filter para cada um):
  - `aistack://chat/{id}` → `DeepLink.Chat` (toque em notificação de conversa);
  - `aistack://pending` → `DeepLink.Pending` (notificação agregada de pendências);
  - `aistack://pair?…` e os links http(s) do `PairLink` → `DeepLink.Pair`.
- Assinaturas das telas chamadas pelo NavHost: F1 pode acrescentar parâmetros com valor padrão, mas não remover nem renomear os existentes. Quem precisar de um callback novo de navegação acrescenta-o com padrão (`= {}`) na própria tela e registra em «pendencias» para o F1 ligar; o F1 liga todos os callbacks que encontrar ao fim.

## 2. Composer (dono: F3)

```kotlin
// feature/chat/composer/ChatComposer.kt
@Composable
fun ChatComposer(
    conversationId: String,
    busy: Boolean,
    online: Boolean,
    onInterrupt: () -> Unit,
    modifier: Modifier = Modifier,
    initialMention: String? = null,
)
```

- O composer é dono do rascunho, anexos, paletas «/» e «@», ditado, seletor de modelo/esforço e do envio (`chatRepo.send`, `queue` com `busy`, `sendNow`). Tem o próprio `ComposerViewModel` (`containerViewModel(key = "composer:$id")`).
- O F2 chama `ChatComposer` no rodapé do chat e remove do `ChatViewModel` o rascunho e o envio. A fila (chips acima do composer, com `unqueue`) e os cartões de permissão/pergunta continuam no F2.
- `initialMention` é inserido uma única vez por valor (`@caminho `).

## 3. Notificações (dono: F5)

- Interface `service/Notifier.kt` e `NotificationCoordinator` são do F5; outras features não postam notificações direto.
- Toque em notificação de conversa: `aistack://chat/{Uri.encode(id)}`; resumo do grupo de pendências: `aistack://pending`.
- O `ConnectionService` (Onda 1) só recebe a ligação mínima que o F5 precisar.

## 4. Propriedade de arquivos

| Agente | Arquivos (sob `app/src/main/java/br/com/amberwrite/aistack/`) |
|---|---|
| F1 | `feature/pair/**`, `feature/sessions/**`, `feature/newsession/**`, `navigation/**`, `MainActivity.kt` |
| F2 | `feature/chat/ChatScreen.kt`, `feature/chat/ChatViewModel.kt`, `feature/chat/thread/**` (novo), `feature/chat/markdown/**` (novo); edições aditivas em `ui/designsystem/components/QuestionCard.kt` e `PermissionCard.kt` só se o multiSelect exigir |
| F3 | `feature/chat/composer/**` |
| F4 | `feature/files/**`, `feature/fileview/**`, `feature/accounts/**`, `feature/settings/**`, `feature/devices/**` |
| F5 | `feature/pending/**`, `service/**` (exceto a ligação mínima no `ConnectionService`) |

Compartilhados, só com edições aditivas e pequenas (releia antes de editar): `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `res/values/strings_<feature>.xml` (um arquivo por feature). `core/**`, `data/**` e `ui/designsystem/**` ficam congelados; se faltar algo no repositório, acrescente uma função nova (não altere as existentes) e descreva no resumo.
