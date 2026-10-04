# 00 — Plano: app Android AiStack (companheiro do desktop)

Fontes normativas: `01-protocolo.md` (fio), `02-features-desktop.md` (recursos e tokens), `03-android-atual.md` (código atual), `04-desktop-mudancas.md` (host). Este arquivo fecha as decisões em aberto e divide o trabalho em ondas sem conflito de arquivos.

## 1. Decisões (respondem às perguntas abertas de 01–04)
1. **Pareamento preservado.** `crypto/*`, a identidade X25519 persistente, os bytes dos hellos, o HKDF e o hostAuth não mudam. Só os defeitos de 03 §2.5 são corrigidos: keyAuth recusado ≠ ONLINE, o link só é gravado depois do sucesso, o `code` de uso único não é reenviado, o scanner volta a ler depois de um QR inválido e a PairScreen mostra os estados.
2. **Chave privada:** é cifrada com uma chave AES do Android Keystore, migrando a chave existente sem re-parear.
3. **Nonce no hello (R-173):** só o cliente muda (04 §f).
4. **Capacidades:** `Hello.caps:["frag"]` é opcional (`#[serde(default)]` no host). Antes de mudar o hello, conferir se o hostAuth assina os bytes do hello. Se assinar, o campo entra sem quebrar a assinatura, ou vai num quadro `Caps` depois do keyAuth. O agente do host decide e registra a escolha em `05-contrato-v2.md`.
5. **R-110:** a fase 1 vira erro sem derrubar a sessão. Depois vem `Tun::RpcPart{id,seq,last,data}` nos dois sentidos, em pedaços de ~45 000 B e com teto de ~8 MiB. Ele cobre o upload de câmera e microfone por `saveUpload`, sem RPC de upload nova. Por último, a paginação de `getConversation` e `getBlock`. O app trata o host antigo (sem `frag`) com mensagens de erro claras.
6. **Origem «mobile».** A migração no banco adiciona `origin` e `origin_device`. A origem vem do `Caller` da sessão autenticada no relay e nunca do cliente. Uma conversa bifurcada (fork) herda a origem de quem pediu o fork. Quem chama por HTTP/Tailscale continua local.
7. **Ícone de celular na sidebar do desktop:** SVG Smartphone (lucide) no topo do item, com tooltip «Iniciada no celular (nome do aparelho)». O desktop mantém o controle total.
8. **Sem FCM.** Um Foreground Service `remoteMessaging` mantém o túnel aberto, porque esse tipo não tem o teto de 6 h do dataSync. O app pede isenção de otimização de bateria (opcional). A reconexão usa backoff exponencial com jitter de 1 s a 60 s e volta a 1 s quando a rede volta.
9. **Pendências e retomada:** `listPending` e `subscribe` (locais da sessão), com `resync` em Lagged, conforme 04 §c. O app chama `listPending` e `listConversations` ao conectar e ao receber `resync`.
10. **Sub-agentes:** evento ao vivo `subagent` (`SubagentActivity`, 04 §d), sem persistir nos blocos nesta versão. No histórico, o app reconstrói o sub-agente pelas ferramentas Task/Agent (toolStart, toolInput, toolResult).
11. **Perguntas estruturadas:** tratar `permissionRequest` com tool `AskUserQuestion`, `ask_question` ou `AskFollowupQuestion` como cartão de pergunta com opções. A resposta usa o mesmo `answerPermission`; o formato está em 01.
12. **Política remota:** `REMOTE_ALLOWED`, `REMOTE_DENIED` e o teste de contrato (04 §h). O modo `bypassPermissions` é negado ao celular: o host o rebaixa para `default` e avisa. `pty*`, `bws*`, `cdp*`, `gitDiscard`, `installCli` e `deleteConversation` ficam negados.
13. **Explorador de arquivos:** `listDir` e `readFile` com escopo: projetos conhecidos e `extraDirs` da conversa, até 1 MiB por arquivo, e binário vira um aviso.
14. **projectPath remoto** em `createConversation`: `recentProjects` mais as pastas alcançáveis por `listDir` dentro desse escopo.
15. **Aparelhos:** o celular lista os aparelhos e revoga só a si mesmo («Desparear»). Revogar os outros continua no desktop.
16. **Slash:** `listSlashCommands` liberada (04 §g). Os comandos locais do desktop (`/btw`, `/grill-me`) aparecem marcados como «só no desktop» ou são omitidos.
17. **Microfone:** ditado com `SpeechRecognizer`, em pt-BR com parciais ao vivo, como no desktop. A gravação de áudio como anexo, via `saveUpload` com frag, é segunda prioridade.
18. **DI manual** (`AppContainer`), sem Hilt. **Nome do aparelho:** `Build.MODEL` por padrão, editável no pareamento.
19. **Toolchain:** compileSdk e targetSdk 36 (o emulador é API 37; Live Updates/ProgressStyle exigem 36) e minSdk 26. Atualizar Kotlin, Compose BOM e AGP só se o build continuar verde. Começa com os pacotes declarados e sem uso; proguard-rules.pro passa a existir.
20. **Agendamento de mensagens** (o timer do desktop) fica fora desta versão.

## 2. Arquitetura Android
Pacotes em `br.com.amberwrite.aistack` (03 §4.3):
- `core/crypto`: intocado, salvo a cifra da chave privada pelo Keystore.
- `core/relay`: `RelayClient` com backoff e estados `Connecting/Handshaking/Online/Offline/AuthRejected/HostOffline`.
- `core/rpc`:
  - `RpcClient.call<T>(method, params, timeout=20s)`, com a reconstrução de `RpcPart`;
  - `EventStream: SharedFlow<HostEvent>` com os 15 EngineEvent e os sintéticos (`conversations-changed`, `resync`, `subagent`, `mcp-update`...).
- `data/model`:
  - `Conversation` (com `origin`), `Block` (todos os tipos), `HostEvent` (sealed), `PendingItem`, `Account`, `SlashCommand`, `DirEntry`;
  - `updatedAt` aceita ms e RFC3339.
- `data/repo`: `SessionsRepo`, `ChatRepo` (estado por conversa, filtro por `conversationId`), `PendingRepo`, `FilesRepo`, `AccountsRepo` e `DevicesRepo`, todos em `StateFlow`.
- `data/store`: `PairingStore`, com EncryptedSharedPreferences e Keystore.
- `service`:
  - `ConnectionService` (FGS `remoteMessaging`), dono único da conexão;
  - `Notifier` com os canais `pending` (alta prioridade, ações), `progress` (Live Update) e `done`;
  - `NotificationActionReceiver`, cuja resposta só confirma na notificação depois do `rpcResult` ok e, se falhar, reposta com erro.
- `feature/*`: pairing, sessions, newsession, chat (`thread/`, `composer/`), files, pending, accounts e settings. Cada uma tem `XxxScreen` + `XxxViewModel`.
- `ui/designsystem` e `ui/icons`.
- `AppContainer` e `MainActivity` (só NavHost) mais `AiStackApp` (NavHost com rotas fixas, §4).

Regra de UI: `MainActivity` não guarda estado; os ViewModels leem os repos, e os repos leem o `RpcClient`/`EventStream` do `AppContainer`.

## 3. Design e movimento (requisito: «extremamente bonito»)
- **Tokens** vindos de `02` (paleta OKLCH convertida para sRGB, claro e escuro, mais as cores dos 7 provedores e do EffortSlider). Tipografia: Inter (UI), Source Serif 4 (títulos de destaque) e JetBrains Mono (código), em `res/font` com a licença OFL junto. O tema segue o sistema e tem opção manual; Material You é opcional, desligado por padrão para preservar a marca.
- **SVG obrigatório:** toda imagem é VectorDrawable (`res/drawable/*.xml`) ou `ImageVector`, convertida dos SVG do desktop:
  - marca aistack (os três quadrados; o multiply é recriado com sobreposição de cores);
  - logos dos provedores (thesvg.org, os paths que já estão no desktop);
  - subconjunto de ícones lucide;
  - ilustrações de estados vazios e do onboarding;
  - ícone adaptativo e monocromático.
  Proibido usar PNG, JPG ou WebP para imagem do app.
- **Animações:**
  - `AnimatedVectorDrawable` da marca (pulso durante o streaming e montagem na abertura);
  - transições de navegação com shared elements (item da lista → cabeçalho do chat);
  - `AnimatedContent` e `animateItem` nas listas;
  - shimmer no texto em streaming, typing caret e cartões de ferramenta que expandem com mola;
  - sub-agentes com linha do tempo que «respira» enquanto rodam;
  - haptics em permitir/negar;
  - movimento reduzido respeitado (`ANIMATOR_DURATION_SCALE`).
- **Componentes:** `StatusDot` (online, ocupado, pendente, erro), `ProviderBadge`, `OriginBadge` (celular), `GlassCard`, `ShimmerText`, `ToolCard`, `PermissionCard`, `QuestionCard`, `SubagentTimeline`, `SlashPalette`, `MentionPopup`, `AttachmentChip`, `MicButton` (onda reativa ao nível do áudio), `EmptyState` (SVG) e `BottomSheet`s.
- Layout adaptativo para telas grandes (Pixel 10 Pro XL e dobráveis): em largura expandida, lista e chat ficam lado a lado (list-detail).

## 4. Telas e rotas (NavHost fixo)
`pair` · `sessions` (início) · `newSession` · `chat/{id}` · `files/{convId}?path=` · `fileView/{convId}?path=` · `pending` · `accounts` · `settings` · `devices`.

1. **Pareamento:** onboarding animado → QR (CameraX + ML Kit) ou link → estados ao vivo → sucesso com animação. Re-parear pede confirmação.
2. **Sessões:**
   - busca, filtro por projeto, grupos por data;
   - cada item mostra provedor, título, projeto, `StatusDot` ao vivo (ocupado ou pendente), `OriginBadge` e prévia da última mensagem;
   - deslizar para arquivar ou renomear (os RPCs permitidos);
   - FAB «Nova sessão»;
   - topo com o estado da conexão e o número de pendências.
3. **Nova sessão:** projeto (recentes + navegar), provedor e modelo (ModelPicker), esforço (slider com as cores do desktop), modo de permissão (sem bypass) e primeira mensagem. Ao criar, abre o chat; a sessão aparece no desktop com o ícone de celular.
4. **Chat:**
   - thread com Markdown completo e código realçado;
   - thinking recolhível;
   - cartões de ferramenta com entrada e resultado;
   - sub-agentes (timeline aninhada sob a ferramenta Task, mais o painel «Agentes»);
   - permissões e perguntas inline;
   - fila (queue, sendNow, unqueue) e botão de interromper (`interrupt`);
   - erro de turno visível; `isStreaming` reseta em TurnComplete, TurnError e Exited.
   - Trocar de conversa não interrompe nada no host.
5. **Composer:**
   - `/` abre a SlashPalette (`listSlashCommands`);
   - `@` abre o MentionPopup (`listDir`);
   - anexos por câmera (CameraX), galeria (Photo Picker) ou arquivo (SAF), enviados por `saveUpload` com frag;
   - microfone para ditado;
   - seletor rápido de modelo e esforço.
6. **Explorador:** navegação por `listDir` com breadcrumbs, visualizador com realce e números de linha, e «Mencionar no chat».
7. **Pendências:** caixa de entrada de todas as permissões e perguntas de todas as conversas (`listPending` + eventos), com resposta direta.
8. **Contas e cotas:** `listAccounts` e o estado dos slots, com barras de cota animadas.
9. **Ajustes e aparelhos:** tema, notificações, isenção de bateria, nome do aparelho, relay e Desparear.

**Notificações em tempo real:**
- turno em andamento numa notificação Live Update (ProgressStyle, com o nome da ferramenta atual, sub-agentes e cronômetro);
- permissão e pergunta com as ações Permitir, Negar e Responder (RemoteInput, que vira a resposta da pergunta ou mensagem);
- turno concluído ou com erro;
- toque na notificação abre o deep link para o `chat/{id}`.

## 5. Ondas (≤5 agentes por workflow, effort high; os arquivos de cada agente não se sobrepõem)
**Onda 1 — Fundação:**
- **A1 core:** `core/*`, `data/*`, `service/*`, `AppContainer`, NavHost com rotas e telas placeholder `feature/<x>/<X>Screen.kt`, correções P0 do cliente, testes JVM e build verde.
- **A2 design:** `ui/designsystem/*`, `ui/icons/*`, `res/drawable/*.xml` (SVG → vector), `res/font`, `res/values*`, ícone adaptativo, AVD da marca e a galeria de componentes `DesignCatalogScreen` (rota debug).
- **D1 host:** no worktree `AiStack/worktrees/mobile`, branch `feat/mobile-companion` a partir de `main`. Segue 04 §«Ordem de implementação» (itens 1 a 7) e o ícone na Sidebar.tsx, com `cargo test` e `tsc` verdes. Escreve `docs/spec/05-contrato-v2.md` (no repo do Android) com os nomes e formatos finais. **Nunca tocar em `src-tauri/src/window_persistence.rs`**, não commitar no `main` e não fazer deploy do relay.

**Onda 2 — Telas** (lendo `05-contrato-v2.md`):
- F1: pairing, sessions e newsession.
- F2: chat/thread (blocos, ferramentas, sub-agentes, permissões e perguntas, fila, interromper).
- F3: chat/composer (slash, @, câmera, galeria, arquivo, microfone, upload).
- F4: files, accounts e settings/devices.
- F5: pending, mais Notifier e Live Updates (service/notify), com os deep links.

**Onda 3 — Integração e testes:** build, lint e testes; instalar no `emulator-5554`; roteiro E2E com a câmera do emulador e o pareamento real; capturas de tela de cada tela em claro e escuro; revisão adversarial (protocolo, segurança, UX). Depois, as correções. O deploy do relay (F3-95) e o merge do branch do desktop ficam com o usuário.

## 6. Critérios de pronto
- Build e testes verdes.
- O app pareia (o fluxo atual continua funcionando).
- Lista e abre as sessões do desktop.
- Cria uma sessão que aparece no desktop com o ícone de celular.
- Faz streaming com ferramentas e sub-agentes.
- Responde a permissões pela notificação.
- Navega pelos arquivos e anexa uma foto da câmera.
- Dita pelo microfone.
- Todas as imagens são vetoriais.
- Tema claro e escuro.
- Sem travar com respostas acima de 64 KiB.
