# 04 — Mudanças no desktop (Rust + React) para o app Android

Escopo: as alterações no AiStack desktop (`/home/diego/Documents/AiStack`, HEAD `3a55fcf`) que o app Android precisa para:

- ver e controlar as sessões do desktop;
- iniciar sessões pelo celular, que aparecem na sidebar com um ícone de celular;
- receber pendências e progresso;
- ver sub-agentes, slash commands, explorador de arquivos, câmera e microfone.

Regras deste documento:

- **Projeto apenas.** Nenhuma linha de código foi alterada.
- As referências `arquivo:linha` valem para o HEAD citado e foram conferidas por leitura.
- O que não foi verificado aparece marcado como «não verificado».
- `src-tauri/src/window_persistence.rs` tem alterações não commitadas de outra sessão. **Não deve ser tocado.** Nada aqui depende dele.
- A implementação deve ser feita depois, numa worktree separada, para não colidir com o working tree atual.

Convenção de caminhos:

- `rs/` = `src-tauri/src/`
- `web/` = `src/`
- `relay/` = crate do relay

---

## 0. Como o desktop funciona hoje (o que importa para o celular)

| Ponto | Onde | Fato |
|---|---|---|
| Despacho único de RPC | `rs/api.rs:457` `dispatch(rt, method, p)` | Usado pelo IPC Tauri (`rs/lib.rs:31-38`), pelo HTTP (`rs/server.rs:94-112`) e pelo túnel (`rs/remote_relay.rs:513`). **Nenhum dos três carrega a identidade de quem chama.** |
| Emissor único de eventos | `rs/lib.rs:109-112` | O fechamento `emit` faz `handle.emit` (janela) **e** `remote_emit` (broadcast). |
| Broadcast | `rs/server.rs:29-36` | Canal broadcast(4096) com `{"event":name,"payload":payload}`. |
| Eventos de conversa | `rs/runtime.rs:1070-1072` `emit_conv` | Emite `conv-event` com `{conversationId, turn, event}`. |
| Todo evento de motor sai | `rs/runtime.rs:1373` | Única exceção: `McpStatus`, que retorna antes. |
| Lista mudou | `rs/runtime.rs:860` (create), `:893-894` (título), `:998` (delete) | Emite `conversations-changed` com `{}`. |
| Túnel → eventos | `rs/remote_relay.rs:543-550` | Encaminha **todos** os eventos, sem filtro, de **todas** as conversas, inclusive `pty-output`. |
| Limite de quadro | `rs/remote_relay.rs:40` `MAX_DATA` = 64 KiB | Vale nos **dois sentidos**. Acima do limite, `send_frame` (`:582-596`) faz `bail!`. |
| Fila de saída | `rs/remote_relay.rs:376` | Capacidade 256 com `try_send`. Fila cheia derruba a sessão. |

Consequência: quase tudo o que o celular precisa já trafega. Faltam três coisas:

1. **Identidade de quem chama** (origem «mobile» e política de métodos);
2. **Estado consultável** (pendências, retomada após reconexão);
3. **Tamanho** (R-110, nos dois sentidos).

---

## (a) Origem «mobile» na conversa e ícone de celular na sidebar

### a.1 Persistência

- Nova migração `src-tauri/migrations/0003_origin.sql`, no padrão de `0002_extra_dirs.sql`:
  ```sql
  ALTER TABLE conversations ADD COLUMN origin TEXT NOT NULL DEFAULT 'desktop';
  ALTER TABLE conversations ADD COLUMN origin_device TEXT;
  ```
  As linhas antigas ficam com `'desktop'`, sem backfill.
- `rs/store.rs:19-36` (struct `Conversation`): acrescentar `pub origin: String` e `pub origin_device: Option<String>`. A struct usa serde camelCase, então o JSON sai como `origin` e `originDevice`.
- `rs/store.rs:74-90` `conversation_from_row`: ler as duas colunas com `try_get`. As consultas usam `SELECT *` (`:239`, `:245`), então nada mais precisa mudar ali.
- `rs/store.rs:217-236` (INSERT de create): incluir as duas colunas a partir de `NewConversation`, que ganha os mesmos dois campos.
- `rs/store.rs:296-298` (INSERT de fork): as colunas são explícitas, então um fork sai como `'desktop'` por padrão. Herdar ou não a origem é uma decisão em aberto (ver perguntas). Herdar exige apenas acrescentar a coluna ao INSERT…SELECT.

### a.2 Identidade de quem chama (sem confiar no cliente)

- Em `rs/api.rs`:
  ```rust
  pub enum Caller { Local, Remote { device_id: String, device_name: String } }
  pub async fn dispatch_as(rt: &Runtime, caller: &Caller, method: &str, p: Value) -> Result<Value>
  pub async fn dispatch(rt, method, p) -> Result<Value> { dispatch_as(rt, &Caller::Local, method, p).await }
  ```
  Com isso, `rs/lib.rs` e `rs/server.rs` ficam inalterados.
- `rs/remote_relay.rs:513`: trocar por `dispatch_as(rt, &Caller::Remote{..}, &method, params)`.
  - O `device_id` vem da sessão autenticada (keyAuth em `:478-500`).
  - O nome vem do `Device` que `devices::touch_active` (`rs/devices.rs:95`, retorna `Option<Device>`) devolve em `:492`, ou do resultado do `Pair`.
  - **O cliente não informa a origem**: um campo `origin` vindo nos params é ignorado.
- `rs/api.rs:585-598` (braço `createConversation`): passar `origin = "mobile"` e `origin_device = Some(device_id)` quando `Caller::Remote`. Caso contrário, `"desktop"`/`None`.
- `rs/runtime.rs:828-862` `create_conversation(...)`: ganha um parâmetro `origin: (String, Option<String>)`, que repassa para `NewConversation` (`:841-851`).
- HTTP/Tailscale (`rs/server.rs:94-112`) continua `Local`. Se deve contar como remoto é uma decisão em aberto.

### a.3 React

- `web/lib/types.ts:56-70` (`interface Conversation`): acrescentar `origin?: 'desktop' | 'mobile'` e `originDevice?: string | null`. São opcionais para tolerar um backend antigo.
- `web/components/layout/Sidebar.tsx`:
  - Import Lucide em `:3-36`: acrescentar `Smartphone`. O ícone já é usado em `web/components/.../RemotePanel.tsx:6,227` e `RelayPair.tsx:6`; Lucide é SVG, o que atende à regra de SVG.
  - `ConversationItem` (`:749-918`), no span do título (`:834-837`), logo antes do ícone de ramo que já existe em `:835`:
    ```tsx
    {c.origin === 'mobile' && (
      <Smartphone className="mr-1 inline size-3 text-accent"
        aria-label="Iniciada no celular"
        title={c.originDevice ? `Iniciada no celular (${deviceName})` : 'Iniciada no celular'} />
    )}
    ```
    `deviceName` vem de `listDevices`, já disponível no RemotePanel. Se não for carregado, mostre só «Iniciada no celular».
  - O menu de ações (`:863+`) não muda, e a conversa continua totalmente controlável no desktop. A origem é só um rótulo, não uma trava.
  - Opcional: animar a entrada do item com o `motion`/`AnimatePresence` já importado em `:2` (fade e slide de 150 ms), só quando `origin==='mobile'` e o item é novo.

---

## (b) Conversa criada no celular aparece ao vivo na sidebar

**Já funciona pelo caminho lido** (verificado por leitura; não executado ponta a ponta):

1. `createConversation` remoto chega em `rs/runtime.rs:828-862`.
2. `:860` emite `self.emit(EVT_CONVERSATIONS, json!({}))`.
3. `rs/lib.rs:111` faz o emit para a janela Tauri.
4. `web/App.tsx:113-114` trata `case 'conversations-changed': app.loadConversations()`.
5. `web/stores/app.ts:329-335` chama `listConversations` e `recentProjects`, e o item novo aparece.

Ajustes recomendados:

- **b.1 Payload mais rico, opcional e compatível.** Em `:860`, emitir `{id, reason:"created", origin}` em vez de `{}`. No React, se `origin==='mobile'`, mostrar um toast «Nova sessão iniciada no celular» com o ícone `Smartphone` e destacar o item. Quem não lê o payload segue recarregando.
- **b.2 Indicador «ocupado» para conversas não abertas.**
  - `web/stores/chat.ts:398-416` (`enqueue`) descarta os eventos de conversas sem thread carregada (`:409-410`, `if (!t) continue;`).
  - Por isso, o ponto de atividade da sidebar (`Sidebar.tsx:764`, `useChat(s=>s.threads[c.id]?.busy)`) **não acende** numa sessão iniciada no celular que não está aberta no desktop.
  - Correção mínima:
    - criar um mapa `busyById` no store `app`, alimentado em `web/App.tsx:40-90` pelos eventos `turnStarted` (`rs/runtime.rs:898-902`) → true e `turnComplete`/`turnError`/`exited` → false, antes do filtro do `chat.ts`;
    - na sidebar, usar `threads[c.id]?.busy ?? busyById[c.id]`.

---

## (c) Pendências e progresso de todas as conversas para o celular

### c.1 O que o túnel encaminha hoje

- `rs/remote_relay.rs:543-550` repassa **tudo** o que passa pelo broadcast:
  - todos os `conv-event` de todas as conversas (TextDelta, ThinkingDelta, ToolStart/Input/Result, PermissionRequest/Cancelled, TurnComplete/Error, RateLimit*, Status, turnStarted, steer, failover);
  - `conversations-changed`, `queue-update` e `notice`;
  - `pty-output` e os demais eventos globais.
- Portanto, com a conexão aberta, **nada falta** para notificar em tempo real.

### c.2 Lacunas

1. **Sem retomada.** Após reconectar, ou após `Lagged` (`:551-553`, que só gera log de debug) ou após um período offline, o celular não sabe quais permissões continuam pendentes.
   - `TurnState` (`rs/runtime.rs:200-209`) não guarda as permissões pendentes.
   - `getConversation` só traz os blocos persistidos, e o `assembler.finish()` só grava no fim do turno (`:1314-1329`). Por isso, um turno em andamento também não tem parcial consultável.
2. **Volume e bateria.** Todos os deltas de todas as conversas, mais o `pty-output`, chegam ao celular.
3. **Fila de saída.** Uma rajada de eventos enche a fila de 256 e derruba a sessão (`:376`, `try_send`).

### c.3 Desenho mínimo

- **Pendências no host.**
  - `TurnState` ganha `pending_perms: Vec<PendingPerm{request_id, tool, input_preview, tool_use_id, since}>`.
  - Insere em `handle` (`rs/runtime.rs:1286-1374`) no `PermissionRequest`.
  - Remove no `PermissionCancelled`, em `answer_permission` (`:931-934`) e em `TurnComplete`/`TurnError`/`Exited`.
  - O `input_preview` é truncado (2 KiB) por causa do R-110.
- **Nova RPC `listPending`** (somente leitura) retornando `[{conversationId, title, projectPath, busy, turn, pendingPermissions:[...], lastEventAt}]`, só com conversas ativas (busy ou com pendência). É a base das notificações «pendências pareadas do desktop» e da retomada.
- **`getConversation`** passa a incluir `pending` (a mesma lista, só daquela conversa) e `busy`.
- **Assinatura opcional por sessão do túnel**, com o padrão atual («tudo») para não quebrar o cliente existente:
  - nova RPC local `subscribe{conversations: "all" | [ids], level: "full" | "summary"}` interceptada no loop da sessão (`rs/remote_relay.rs:504-568`), não em `dispatch`;
  - `summary` repassa apenas turnStarted, PermissionRequest/Cancelled, TurnComplete/Error, Exited, RateLimit, conversations-changed e queue-update, sem deltas e sem `pty-output`. A lista das conversas abertas recebe `full`;
  - `pty-output` nunca vai para um aparelho remoto, já que pty está bloqueado em (h).
- **Lagged.** Em `:551-553`, além do log, enviar `Tun::Event{event:"resync", payload:{}}`. O celular reage chamando `listPending` e `listConversations`.
- **Push com o app fechado (FCM).** Está fora do escopo do host atual: o relay só tem WebSocket. É uma decisão em aberto. Sem push, as notificações exigem um serviço em primeiro plano no Android mantendo o túnel aberto.

---

## (d) Sub-agentes: `parent_tool_use_id`

O que acontece hoje (verificado por leitura):

- `rs/engines/claude.rs:71-72` calcula `nested = v.get("parent_tool_use_id").is_some_and(|p| !p.is_null())`.
- `rs/engines/claude.rs:105-107` **descarta** inteiramente os `stream_event`, `assistant` e `user` aninhados (`if !nested`).
- `ToolStart` sai sempre com `nested: false`: claude.rs `:188` e `:254`, codex.rs `:277`, agy.rs `:106`, openai.rs `:585`.
- O React (`web/App.tsx:57-64`) detecta sub-agente por `nested === true` **ou** pelo nome da ferramenta (contém agent/subagent, ou é `Task`/`delegate`). Na prática, só a segunda heurística dispara.
- Resultado: o celular, e também o desktop, vê o `ToolStart` da ferramenta `Task` e depois o `ToolResult` final, **sem nada do que o sub-agente fez no meio**.

O que falta, no desenho mínimo:

- Nova variante em `rs/engines/mod.rs:16-85`:
  ```rust
  SubagentActivity { parent_tool_use_id: String, kind: String /* "tool" | "text" | "result" */,
                     tool_id: Option<String>, name: Option<String>, text: Option<String> }
  ```
  É serializada em camelCase, como as demais.
- Em `claude.rs:105-107`, no ramo `nested`, emitir:
  - `SubagentActivity` para mensagens `assistant` completas (cada bloco `tool_use` vira `kind:"tool"` com nome; o texto vira `kind:"text"`, truncado a 2 KiB);
  - para mensagens `user` com `tool_result`, `kind:"result"` com uma prévia curta.
  - **Não** emitir os `stream_event` aninhados (deltas), para conter o volume.
- `Assembler.apply` ignora a variante pelo `_ => {}` (cerca de `rs/runtime.rs:172`), então ela não é persistida (decisão: só ao vivo). Ela sai pelo caminho comum em `rs/runtime.rs:1373`.
- React (opcional): em `App.tsx:57-64`, agrupar `subagentActivity` sob o card da ferramenta-pai. O app Android usa o mesmo evento para a árvore de sub-agentes.
- Codex, agy e openai: nenhum equivalente foi encontrado («não verificado» se o CLI do codex expõe sub-agentes).

---

## (e) R-110: resposta maior que 64 KiB derruba a sessão

Fatos:

- `rs/remote_relay.rs:533` usa `send_frame(&out, device, &session.seal(&msg)?)?;`. Um RpcResult selado acima de `MAX_DATA` vira `Err`, o `?` sai do loop e **a sessão morre**. O limite medido é de cerca de 65.450 B de resposta (r110-medida.md).
- `:549` corre o mesmo risco com eventos: um `ToolResult` ou `ToolInput` grande, como a leitura de um arquivo longo, derruba a sessão.
- No sentido cliente→host vale o mesmo limite. `saveUpload` (`rs/api.rs:169-174` `UploadArg{name, mime, data base64}`, `:809-817`, `MAX_UPLOAD` = 25 MiB em `:209`) **não cabe** no túnel para fotos ou áudio acima de cerca de 45 KiB brutos.

Correção em três fases (a fase 1 é obrigatória antes de liberar o app):

1. **Não morrer.**
   - Em `:533`, se o selado passar de `MAX_DATA`, responder `RpcResult{id, error:"resposta excede o limite do túnel (N bytes)"}` e seguir.
   - Em `:549`, para `conv-event` acima do limite, truncar `output`/`input`/`text` com `truncated:true` e o tamanho original. Se ainda não couber, enviar `{event:"eventTooLarge", payload:{conversationId, turn, kind}}`.
   - Envolver os dois `send_frame` para que só um erro de canal fechado encerre a sessão.
2. **Fragmentação genérica.**
   - Novo `Tun::RpcPart{id, seq, last, data /* b64u */}` em `rs/tunnel.rs:51-99`, com pedaços de cerca de 45.000 B (folga para selo e base64) e teto total de cerca de 8 MiB por resposta.
   - Só é usado se o `Hello` do cliente anunciar `caps:["frag"]` (campo opcional com `#[serde(default)]`). Cliente antigo recebe o erro da fase 1.
   - No sentido c2h, o mesmo `RpcPart` reconstrói `Rpc.params`, o que resolve o upload de câmera e microfone sem RPC nova. Alternativa equivalente: `uploadBegin{name,mime,size}` → `uploadChunk{uploadId,seq,data}` → `uploadEnd{uploadId}`, devolvendo `{path, mime}` (o `Attachment` de `rs/engines/mod.rs:152-155`), que vai para `SendArg.attachments` (`rs/api.rs:76-81`).
3. **Paginação de `getConversation`.**
   - Params opcionais `{beforeTurn?, limitTurns?}`. Sem eles, o comportamento atual é mantido.
   - Nova `store::list_blocks_page`, junto de `list_blocks` (`rs/store.rs:410-428`), usando o índice `blocks_conversation(conversation_id,turn,seq)`:
     ```sql
     SELECT turn,seq,kind,content_json,created_at FROM blocks
      WHERE conversation_id=?1 AND turn IN (
        SELECT DISTINCT turn FROM blocks WHERE conversation_id=?1 AND (?2 IS NULL OR turn<?2)
        ORDER BY turn DESC LIMIT ?3)
      ORDER BY turn,seq
     ```
   - A resposta ganha `page:{hasMore, oldestTurn}`.
   - Blocos `toolResult` acima de 16 KiB saem truncados com `truncated:true`, e uma nova RPC `getBlock{conversationId, turn, seq}` busca o bloco inteiro (pela fase 2).
   - `listWorkspaceFiles` (`rs/api.rs:846-862`, limite padrão de 3000 numa lista plana) também estoura. O celular usa `listDir` (h.3).

Teste de contrato: uma resposta de 100 KiB e um evento de 100 KiB **não** podem encerrar a sessão. Com `frag`, a resposta chega íntegra.

---

## (f) Itens de host do GAP-MOBILE §2

| Item | Onde | Desenho |
|---|---|---|
| Nome do interrupt | `rs/api.rs:618-622` (braço `"interrupt"`) | O app Android chama outro nome. Correção principal: o cliente usa `interrupt`. Tolerância no host: `"interrupt" \| "interruptConversation" =>`. O método fica em `rs/runtime.rs:923-929`. |
| Nonce do aparelho (R-173) | `rs/tunnel.rs:154-159` (`hello`), `:163-177` (`into_session`; `Hkdf::new(None, shared)` em `:169`) | **A correção necessária é só no cliente.** O host assina os bytes crus do primeiro quadro (`rs/remote_relay.rs:445-454`, transcrito `"aistack-host-auth:" + first + eph_hello`) e gera uma efêmera por sessão (`:439`). Opcional no host: `Hello` ganha `#[serde(default, rename="n", skip_serializing_if="Option::is_none")] nonce: Option<String>`. Se presente, vira o salt do HKDF; se ausente, o comportamento atual (compatível). |
| `drop(tx)` no relay (F3-95) | `relay/src/client.rs:142-143` | Fica no **crate do relay**, não no host. Soltar `tx` antes de `writer.await` para o writer terminar. Exige novo deploy do relay. |

Defeitos novos encontrados por leitura (não reproduzidos):

- **F-a, sessão zumbi em `client_left`.**
  - O supervisor (`rs/remote_relay.rs:318-324`) chama `spawn_device` para **qualquer** mensagem de texto com campo `device`, inclusive `client_left`.
  - A sessão criada fica esperando o hello até o `HELLO_TIMEOUT` (15 s, `:46`).
  - Correção: só fazer spawn em `type=="client_joined"`. Em `client_left`, remover e abortar a sessão do aparelho.
- **F-b, limpeza que apaga a sessão nova.**
  - `spawn_device` (`:366-408`) insere a sessão nova (`:381-385`, substituindo a antiga), mas a limpeza em `:404-405` faz `st.sessions.remove(&device)` **incondicionalmente**.
  - Se uma sessão antiga, como o zumbi de F-a após uma reconexão em menos de 15 s, terminar depois, ela remove a entrada da sessão viva.
  - A partir daí, `deliver_incoming` (`:350-364`) descarta os quadros, e o hello do celular fica sem resposta.
  - Correção: remover só se `h.outgoing.same_channel(&out_tx)`.
- **F-c, RPCs em série bloqueiam eventos.**
  - O loop (`:504-568`) despacha cada RPC dentro do `select`. Uma RPC lenta, como `sendMessage` com `ensure_live`, `gitPushOrPr` ou `pickFolder` (que abre um diálogo **no desktop**), congela os eventos daquele aparelho.
  - Correção: `tokio::spawn` por RPC, com o resultado voltando por um `mpsc` para o próprio `select`. O `seal` continua serial (contador de nonce).
- Cosmético: comentário de doc duplicado em `:347-348`.

---

## (g) RPC de slash commands

**Já existe**: `rs/api.rs:656-661` `"listSlashCommands"` com `SlashCommandsArg` (`:36-39`, `{provider, projectPath?}`). Ela chama `slash_commands::list_commands(provider, project_path, home)` (`rs/slash_commands.rs:24-28`) e devolve `[SlashCommandItem{name, description, provider, source:"builtin"|"custom"|"skill"}]` (`:14-22`).

Mudanças:

- Liberar a RPC no perfil remoto (h).
- O tamanho da resposta é «não verificado». Deve ser pequeno, mas com muitas skills pode se aproximar de 64 KiB. A fase 1 de (e) cobre o pior caso.
- Executar o comando: o celular envia o texto `/comando args` por `sendMessage`, como o desktop faz. O comportamento por provedor é «não verificado».

---

## (h) Lista mínima de métodos permitidos para o aparelho remoto

Base: o inventário `docs/auditoria/rpc-inventario.md` classifica 85 métodos, 18 deles de alto risco (§1, §3), e define a política no §4.

### h.1 Mecanismo

- Em `rs/api.rs`, `const REMOTE_ALLOWED: &[&str]`. Em `dispatch_as`, quando `Caller::Remote` e o método não está na lista, `bail!("método não permitido para aparelho remoto: {method}")`. O erro volta como `RpcResult.error` e a sessão continua.
- Verificações por argumento, em `dispatch_as`, antes do braço:
  - `createConversation`: `projectPath` precisa estar em `recentProjects` ou dentro de `HOME`. `permissionMode: Bypass` é rejeitado; rebaixar para Ask é uma alternativa em aberto.
  - `setConversationOptions`: rejeitar `Bypass` (`PermissionMode` em `rs/engines/mod.rs:116-123`).
  - `revokeDevice`: só o próprio `device_id`.
  - `saveUpload`/upload fragmentado: grava apenas no diretório temporário de uploads do app.
  - `listDir`/`readFile` remotos: `resolve_in_scope(path)` (canonicaliza e exige prefixo em um projeto conhecido ou em `extraDirs` da conversa).
- Teste de contrato: todo braço de `dispatch` precisa estar em `REMOTE_ALLOWED` **ou** numa lista `REMOTE_DENIED` explícita, ou o teste falha. Assim, um método novo nunca fica liberado por omissão.

### h.2 Permitidos

- **Leitura geral:** `appInfo`, `listAccounts` (somente leitura), `getCatalog`, `relayStatus`, `listDevices`.
- **Conversas:**
  - `listConversations`, `recentProjects`, `getConversation` (com paginação), `searchConversations`;
  - `createConversation`, `renameConversation`, `archiveConversation`, `forkConversation` (os nomes exatos dos braços estão no inventário §3);
  - `setConversationOptions`, sem bypass.
- **Interação:** `sendMessage`, `queueMessage`, `sendNow`, `unqueueMessage`, `interrupt` (com o alias), `answerPermission`, `listSlashCommands`.
- **Contexto:** `gitInfo`, `gitDiff` (somente leitura), `listMcp`.
- **Arquivos e mídia:** `saveUpload` (via fragmentação ou chunk), mais as novas `listDir` e `readFile` com escopo.
- **Novas:** `listPending`, `getBlock`, `subscribe` (local da sessão), `uploadBegin`/`uploadChunk`/`uploadEnd` (se for a via escolhida).
- **Restrita:** `revokeDevice`, só para o próprio aparelho.

### h.3 Explorador de arquivos

- Nova `listDir{path}` que lista **um nível**: `[{name, kind:"file"|"dir", size, mtime}]`, ordenada (pastas primeiro), com até 500 entradas e `truncated`.
- `readFile{path, offset?, limit?}` com escopo e limite de 48 KiB por página.
- Ambas usam `resolve_in_scope`.
- Prévia de imagem: `readFile` em base64 paginado pela fase 2.

### h.4 Negados (padrão)

Tudo o que não está em h.2. Explicitamente:

- `pickFolder`/`pickDirectories` (bloqueiam num diálogo do desktop);
- `pty*`;
- `bws*`;
- `cdp*`;
- `copyFile`;
- `cleanTempDir`;
- `addOwnMcp`;
- `fetchApiModels` (baseUrl livre);
- `relayPairInfo` (sobretudo com force);
- instalar, atualizar ou fazer logout de CLIs;
- `listWorkspaceFiles` (tamanho; o celular usa `listDir`).

A escalada de privilégio (bypass, shell, segredos) só com confirmação local no desktop, conforme o §4 do inventário. Essa confirmação está fora do escopo mínimo.

---

## Ordem de implementação sugerida

1. **Robustez do túnel**: R-110 fase 1 (e), F-a e F-b, F-c (concorrência de RPC), e o `resync` em Lagged. Sem isso, o app cai em uso real.
2. **Identidade e política**: `Caller` e `dispatch_as`, `REMOTE_ALLOWED` com teste de contrato (h), e o alias `interrupt` (f).
3. **Origem mobile**: migração 0003, store, `create_conversation` com origem, `types.ts` e o ícone na Sidebar (a), mais o payload rico e o `busyById` (b).
4. **Pendências**: `pending_perms`, `listPending`, `pending` em `getConversation` e `subscribe` (c).
5. **Tamanho**: fragmentação `RpcPart` com `caps:["frag"]` (cobre upload de câmera e microfone), paginação de `getConversation` e `getBlock` (e).
6. **Sub-agentes**: `SubagentActivity` (d).
7. **Explorador**: `listDir` e `readFile` com escopo (h.3).
8. **Relay**: F3-95 `drop(tx)` e o deploy (f).
9. **Opcional**: o nonce R-173 no host (f).

Cada etapa é compatível com o cliente atual: campos novos são opcionais, e o comportamento padrão continua igual.

---

## Itens «não verificado»

- O fluxo (b) ponta a ponta não foi executado; a conclusão vem de leitura.
- O tamanho real da resposta de `listSlashCommands`.
- `AskUserQuestion` chega provavelmente como `PermissionRequest{tool:"AskUserQuestion"}`, já que não há tratamento especial em `claude.rs:129`. O app deve renderizar o caso como pergunta.
- Se codex, agy e openai expõem atividade de sub-agente.
- Os defeitos F-a e F-b foram deduzidos da leitura e não foram reproduzidos.
- Se o relay entrega `client_left` antes do `client_joined` na reconexão, o que agravaria o F-b.
