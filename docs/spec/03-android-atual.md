# 03 — Estado atual do app Android (aistack-android)

> Levantamento somente leitura do repositório `/home/diego/Documents/aistack-android` (commit `b5bf16c`), feito em 2026-10-03. Os caminhos de código abaixo são relativos a `app/src/main/java/br/com/amberwrite/aistack/`, salvo quando indicado. O que não foi conferido está marcado como «não verificado». O app não foi instalado nem executado no emulador neste levantamento: só compilado e testado em JVM.

## 1. Versões e resultado do build

### 1.1 Toolchain e configuração

| Item | Valor | Fonte |
|---|---|---|
| Gradle (wrapper) | 8.12 | `gradle/wrapper/gradle-wrapper.properties:3` |
| Android Gradle Plugin | 8.8.0 | `build.gradle.kts:2` |
| Kotlin / plugin Compose | 2.0.21 / 2.0.21 | `build.gradle.kts:3-4` |
| namespace / applicationId | `br.com.amberwrite.aistack` | `app/build.gradle.kts` |
| compileSdk / minSdk / targetSdk | 35 / 26 / 35 | `app/build.gradle.kts:9,13,14` |
| versionName (versionCode) | 1.0.0 (1) | `app/build.gradle.kts:16` |
| Java / jvmTarget | 17 | `app/build.gradle.kts:37` |
| Release | `isMinifyEnabled = false`; aponta para `proguard-rules.pro`, que não existe no repositório | `app/build.gradle.kts:23` |
| Catálogo de versões | não existe `libs.versions.toml`; as versões estão fixas no `build.gradle.kts` | — |

### 1.2 Dependências

- **UI:** Compose BOM 2024.10.01 (`app/build.gradle.kts:52`), Material3, material-icons-extended, activity-compose 1.9.3, lifecycle 2.8.7, core-ktx 1.15.0, appcompat 1.7.0, material 1.12.0.
- **navigation-compose 2.8.3** (`app/build.gradle.kts:63`): declarada, mas nunca usada. Nenhum `NavHost` aparece no código; a navegação é feita com `if/else` em `MainActivity.kt:281-298`.
- **Câmera e QR:** CameraX 1.4.0 (`app/build.gradle.kts:66-70`) e ML Kit barcode-scanning 17.3.0 (`:71`).
- **Rede:** OkHttp 4.12.0 e Gson 2.11.0 (`:74-75`).
- **Criptografia:** BouncyCastle `bcprov-jdk18on` 1.78.1 (`:78`).
- **security-crypto 1.1.0-alpha06** (`:79`): declarada, mas não usada. Não há `EncryptedSharedPreferences` no código e a chave privada fica em texto puro (ver 2.4).
- **Testes:** JUnit 4.13.2, com BouncyCastle e Gson em `testImplementation` (`:83-84`).

### 1.3 Manifesto

Permissões declaradas:

- INTERNET e ACCESS_NETWORK_STATE;
- CAMERA, com `required=false`;
- RECORD_AUDIO;
- POST_NOTIFICATIONS;
- FOREGROUND_SERVICE e FOREGROUND_SERVICE_DATA_SYNC;
- VIBRATE.

O app usa `allowBackup=false`. A `MainActivity` é `singleTask` com `adjustResize` e tem dois filtros de intent: LAUNCHER e VIEW/BROWSABLE para `aistack://pair`. O `NotificationActionReceiver` não é exportado. O `AiStackTaskService` não é exportado e usa `foregroundServiceType="dataSync"`.

### 1.4 Tamanho do código

São cerca de 4.328 linhas de Kotlin (contadas com `wc -l` em código e testes). A auditoria do desktop cita 4.195 linhas (`AiStack/docs/auditoria/GAP-MOBILE.md:3`); a diferença vem de commits posteriores, como `b5bf16c` e `f47cf9e`.

| Arquivo | Linhas |
|---|---|
| MainActivity | 427 |
| ChatScreen | 410 |
| RelayClient | 339 |
| QrScannerScreen | 283 |
| BrandHero | 256 |
| DrawerContent | 235 |
| MarkdownText | 212 |
| CryptoEngine | 211 |
| ModelPickerSheet | 209 |
| PairScreen | 199 |
| ToolExecutionCard | 196 |
| PermissionCard | 132 |
| TaskNotificationManager | 122 |
| ThinkingCard | 116 |
| AiStackTaskService | 100 |
| Models | 88 |
| AiStackConnectionManager | 87 |
| DeviceIdentity | 77 |
| RelayProtocol | 69 |
| AiStackApplication | 59 |
| PairLink | 48 |
| Theme | 47 |
| NotificationActionReceiver | 37 |
| Color | 33 |
| Testes: CryptoEngineTest | 112 |
| Testes: PairLinkTest | 54 |
| Testes: RelayProtocolTest | 37 |

Recursos em `app/src/main/res`:

- `drawable/ic_launcher_background.xml` e `ic_launcher_foreground.xml` (vetores);
- `mipmap-anydpi-v26/ic_launcher*.xml`;
- `values/strings.xml` e `themes.xml`.

Não há nenhuma outra imagem. Os logos de provedor do `BrandHero` são círculos desenhados em `Canvas` (`ui/components/BrandHero.kt:197-253`), não SVG.

### 1.5 Resultado do build (executado)

Comando: `./gradlew assembleDebug testDebugUnitTest`.

- **Resultado:** BUILD SUCCESSFUL em 23 s, 43 tarefas acionáveis (8 executadas), código de saída 0.
- **APK:** `app/build/outputs/apk/debug/app-debug.apk`, cerca de 48 MB, sem minificação.
- **Testes:** 12 testes, 0 falhas:
  - `RelayProtocolTest`: 3;
  - `PairLinkTest`: 5;
  - `CryptoEngineTest`: 4 (acordo X25519, seal e open, rejeição de replay, hostAuth Ed25519).
- **Avisos de depreciação:**
  - `ui/screens/ChatScreen.kt:341`: `Icons.Filled.Send`, que deve virar `Icons.AutoMirrored.Filled.Send`;
  - `ui/screens/DrawerContent.kt:161`: `Divider`, que deve virar `HorizontalDivider`.

O que os testes não cobrem:

- vetor de teste cruzado com o host Rust (HKDF, transcript, quadro AES-GCM);
- `b64uDecode`, que depende de `android.util.Base64` e por isso não roda em JVM pura;
- a máquina de estados do `RelayClient`;
- teste instrumentado em aparelho ou emulador.

## 2. Fluxo de pareamento que funciona hoje

O pareamento já funciona, segundo o usuário. Esta seção descreve o fluxo como está no código e confere cada passo com o host desktop (`AiStack/src-tauri/src/remote_relay.rs` e `tunnel.rs`), para que a reescrita não quebre a compatibilidade.

### 2.1 Entrada do link

1. **Origens possíveis do link:**
   - QR lido pela câmera (`ui/scanner/QrScannerScreen.kt:96-101`, filtro em `:271-274`);
   - deep link `aistack://pair` (`MainActivity.kt:52-56` em `onCreate` e `:71-82` em `onNewIntent`);
   - texto colado no `PairScreen` (`ui/screens/PairScreen.kt:155-159`).
2. **`PairLink.parse()`** (`relay/PairLink.kt:13-32`):
   - aceita `aistack://pair?...` ou URL http/https;
   - decodifica a query com `URLDecoder` (`:34-41`);
   - exige `relay`, `host` e `pk`; `code` é opcional.

   O que cada parâmetro significa:
   - `host` é o hex do SHA-256 da chave Ed25519 do host;
   - `pk` é a X25519 estática do host. É salva, mas nunca é usada no handshake;
   - `code` é o código de uso único.
3. **`connectWithLink`** (`MainActivity.kt:92-96`):
   - grava `relay/host/pk` em SharedPreferences `aistack_remote_config` (`relay/AiStackConnectionManager.kt:37-44`). O `code` não é gravado;
   - cria o cliente (`AiStackConnectionManager.kt:52-60`).

   **Defeito:** o link é gravado antes de o pareamento dar certo.

### 2.2 Identidade do aparelho

`DeviceIdentity.getOrCreate()` (`crypto/DeviceIdentity.kt:29-75`) é chamado na primeira vez:

- gera um par X25519 com `SecureRandom` (`:43-44`);
- gera o id `dev-` + base64url de 6 bytes aleatórios (`:56`);
- grava id, nome, chave pública e chave privada em SharedPreferences `aistack_device_identity` (`:23`, `:61-72`), em texto puro;
- usa o nome padrão fixo `"Samsung Galaxy"` (`:29`).

Essa chave X25519 é a identidade persistente que o host registra no pareamento.

### 2.3 Handshake no relay (`relay/RelayClient.kt`)

| Passo | Cliente Android | Host desktop (conferido) |
|---|---|---|
| 0. Conexão | WebSocket em `${relay}/client?host=<hostId>&device=<deviceId>` (`RelayClient.kt:86-90`); OkHttp com ping de 20 s, connect de 15 s e read 0 (`:65-69`) | — |
| 1. Hello em claro | Em `onOpen`, envia o binário `{"t":"hello","k":<X25519 persistente b64url>,"aead":"a"}` e guarda os bytes exatos em `clientHelloBytes` (`:104-119`) | Lê o hello, usa `k` como chave do aparelho, gera uma efêmera e responde com hello (`remote_relay.rs:429-441`) |
| 1b. Chaves | Ao receber o hello do host, guarda `hostHelloBytes` e faz X25519(priv persistente, `k` efêmera do host) (`:167-182`), seguido de HKDF-SHA256 com salt vazio e infos `"aistack-tunnel-v1 c2h"` (envio) e `"aistack-tunnel-v1 h2c"` (recepção) (`crypto/CryptoEngine.kt:58-74`) | `tunnel.rs:170-175`: `expand_multi_info([HKDF_INFO, " h2c"])` com `HKDF_INFO="aistack-tunnel-v1"`; os rótulos coincidem |
| 2. hostAuth (cifrado) | Abre o quadro e monta o transcript `"aistack-host-auth:" ‖ clientHelloBytes ‖ hostHelloBytes` (`:184-204`). `verifyHostAuth` exige `sha256Hex(pk) == link.host`, sem diferenciar maiúsculas, e a assinatura Ed25519 válida (`CryptoEngine.kt:81-102`). Se falhar, lança `SecurityException` ("Possível MITM", `:209`) | Assina o mesmo transcript (`remote_relay.rs:447-454`) |
| 3a. Pareamento (com `code`) | Envia selado `{"t":"pair","code","device":{id,name,pk}}` (`:215-226`); com `pair_result ok` vai a ONLINE e ao passo 5 (`:240-251`), senão lança "Pareamento recusado" | Usa a chave do hello, não o `pk` do JSON (`remote_relay.rs:460-476`) |
| 3b. Reconexão (sem `code`) | RPC `keyAuth {pk}` com o id guardado em `keyAuthRpcId` (`:227-237`); como o contador começa em 1 (`:62`), o id costuma ser 1 | Exige `pk == k` do hello e o aparelho ativo e não revogado. Responde `RpcResult` com o id e, se o id não for 1, também com id 1. Se recusar, envia `RpcResult{error}` e encerra (`remote_relay.rs:478-499`) |
| 4/5. Túnel | `handleTunnelMessage` (`:262-304`): `rpcResult` completa o `CompletableDeferred` pendente; `event` vai para o `SharedFlow events`; `close` com `revoked` grava o erro e desconecta | RPCs passam por `api::dispatch`; eventos vêm do `broadcast_emit`. O host converte o `updatedAt` de `listConversations` para milissegundos (`remote_relay.rs:505-525`) |

**Perfil AEAD "a"** (`crypto/CryptoEngine.kt:106-211`), compatível com `AeadProfile::AesGcm` em `tunnel.rs:37-40`:

- AES-256-GCM;
- quadro `ctr(8, u64 BE) ‖ iv(12) ‖ ct+tag`;
- AAD igual ao contador;
- IV igual ao contador em 8 bytes seguido de 4 zeros;
- janela anti-replay de 256 e salto máximo de 4096, iguais a `REPLAY_WINDOW`/`MAX_SKIP` em `tunnel.rs:26-28`;
- `seal` e `open` com `@Synchronized` (`:133`, `:154`).

**Controle e reconexão:**

- O relay pode mandar o quadro de texto `{"type":"host_offline"}`, que leva o estado a DISCONNECTED (`:132-145`).
- A reconexão é fixa em 3 s, sem backoff (`:330-338`), e é disparada por `onClosed`/`onFailure` (`:147-162`).

### 2.4 O que preservar intacto na reescrita

1. **Formato do QR e do deep link:** `aistack://pair?relay=&host=&pk=&code=`, com aceitação de http/https (`PairLink.kt`).
2. **Identidade persistente X25519 e `device_id`:** o host autentica o aparelho pela X25519 do hello (`remote_relay.rs:486-490`). Se a chave mudar, o aparelho deixa de ser reconhecido.

   Para migrar a chave para o Keystore ou para `EncryptedSharedPreferences`, é preciso ler os valores atuais de `aistack_device_identity` e regravá-los. Gerar uma chave nova obriga a parear de novo.

   O X25519 do Android Keystore só existe a partir da API 31 e o minSdk é 26 («não verificado» no emulador). Por isso o caminho viável é cifrar a chave em repouso com uma chave AES do Keystore.
3. **Bytes exatos dos hellos:** o transcript usa os bytes serializados como foram enviados e recebidos. Não se pode re-serializar o hello depois de enviado.
4. **Derivação e quadro:**
   - infos HKDF `"aistack-tunnel-v1 c2h/h2c"` com salt vazio;
   - a ordem `Pair(send=c2h, recv=h2c)`;
   - o perfil `"a"`, o layout do quadro, a janela de 256 e o salto de 4096.
5. **Verificação do host:** `sha256(pk Ed25519) == host` mais a assinatura do transcript, antes de qualquer segredo do aparelho.
6. **Mensagens do túnel:**
   - `hello`, `hostAuth`, `pair`, `pairResult` (`ok`, `error`), `rpc` (`id: Long`, `method`, `params`), `rpcResult`, `event` (`event`, `payload`) e `close` (`reason`), definidas em `relay/RelayProtocol.kt`;
   - o RPC `keyAuth {pk}` como primeiro RPC na reconexão.
7. **Endpoint do relay:** `/client?host=&device=` e o quadro de texto `host_offline`.
8. **BouncyCastle** registrado como provider em `AiStackApplication.kt:26-27`, com `bcprov-jdk18on`.

### 2.5 Defeitos do fluxo de pareamento a corrigir sem mudar o protocolo

- **keyAuth recusado marcado como ONLINE.**
  - Causa: o `rpcResult` do keyAuth não está em `pendingRpcs`, então cai no ramo `else if` (`RelayClient.kt:278-283`), que ignora `error`.
  - Efeito: o host envia o erro e encerra a sessão (`remote_relay.rs:481-484`). O app mostra ONLINE por um instante, cai e reconecta a cada 3 s, em laço.
  - Conferido por leitura dos dois lados; não executado.
- **Link gravado antes do sucesso** (`MainActivity.kt:93`). Se o código for inválido ou expirar, a próxima abertura tenta `keyAuth` com um aparelho desconhecido e cai no laço acima.
- **Código de uso único reaproveitado.** `isPairing` só vira falso no sucesso (`RelayClient.kt:71`, `:245`). Se a conexão cair no meio, a reconexão automática reenvia o mesmo `code` («não verificado» o efeito no host).
- **PairScreen nunca mostra CONNECTING, HANDSHAKING nem erro.** Só aparece com `activeRelayClient == null` (`MainActivity.kt:289`), e `connectWithLink` cria o cliente na hora (`:94`). O usuário vai direto para o chat com o túnel ainda negociando. Os estados existem em `PairScreen.kt:171-191`, mas são inalcançáveis.
- **Scanner preso depois de um QR inválido.** `scanned = true` é marcado antes do `PairLink.parse` (`QrScannerScreen.kt:234-236` contra `:97-100`); se a análise devolver `null`, o scanner para sem aviso. O `cameraExecutor` (`:206`) nunca recebe `shutdown`.
- **Deep link re-pareia sem confirmação** (`MainActivity.kt:71-82`). É o F3-86 da auditoria.
- **RPC sem prazo** (`RelayClient.kt:314-328`). Se o host não responder (R-110, acima de 64 KiB), o `await()` fica pendurado para sempre. Os `pendingRpcs` também não são falhados quando a conexão cai.
- **Nonce do aparelho ausente no hello** (R-173, «PROVADO em F3-85» segundo `GAP-MOBILE.md:20`). Corrigir isso exige mudança coordenada com o host, o que é mudança de protocolo; fica fora do «preservar intacto» e precisa de versão.
- **Nome padrão fixo "Samsung Galaxy"** (`DeviceIdentity.kt:29`). Deve usar `Build.MODEL` ou o nome escolhido pelo usuário.
- **Parâmetro `pk` do QR sem uso.** Pode servir para fixar a chave do host, mas isso também é mudança de protocolo.

## 3. UI e serviços atuais, com o estado de cada um

### 3.1 Inventário

| Componente | O que faz hoje | Estado |
|---|---|---|
| `MainActivity.kt` (427) | Concentra todo o estado em `remember` dentro do composable (`:99-114`): conversas, contas, mensagens, conversa ativa, provedor, esforço e streaming. Também trata eventos, carga inicial e chamadas RPC | Funciona para o caminho feliz; não tem ViewModel nem sobrevive a mudança de configuração (rotação recria o estado e o coletor de eventos, «não verificado» em execução). Deve ser **reescrito** |
| Eventos ao vivo (`MainActivity.kt:116-205`) | Trata só `conv-event` com `TextDelta`, `ThinkingDelta`, `ToolStart`, `PermissionRequest` e `TurnComplete` | Lê `conversationId` (`:122`), mas **não filtra** pela conversa ativa: deltas de outra conversa entram na tela. Ignora `Ready`, `ToolInput`, `ToolResult`, `PermissionCancelled`, `RateLimitWait`, `RateLimit`, `Status`, `TurnError`, `Exited` e `McpStatus` (`AiStack/src-tauri/src/engines/mod.rs:19-82`). Ignora também os eventos de topo `conversations-changed`, `accounts-update`, `usage-update`, `queue-update`, `notice`, `devices-changed` e `relay-status` (`AiStack/src-tauri/src/runtime.rs:26-33`, `remote_relay.rs:32-34`) |
| Carga inicial (`MainActivity.kt:208-279`) | `listConversations`, que aceita `updatedAt` em milissegundos ou ISO; abre só a primeira conversa com `getConversation` | O histórico só entende os blocos `user`, `text` e `thought`. Ferramentas, permissões e diffs do histórico se perdem. Falhas são engolidas por `catch (_: Exception) {}` (`:273`) |
| `ui/screens/PairScreen.kt` (199) | BrandHero animado, botão "Escanear QR Code no PC", campo de link manual e estados de conexão | Visual pronto; os estados de conexão nunca aparecem (2.5). **Reaproveitar** com ajustes |
| `ui/scanner/QrScannerScreen.kt` (283) | Pede permissão de câmera; CameraX com Preview e ImageAnalysis (KEEP_ONLY_LATEST) mais ML Kit; retículo com linha animada por `rememberInfiniteTransition` (`:155-156`) | Funciona com QR válido; fica preso com QR inválido e vaza o executor. **Reaproveitar** com correção |
| `ui/screens/ChatScreen.kt` (410) | TopBar com título, chip de provedor e ponto de estado do relay (`:150-190`); lista com rolagem automática (`:125`); campo "Comande o AiStack..." (`:284`); ditado por `RecognizerIntent` (`:114`, `:263-275`); Stop quando em streaming (`:301-313`) e Send (`:320-341`); abre o ModelPickerSheet (`:354`) | Sem anexos, câmera, `/comandos`, menção de arquivo, fila nem sub-agentes. O microfone usa o reconhecedor do sistema, não grava áudio. **Reescrever** a composição e reaproveitar peças visuais |
| `ui/screens/DrawerContent.kt` (235) | "Nova conversa" (`:99-104`), lista de conversas, bloco de contas com barra de uso e cor por limiar (`:173-180`), "Desconectar" (`:222`) | A lista de contas nunca é preenchida, porque ninguém chama `listAccounts`. Nova conversa não interrompe o turno em curso (`MainActivity.kt:339-343`). Não tem busca, renomear, arquivar, projeto nem indicador de origem (celular). **Reescrever** |
| `ui/components/ModelPickerSheet.kt` (209) | `ModalBottomSheet` com os 7 provedores (`:99`) e slider de esforço low, medium, high, max (`:72-73`) | Muda só o estado local (`MainActivity.kt:415-418`) e não chama `setConversationOptions`. Não lista modelos reais. Pode **reaproveitar** o visual |
| `ui/components/MarkdownText.kt` (212) | Parágrafos, bloco de código com botão de copiar, negrito, itálico e código em linha (`:126-200`) | Sem título, lista, tabela, link nem citação (`GAP-MOBILE.md:38`). Trocar por uma biblioteca de Markdown ou ampliar o parser |
| `ui/components/ThinkingCard.kt` (116) | Cartão expansível com `AnimatedVisibility` (`:94`) e rótulo de "pensando" durante o streaming | Funciona. **Reaproveitar** |
| `ui/components/ToolExecutionCard.kt` (196) | Cartão de ferramenta expansível (`:130`) | Fica girando para sempre porque `ToolResult` nunca chega ao modelo (`GAP-MOBILE.md:18`). **Reaproveitar** o visual e ligar o resultado e o diff (as cores `DiffAdd*` já existem em `Color.kt:30-33`) |
| `ui/components/PermissionCard.kt` (132) | Botões Permitir e Negar ligados a `onPermissionDecision` | A resposta usa `activeConvId` (`MainActivity.kt:420`), não o `conversationId` do evento: responder um pedido de outra conversa vai para o id errado. **Reaproveitar** e corrigir |
| `ui/components/BrandHero.kt` (256) | Marca animada com `Animatable` (translação, escala, rotação, `:57-160`) e "logos" de provedor desenhados como círculos em `Canvas` (`:197-253`) | Bonito, mas não usa SVG; os logos são aproximações. Trocar por `ImageVector`/VectorDrawable a partir de SVG, como pede o usuário |
| `ui/theme/Theme.kt` e `Color.kt` | Só `darkColorScheme` (`Theme.kt:13`); tokens de fundo, superfície, texto, provedores, estado e diff (`Color.kt:6-33`) | Sem tema claro nem tipografia própria. **Reaproveitar** os tokens como base do design system |
| `model/Models.kt` (88) | Enum `Provider` (claude, codex, agy, kimi, deepseek, glm, qwen), `ConversationItem`, `ChatBlock` (Text, Thinking, ToolCall, Permission), `ChatMessage`, `UsageWindow` e `AccountStatus` | Incompleto em relação às 15 variantes de evento e aos tipos de bloco do host. Reescrever como modelo de domínio |
| `service/AiStackTaskService.kt` (100) | Serviço em primeiro plano do tipo `dataSync`, com START, UPDATE e STOP; cronômetro de 1 s que atualiza a notificação (`:73-76`); `START_NOT_STICKY` (`:49`) | Iniciado com `startService`, não `startForegroundService` (`MainActivity.kt:175`, `:374`): em segundo plano no API 26+ pode lançar `IllegalStateException` («não verificado»). Não segura a conexão, que vive no processo e na Activity. O cronômetro zera a cada START. **Reescrever** como serviço de conexão persistente |
| `service/TaskNotificationManager.kt` (122) | Notificação contínua com id 1001 (`:15`); notificação heads-up de permissão com ações Permitir e Negar (`:60-100`) | O id da notificação de permissão é `2000 + hashCode % 1000` (`:96`) e pode ser negativo ou colidir. Texto fixo "Executando…". **Reaproveitar** a estrutura |
| `service/NotificationActionReceiver.kt` (37) | Recebe Permitir ou Negar e chama `AiStackConnectionManager.answerPermission` | Só funciona com o processo e o cliente vivos; se não estiverem, a resposta se perde em silêncio (`AiStackConnectionManager.kt:68-86`; `GAP-MOBILE.md:20`, F3-87) |
| `AiStackApplication.kt` (59) | Registra o BouncyCastle e cria os canais `aistack_tasks_channel` (LOW) e `aistack_permission_channel` (HIGH) (`:35-56`) | Funciona. **Reaproveitar** |
| `relay/AiStackConnectionManager.kt` (87) | Singleton com `connectWith`, `disconnect`, link salvo e `answerPermission` | Mistura persistência, ciclo de vida e RPC. Reescrever como repositório e serviço |

### 3.2 RPCs usados hoje

O app usa 7 RPCs:

| RPC | Onde | Situação |
|---|---|---|
| `keyAuth` | handshake | correto |
| `listConversations` | `MainActivity.kt:213` | correto |
| `getConversation` | `MainActivity.kt:251`, `:313` | correto |
| `createConversation` | `MainActivity.kt:380-386` | nasce com `projectPath:""` e sem modelo |
| `sendMessage` | `MainActivity.kt:395` | correto |
| `interruptConversation` | `MainActivity.kt:408` | **o método não existe no host**, que só conhece `interrupt` (`AiStack/src-tauri/src/api.rs:618`); o erro é engolido (`:409-411`) |
| `answerPermission` | `AiStackConnectionManager.kt:68-86` | correto |

### 3.3 Cruzamento com `GAP-MOBILE.md` §2 (P0)

| # | Defeito P0 da auditoria | Confirmação no código atual |
|---|---|---|
| 1 | Interromper não funciona: o app chama `interruptConversation` | **Confirmado:** `MainActivity.kt:408`; o host só tem `"interrupt"` (`api.rs:618`). Ainda presente |
| 2 | Eventos descartados e estado preso: `ToolResult`, `ToolInput`, `PermissionCancelled`, `TurnError` e `Exited` ignorados; `isStreaming` só volta a falso com `TurnComplete` | **Confirmado:** o `when` de `MainActivity.kt:127-200` só tem 5 ramos; `isStreaming = false` só aparece em `:197` e no erro de envio (`:398`). Ainda presente |
| 3 | Conversa grande trava a sessão (R-110); RPC sem prazo e `catch` vazio | **Confirmado do lado do app:** `RelayClient.call` sem timeout (`:314-328`); `catch (_: Exception) {}` em `MainActivity.kt:273` e `:335`. O lado do host (64 KiB) é «não verificado» aqui |
| 4 | Segurança do pareamento: nonce ausente no hello; deep link re-pareia sem confirmação; permissão pela notificação pode se perder; chave sem Keystore | **Confirmado nos quatro pontos:** o hello só tem `t`, `k` e `aead` (`RelayClient.kt:110-114`); `onNewIntent` chama `connectWithLink` direto (`MainActivity.kt:71-82`); `answerPermission` retorna em silêncio sem cliente (`AiStackConnectionManager.kt:69`); chave privada em SharedPreferences simples (`DeviceIdentity.kt:61-72`) |
| 5 | Reconexão fixa de 3 s sem backoff, `keyAuth` recusado marcado ONLINE, RPC sem prazo; FD preso no relay | **Confirmado no app:** `RelayClient.kt:330-338` e `:278-283`. O FD preso é do servidor relay, fora deste repositório, «não verificado» aqui. O commit `b5bf16c` passou a rastrear `keyAuthRpcId`, mas continua ignorando `error` |
| 6 | Trocar de conversa não para o turno | **Confirmado:** `onNewConversation` só zera o estado (`MainActivity.kt:339-343`); `onSelectConversation` (`:304-337`) também não interrompe |
| 7 | Gaveta enganosa: ponto sempre verde, contas nunca preenchidas | **Contas confirmadas:** `accounts` é criada vazia (`MainActivity.kt:108`) e nada a preenche. **Ponto verde: parcialmente divergente.** A cor da conta depende do uso (`DrawerContent.kt:176-180`); como a lista é vazia, nada aparece. O ponto de estado do relay no topo do chat varia com `relayState` (`ChatScreen.kt:168-190`). Não localizei um ponto "sempre verde" fixo na gaveta («não verificado» se a auditoria se referia a uma versão anterior) |

Defeitos encontrados neste levantamento e ausentes do §2 da auditoria:

- link gravado antes do sucesso do pareamento;
- PairScreen com estados inalcançáveis;
- scanner preso depois de um QR inválido;
- eventos não filtrados por conversa;
- `PermissionCard` responde com o `activeConvId`;
- `startService` em vez de `startForegroundService`;
- id de notificação negativo;
- `code` reaproveitado na reconexão durante o pareamento;
- `navigation-compose` e `security-crypto` declaradas e sem uso;
- `proguard-rules.pro` ausente.

### 3.4 Cobertura dos requisitos do pedido

| Requisito | Hoje |
|---|---|
| Notificações em tempo real das tarefas | Parcial: notificação contínua com texto fixo e cronômetro, só com a Activity viva |
| Notificar pendências do desktop (permissões) | Parcial: heads-up com ações, mas sem conexão em segundo plano e sem `PermissionCancelled` |
| Mostrar sub-agentes | Não existe; nenhum evento ou bloco de sub-agente é tratado |
| Comandos de barra (`/`) | Não existe |
| Explorador de arquivos | Não existe. O host tem `readFile`, `listWorkspaceFiles` e `scanFolder`, mas a auditoria pede política por método antes de abrir esses métodos (`GAP-MOBILE.md:43`) |
| Câmera | Só para ler QR; não há anexo de foto |
| Microfone | Só ditado por `RecognizerIntent` |
| Ler e interagir com sessões do desktop | Parcial: lista, abre e envia; o histórico perde ferramentas e o interromper falha |
| Iniciar sessão no celular que aparece no desktop com ícone de celular | Cria a conversa, que aparece no desktop porque usa o mesmo `dispatch`. **Não há marcação de origem:** `createConversation` não envia campo de origem (`MainActivity.kt:380-386`), e não verifiquei se o host aceita esse campo («não verificado»; depende do desktop) |
| Imagens em SVG | Não: só o ícone do launcher é vetor; logos desenhados em Canvas |
| Animações | Algumas: BrandHero, linha do scanner, `AnimatedVisibility` nos cartões, rolagem animada |

## 4. Recomendação

### 4.1 Reaproveitar como está, ou quase

| O quê | Por quê | Ajuste |
|---|---|---|
| `relay/PairLink.kt` | Formato compatível com o desktop, com testes | Nenhum |
| `relay/RelayProtocol.kt` | Mensagens do túnel conferidas com o host | Acrescentar só tipos novos |
| `crypto/CryptoEngine.kt` (HKDF, X25519, Ed25519, `TunnelSession`) | É o núcleo que já funciona e coincide com `tunnel.rs` | Remover o `init` não usado (`:59-61`); acrescentar vetores de teste gerados pelo host Rust |
| `crypto/DeviceIdentity.kt` | Identidade que o host já registrou | Cifrar a chave em repouso com uma chave AES do Keystore, migrando os valores existentes sem regenerar; trocar o nome padrão por `Build.MODEL` ou pelo nome escolhido |
| Handshake do `RelayClient` (`:81-260`) | Funciona | Extrair para uma classe `Handshake` testável, sem mudar bytes nem ordem |
| `AiStackApplication.kt` (provider BC, canais) | Funciona | Acrescentar canais (sub-agentes, conclusão, conexão) |
| Tokens de `Color.kt` e `Theme.kt` | Base do visual escuro | Acrescentar tema claro e tipografia |
| Visual de `ThinkingCard`, `ToolExecutionCard`, `PermissionCard`, `ModelPickerSheet` e `QrScannerScreen` (retículo) | Prontos e animados | Ligar a estado real (resultado da ferramenta, `conversationId`, `setConversationOptions`) |

### 4.2 Reescrever

- **`MainActivity.kt`:** fica como hospedeiro fino de `NavHost`, usando o `navigation-compose` já declarado. O estado vai para ViewModels com `StateFlow`.
- **Camada RPC tipada:** cada método com prazo (`withTimeout`), falha de todos os pendentes quando a conexão cai, nomes conferidos por teste de contrato com o host (`interrupt`, e não `interruptConversation`), e tratamento do `error` do `keyAuth`.
- **Reconexão:** backoff exponencial com jitter, reação a `ConnectivityManager.NetworkCallback` e um único dono da conexão.
- **Serviço de conexão persistente em primeiro plano:** substitui `AiStackTaskService`. Usa `startForegroundService` e mantém o túnel vivo enquanto houver turno em curso ou pendência. Mantém o tipo `dataSync`; o limite de tempo do `dataSync` no Android 15 é «não verificado».

  É por esse serviço que as notificações de permissão, conclusão e sub-agentes passam a funcionar com o app fechado. O receiver de ação de notificação passa a falar com o serviço e a confirmar a entrega.
- **Fluxo de eventos:** um despachante por `conversationId` que trate as 15 variantes de `conv-event`, mais os eventos de topo (`conversations-changed`, `accounts-update`, `usage-update`, `queue-update`, `devices-changed`, `notice`).
- **Pareamento (UI):** uma tela com estados reais (conectando, negociando, erro com nova tentativa) e confirmação antes de aceitar um deep link quando já existe pareamento. O link só é gravado depois de `pairResult.ok`. O scanner é reativado depois de um QR inválido.
- **Chat e gaveta:** chat com sub-agentes, comandos de barra, anexos (câmera, galeria, gravação de áudio), fila e Markdown completo. Gaveta substituída por uma tela de sessões com busca, projeto, provedor e selo de origem (celular), mais uma tela de contas e cotas.
- **Imagens:** logos e ilustrações passam a ser SVG convertidos em VectorDrawable ou `ImageVector`, eliminando os círculos desenhados em `Canvas`.

### 4.3 Estrutura de pacotes proposta

```
br.com.amberwrite.aistack
├── AiStackApplication.kt          (reaproveitado: provider BC + canais)
├── MainActivity.kt                (fino: tema + NavHost + deep link -> PairingViewModel)
├── core/
│   ├── crypto/                    CryptoEngine, TunnelSession, DeviceIdentity (+ KeystoreWrapper)  [INTACTO no protocolo]
│   ├── relay/                     PairLink, RelayProtocol, Handshake, RelayConnection (estado, backoff)  [handshake INTACTO]
│   └── rpc/                       RpcClient (prazo, cancelamento), RpcMethods (nomes conferidos por teste de contrato)
├── data/
│   ├── model/                     Conversation, Block (todas as variantes), ConvEvent (15 variantes), Account, Usage, Device, FileNode
│   ├── repo/                      ConversationRepository, EventRepository (dispatcher por conversationId),
│   │                              AccountRepository, FileRepository, PairingRepository (link salvo só após sucesso)
│   └── store/                     preferências (DataStore) e identidade cifrada
├── service/
│   ├── ConnectionService.kt       foreground service persistente (substitui AiStackTaskService)
│   ├── notifications/             canais, NotificationFactory, ações com confirmação de entrega
│   └── NotificationActionReceiver.kt
├── feature/
│   ├── pairing/                   PairScreen, QrScanner (CameraX/ML Kit), PairingViewModel
│   ├── sessions/                  lista/busca/filtros, selo de origem (celular), novo chat com projeto/modelo/esforço
│   ├── chat/                      ChatScreen, composer (slash commands, menção, anexos câmera/galeria/áudio),
│   │                              blocos (texto, pensamento, ferramenta+diff, permissão, pergunta), sub-agentes, fila
│   ├── files/                     explorador (depende da política por método no host)
│   ├── accounts/                  contas, cotas, failover
│   └── settings/                  aparelho, nome, revogar, tema
└── ui/
    ├── designsystem/              tokens (Color/Theme/Type), componentes base, motion (specs de animação)
    └── icons/                     ImageVectors gerados de SVG (provedores, ações, ilustrações)
```

Diretrizes:

- **Injeção de dependências** manual (um `AppContainer` na `Application`) ou Hilt. A escolha está em aberto.
- **Testes JVM** para `core/*` com vetores do host e para `data/repo` com um relay falso.
- **Testes instrumentados** de pareamento no emulador Pixel 10 Pro XL.

### 4.4 Ordem sugerida

1. **Corrigir sem mudar o protocolo:**
   - erro do `keyAuth`;
   - `interrupt`;
   - prazo de RPC;
   - link gravado só após o sucesso;
   - scanner;
   - `startForegroundService`.
2. **Extrair `core/` e `data/`**, mantendo a UI atual para validar que o pareamento continua funcionando no emulador.
3. **Construir as novas telas** sobre os ViewModels.
4. **Dependências do desktop:** campo de origem da sessão (ícone de celular), política por método para arquivos e terminal, e nonce no hello (protocolo v2).
