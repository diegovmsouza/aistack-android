# 07 — API do núcleo Android (core, data, service, feature)

Referência para quem mexe nas telas ou no núcleo do app. O contrato de fio é o `05-contrato-v2.md`, que prevalece em caso de conflito. Este documento descreve **como o app o implementa** e onde cada coisa mora.

Pacote base: `br.com.amberwrite.aistack`.

## 1. Mapa de pacotes

| Pacote | Conteúdo |
|---|---|
| `core.crypto` | `CryptoEngine` (X25519, HKDF, AEAD, base64url), `DeviceIdentity` (par de chaves do aparelho), `KeystoreCipher` (cifra a chave privada com uma chave do Android Keystore). |
| `core.relay` | `RelayConnection` (WebSocket, handshake E2E, reconexão), `ConnectionState`, `Backoff`, `NetworkMonitor`, `PairLink`, `RelayProtocol` (quadros JSON). |
| `core.rpc` | `RpcClient` (ids u64, timeouts, eventos), `RpcPart` (fragmentação `rpcPart`), `EventParser` + `HostEvent`/`EngineEvent`, `RpcException`, `JsonExt` (leitura tolerante de JSON). |
| `data.model` | Modelos de domínio (`Conversation`, `ChatState`, `ChatItem`, `PermissionRequest`, `Question`, `AccountStatus`, `PairedDevice`…) com `parse` próprios. |
| `data.store` | `PairingStore` (link pareado) e `SettingsStore` (preferências). |
| `data.repo` | Repositórios por assunto, `ChatReducer` (função pura) e `SyncCoordinator`. |
| `service` | `ConnectionService` (foreground), `Notifier`/`AndroidNotifier`, `NotificationCoordinator`, `NotificationActionReceiver`, `BootReceiver`, `NotificationChannels`. |
| `feature.*` | Telas Compose + ViewModels (`pair`, `sessions`, `newsession`, `chat`, `files`, `fileview`, `pending`, `accounts`, `settings`, `devices`) e utilitários em `feature.common`. |
| `navigation` | `AiStackApp` (NavHost), `Routes`, `DeepLink`. |
| `ui.designsystem`, `ui.icons`, `ui.theme` | Design system (dono: A2). O núcleo só consome. |

## 2. Injeção de dependências: `AppContainer`

Não há framework de DI. `AiStackApplication.container(context)` devolve o `AppContainer` único do processo, que cria e liga tudo:

- **Lojas:** `pairingStore`, `settingsStore`, `identity` (getter preguiçoso; pode tocar o Keystore, então leia fora da thread principal).
- **Transporte:** `rpc: RpcClient`, `connection: RelayConnection`, `connectionState: StateFlow<ConnectionState>`.
- **Repositórios:** `sessionsRepo`, `pendingRepo`, `chatRepo`, `filesRepo`, `accountsRepo`, `devicesRepo`.
- **Notificações:** `notifier: Notifier`.
- **Estado de interface:** `isForeground`, `visibleChat` (conversa aberta na tela; suprime notificações dela). Use `setVisibleChat(id)` e `clearVisibleChat(id)`.
- **Ações:**
  - `connectSaved()` liga com o link salvo.
  - `retry()` força uma tentativa já.
  - `awaitOnline(timeoutMs)` espera até ficar online.
  - `pair(link)` pareia.
  - `setKeepConnected(v)` grava a preferência e inicia ou para o serviço.
  - `renameDevice(name)` renomeia o aparelho.
  - `suspend unpair()` desconecta, limpa as lojas e os repositórios e revoga o aparelho quando possível.

**Ciclo de vida.** O container observa o `ProcessLifecycleOwner`:

- Em primeiro plano, `connectSaved()` roda e o `ConnectionService` sobe se `keepConnected` estiver ligado.
- Em segundo plano, a ligação é encerrada se `keepConnected` estiver desligado.

A `MainActivity` não inicia conexão.

## 3. Transporte (`core.relay`, `core.rpc`)

### 3.1 Conexão

`RelayConnection` faz o handshake do contrato v2 §17. O `hello` leva `caps:["frag"]`. O código de pareamento sai uma única vez; depois vale só `keyAuth`, com o id RPC 1 reservado. Os estados são expostos por `ConnectionState`. A reconexão usa `Backoff`:

- espera exponencial de 1 s até 60 s;
- *equal jitter*, sorteado entre 50% e 100% do teto;
- `reset()` ao ficar online ou quando a rede volta.

O `Listener` recebe `onPaired(link sem código)` e `onRevoked()`.

### 3.2 RPC

`RpcClient`:

- `suspend call(method, params, timeoutMs)` devolve o `result` (`JsonElement`) ou lança `RpcException`.
- Os tipos de falha são `REMOTE`, `TIMEOUT`, `DISCONNECTED`, `FRAGMENT`, `UNKNOWN_METHOD` e `NOT_ALLOWED`.
- `Throwable.userMessage` dá um texto pronto para a interface.
- `events: SharedFlow<HostEvent>` publica os eventos do host já interpretados.

### 3.3 Fragmentação (`RpcPart.kt`)

Constantes em `RpcFrag`: `THRESHOLD` 60 000, `CHUNK` 45 000, `MAX_TOTAL` 8 388 608, `MAX_PARTS` 187, `TIMEOUT_MS` 30 000 e `MAX_INFLIGHT` 4.

- **Envio (`RpcFragmenter`):** `shouldFragment` só retorna verdadeiro com o eco de `frag` e acima do limiar. `split(id, json)` gera os quadros em ordem de `seq`, com `last` no final.
- **Recebimento (`RpcPartAssembler`):** classe pura com relógio injetável.
  - `accept(...)` devolve `Incomplete`, `Complete(id, frame)`, `Failed(id, msg, timeout)` ou `Ignored`.
  - Um id que falhou fica "envenenado": os pedaços seguintes são ignorados até o `last`.
  - `sweepExpired()` falha as remontagens que passaram de 30 s.

### 3.4 Eventos

`EventParser.parse(name, payload)` **nunca lança**. O que não reconhece vira `HostEvent.Unknown` ou `EngineEvent.Unknown`. Um `conv-event` vira `HostEvent.Conv(conversationId, turn, event: EngineEvent, truncated)`.

### 3.5 Datas

`JsonElement.asEpochMillis()` e `JsonObject.epochMillis(key)` aceitam:

- número em ms;
- número em texto;
- RFC 3339 / ISO-8601, com fração e fuso.

Isso atende o contrato v2 §15 e o `Block.createdAt`.

## 4. Dados (`data.store`, `data.repo`)

### 4.1 Lojas

- **`PairingStore`:** `link: StateFlow<PairLink?>`, `isPaired`, `save(link)`, `clear()`. O link é salvo sem o código.
- **`SettingsStore`:** `settings: StateFlow<Settings>` e `update { it.copy(...) }`. Os campos são:
  - `startAtBoot` (padrão `false`);
  - `keepConnected` (padrão `true`);
  - `notifyDone` (padrão `true`);
  - `theme`: `"system"` (padrão), `"dark"` ou `"light"`.

### 4.2 Repositórios

Todos expõem `StateFlow` e são alimentados por `onEvent(HostEvent)`, que o `SyncCoordinator` chama a partir de `rpc.events`. Ao reconectar, o `SyncCoordinator` recarrega o que estiver aberto.

| Repositório | API principal |
|---|---|
| `SessionsRepo` | `state` (conversas, `includeArchived`, carregando/erro), `find(id)`, `reload()`, `setIncludeArchived`, `recentProjects()`, `getCatalog(provider)`, `listSlashCommands`, `createConversation(provider, projectPath, permissionMode, model)`, `rename`, `archive`, `fork`, `setConversationOptions`. |
| `ChatRepo` | `open(id)`/`close(id)` controlam a assinatura `full`/`summary`; o estado é `StateFlow<ChatState>`. Também `refresh`, `loadOlder`, `expandBlock`, `send`, `queue`, `sendNow`, `unqueue`, `interrupt`, `answerPermission`, `answerQuestion` (AskUserQuestion via `answers`), `dismissQuestion` (equivale a negar), `answerToolQuestion` (`ask_question` via `sendMessage` no formato `"{n}. {label}{: detalhe}"`), `openToolQuestion(state)` e `reattachAll()`. |
| `PendingRepo` | `items: StateFlow<List<PendingConversation>>`, `supported` (o host tem `listPending`), `reload()`, `answer(conv, req, decision): ActionResult`, `find`. |
| `FilesRepo` | `listDir(path?)` → `DirListing`; `readFile(path, maxBytes?)` → `FileContent.Text`/`Binary`; `saveUpload(bytes, name, mime)`. |
| `AccountsRepo` | `state` (contas, versão do desktop, MCP), `reload()`, `loadExtras()`. |
| `DevicesRepo` | `state` (aparelhos, status do relay), `currentDeviceId`, `reload()`, `revokeSelf()`. |

### 4.3 `ChatReducer`

`ChatReducer` é uma função pura, sem I/O e testada na JVM:

- **`apply(state, HostEvent)`:** filtra por `conversationId` e trata `QueueUpdate`, `EventTooLarge` e `Notice`.
- **`applyEngine(state, turn, EngineEvent)`:** monta os `ChatItem` ao vivo, com chaves `L:turn:…`.
- **Fim de turno** (`turnComplete`, `turnError` e `exited`) zera `busy`/`isStreaming`/`status`, limpa `pending` e marca as ferramentas `RUNNING` como `INTERRUPTED`.
- **Recarga:** `truncated` e `eventTooLarge` ligam `needsReload`.
- **Página do host:** `applyLatest` e `applyOlder` aplicam as páginas de `getConversation`, cujos blocos persistidos têm chave `B:turn:seq`.

## 5. Serviço e notificações (`service`)

- **`ConnectionService`:** serviço em primeiro plano do tipo `remoteMessaging`. Mantém a ligação viva e mostra a notificação de conexão.
- **`Notifier`:** interface implementada por `AndroidNotifier`. Métodos:
  - `showPermission(request, title, error?)` e `cancelPermission(conv, req)`;
  - `showProgress(busy)`;
  - `showDone(conv, title, text, isError)` e `cancelDone(conv)`;
  - `connectionNotification(state)`.
- **`NotificationCoordinator`:** observa eventos e repositórios e decide o que notificar. Não notifica a conversa em `visibleChat`, e só notifica "tarefa concluída" quando `notifyDone` está ligado.
- **`NotificationActionReceiver`:** botões Permitir/Negar da notificação (`ACTION_ANSWER`, extras `conversationId`, `requestId`, `decision`). Conecta se for preciso, com teto de 25 s.
- **`BootReceiver`:** sobe o serviço quando `startAtBoot` e `keepConnected` estão ligados.
- **Canais:** `aistack_connection`, `aistack_pending`, `aistack_progress`, `aistack_done`.
- **Toque na notificação:** abre `aistack://chat/{id}` com o id codificado por `Uri.encode`, via `AndroidNotifier.chatIntent`.

## 6. Navegação (`navigation`)

`MainActivity` (`singleTask`) só aplica o tema e hospeda `AiStackApp(deepLink, onDeepLinkConsumed)`:

- O tema vem de `settings.theme`: `"dark"`, `"light"` ou o do sistema.
- `AiStackTheme` envolve toda a interface.
- No Android 13 ou mais novo, a activity pede `POST_NOTIFICATIONS` uma vez se ainda não foi concedida.

### 6.1 Rotas (`Routes`)

| Rota | Tela | Observações |
|---|---|---|
| `pair` | `PairScreen` | Início quando não pareado. `onPaired` vai para `sessions` e limpa a pilha. |
| `sessions` | `SessionsScreen` | Início quando pareado. |
| `newSession` | `NewSessionScreen` | Ao criar, troca a si mesma por `chat/{id}`. |
| `chat/{id}` | `ChatScreen` | Se a pilha estiver vazia (aberto por notificação), voltar leva a `sessions`. |
| `files/{convId}?path=` | `FilesScreen` | Sem `path`, usa o `projectPath` da conversa. |
| `fileView/{convId}?path=` | `FileViewScreen` | Texto mono selecionável; binário mostra só informações. |
| `pending` | `PendingScreen` | Pendências de todas as conversas. |
| `accounts` | `AccountsScreen` | Contas, cotas e MCP. |
| `settings` | `SettingsScreen` | Desparear volta a `pair` e limpa a pilha. |
| `devices` | `DevicesScreen` | Aparelhos pareados. |
| `designCatalog` | `DesignCatalogScreen` | Só existe em build debug. |

Os argumentos são codificados com `Uri.encode`: use sempre `Routes.chat(id)`, `Routes.files(convId, path)` e `Routes.fileView(convId, path)`.

Se `pairingStore.link` virar `null` (despareado ou revogado), o app volta a `pair` e limpa a pilha.

### 6.2 Deep links (`DeepLink`)

Os deep links são tratados manualmente, não com `navDeepLink`, porque com `singleTask` eles chegam também por `onNewIntent`. `DeepLink.parse(texto)` é testável na JVM:

- **`aistack://chat/{id}` → `DeepLink.Chat`:** navega para o chat, só se o app estiver pareado.
- **`aistack://pair?…` → `DeepLink.Pair`**, usando `PairLink.parse`:
  - sem pareamento, abre `pair` com o link pré-carregado;
  - já pareado, um `ConfirmDialog` pede confirmação antes de trocar o pareamento.

## 7. Camada de telas (`feature`)

### 7.1 Convenções

- Cada tela tem um `ViewModel` que recebe o `AppContainer`. Crie-o com `containerViewModel(key) { container -> VM(container, …) }`, e use `key` quando houver argumentos.
- O estado sai como `StateFlow<UiState>`, coletado com `collectAsStateWithLifecycle()`.
- As ações são métodos do ViewModel. Erros de ação viram texto via `userMessage`.
- `ChatViewModel` chama `chatRepo.open` ao nascer e `close` em `onCleared`. A tela marca `visibleChat` com um `DisposableEffect`.

### 7.2 `feature.common.FeatureSupport`

- `FeatureScaffold(title, onBack?, modifier, subtitle?, actions, content)` monta a barra superior, aplica os *insets* e organiza o conteúdo em coluna.
- `CenteredLoading(modifier, text?)` e `ErrorStrip(message, modifier)`.
- `ConnectionState.toBanner()`, `bannerDetail()` e `label()` adaptam o estado para o `ConnectionBanner`.
- `relativeTime(epochMs?)` gera "há 5 min" ou "em 2 horas", aceitando datas futuras. `formatBytes(Long?)` formata tamanhos.

### 7.3 `feature.common.FeatureWidgets`

- `AiTextInput(value, onValueChange, …, mono)`.
- `SectionTitle(text)`.
- `SwitchRow(title, checked, onCheckedChange, description?, enabled)`.
- `ConfirmDialog(title, text, confirmLabel, onConfirm, onDismiss, dismissLabel = "Cancelar", destructive)`.

### 7.4 Perguntas no chat

- **`AskUserQuestion`** (pedido de permissão): `QuestionCard` em sequência, uma pergunta por vez. As respostas são enviadas em `answerQuestion`; "Pular" chama `dismissQuestion`, que equivale a negar.
- **`ask_question`/`AskFollowupQuestion`** (ferramenta): `QuestionCard` com a escolha, enviada como mensagem via `answerToolQuestion`.

## 8. Testes (JVM)

Os testes ficam em `app/src/test/java/br/com/amberwrite/aistack/` e rodam com `./gradlew testDebugUnitTest`.

| Teste | Cobre |
|---|---|
| `core/crypto/CryptoEngineTest` | Primitivas criptográficas. |
| `core/relay/PairLinkTest`, `RelayProtocolTest` | Link de pareamento e quadros. |
| `core/relay/BackoffTest` | Teto exponencial, faixa do jitter e `reset`. |
| `core/rpc/RpcPartTest` | Divisão, remontagem, fora de ordem, envenenamento, `MAX_PARTS`, `MAX_INFLIGHT` e tempo esgotado com relógio falso. |
| `core/rpc/EventParserTest` | Exemplos das specs 01/05 (textDelta, turnStarted, toolStart, toolResult truncado, subagentActivity, AskUserQuestion, turnComplete, eventTooLarge, desconhecidos). |
| `core/rpc/UpdatedAtTest` | `updatedAt` em ms, texto e RFC 3339. |
| `data/repo/ChatReducerTest` | Sequência completa de um turno, fim de turno, interrupção, filtro por conversa, permissões e `needsReload`. |
| `navigation/DeepLinkTest` | Links `chat` e `pair`. |
