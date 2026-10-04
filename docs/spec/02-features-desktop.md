# 02 — Recursos da UI do desktop (AiStack) a espelhar no Android

> Fonte: leitura **somente-leitura** do repositório `/home/diego/Documents/AiStack` (Tauri: Rust em `src-tauri/src`, React + zustand + motion/react + Radix + lucide + Tailwind com tokens OKLCH).
> Todas as citações são `arquivo:linha` relativas a esse repositório. Linhas marcadas com `~` são aproximadas (±3). Tudo que não foi conferido no código está marcado **«não verificado»**.

---

## 0. Transporte comum (o que o Android já reutiliza)

| Item | Evidência | Nota mobile |
|---|---|---|
| Uma única função `call(method, params)` atende IPC Tauri (`invoke('rpc')`), relay E2E (`RelayClient.call`) e HTTP `POST /rpc/<method>` | `src/lib/api.ts:76-101` | O Android fala só o caminho relay → mesmo `api::dispatch`; nomes e formatos de parâmetros são idênticos aos do desktop. |
| Eventos assinados pelo front: `conv-event`, `usage-update`, `accounts-update`, `conversations-changed`, `notice`, `queue-update`, `pty-output`, `pty-exit`, `mcp-update`, `api-accounts-changed` | `src/lib/api.ts:103-114` | No relay, os eventos chegam por `relayClient.onEvent` (`api.ts:55-57,136-141`). |
| Eventos emitidos pelo backend mas **não** assinados pelo front: `relay-status`, `devices-changed` | `src-tauri/src/remote_relay.rs:32,34` | O app pode ignorar ou consumir. Se chegam pelo túnel ao celular: «não verificado». |
| Payload de `conv-event`: `{conversationId, turn, event: EngineEvent}` | `src/lib/types.ts:129-133` | Fonte de verdade para streaming, permissões, tarefas, sub-agentes e notificações. |
| `EngineEvent` (turnStarted, ready, textDelta[author?], thinkingDelta, toolStart[nested?], toolInput, toolResult, permissionRequest, permissionCancelled, rateLimitWait, rateLimit, status, turnComplete, turnError, exited, failover, steer) | `src/lib/types.ts:102-127` | Reproduzir o reducer de `useChat.enqueue` (`src/stores/chat.ts`). |
| Blocos persistidos: `user, text, thinking, tool, error, notice, steer, compacted, compact_boundary` | `src/lib/types.ts:77-83` | Vêm em `getConversation` → `{conversation, blocks, busy, queue}` (`src-tauri/src/api.rs:576-584`). |
| `Conversation`: id, provider, nativeSessionId, projectPath, title, model, effort, permissionMode, activeSlot, extraDirs, createdAt, updatedAt, archived. **Não há campo de origem nem de dispositivo.** | `src/lib/types.ts:56-70` | Ver a lacuna G1 (ícone de celular). |
| `Notice {level info\|warning\|error, message, provider?}` | `src/lib/types.ts:135-140` | Exibir como toast/snackbar. |
| Limite de upload `MAX_UPLOAD` = 25 MiB | `src-tauri/src/api.rs:209` | Vale para câmera, áudio e arquivos enviados via `saveUpload`. |

**Métodos do `dispatch`** (`src-tauri/src/api.rs`, linha do braço do `match`):

- **App e CLIs:** appInfo 459 · checkCli 481 · installCli 486 · updateCli 497 · updateAllClis 508
- **Contas:** listAccounts 525 · refreshAccounts 526 · setAccountLabel 534 · warmAccount 540 · setActiveSlot 545 · logoutAccount 551 · getCatalog 557
- **Conversas:** listConversations 561 · recentProjects 565 · getConversation 576 · createConversation 585 · sendMessage 599 · queueMessage 604 · sendNow 609 · unqueueMessage 614 · interrupt 618 · answerPermission 623 · setConversationOptions 628 · renameConversation 632 · archiveConversation 638 · deleteConversation 645 · forkConversation 650 · listSlashCommands 656 · switchConversationSlot 662
- **Sistema de arquivos e seletores:** isDirectory 667 · pickDirectories 671 · pickFolder 685 · readClipboardImage 694 · copyFile 715
- **Git:** gitInfo 725 · gitSwitch 729 · gitWorktreeCreate 734 · gitWorktreeDelete 742 · gitDiff 749 · gitCommit 754 · gitDiscard 759 · gitPushOrPr 764
- **Arquivos e anexos:** readFile 769 · openInEditor 790 · saveUpload 809 · scanFolder 818 · unpackZip 826 · cleanTempDir 831 · listAssets 836 · listWorkspaceFiles 846
- **MCP e contas de API:** listMcp 863 · setMcpEnabled 864 · listApiAccounts 869 · saveApiAccount 870 · removeApiAccount 875 · fetchApiModels 880 · addOwnMcp 897 · removeOwnMcp 902
- **Claude Chrome e busca:** getClaudeChrome 907 · setClaudeChrome 908 · searchConversations 913
- **PTY:** ptyOpen 917 · openExternalAgyLogin 934 · openExternalOauthLogin 939 · ptyWrite 944 · ptyResize 949 · ptyClose 954
- **Relay e dispositivos:** relayStatus 960 · relayPairInfo 964 · listDevices 993 · revokeDevice 997
- **Bitwarden (BWS):** bwsStatus 1010 · bwsSetToken 1014 · bwsDeleteToken 1019 · bwsSync 1023 · bwsListSecrets 1027 · bwsGetSecret 1031 · bwsCreateSecret 1036 · bwsDeleteSecret 1044 · bwsListProjects 1049
- **Chrome CDP:** cdpStatus 1055 · cdpLaunch 1059 · cdpOpenTab 1064 · cdpActivateTab 1069 · cdpCloseTab 1074

`login`/`shell` (921-922) parecem ser `kind` internos do `ptyOpen`, não métodos próprios («não verificado»).

---

## 1. Tabela-mestre de recursos

| # | Recurso | Componente desktop (arquivo:linha) | RPC / evento | Comportamento visível | Nota mobile |
|---|---|---|---|---|---|
| 1 | Bootstrap e eventos globais | `src/App.tsx:38-130` | todos os eventos de §0 | `conv-event` → `useChat.enqueue`. `toolStart`/`toolResult` criam e fecham tarefas. `failover` → reloadInfo. `turnComplete` → notificação do SO. `notice` → toast. `queue-update` → setQueue (`App.tsx:41-126`). | Serviço em foreground ou WorkManager mantendo o túnel e alimentando um store equivalente. |
| 2 | Sidebar: lista de conversas | `src/components/layout/Sidebar.tsx:684` (lista), 760-910 (linha) | `listConversations {includeArchived:true}`; recarrega em `conversations-changed` | Agrupar por projeto, data, status ("Em execução"/"Finalizadas") ou nenhum (`Sidebar.tsx:122-136,469-520`). Linha com ponto pulsante na cor do provedor quando ocupada e Loader2 girando (825-855); senão, tempo relativo. Ícone GitFork violeta em forks (835). Subtítulo: branch (`gitInfo`, 781) ou snippet da busca. Seção "Arquivadas · N" (696). | Lista com grupos colapsáveis. Ícone de celular por conversa **depende de backend novo** (G1). |
| 3 | Sidebar: ações da conversa | `Sidebar.tsx:797,860-910` | `renameConversation {id,title}` · `setConversationOptions {id, projectPath}` · `archiveConversation {id, archived}` · `deleteConversation {id}` | Renomear inline (Enter/Esc); menu "Opções da conversa" com mover pasta, arquivar/desarquivar e excluir. | Swipe actions + bottom sheet. Excluir exige confirmação (é irreversível). |
| 4 | Sidebar: busca | `Sidebar.tsx:541` | `searchConversations {query}` → `[{conversationId, snippet}]` | Destaca o termo no título e no snippet. | SearchBar no topo da lista. |
| 5 | Sidebar: nova pasta / projeto | `Sidebar.tsx:239,413-417,624,635` | `pickDirectories`/`pickFolder` (remoto abre diálogo **no host**), `isDirectory`, `recentProjects` | "Nova pasta / abrir projeto", "Nova conversa nesta pasta", "Configurações do projeto". | No celular, `pickFolder` abre o diálogo na máquina desktop. Preferir `recentProjects` + navegador próprio (G7). |
| 6 | Sidebar: tema e marca | `Sidebar.tsx:952` (tema), wordmark com três quadrados (ver §4.6) | — | Alterna sistema, claro e escuro. | Seguir `isSystemInDarkTheme` com override. |
| 7 | Thread (chat) | `src/components/chat/Thread.tsx` (869 l.) | `getConversation {id}`; `conv-event` | Janela de 12 turnos com carga ao rolar para cima ("N trocas anteriores"). Ctrl+F busca com CSS Highlight. Botão "Ir para o fim" (235). Status: Iniciando, Consultando, "Compactando o contexto" (17-22). | LazyColumn invertida com paginação. Ver G6 (limite de quadro de 64 KiB). |
| 8 | Turno do assistente | `Thread.tsx:763-823` | `answerPermission {id, requestId, decision}` (771-774) | Itens text, thinking, tool, permission, error, failover e steer. Durante o streaming: ProviderMark girando + shimmer de status, ou o contador de rate limit. | — |
| 9 | Rodapé do turno | `Thread.tsx:840-869` | dados de `turnComplete` | Aparece no hover: Copiar · duração · tokens · USD · "Conta X". | Toque longo / expandir. |
| 10 | Fork / retroceder | `Thread.tsx:507-583` | `forkConversation {id, upToTurn}` | "Retroceder até aqui" na mensagem do usuário. | Menu de contexto da bolha. |
| 11 | Composer | `src/components/composer/Composer.tsx` (1267 l.) | `createConversation`, `sendMessage`, `queueMessage`, `sendNow`, `interrupt` | Ver §2.1. | Composer fixo com IME e sheet de opções. |
| 12 | Model / effort / permissão | `ModelPicker.tsx`, `EffortSlider.tsx`, `Composer.tsx:20-25` | `getCatalog` (via `loadCatalog`, `src/stores/app.ts:320`), `setConversationOptions {id, model?, effort?, permissionMode?, projectPath?, extraDirs?}` (`api.rs:628-630`) | Abas de provedor; aba desabilitada se o CLI não estiver instalado (`ModelPicker.tsx:135,193`). Effort em grade "matrix" com mola. Modos de permissão com ícones. | Bottom sheet com abas e slider. |
| 13 | Slash commands | `Composer.tsx:178-183,246-351`; `src-tauri/src/slash_commands.rs:24-380` | `listSlashCommands {provider, projectPath}` | Menu "Comandos (<provider>)". O comando escolhido vira chip "ativo" e é **prefixado ao texto**. `/btw` abre a janela flutuante. Ver §2.2. | Barra de slash acima do teclado. |
| 14 | Menção @arquivo | `Composer.tsx:185-196,246-309,369-385` | `listWorkspaceFiles {path, extraDirs, limit:2000}` (`api.ts:262-272`) | Regex `@([a-zA-Z0-9_\-./\\]*)$`. Filtro por substring, top 20. Insere `@rel `. | Popup sobre o teclado. |
| 15 | @bws (segredos) | `Composer.tsx:246-309` | `bwsListSecrets` / `bwsGetSecret` | `@bws` lista segredos (top 15). Insere `[BWS] key` no corpo. | Opcional. Tratar como alto risco remoto. |
| 16 | Anexos, colar, arrastar | `Composer.tsx:580-728,~914-945` | `saveUpload {name, mime, data(base64)}` (`api.ts:275-284`), `scanFolder`, `unpackZip` → `{dir, files}`, `cleanTempDir`, `readClipboardImage` | Overlay "Solte para anexar". Shimmer "Anexando…". Imagem colada vira `screenshot-<ts>.png`. Toast "Pasta anexada: N". | Câmera, galeria e SAF → `saveUpload` (≤ 25 MiB). |
| 17 | Thumbnails de anexo | `Thread.tsx:612-730` (AttachmentThumb) | `readFile {path}` → `{content b64, mime}` (`api.ts:183-194`) | Preview da imagem, "Abrir no aplicativo padrão", remover. | Coil com bytes vindos do `readFile`. |
| 18 | Voz (microfone) | `Composer.tsx:80-164`; `src/lib/voice.ts` | — (Web Speech no cliente) | Botão Mic pulsa em vermelho; Ctrl+Space é push-to-talk. A transcrição final entra no cursor. No WebKitGTK aparece aviso de indisponível. | `SpeechRecognizer` do Android **local**. Não existe RPC de áudio. |
| 19 | Fila de mensagens | `Composer.tsx:530-576`; `Thread.tsx:222-224,321-400` | `queueMessage`, `sendNow`, `unqueueMessage {id, queueId}`; evento `queue-update {conversationId, queue}` | Com o turno ocupado, Enter enfileira e Ctrl+Enter envia agora. Bolhas na fila com "Enviar agora", "Editar" (cancela e edita) e "Remover". | Chips de fila acima do composer. |
| 20 | Steer (Codex) | `Composer.tsx:198-199`; `Thread.tsx:585` | `sendNow`; evento `steer` | Com `provider==='codex'`, a mensagem entra no meio do turno. Nos outros provedores, interrompe e envia. | O texto do botão varia por provedor. |
| 21 | Cartão de permissão | `src/components/chat/Items.tsx:416-480` | `permissionRequest` / `permissionCancelled`; `answerPermission {id, requestId, decision}` | Ver §2.4. | Notificação acionável + cartão in-app. |
| 22 | Pergunta interativa | `src/lib/questionParser.ts:8-244`; `Main.tsx:60-91`; `QuestionCard.tsx` (306 l.) | detecção local; resposta via `sendMessage` (`chat.ts:386-390`) | Cartão acima do composer com opções numeradas, "(Recomendado)" e "Outro". Ver §2.5. | Notificação + cartão. Ver G3. |
| 23 | Erros, failover, rate limit | `Items.tsx:483-545` | `turnError`, `failover`, `rateLimitWait` | Títulos de erro; pílula "Conta A → Conta B · motivo"; "Em pausa pelo rate limit · retomando em m:ss". | — |
| 24 | Cartões de ferramenta | `Items.tsx:101-122,289-372,548-600`; `src/lib/tools.ts:16-89` | `toolStart`, `toolInput`, `toolResult` | Ícone por tipo, verbo em execução/concluído, diff inline ("Diff de Alteração"), Entrada/Saída, "Working..". Rajadas viram "Explorou …". | Cartões expansíveis. |
| 25 | Sub-agentes | `src/stores/agents.ts`; `App.tsx:57-73`; `AgentsPanel.tsx`; `AgentFloatingWindow.tsx`; `src/lib/agentsCatalog.ts` | heurística sobre `toolStart`, **somente no cliente** | Badge squircle, árvore por `parentId`, janela flutuante. Ver §2.6. | Ver G4. |
| 26 | Tarefas | `Inspector.tsx:169-316`; `App.tsx:43-89` | `toolStart` / `toolResult` / `turnComplete` / `exited` | Seções "Tarefas em execução", "Em execução" e "Concluídas"; "Ver no terminal"; excluir. | Notificação de progresso por conversa ocupada. |
| 27 | Notificações do SO | `src/lib/notifications.ts:1-60`; `App.tsx:90-99` | `turnComplete` | Só quando a janela está sem foco. Título "AiStack", corpo "<título>\n<Provedor> concluiu a tarefa", `tag aistack-conv-<id>`; o clique foca a conversa. | Ver §2.7 e G2. |
| 28 | Header | `src/components/layout/Header.tsx` (181 l.) | `openTerminal`→`ptyOpen {provider, slot, kind:'shell', cwd}`; `switchConversationSlot {id, slot}` (`Header.tsx:~138-154`) | "Procurar na conversa (Ctrl+F)", terminal, inspector "Uso e contas" com badge de atualização, indicador de compactação (82), menu "Conta desta conversa" (A/B com email). | TopAppBar com menu de conta. |
| 29 | Inspector | `src/components/layout/Inspector.tsx:18-23` | — | Abas Tarefas, Agentes, Arquivos e Assets; painel redimensionável. | Abas em bottom sheet ou tela. |
| 30 | Contas, cotas, aquecimento | `src/components/usage/AccountUsage.tsx` (376 l.); `Inspector.tsx:318-447` (UsageTab, aba accounts do SettingsModal 216) | `listAccounts`, `refreshAccounts`, `setActiveSlot`, `logoutAccount`, `setAccountLabel`, `warmAccount {provider, slot}`, `saveApiAccount`/`removeApiAccount`/`listApiAccounts`/`fetchApiModels`; eventos `usage-update`, `accounts-update`, `api-accounts-changed` | Barras por janela (5h, semanal, mensal) com %, "· resetIn" e tooltip "Reinicia …" (135-151). Ponto de status ok/danger. "Aquecer conta X" (Flame) ou "Janela de 5h já aberta" (244-270). "Não instalado" e badge de atualização (275-370). | Tela "Contas" com cards por provedor. |
| 31 | Welcome | `Main.tsx:95-110` | — | Saudação com o nome do home. Bloqueia quando a pior janela tem `usedPct ≥ 98`. | Replicar a regra do bloqueio. |
| 32 | Git: branch e push | `WorkspaceBar.tsx:309-552` | `gitInfo`, `gitSwitch {path, branch}` (379), `gitPushOrPr {path, action: push\|pr\|merge}` (331-340) | Chip de branch, lista "Branches" (439), contador de sujos que abre a revisão. | Chip + sheet. |
| 33 | Git: worktree | `WorkspaceBar.tsx:97-190` | `gitWorktreeCreate {path, worktreePath, branch}`, `gitWorktreeDelete` | "Iniciar nova worktree" (padrão `<repo>/worktrees/worktree-<uuid8>`); remover; fallback para o repo principal se a worktree sumir (`Composer.tsx:427-527`, `Sidebar.tsx:413-417`). | — |
| 34 | Git: revisão | `src/components/git/GitReviewModal.tsx` (518 l.) | `gitInfo` 54, `gitDiff` 82, `gitCommit` 103, `gitDiscard {path, file}` 126, `gitPushOrPr` 146 | "N arquivo(s) modificado(s)" ou "Nenhuma alteração pendente" (241). Diff por arquivo, "Copiar Diff" (419), commit, descartar. | Tela de revisão. Descartar é destrutivo e exige confirmação. |
| 35 | Pastas extras | `WorkspaceBar.tsx:53-92` | `pickFolders(…, true)` → `pickDirectories`; `setConversationOptions {extraDirs}` | Chips removíveis. Tooltip "Adicionar pastas ao contexto (acesso sem pedir permissão)". | — |
| 36 | Explorador / visualizador de arquivos | `src/components/viewer/FileViewerPanel.tsx` (471 l.) | `gitDiff {path, file}` 71, `readFile {path}` 110, `openInEditor {path, line}` 166 | Abas de arquivo; "Nenhum arquivo aberto" (149); "Arquivo binário" (321). | Explorador = `listWorkspaceFiles` (árvore) + `readFile`. `openInEditor` abre o editor **no desktop**. |
| 37 | Assets | `src/components/assets/AssetsPanel.tsx` (801 l.) | `listAssets {path}` (287) | Filtros Todos, Imagens, Shaders, Vídeos, Textos, Binários e "Nesta conversa" (47-55). | Grade de thumbnails. |
| 38 | Terminal embutido | `Header.tsx` → ptyOpen; `ptyBus` em `api.ts:309-331` | `ptyOpen`, `ptyWrite`, `ptyResize`, `ptyClose`; eventos `pty-output {id, data b64}`, `pty-exit {id, code}` | xterm com temas (`SettingsModal.tsx:35-41`). | Alto risco remoto. Opcional/fase 2. |
| 39 | /btw (pergunta paralela) | `src/components/chat/BtwFloatingWindow.tsx` (311 l.) | `createConversation` 94 + `sendMessage` 106 | Janela flutuante: "Perguntas rápidas sem poluir o histórico" (168). | Bottom sheet. |
| 40 | Agendar mensagem | `Composer.tsx:~1209-1239`; `src/stores/scheduler.ts` | `createConversation` + `sendMessage` (149) disparados por **timer do cliente** a cada 15 s (160-170); persiste em localStorage (48,68) | Ícone Hourglass "Agendar mensagem"; SchedulePicker com calendário. | Só roda com o desktop aberto. No celular: AlarmManager próprio ou recurso no host («não verificado» se o host agenda). |
| 41 | Configurações | `src/components/settings/SettingsModal.tsx:195-228` | vários | Abas: Geral & Interface, Contas & Limites de Cota, CLIs, Chrome CDP, Bitwarden, MCP, Terminal, Relay Remoto & Dispositivos. Tamanho de fonte (277) e tema (356-361). | No celular: Geral, Contas e Dispositivos. O resto é só leitura ou fica fora. |
| 42 | MCP | `SettingsModal.tsx:220,501` | `listMcp`, `setMcpEnabled`, `addOwnMcp`, `removeOwnMcp`; evento `mcp-update` | Lista e toggles. | Só leitura e toggle. |
| 43 | Claude Chrome / Ultracode | `Composer.tsx:207-221,~1116-1161` | `getClaudeChrome`, `setClaudeChrome {on}`; Ultracode = localStorage `aistack.ultracode`, prefixo "ultracode\n\n" para Claude | Toggles no composer; Ultracode com ícone Zap âmbar. | Ultracode é local: replicar o prefixo. |
| 44 | Dispositivos / relay | aba remote do SettingsModal (RemotePanel) | `relayStatus`, `relayPairInfo`, `listDevices`, `revokeDevice` | Pareamento por QR e lista de dispositivos («não verificado» em detalhe nesta passada). | O app já pareia (motor existente). |

---

## 2. Detalhamento por área

### 2.1 Composer (`src/components/composer/Composer.tsx`)

- **Rascunho por conversa:** store `useDrafts`, chave id da conversa ou `"new"` (39-48).
- **Envio** (`send()`, 427-527):
  1. Monta o corpo como prefixo de comandos + `[BWS] key` + texto.
  2. Exige `projectPath` e valida com `isDirectory`. Se a worktree foi apagada mas o repo principal existe, usa o principal.
  3. Ultracode prefixa `ultracode\n\n` (Claude). `/grill-me` injeta instruções.
  4. Sem id de conversa, chama `createConversation {provider, projectPath, model, effort, permissionMode, extraDirs}`.
  5. Faz `optimisticSend` e depois `sendMessage {id, text, attachments}`.
  6. Em caso de erro, devolve o texto ao campo.
- **Trocar opções** (`update()`, 387-425):
  - sem conversa, só altera o rascunho;
  - com conversa, chama `setConversationOptions`;
  - trocar `projectPath` com o turno ocupado faz `forkConversation {id, upToTurn}` antes.
- **Teclado** (~1027-1033):
  - Enter envia;
  - com o turno ocupado, Enter enfileira e Ctrl/Cmd+Enter envia agora;
  - Esc com o turno ocupado chama `interrupt {id}` (578).
- **Placeholders** (~1034-1039): "Como posso ajudar hoje?" (nova), "Responder…" (em conversa), e com o turno ocupado "Enter enfileira · Ctrl+Enter entrega no turno atual / interrompe e envia agora".
- **Visual:**
  - texto 15px; textarea com altura máxima de 130px;
  - caixa com `--radius-composer` 22px, borda de 1.5px e anel de foco de 4px com o acento a 14%;
  - botões com tooltip: Anexar (~1049), Voz (~1061), Chrome (~1116-1131), Ultracode (~1136-1161), ModelPicker (~1169), "Enviar agora" (Zap) / "Enfileirar" (ListPlus) (~1182-1197), "Parar (Esc)" (~1202), "Agendar mensagem" (~1209-1219); WorkspaceBar (~1236).
- **Ícones dos modos de permissão** (20-25): ask = ShieldCheck · acceptEdits = FileEdit · plan = Map · bypass = ShieldAlert.
- **Limpeza:** `cleanTempDir` roda após o fim do turno, para zips descompactados (166-174).

### 2.2 Slash commands

- **Não existe RPC "executar comando".** O comando é texto prefixado à mensagem (`Composer.tsx:311-351`, `send()`). `/btw` (janela flutuante) e `/grill-me` (injeção de instruções) são lógica local do Composer.
- **Gatilho:** `/(?:^|\s)\/([a-zA-Z0-9_-]*)$/`; o filtro é por prefixo (`Composer.tsx:246-309`).
- **Lista por provedor** (`src-tauri/src/slash_commands.rs`):
  - **Claude** (33-124): /help /compact /clear /cost /doctor /init /review /pr-comments /teamwork-preview /agents /skills /plan /goal /btw /grill-me
  - **Codex** (125-204): /help /compact /clear /plan /goal /teamwork-preview /agents /skills /diff /model /status /btw /grill-me
  - **Agy/Gemini** (205-284): /goal /plan /schedule /browser /grill-me /boost /learn /teamwork-preview /agents /skills /mcp /btw /clear
  - **Provedores de API** (kimi/deepseek/glm/qwen): nenhum (285)
  - **Custom Claude:** `~/.claude/commands/*.md` e `<projeto>/.claude/commands` → `source: "custom"` (288-320)
  - **Skills Agy:** `~/.gemini/antigravity-cli/skills`, `…/builtin/skills`, `~/.gemini/antigravity/builtin/skills`, `<proj>/.gemini/skills`, `<proj>/.agent/skills` e `~/.gemini/config/plugins/*/skills` → `source: "skill"` (321-380)
- **Mobile:** chamar `listSlashCommands {provider, projectPath}` ao trocar de provedor ou projeto. Mostrar a barra com o cabeçalho "Comandos (<provider>)" e o estado vazio (`Composer.tsx:729-742`). Reproduzir os chips de comando ativo e o tratamento local de `/btw` e `/grill-me`.

### 2.3 Anexos, câmera e microfone

- **Arquivo genérico:** `uploadFile` → `saveUpload {name, mime, data}` grava em `~/.aistack/uploads` e devolve `{path, mime}` (`api.ts:274-284`). O anexo enviado é `Attachment {path, mime}` (`types.ts:72-75`).
- **Pasta:** `scanFolder` (toast "Pasta anexada: N"). **Zip:** `unpackZip` → `{dir, files}` (`Composer.tsx:580-641`).
- **Colar:** `screenshot-<ts>.png`; no Tauri, com fallback em `readClipboardImage` (~914-945).
- **Câmera:** não existe no desktop. No mobile: CameraX/`TakePicture` → JPEG/PNG → `saveUpload` (respeitar 25 MiB, `api.rs:209`).
- **Microfone:** no desktop é só reconhecimento de fala no cliente (`src/lib/voice.ts`, `Composer.tsx:80-164`). **Não há transcrição no host.** No mobile: `SpeechRecognizer` local que insere texto. Enviar áudio bruto como anexo (via `saveUpload`) só ajudaria se o CLI aceitasse áudio («não verificado»).

### 2.4 Permissões (`Items.tsx:416-480`)

- **Evento:** `permissionRequest {requestId, tool, input, toolUseId, suggestions, reason}`. `permissionCancelled {requestId}` encerra o pedido (`types.ts:110-119`).
- **Cartão:**
  - ícone ShieldAlert; borda `warn` + fundo `warn` a 7% enquanto pendente;
  - título "Permitir <tool>?", que depois vira Permitido / Negado / Cancelado;
  - detalhe em bloco mono a partir de `command`, `file_path`, `url` ou `path`; mostra `reason`.
- **Botões:**
  - "Permitir" → `{allow, remember:false}`
  - "Sempre permitir neste projeto" → `{allow, remember:true}`, só se houver `suggestions`
  - "Negar" → `{deny}`
- **RPC:** `answerPermission {id, requestId, decision}` (`Thread.tsx:771-774`; `api.rs:623-626`). Formato exato do enum `decision` no Rust: «não verificado» (inferido do front).
- **Mobile:** notificação com ações Permitir/Negar (a ação "Sempre" fica só no app) + cartão in-app. Cancelar a notificação quando chegar `permissionCancelled` ou o turno terminar.

### 2.5 Perguntas interativas (AskUserQuestion)

- **Detecção pelo nome da ferramenta:** `extractInteractiveQuestionFromTool` só reconhece `ask_question` e `AskFollowupQuestion`, lendo `input.questions[0]` ou `input.question/options/is_multi_select` (`src/lib/questionParser.ts:175-221`).
- **Detecção pelo texto:** `extractInteractiveQuestionFromText` lê uma lista numerada no fim do último bloco de texto (52-…). `parseOptionLabel` reconhece "(Recomendado)/(Recommended)" (8-50). Só roda com o turno parado (224-244).
- **`AskUserQuestion` (Claude) não aparece em `src-tauri` nem no parser** (grep vazio). Como chega (talvez como `permissionRequest` com `tool: "AskUserQuestion"`): «não verificado».
- **UI** (`QuestionCard.tsx`, acima do composer, `Main.tsx:60-91`):
  - teclas numéricas, setas, Enter e Esc; opção "Outro" com texto livre;
  - resposta no formato `"<n>. (Recomendado) label: detail"` (40-60);
  - responder → `answerQuestion` = dismiss + `optimisticSend` + `sendMessage` (`chat.ts:386-390`);
  - pular → `dismissQuestion`, só local (`chat.ts:374-384`).
- **Mobile:** portar o parser para Kotlin e notificar quando uma pergunta é detectada no `turnComplete`.

### 2.6 Sub-agentes

- **Estado só no cliente:** `useAgents`, persistido em localStorage `aistack.scaled_agents.v1` (`src/stores/agents.ts:64-83`).
  - `ScaledAgent`: id, profileId, role, shortRole, name, category, badgeSvg, parentId, provider, model, status `running|completed|failed`, prompt, startedAt, durationSeconds, worktreeBranch, steps[{kind thought|tool|code|output|status}], development (`agents.ts:17-37`).
- **Criação:** em `toolStart`, quando `nested===true`, ou o nome contém agent/subagent, ou é `Task`/`delegate` (`App.tsx:57-73`).
- **Não há conclusão automática.** `completeAgent` só existe como botão manual (`AgentFloatingWindow.tsx:328`). `toolResult` só atualiza tarefas (`App.tsx:74-80`).
- **Autoria:** `textDelta.author?: AgentIdentity {id?, name?, provider?, model?}` (`types.ts:86-91,105`) permite atribuir texto ao sub-agente.
- **UI:**
  - **Painel** (`AgentsPanel.tsx`): "Agentes", "N ativos", filtro, "Limpar todos" e o estado vazio "Nenhum agente ativo". Badge squircle de 32px com brilho ciano enquanto roda; pílulas Concluído/Falhou; árvore por `parentId` (19-48, 57-105, 168-272).
  - **Janela flutuante** (`AgentFloatingWindow.tsx`, 446 l.): abre com animação "mac-jelly" a partir do retângulo do badge; maximizar, fechar (Esc), copiar prompt, navegação pai/filho, redimensionar.
  - **Catálogo** (`src/lib/agentsCatalog.ts`, 630 l.): perfis com `svgFile '/agent-badges/<id>.svg'`; 29 perfis oficiais (`docs/PLAN.md:198`).
- **Mobile:**
  - reconstruir a partir de `conv-event`: `toolStart` (nested ou Task) → running; `toolResult` com o mesmo `id` → completed/failed;
  - **melhor que o desktop**, que nunca conclui sozinho;
  - os SVGs dos badges já existem (§4.7).

### 2.7 Notificações

- **Desktop:** só no `turnComplete` e só sem foco (`notifications.ts:27-58`; `App.tsx:90-99`).
  - Título "AiStack"; corpo `"<título>\n<Provedor> concluiu a tarefa"`; `tag aistack-conv-<convId>` (as notificações se substituem); o clique foca a conversa.
- **Não há notificação do SO para permissão, pergunta, erro, failover ou rate limit:** esses aparecem só in-app.
- **Mobile** (canais sugeridos):
  1. Pendências: `permissionRequest`, alta prioridade, com ações.
  2. Perguntas: detectadas no `turnComplete`.
  3. Tarefas concluídas: `turnComplete`.
  4. Progresso: notificação contínua enquanto alguma conversa estiver ocupada, com contagem de tools e sub-agentes.
  5. Avisos: `notice` warning/error, `turnError`, `failover`, `rateLimitWait`.
- Isso exige um socket vivo em foreground service; push sem socket depende de FCM, que não existe hoje («não verificado» se há plano de push).

### 2.8 Contas, cotas e failover

- **Tipos** (`types.ts:3-34`): `WindowKind fiveHour|weekly|monthly|other`; `LimitStatus ok|warning|rejected|unknown`; `AuthState unknown|authenticated|logged_out|error`; `UsageReport {windows, status, plan}`.
- **Slots e failover:** cada conversa usa um slot a/b. O failover gera o evento `failover {provider, from, to, reason}`, mostrado como pílula "Conta A → Conta B · troca manual | limite atingido | limite próximo" (`Items.tsx:509-521`).
- **Rate limit:** `rateLimitWait {secondsRemaining}` mostra a contagem regressiva (`Items.tsx:523-545`).
- **Medidores:** `worstPct` (`AccountUsage.tsx:12`) define a cor do medidor. A Welcome bloqueia com 98% ou mais (`Main.tsx:95-110`).

### 2.9 Projetos e sessões iniciadas no celular

- `createConversation {provider, projectPath, model, effort, permissionMode, extraDirs}` (`api.rs:585-597`) cria a sessão no host. O desktop recebe `conversations-changed` e ela aparece na sidebar.
- **Não há como marcar a origem.** `CreateArg` e `Conversation` não têm `origin`/`device` (`types.ts:56-70`; `store.rs:21-36`). Ver G1.

---

## 3. Fluxos-chave (sequência de RPC/eventos)

1. **Abrir conversa:** `getConversation {id}` → render dos blocos → assinar `conv-event`/`queue-update` filtrando por `conversationId`.
2. **Nova sessão no celular:** `listSlashCommands` (opcional) → `createConversation` → `sendMessage` → eventos `turnStarted…turnComplete`.
3. **Pendência:** `permissionRequest` → notificação → `answerPermission` → `permissionCancelled` ou continuação.
4. **Turno ocupado:** `queueMessage` ou `sendNow` (Codex faz steer) → `queue-update`. `interrupt {id}` para parar.
5. **Anexo da câmera:** captura → base64 → `saveUpload` → `{path, mime}` → `sendMessage {attachments:[{path, mime}]}`.

---

## 4. Tokens de design

### 4.1 Paleta OKLCH (`src/styles/tokens.css`)

| Token | Claro (`:root`, 4-42) | Escuro (`.dark`, 44-74) |
|---|---|---|
| `--bg` | oklch(0.985 0.004 85) | oklch(0.165 0.006 270) |
| `--sidebar` | oklch(0.963 0.006 85) | oklch(0.14 0.006 270) |
| `--surface` | oklch(1 0 0) | oklch(0.205 0.007 270) |
| `--surface-2` | oklch(0.967 0.005 85) | oklch(0.235 0.008 270) |
| `--surface-3` | oklch(0.935 0.007 85) | oklch(0.27 0.009 270) |
| `--user-bubble` | oklch(0.43 0.14 255) | oklch(0.38 0.11 255) |
| `--user-bubble-fg` | oklch(0.99 0.005 255) | oklch(0.98 0.005 255) |
| `--line` | oklch(0.905 0.007 85) | oklch(1 0 0 / 0.075) |
| `--line-strong` | oklch(0.84 0.009 85) | oklch(1 0 0 / 0.14) |
| `--fg` | oklch(0.2 0.012 70) | oklch(0.955 0.004 270) |
| `--fg-2` | oklch(0.43 0.012 70) | oklch(0.74 0.008 270) |
| `--fg-3` | oklch(0.58 0.01 70) | oklch(0.56 0.01 270) |
| `--glass` | oklch(1 0 0 / 0.78) | oklch(0.21 0.008 270 / 0.72) |
| `--ok` | oklch(0.62 0.14 152) | oklch(0.74 0.15 152) |
| `--warn` | oklch(0.7 0.15 72) | oklch(0.8 0.15 78) |
| `--danger` | oklch(0.58 0.2 25) | oklch(0.68 0.19 25) |
| `--accent-fg` | oklch(0.99 0 0) | oklch(0.16 0.01 270) |

- **Sombras:**
  - `--shadow-float`: claro `tokens.css:18-19`, escuro 58-59
  - `--shadow-pop`: claro 20, escuro 60
- **Acento:** `--accent` segue o provedor via `[data-provider='x']` (76-82). `--accent-soft` = `color-mix(in oklch, var(--accent) 12%, transparent)` (35, 85).
- **Conceito:** claro é "papel quente" (matiz 85); escuro é "grafite profundo" (matiz 270) (1-2).

### 4.2 Cores de provedor

| Provedor | Claro | Escuro | Marca (logo) |
|---|---|---|---|
| claude | oklch(0.64 0.15 40) | oklch(0.72 0.14 42) | path inline em `currentColor` (`ProviderMark.tsx:8`) |
| codex | oklch(0.24 0.005 270) | oklch(0.88 0.004 270) | path inline em `currentColor` (`ProviderMark.tsx:9`) |
| agy (Gemini) | oklch(0.58 0.20 250) | oklch(0.70 0.18 248) | `src/assets/providers/gemini.svg` (gradiente com máscara, viewBox 0 0 296 298) |
| kimi | oklch(0.58 0.20 255) | oklch(0.68 0.18 255) | `KimiMark`, fundo #121316 (`CustomIcons.tsx:45-60`) |
| deepseek | oklch(0.58 0.19 242) | oklch(0.70 0.18 242) | `DeepSeekMark` #4D6BFE (`CustomIcons.tsx:63-72`) |
| glm | oklch(0.60 0.19 215) | oklch(0.72 0.18 215) | `GlmMark` em currentColor (`CustomIcons.tsx:75-86`) |
| qwen | oklch(0.58 0.22 280) | oklch(0.68 0.20 280) | `QwenMark` #615CED (`CustomIcons.tsx:89-103`) |

Os tokens kimi, deepseek, glm e qwen existem em `tokens.css`, mas `@theme inline` só expõe `--color-claude`, `--color-codex` e `--color-agy` como utilitários (`src/styles/index.css:9-30`).

### 4.3 Paletas do EffortSlider (`src/components/composer/EffortSlider.tsx`)

- **Claude** (20-29): #7c2d12, #9a3412, #b45309, #c2410c, #da7756, #d97706, #ea580c, #f97316, #fb923c, #ffffff
- **GPT/Codex** (34-42): #171717, #262626, #383838, #525252, #737373, #a3a3a3, #d4d4d4, #f5f5f5, #ffffff
- **Gemini** (47-56): #1e3a8a, #1d4ed8, #2563eb, #0284c7, #38bdf8, #60a5fa, #06b6d4, #22d3ee, #a5f3fc, #ffffff
- **Kimi** (61-69): #090d16, #0f172a, #172554, #1e3a8a, #2563eb, #3b82f6, #60a5fa, #93c5fd, #ffffff
- **Seguinte (a partir da linha 74)**: começa em #082f49, #0369a1… (provedor exato «não verificado»)
- **`PROVIDER_EFFORT_THEMES`** (105-165): gradiente do texto, brilho do thumb (ex.: Claude rgba(234,88,12,0.65)), classe do badge e borda do spinner.
- **Thumb:** mola com stiffness 500 e damping 36 (394-396). Tooltip "Controla a profundidade de raciocínio" (332).

### 4.4 Tipografia (`src/styles/index.css`)

- **Fontes:**
  - Sans: Inter Variable (3, 31)
  - Serif: Source Serif 4 Variable (4, 32)
  - Mono: JetBrains Mono Variable (5, 33)
  - Todas via `@fontsource-variable`. **Android:** empacotar Inter, Source Serif 4 e JetBrains Mono como fontes variáveis (licença OFL).
- **Escala base:** `html.font-size-sm` 13.5px · `md` 15px · `lg` 16.5px (`tokens.css:87-89`), configurável em "Tamanho da fonte" (`SettingsModal.tsx:277`).
- **Tamanhos recorrentes:**
  - linhas da sidebar 13px e subtítulo 11–11.5px (`Sidebar.tsx:821,841,847`);
  - texto do composer 15px;
  - títulos de modal 14px semibold (`SettingsModal.tsx:133`).

### 4.5 Raios, layout e movimento

- **Raios:** `--radius-composer` 22px (`tokens.css:40`). Linhas da sidebar usam `rounded-lg` e menus `rounded-md` (`Sidebar.tsx:815,868`). Badge de agente é squircle de 32px (painel) e 56px (`agents_manifest.json`, "56x56 iOS squircle").
- **Largura do feed:** `--feed-max-width` 46rem (`tokens.css:41`).
- **Easing:**
  - `--ease-spring` cubic-bezier(0.23, 1, 0.32, 1)
  - `--ease-out` cubic-bezier(0.16, 1, 0.3, 1) (`tokens.css:38-39`)
- **Keyframes** (`src/styles/index.css`):
  - `shimmer`: background-position de 100% a −150% (184)
  - `pulse-dot`: opacidade 0.35↔1 e escala 0.85↔1 (193); usado no ponto de conversa ocupada a 1.2s (`Sidebar.tsx:827`)
  - `comet-h-sweep` (205), `menu-in` com scale 0.96 e translateY −2px (245), `fade-in` (303), `sheet-up` com translateY 24px e scale 0.985 (314)
  - highlight de busca `::highlight(aistack-search)` com `--warn` a 45% (~309)
- **Animações em motion/react:**
  - fila em `AnimatePresence` (`Thread.tsx:222-224`)
  - "mac-jelly" na janela de agente (`AgentFloatingWindow.tsx`)
  - mola do slider (`EffortSlider.tsx:394-396`)
  - QuestionCard
  - classe `press` nos botões (definição exata em index.css: «não verificado»)
- **Compose (sugestão):** `spring(dampingRatio≈0.8, stiffness≈500)` e `CubicBezierEasing(0.23f, 1f, 0.32f, 1f)`.

### 4.6 Marca e logo (SVG)

- **`src/assets/brand/aistack-mark.svg`:** três quadrados sobrepostos, viewBox 0 0 1024 1024, todos 480×480 com `rx="120"`:
  - Claude: x/y 152, `#E0845A`
  - Codex: x/y 272, `#E8E9EC`, `mix-blend-mode:multiply`
  - Gemini: x/y 392, `#6E9BF5`, `mix-blend-mode:multiply`
  - Fundo transparente; grupo com `isolation:isolate`.
- **`src/assets/brand/aistack-icon.svg`:** a mesma marca sobre um ladrilho de 928×928, `rx="208"`, com gradiente vertical `#26262C`→`#141417` e contorno branco a 8%.
- **Atenção:** nenhum `.tsx` importa esses arquivos (grep vazio). O wordmark da Sidebar repete os quadrados inline com `mix-blend` (detalhe de linha «não verificado» nesta passada).
- **Android:**
  - converter para VectorDrawable / adaptive icon;
  - **`mix-blend-mode` não existe em VectorDrawable**: precomputar as cores das interseções ou desenhar com `Canvas` + `BlendMode.Multiply` no Compose.

### 4.7 Ícones e fontes SVG

- **Origem dos logos:** thesvg.org (github.com/glincker/thesvg) (`ProviderMark.tsx:6-7`). Dependência `@thesvg/mcp-server ^0.8.3` (`package.json:26`). Confirmado em `docs/PLAN.md:79,139,198`.
- **Arquivos:**
  - `assets/svg/`: claude-code.svg, codex.svg, deepseek.svg, gemini.svg, kimi.svg, openai-badge.svg, qwen.svg, zdotai.svg (GLM), add-circle-svgrepo-com.svg, arrow-top/bottom-svgrepo-com.svg
  - `src/assets/providers/`: gemini.svg, kimi.svg, deepseek.svg, zdotai.svg, arrow-top/bottom.svg
  - `src/assets/icons/`: add-circle.svg, arrow-top.svg, arrow-bottom.svg (também inline em `CustomIcons.tsx:3-43`)
  - **Badges de agentes:** `assets/agent-badges/*.svg` e `public/agent-badges/*.svg` (29 perfis: agile-*, data-*, dba-*, dev-*, qa-*, sec-*, sre-*), mais `agents_manifest.json`, `png/` e `showcase.html`
- **Ícones de UI:** lucide-react.
  - Usados: ShieldCheck, ShieldAlert, FileEdit, Map, Zap, ListPlus, Hourglass, Mic, Flame, GitFork, GitBranch, Loader2, MoreHorizontal, Activity, Bot, ListChecks, Globe, SquareTerminal, PencilLine, FileText, Wrench, Monitor, Sun, Moon, ImageIcon, FileCode2, Film, Package, Sparkles…
  - **Android:** importar os SVGs do lucide (ISC) como ImageVector. Não usar PNG (requisito do usuário: só SVG).
- **Ícone de ferramenta por tipo** (`Items.tsx:112-122`): file → FileText · edit → PencilLine · terminal → SquareTerminal · search · web → Globe · agent → Bot · todo → ListChecks · plug · tool → Wrench.

---

## 5. Lacunas e decisões pendentes (para o app mobile)

| ID | Lacuna | Evidência | Proposta |
|---|---|---|---|
| G1 | Não há campo de origem/dispositivo na conversa: impossível mostrar o ícone de celular na sidebar do desktop | `types.ts:56-70`; `store.rs:21-36`; `api.rs:585-597` | Backend: `origin: "desktop"\|"mobile"` + `deviceId?` em `Conversation`/`CreateArg`, preenchido pelo host a partir da identidade do dispositivo do relay (não confiar no cliente). Sidebar: ícone Smartphone. |
| G2 | O desktop só notifica `turnComplete`; permissões e perguntas não têm notificação do SO | `notifications.ts:22-58` | Notificações geradas no Android a partir de `conv-event`. Socket vivo via foreground service. |
| G3 | O parser não trata `AskUserQuestion` (só `ask_question`/`AskFollowupQuestion`) | `questionParser.ts:175-221`; grep vazio em `src-tauri` | Verificar como o Claude CLI emite AskUserQuestion no stream-json (permissionRequest?) e tratar nos dois clientes. |
| G4 | Sub-agentes são só do cliente: heurísticos, sem conclusão automática, presos ao localStorage | `agents.ts:64-83`; `App.tsx:57-80`; `AgentFloatingWindow.tsx:328` | Reconstruir no Android via toolStart/toolResult. Ideal: estado de sub-agente no host (evento novo). |
| G5 | Nome divergente: o Android chama `interruptConversation`, mas o host expõe `interrupt` | `api.rs:618`; GAP-MOBILE.md | Corrigir o cliente Android. |
| G6 | Limite de quadro de 64 KiB no relay (R-110) vs `getConversation` devolvendo todos os blocos | `api.rs:576-584`; GAP-MOBILE.md | Paginação de blocos no host ou fragmentação de quadro. |
| G7 | `pickDirectories`/`pickFolder`/`openInEditor` abrem UI **no desktop** | `api.rs:671,685,790`; `api.ts:208-234` | No mobile: navegador próprio de pastas (requer RPC `listDir` novo, ou `listWorkspaceFiles` + `recentProjects`). |
| G8 | 18 de 85 métodos são de alto risco remoto (pty*, bws*, cdp*, gitDiscard, deleteConversation, installCli…) | GAP-MOBILE.md | Política de allowlist ou confirmação no host para dispositivos remotos. |
| G9 | Agendamento roda num timer do front (15 s), só com o desktop aberto | `scheduler.ts:140-170` | Decidir se o mobile agenda localmente ou se o host ganha um scheduler. |
| G10 | `relay-status`/`devices-changed` não são assinados pelo front | `remote_relay.rs:32,34`; `api.ts:103-114` | Confirmar se chegam ao dispositivo pareado. |
| G11 | Voz é só reconhecimento local; não há RPC de áudio | `voice.ts`; `Composer.tsx:80-164` | `SpeechRecognizer` no Android; anexo de áudio só se o provedor suportar. |

### Itens «não verificado» nesta catalogação

- Formato serde de `decision` em `answerPermission` (inferido: `{allow, remember}` / `{deny}`).
- Como o AskUserQuestion do Claude chega ao front.
- Se `relay-status` e `devices-changed` trafegam pelo túnel até o celular.
- Detalhes do RemotePanel (aba "Relay Remoto & Dispositivos").
- Linha exata do wordmark inline da Sidebar e definição da classe `press`.
- Provedor da paleta que começa em `EffortSlider.tsx:74`.
- `login`/`shell` como `kind` do `ptyOpen` (921-922).
