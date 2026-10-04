# 01 — Contrato de fio do app Android (AiStack relay + túnel E2E + RPC/eventos)

> Escopo: o que o app Android precisa falar com o host desktop do AiStack, com exemplos JSON exatos.
> Fonte: o código em `/home/diego/Documents/AiStack` (somente leitura). As referências são `arquivo:linha`, relativas a essa raiz.
> Convenção: **«não verificado»** marca o que não foi confirmado no código. **«inferido»** marca uma dedução a partir de código lido, sem teste em execução.
> Cliente de referência: o cliente web TypeScript `src/lib/relay.ts`, que fala exatamente este protocolo pelo navegador.

---

## 0. Visão geral das camadas

```
[Android] --WS /client?host=<hostId>&device=<deviceId>--> [relay] --WS /host--> [AiStack desktop]
            payload binário opaco (cifrado E2E)          cabeçalho 0x01|len|device|payload
```

1. **Relay (texto e binário).** É um WebSocket burro que roteia quadros binários por `device_id`. Ele também envia algumas mensagens de controle em texto JSON.
2. **Túnel.** Depois do `hello` em claro, todo quadro binário é um `Tun` cifrado (X25519 + HKDF-SHA256 + AEAD).
3. **Aplicação.** Dentro do túnel trafegam `Tun` JSON com `t ∈ {hello, pair, pairResult, rpc, rpcResult, event, close, hostAuth}` (`src-tauri/src/tunnel.rs:50-99`).

---

## 1. Quadros do túnel, handshake e RPC

### 1.1 Relay (transporte)

| Item | Valor | Fonte |
|---|---|---|
| URL do host (padrão) | `wss://aistack.amberwrite.com.br/host` | `src-tauri/src/remote_relay.rs:36` |
| Rota do cliente | `GET /client?host=<host_id>&device=<device_id>` (WebSocket) | `relay/PROTOCOL.md:14` |
| `host_id` | hex de `sha256(pubkey Ed25519 do host)` | `relay/PROTOCOL.md:13`, `src/lib/relay.ts:238-252` |
| `device_id` | 1 a 64 caracteres de `[A-Za-z0-9_-]`, escolhido pelo cliente. Uma nova conexão com o mesmo id substitui a anterior. | `relay/PROTOCOL.md:17` |
| `device_id` do cliente web | `dev-<b64u de 6 bytes aleatórios>` | `src/lib/relay.ts:75` |
| Limite por mensagem WS | 1 MiB, com close 1009 | `relay/PROTOCOL.md:65-66,99` |
| Clientes por host | 16; o 17º recebe close 4002 | `relay/PROTOCOL.md:68,97` |
| Host offline ao conectar | close 4003 imediato | `relay/PROTOCOL.md:83,98` |
| Ping | O relay pinga a cada 20 s; o cliente deve responder ao pong padrão do WS | `relay/PROTOCOL.md:87-89` |

**Mensagens de controle em texto** (`relay/PROTOCOL.md:76-81`):
- O cliente recebe `{"type":"host_online","host":"<host_id>"}` ao conectar com o host online.
- O cliente recebe `{"type":"host_offline","host":"<host_id>"}` quando o host cai.
- As outras (`client_joined`, `client_left`, `host_challenge`, `host_error`) vão só para o host.
- O app Android **deve ignorar** mensagens de texto que não reconheça.
- O túnel só usa **quadros binários**.

**Formato do quadro**:
- No lado do host, o quadro é `0x01 | len u8 | device_id | payload` (`src-tauri/src/remote_relay.rs:581-596`, `relay/PROTOCOL.md:52-63`).
- **O cliente envia e recebe só o `payload`.** O relay põe e tira o cabeçalho.

> Observação de robustez: `relay-limites.md` (auditoria) registra um vazamento de descritores (CLOSE_WAIT) no relay a cada saída de cliente. Por isso, convém o Android **não reconectar em laço apertado**: usar backoff exponencial.

### 1.2 Handshake do túnel

Sequência (`src-tauri/src/remote_relay.rs:429-502`):

```
Android                                             Host
  |-- (binário, em claro) hello  -------------------->|
  |<------------------ (binário, em claro) hello -----|  efêmero, mesmo aead
  |<------------------ (cifrado) hostAuth ------------|  ctr=0 h2c
  |-- (cifrado) keyAuth (rpc id=1) OU pair ---------->|  ctr=0 c2h
  |<------------------ (cifrado) rpcResult / pairResult|
  |== sessão: rpc/rpcResult/event/close ==============|
```

**Passo 1: hello do cliente, em claro.** São bytes UTF-8 do JSON enviados como quadro **binário**:

```json
{"t":"hello","k":"<b64u da X25519 PERSISTENTE do aparelho, 32 bytes>","aead":"x"}
```

- `aead` aceita dois valores (`src-tauri/src/tunnel.rs:33-41`):
  - `"x"`: XChaCha20-Poly1305. É o padrão quando o campo **falta** (`remote_relay.rs:438`).
  - `"a"`: AES-256-GCM. É o que o cliente web usa (`src/lib/relay.ts:222`).
- O cliente envia sua chave **persistente**, não uma efêmera. É ela que o host confere no registro de aparelhos (`devices.rs:95`, `touch_active`).
- O timeout para o hello chegar é de 15 s (`remote_relay.rs:46,430-433`).

**Passo 2: hello do host, em claro.**

```json
{"t":"hello","k":"<b64u X25519 EFÊMERA do host>","aead":"x"}
```

- O host ecoa o `aead` exatamente como recebeu. Se o cliente omitiu o campo, a resposta também vem sem ele, por causa do `skip_serializing_if` (`tunnel.rs:59-60`, `remote_relay.rs:440`).
- **Guarde os bytes exatos** desse hello e os do seu próprio hello. Os dois entram no transcript do hostAuth.

**Derivação das chaves** (`tunnel.rs:161-176`):

```
shared = X25519(priv_persistente_aparelho, k_efêmera_host)   # rejeitar shared não-contributivo
prk    = HKDF-SHA256(salt = vazio/None, ikm = shared)
h2c    = HKDF-Expand(prk, info = "aistack-tunnel-v1 h2c", 32)
c2h    = HKDF-Expand(prk, info = "aistack-tunnel-v1 c2h", 32)
# O aparelho CIFRA com c2h e DECIFRA com h2c.
```

O `info` é a concatenação `"aistack-tunnel-v1"` + `" h2c"`, ou seja, com espaço (`tunnel.rs:24,172-173`).

**Quadro cifrado** (`tunnel.rs:208-270,330-348`):

```
ctr (u64 big-endian, 8 bytes)  ||  nonce  ||  ciphertext || tag(16)
AAD = os 8 bytes do ctr
"x": nonce = 24 bytes = ctr BE (8) + 16 zeros     → XChaCha20-Poly1305
"a": nonce = 12 bytes = ctr BE (8) + 4 zeros      → AES-256-GCM
plaintext = JSON UTF-8 de um Tun
```

- O contador de envio começa em **0** e incrementa 1 por mensagem, de forma independente por direção (`tunnel.rs:196-206,221-226`).
- Regras de recepção do anti-replay (`tunnel.rs:26-28,280-300`):
  - Janela de 256.
  - Salto máximo de 4096; acima disso a sessão morre.
  - O contador só é marcado como visto depois que o tag valida.
- Um quadro cifrado inválido que chega ao host derruba a sessão (`remote_relay.rs:540`).

**Passo 3: hostAuth, primeira mensagem cifrada do host** (`remote_relay.rs:445-454`, `tunnel.rs:92-98`):

```json
{"t":"hostAuth","pk":"<b64u Ed25519 pub do host, 32B>","sig":"<b64u assinatura Ed25519, 64B>"}
```

- A assinatura é feita sobre: `"aistack-host-auth:" || bytes_do_hello_do_cliente || bytes_do_hello_do_host`.
- O cliente **deve** executar duas checagens; se qualquer uma falhar, aborta (`src/lib/relay.ts:238-252`):
  1. A assinatura valida com `pk`.
  2. `hex(sha256(pk)) == host` do link de pareamento.
- Observação de ordem: o host envia o hostAuth **antes** de ler a identificação. O cliente pode, em paralelo, já enviar o `keyAuth`/`pair`. O cliente web espera o hostAuth antes de enviar (`src/lib/relay.ts:238-267`), e esse é o comportamento recomendado.

**Passo 4a: aparelho já pareado, `keyAuth`** (`remote_relay.rs:478-500`).

Pedido do aparelho:
```json
{"t":"rpc","id":1,"method":"keyAuth","params":{"pk":"<a MESMA b64u enviada no hello.k>"}}
```

Resposta de sucesso:
```json
{"t":"rpcResult","id":1,"result":{"ok":true}}
```

Respostas de erro (o host fecha a sessão em seguida):
```json
{"t":"rpcResult","id":1,"error":"chave não confere com o hello"}
{"t":"rpcResult","id":1,"error":"aparelho desconhecido, revogado ou com chave divergente"}
```

- Particularidade: se o `id` for diferente de 1, o host responde com o `id` pedido **e** envia um segundo `rpcResult` com `id:1` (`remote_relay.rs:496-498`). **Use `id:1` no keyAuth**, como faz o cliente web, cujos ids começam em 1 (`src/lib/relay.ts:178,339-348`).
- O timeout para a identificação é de 15 s (`remote_relay.rs:457-460`).
- Qualquer outra mensagem nessa fase gera o erro `"aparelho não pareado e sem código de pareamento"` e a sessão é encerrada sem resposta (`remote_relay.rs:501`).

**Passo 4b: primeiro pareamento, `pair`** (`remote_relay.rs:462-477`, `tunnel.rs:62-69,101-108`).

Pedido do aparelho:
```json
{"t":"pair","code":"<b64u de 12 bytes, vindo do QR>","device":{"id":"<device_id>","name":"Pixel 10 Pro XL","pk":"<b64u X25519 persistente>"}}
```

Respostas possíveis:
```json
{"t":"pairResult","ok":true}
{"t":"pairResult","ok":false,"error":"código de pareamento inválido ou expirado"}
```

- O host **ignora** o `device.pk` do JSON e grava a chave do `hello` (`remote_relay.rs:463-465`).
- O código tem validade de 600 s e é de **uso único**: é consumido mesmo se o resto falhar (`devices.rs:13,147-153`).
- Repetir o pareamento com o mesmo `device.id` troca a chave e retira a revogação (`devices.rs:155-163`).
- Depois de `pairResult ok`, a sessão **já entra** no laço de RPC; não é preciso um keyAuth (`remote_relay.rs:504-506`).
- Nas reconexões seguintes, use `keyAuth`. O código não deve ser guardado (`src/lib/relay.ts:380-382`, `relay/PROTOCOL.md:264-271`).

**O link do QR** vem de `relayPairInfo`, em `api.rs:964-990` (no arquivo bruto; a leitura usou trechos com deslocamento):
```json
{"url":"https://aistack.amberwrite.com.br/pair?relay=wss%3A%2F%2Faistack.amberwrite.com.br&host=<hex>&pk=<b64u>&code=<b64u>",
 "customSchemeUrl":"aistack://pair?relay=wss://aistack.amberwrite.com.br&host=<hex>&pk=<b64u>&code=<b64u>",
 "relay":"wss://aistack.amberwrite.com.br","host":"<hex>","pk":"<b64u X25519 do host>","code":"<b64u>",
 "expiresInSecs":600,"state":"online"}
```

- O `pk` do QR é a **X25519 estática** do host (`remote_relay.rs:58-59`).
- O hello do host usa uma chave **efêmera**; a ancoragem de identidade vem do hostAuth (Ed25519 → `host`).
- O papel exato do `pk` X25519 do QR no cliente é **«não verificado»**: o cliente web confere o `host` via hostAuth.

**Codificação.** `b64u` é base64url **sem padding** (`tunnel.rs:110-119`). O texto de `relay/PROTOCOL.md` que diz "base64" está impreciso.

### 1.3 RPC (ida e volta)

Pedido (`tunnel.rs:70-76`):
```json
{"t":"rpc","id":7,"method":"sendMessage","params":{"id":"<convId>","text":"oi","attachments":[]}}
```

- Sem parâmetros: o campo `params` pode ser omitido ou ir como `null`. O host trata `null` como `{}` (`remote_relay.rs:513`, `api.rs:17-19`). O cliente web envia `params:null` (`src/lib/relay.ts:336`).
- O host processa os RPCs **em série** dentro de cada sessão, no laço `select!` de `remote_relay.rs:507-542`. Um RPC lento atrasa os eventos e os outros RPCs da mesma sessão «inferido».

Respostas (`tunnel.rs:77-84`, `remote_relay.rs:512-533`), uma de cada caso:
```json
{"t":"rpcResult","id":7,"result":null}
{"t":"rpcResult","id":8,"result":{"id":"...","provider":"claude"}}
{"t":"rpcResult","id":9,"error":"método desconhecido: interruptConversation"}
```

- Métodos que retornam `Value::Null` chegam como `"result":null`. O `skip_serializing_if = "Option::is_none"` não remove `Some(Null)` (`tunnel.rs:80-81`); isso foi verificado pela leitura do serde.
- O erro é `format!("{e:#}")`, uma cadeia anyhow que junta as causas com `": "` (`remote_relay.rs:531`).
- Método inexistente gera `"método desconhecido: X"` (`api.rs`, braço `other =>`).
- **Não há filtro de métodos no túnel**: os 85 métodos de `api::dispatch` ficam acessíveis, incluindo os de risco (veja `docs/auditoria/rpc-inventario.md`).
- Particularidade de `listConversations` pelo túnel: o host converte cada `updatedAt` de RFC3339 para **epoch em milissegundos (número)** (`remote_relay.rs:516-528`). `createdAt` continua string RFC3339. Em `getConversation`, `conversation.updatedAt` também continua string. O Android precisa aceitar os dois formatos.

Encerramento:
```json
{"t":"close","reason":"cliente saiu"}
```

- O cliente envia esse `close` (`src/lib/relay.ts:351`).
- O host envia `{"t":"close","reason":"revoked"}` quando o aparelho é revogado (`remote_relay.rs:556-565`).

### 1.4 Limite de 64 KiB e o comportamento R-110 (crítico)

- `MAX_DATA = 64 KiB` vale para o **payload cifrado** de cada quadro (`remote_relay.rs:39-40`). `send_frame` falha se o payload passar disso (`remote_relay.rs:582-585`).
- O overhead é de ≈82 bytes: ctr 8 + nonce 24 + tag 16 + o envelope JSON do `rpcResult`. Na prática, o JSON do resultado precisa ficar abaixo de ≈65.450 bytes (`docs/auditoria/r110-medida.md`).
- **O que acontece quando passa** (R-110, medido):
  - `send_frame` devolve `Err` dentro de `device_session`, e o `?` em `remote_relay.rs:533` (ou `:549`, no caso de eventos) **encerra a sessão no host**.
  - O cliente **não recebe erro nem close**: o socket WS com o relay continua aberto, mas o host não responde mais nada.
  - Só uma **nova sessão** (novo hello) recupera a conexão.
- Casos típicos:
  - `getConversation` de uma conversa com cerca de 16 turnos normais, ou 5 turnos com saídas grandes de ferramentas.
  - `readFile` de arquivos com mais de ~48 KB (o base64 cresce 33%).
  - `listWorkspaceFiles` com o limite padrão de 3000.
  - `listConversations` com muitas conversas.
  - Eventos `toolResult`/`toolInput` grandes.
- Uma fila de saída cheia (256 quadros, `remote_relay.rs:376`) também mata a sessão, pelo `try_send` em `remote_relay.rs:594-595`.
- **Obrigações do Android**:
  1. Pôr um **timeout por RPC** (sugestão: 15–20 s). Se estourar, considerar a sessão morta e refazer o handshake.
  2. Detectar silêncio prolongado e reconectar.
  3. Evitar respostas grandes: usar `limit` em `listWorkspaceFiles` e não chamar `readFile` em binários grandes.
  4. Até existir paginação, tratar `getConversation` como um método que pode falhar.

---

## 2. Como os eventos chegam ao cliente

### 2.1 Encanamento

1. `runtime.emit(name, payload)` emite ao mesmo tempo para a janela Tauri e para `server::broadcast_emit` (`src-tauri/src/lib.rs:109-123`, `src-tauri/src/runtime.rs:339-346`).
2. `broadcast_emit` publica `{"event":name,"payload":payload}` num `tokio::broadcast` com capacidade 4096 (`src-tauri/src/server.rs:29-36`).
3. Cada sessão de aparelho faz `events.subscribe()` **depois** da identificação (`remote_relay.rs:506`). Cada item vira o quadro abaixo (`remote_relay.rs:543-550`):

```json
{"t":"event","event":"conv-event","payload":{"conversationId":"…","turn":3,"event":{"type":"textDelta","block":0,"text":"Olá"}}}
```

### 2.2 Broadcast, sem assinatura por conversa

- **Não existe "subscribe" por conversa.** Toda sessão recebe **todos** os eventos de **todas** as conversas, inclusive das que não estão abertas no celular.
- O filtro por `payload.conversationId` fica a cargo do app.
- Para notificações, isso é útil: o app vê `permissionRequest` e `turnComplete` de qualquer conversa.
- **Não há cursor nem replay.**
  - O que aconteceu enquanto o aparelho estava desconectado se perde.
  - Ao reconectar, recarregue o estado com `listConversations` e, para a conversa aberta, `getConversation`. Os `busy` e `queue` de `getConversation` ajudam a reconstruir o estado.
- Atraso (`Lagged`): o host só registra em log debug e **descarta silenciosamente** os eventos perdidos (`remote_relay.rs:551-553`).
- Os deltas `textDelta`/`thinkingDelta` são agregados a cada ~16 ms antes de emitir (`runtime.rs:1518-1530`). Mesmo assim, o volume é alto: o app deve processar fora da thread de UI.

### 2.3 Nomes de evento (o campo `event` do quadro)

| `event` | `payload` | Emissor |
|---|---|---|
| `conv-event` | `{conversationId, turn: number\|null, event: EngineEvent}` | `runtime.rs:1070-1072` (`emit_conv`); `turn:null` no `failover` (`router.rs:167-171`) |
| `queue-update` | `{conversationId, queue:[{id,text,attachments:[{path,mime}]}]}` | `runtime.rs:1384-1386`; `QueuedMessage` camelCase em `runtime.rs:34-42` |
| `conversations-changed` | `{}` | criar (`runtime.rs:860`), título no 1º turno (`runtime.rs:892-895`), excluir (`runtime.rs:998`), renomear/arquivar/fork (`api.rs`, braços de `renameConversation`/`archiveConversation`/`forkConversation`) |
| `notice` | `{level:"info"\|"warning"\|"error", message, provider?, slot?}` | runtime/router (`router.rs:172-175`) |
| `usage-update` | `{provider, slot, report: UsageReport, source}` | `runtime.rs:767` |
| `accounts-update` | `AccountStatus[]` | `runtime.rs:714,744` |
| `mcp-update` | `{}` | runtime (status MCP) |
| `api-accounts-changed` | `{}` | runtime |
| `pty-output` | `{id, data: "<base64>"}` | `pty.rs:16-17` |
| `pty-exit` | `{id, code}` | `pty.rs:16-17` |
| `relay-status` | `{state:"desligado"\|"conectando"\|"online"\|"erro", relayUrl, hostId, hostPk, sessions, error?}` | `remote_relay.rs:32,50-63` |
| `devices-changed` | `Device[]` = `[{id,name,pk,pairedAt,lastSeen,revoked}]` (epoch **segundos**) | `remote_relay.rs:34,571-578`, `devices.rs:15-26` |

Formato de `AccountStatus`, espelhado no TypeScript (`src/lib/types.ts:27-37`):

```json
{"provider":"claude","slot":"a","label":null,"email":"…","plan":"max","authState":"authenticated","usage":{…},"usageAt":"…","installed":true}
```

A struct Rust de `AccountStatus` é **«não verificado»**: o formato acima vem do espelho TS.

---

## 3. Métodos úteis ao app (tabela)

Convenções gerais:
- Os argumentos são camelCase (`api.rs:30-200`, todas as structs com `rename_all="camelCase"`).
- `provider` aceita `"claude"|"codex"|"agy"|"kimi"|"deepseek"|"glm"|"qwen"` (`provider.rs:9`).
- `slot` aceita `"a"|"b"` (`provider.rs:91`).
- `permissionMode` aceita `"ask"|"acceptEdits"|"plan"|"bypass"` (`engines/mod.rs:116`).
- `Attachment` tem o formato `{"path":"/abs","mime":"image/jpeg"}` (`engines/mod.rs:152`).

### 3.1 Conversas

| Método (linha em `api.rs`) | `params` | `result` |
|---|---|---|
| `listConversations` (561) | `{"includeArchived":false}` | `Conversation[]`, até 500, com `updatedAt` em **ms** (§1.3) |
| `searchConversations` (913) | `{"query":"texto"}` | formato «não verificado» (provavelmente `Conversation[]`) |
| `recentProjects` (565) | `null` | `string[]`, até 12 caminhos existentes |
| `getConversation` (576) | `{"id":"<convId>"}` | `{"conversation":Conversation,"blocks":Block[],"busy":bool,"queue":QueuedMessage[]}`. ⚠ É a resposta que dispara o R-110. |
| `createConversation` (585) | `{"provider":"claude","projectPath":"/home/diego/proj","model":null,"effort":null,"permissionMode":"ask","extraDirs":[]}` | `Conversation`. Emite `conversations-changed`. |
| `sendMessage` (599) | `{"id":"<convId>","text":"…","attachments":[{"path":"…","mime":"…"}]}` | `null`. O conteúdo chega por `conv-event`. |
| `queueMessage` (604) | o mesmo de `sendMessage` | `null`, com `queue-update` |
| `sendNow` (609) | o mesmo de `sendMessage` (steer/interjeição) | `null`, com `conv-event {type:"steer"}` |
| `unqueueMessage` (614) | `{"id":"<convId>","queueId":"<id>"}` | valor de `rt.unqueue`, formato «não verificado» |
| `interrupt` (618) | `{"id":"<convId>"}` | `null`. ⚠ `interruptConversation` **não existe** (GAP-MOBILE). |
| `answerPermission` (623) | `{"id":"<convId>","requestId":"<req>","decision":{"behavior":"allow","remember":false}}` ou `{"decision":{"behavior":"deny","message":"não"}}` | `null` (`engines/mod.rs:138`) |
| `setConversationOptions` (628) | `{"id":"…","model":?,"effort":?,"permissionMode":?,"projectPath":?,"extraDirs":?}` (todos opcionais exceto `id`) | `Conversation` atualizado «inferido» |
| `renameConversation` (632) | `{"id":"…","title":"Novo"}` | `null`, com `conversations-changed` |
| `archiveConversation` (638) | `{"id":"…","archived":true}` | `null`, com `conversations-changed` |
| `deleteConversation` (645) | `{"id":"…"}` | `null`, com `conversations-changed` |
| `forkConversation` (650) | `{"id":"…","upToTurn":3}` | `Conversation` novo, com `conversations-changed` |
| `switchConversationSlot` (662) | `{"id":"…","slot":"b"}` | `null` |
| `listSlashCommands` (656) | `{"provider":"claude","projectPath":"/home/diego/proj"}` | `[{"name":"…","description":"…","provider":"claude","source":"builtin"\|"custom"\|"skill"}]` (`slash_commands.rs:13-21`) |

Formato de `Conversation` (`store.rs:19-36`, camelCase):

```json
{"id":"…","provider":"claude","nativeSessionId":null,"projectPath":"/home/diego/proj","title":"…",
 "model":null,"effort":null,"permissionMode":"ask","activeSlot":"a","extraDirs":[],
 "createdAt":"2026-10-03T12:00:00Z","updatedAt":"2026-10-03T12:05:00Z","archived":false}
```

Formato de `Block` (`store.rs:38-46`; leitura em `store.rs:410-425` ordenada por `turn, seq`):

```json
{"turn":0,"seq":0,"kind":"user","content":{"text":"…","attachments":[]},"createdAt":"…"}
{"turn":0,"seq":1,"kind":"tool","content":{"id":"t1","name":"Bash","input":{…},"output":"…","status":"done"},"createdAt":"…"}
```

- `kind` aceita `user|text|thinking|tool|error|notice|steer|compacted|compact_boundary` (espelho TS `src/lib/types.ts:77-83`).
- `status` de ferramenta aceita `running|done|error|interrupted`.
- Os blocos são gravados pelo Assembler (`runtime.rs`, struct `Assembler`; linhas «não verificado») **só no `turnComplete`/`exited`**. O turno em andamento não aparece em `blocks` e precisa ser montado a partir dos `conv-event` ao vivo «inferido».
- O conteúdo exato de cada `kind` além de `user` e `tool` é «não verificado».

### 3.2 Arquivos, câmera e microfone

| Método (linha em `api.rs`) | `params` | `result` |
|---|---|---|
| `saveUpload` (809) | `{"name":"foto.jpg","mime":"image/jpeg","data":"<base64 PADRÃO>"}` | `{"path":"/…/uploads/foto.jpg","mime":"image/jpeg"}`. Limite `MAX_UPLOAD`: 25 MB (`api.rs:209`), mas **o quadro só leva 64 KiB** (veja abaixo). |
| `readFile` (769) | `{"path":"~/proj/a.rs"}` | texto: `{"content":"…","size":123,"isBinary":false}`; binário: `{"content":"<base64>","size":…,"isBinary":true,"mime":"…"}`. Limite de 10 MB, também sujeito ao R-110. |
| `listWorkspaceFiles` (846) | `{"path":"~/proj","extraDirs":[],"limit":300}` | `[{"rel":"src/a.rs","path":"/abs/src/a.rs"}]` (`assets.rs:58-63`). Lista plana que respeita o `.gitignore`. |
| `listAssets` (836) | `{"path":"~/proj"}` | `{"root":"…","items":[{"path","rel","kind","size","modified"}],"truncated":bool}` (`assets.rs:38-56`). Só aceita HOME ou /tmp. |
| `isDirectory` (667) | `{"path":"…"}` | `bool` |
| `scanFolder` (818) | `{"path":"…"}` | formato «não verificado» |
| `gitInfo` (725) / `gitDiff` (749) | `{"path":"…"}` / «não verificado» | `GitInfo` (`src/lib/types.ts:248-257`) / «não verificado» |

- **Câmera e microfone**: o app chama `saveUpload` e depois `sendMessage` com `attachments:[{path,mime}]`. O cliente web faz o mesmo, com base64 **padrão** e não b64u (`src/lib/api.ts:275-284`).
- ⚠ Com o teto de 64 KiB por quadro, um `rpc saveUpload` com mais de ~47 KB de binário (o base64 cresce 33%) **excede o quadro**. O que acontece quando o **cliente** envia um quadro acima de 64 KiB é «não verificado»: o relay aceita até 1 MiB (`relay/PROTOCOL.md:65`) e o host decifra sem conferir `MAX_DATA` na entrada (`remote_relay.rs:350-364`). Fotos precisam ser comprimidas ou redimensionadas.
- Não existe um upload em pedaços (chunked) hoje.
- Áudio para texto: não há método de transcrição no host «não verificado». O app pode transcrever localmente (SpeechRecognizer) ou anexar o áudio como arquivo.
- `~` é expandido pelo host (`expand_home`).

### 3.3 Diagnóstico, contas e aparelhos

| Método (linha em `api.rs`) | `params` | `result` |
|---|---|---|
| `appInfo` (459) | `null` | `{version, home, dataDir, binaries, activeSlots, cliStatus?}` (`src/lib/types.ts:154-161`) |
| `listAccounts` (525) / `refreshAccounts` (526) | `null` | `AccountStatus[]` |
| `getCatalog` (557) | `{"provider":"claude","refresh":false}` | `ModelInfo[]` «inferido» (`src/lib/types.ts:39-46`) |
| `relayStatus` (960) | `null` | `RelayStatus` |
| `listDevices` (993) | `null` | `Device[]` |
| `revokeDevice` (997) | `{"id":"<device_id>"}` | «não verificado» |

---

## 4. Eventos `conv-event`: as 15 variantes de `EngineEvent` e os sintéticos

Serde (`src-tauri/src/engines/mod.rs:16-85`):
- `#[serde(tag="type", rename_all="camelCase", rename_all_fields="camelCase")]`.
- Os campos `Option` **são serializados como `null`**: não há `skip_serializing_if`.
- Todos os exemplos abaixo são o valor de `payload.event` dentro de `{"t":"event","event":"conv-event","payload":{"conversationId":"c1","turn":2,"event":…}}`.

| # | `type` | JSON exato | Notas |
|---|---|---|---|
| 1 | `ready` | `{"type":"ready","nativeSessionId":"sess-123","model":"claude-opus-4","permissionMode":"default"}` | `model`/`permissionMode` podem ser `null` |
| 2 | `textDelta` | `{"type":"textDelta","block":0,"text":"Olá, "}` | Agregado a cada ~16 ms. Acrescente ao bloco `block`. |
| 3 | `thinkingDelta` | `{"type":"thinkingDelta","block":0,"text":"pensando…"}` | |
| 4 | `toolStart` | `{"type":"toolStart","block":1,"id":"toolu_01","name":"Bash","nested":false}` | `nested` é **sempre `false`** hoje (veja §4.2) |
| 5 | `toolInput` | `{"type":"toolInput","id":"toolu_01","input":{"command":"ls"}}` | O JSON completo da entrada |
| 6 | `toolResult` | `{"type":"toolResult","id":"toolu_01","output":"a.txt\nb.txt","isError":false}` | `output` pode ser string, array ou objeto |
| 7 | `permissionRequest` | `{"type":"permissionRequest","requestId":"req-9","tool":"Bash","input":{"command":"rm -rf x"},"toolUseId":"toolu_02","suggestions":null,"reason":null}` | **Pendência** para notificar. Responder com `answerPermission`. |
| 8 | `permissionCancelled` | `{"type":"permissionCancelled","requestId":"req-9"}` | Remove a notificação pendente |
| 9 | `rateLimitWait` | `{"type":"rateLimitWait","secondsRemaining":30}` | |
| 10 | `rateLimit` | `{"type":"rateLimit","windows":[{"kind":"fiveHour","label":"5h","usedPct":82.0,"resetsAt":1767225600,"group":null}],"status":"warning","plan":"max"}` | Newtype `UsageReport` achatado (`usage.rs:35,51`; `resetsAt` é `Option<i64>`). `kind` aceita `fiveHour\|weekly\|monthly\|other`; `status` aceita `ok\|warning\|rejected\|unknown`. A unidade de `resetsAt` (segundos ou ms) é «não verificado». |
| 11 | `status` | `{"type":"status","text":"compacted"}` | `text` pode ser `null`. `"compacted"` vem do `compact_boundary`. |
| 12 | `turnComplete` | `{"type":"turnComplete","usage":{"inputTokens":1200,"outputTokens":340,"cacheReadTokens":0,"cacheWriteTokens":0},"costUsd":0.0123,"durationMs":5400}` | `costUsd`/`durationMs` podem ser `null` (`engines/mod.rs:97`). Notificação de "tarefa concluída". |
| 13 | `turnError` | `{"type":"turnError","kind":"rateLimited","message":"…"}` | `kind` aceita `rateLimited\|auth\|overloaded\|interrupted\|other` (`engines/mod.rs:106`) |
| 14 | `exited` | `{"type":"exited","code":1,"detail":"processo terminou"}` | `code` pode ser `null` |
| 15 | `mcpStatus` | `{"type":"mcpStatus","servers":[{"name":"github","status":"connected","detail":null}]}` | **Nunca chega como `conv-event`.** O runtime intercepta e registra (`runtime.rs`, braço `McpStatus` de `handle`), e a UI recebe só `mcp-update {}`. Para ler, use o RPC `listMcp` (`api.rs:863`). |

Na tabela, o valor `"permissionMode":"default"` de `ready` é ilustrativo: o valor bruto é repassado pelo motor «não verificado».

### 4.1 Eventos sintéticos que também chegam em `conv-event`

Esses tipos não estão no enum Rust; são JSON montados no runtime e espelhados no TS em `src/lib/types.ts:102-127`.

```json
{"type":"turnStarted","slot":"a","user":{"text":"faça X","attachments":[]}}
{"type":"steer","text":"na verdade, Y","attachments":[]}
{"type":"failover","provider":"claude","from":"a","to":"b","reason":"rate limit"}
```

- `turnStarted` vem de `runtime.rs:898-902`. Indica que a conversa ficou "busy" e mostra a mensagem do usuário, inclusive quando ela foi enviada **pelo desktop**.
- `steer` vem de `runtime.rs:1410-1425`.
- `failover` vem de `router.rs:167-171`, sempre com `turn:null`, e é acompanhado de um `notice`.

Ciclo típico de um turno:

```
turnStarted → ready → (thinkingDelta|textDelta|toolStart→toolInput→[permissionRequest→permissionCancelled?]→toolResult)* → turnComplete | turnError → (exited?)
```

- Motores em standby não publicam nada (`runtime.rs`, início de `handle`).
- `textDelta`/`thinkingDelta` usam `block` como índice do bloco dentro do turno.

### 4.2 Como aparecem os sub-agentes

- `src-tauri/src/engines/claude.rs:71-72,105-107`: mensagens do stream do Claude com `parent_tool_use_id` não nulo são **DESCARTADAS** ("a UI mostra só o fio principal"). O texto, o raciocínio e as ferramentas **internos** do sub-agente **não trafegam**.
- `nested` é sempre `false` em todos os motores (`claude.rs:188,254`, `codex.rs:277`, `agy.rs:106`, `openai.rs:585`).
- O sub-agente aparece **apenas** como uma ferramenta do fio principal:

```json
{"type":"toolStart","block":2,"id":"toolu_07","name":"Task","nested":false}
{"type":"toolInput","id":"toolu_07","input":{"description":"Auditar relay","prompt":"…","subagent_type":"general-purpose"}}
{"type":"toolResult","id":"toolu_07","output":[{"type":"text","text":"relatório do sub-agente…"}],"isError":false}
```

- O nome pode ser `"Task"` ou `"Agent"`, conforme a versão do Claude CLI. As chaves do `input` são as do CLI («não verificado» no código do AiStack).
- O desktop detecta sub-agentes por heurística (`src/App.tsx:57-71`): `nested===true` **ou** o nome contém `agent`/`subagent` **ou** é igual a `Task` ou `delegate`. O Android deve usar a mesma heurística.
- Para mostrar o progresso **interno** do sub-agente, é preciso mudar o host para parar de descartar `parent_tool_use_id` e emitir `nested:true` com o `parentToolUseId`. Isso não existe hoje.

### 4.3 Perguntas ("questions") e pendências

- **Permissões**: chegam como `permissionRequest` (§4 #7) e são respondidas com `answerPermission`. Vêm do `control_request can_use_tool` do Claude (`claude.rs:129`).
- **Perguntas** (AskUserQuestion e similares): não há nenhuma ocorrência de `AskUserQuestion` no código Rust nem no TS (grep vazio).
  - O desktop tem um tipo `InteractiveQuestion` (`src/lib/types.ts:272-288`), aparentemente extraído do **texto** da resposta por heurística no front «não verificado».
  - A forma de entrega de uma pergunta estruturada é **«não verificado»**. A hipótese mais provável é que venha como um `permissionRequest` com `tool:"AskUserQuestion"` ou como `toolStart`/`toolInput` com esse nome.

---

## 5. Como a UI desktop fica sabendo de uma conversa nova (sessão iniciada no celular)

- **Fluxo atual (funciona sem mudança):**
  1. O celular chama `createConversation`.
  2. `runtime.create_conversation` emite `conversations-changed {}` (`runtime.rs:840-861`).
  3. Esse evento vai **ao mesmo tempo** para a janela Tauri e para os túneis (`lib.rs:109-113`).
  4. No desktop, `App.tsx:113-115` chama `loadConversations()`, que executa `listConversations` + `recentProjects` (`src/stores/app.ts:329-335`).
  5. **A sessão criada no celular já aparece na sidebar do desktop.**
- No 1º `sendMessage`, o título é gerado e há um novo `conversations-changed` (`runtime.rs:892-895`).
- Os `conv-event` de qualquer conversa chegam também à janela desktop. Se o usuário abrir a conversa no desktop, ele vê o turno ao vivo; o controle é compartilhado, porque os dois lados chamam o mesmo `api::dispatch`.
- **O que falta para o ícone de celular:**
  - Não existe campo de origem: `Conversation` (`store.rs:19-36`) e o banco não têm `origin`, `device` nem nada parecido (grep sem resultado). O handler de RPC do túnel também não repassa o `device_id` ao `dispatch` (`remote_relay.rs:513`).
  - Proposta (precisa mudar o host, fora deste documento):
    1. Acrescentar `origin: "desktop"|"mobile"` e `originDevice?: <device_id>` à tabela e ao struct `Conversation`.
    2. Em `device_session`, preencher a origem ao tratar `createConversation`, como já se faz o pós-processamento de `listConversations` em `remote_relay.rs:516-528`, ou passar um contexto de chamador para o `dispatch`.
    3. Na `Sidebar` desktop, desenhar o ícone de celular (SVG) quando `origin==="mobile"`.
  - Alternativa sem migração: o app envia um parâmetro extra `origin` em `createConversation`. Hoje ele seria **ignorado**, porque o serde de `CreateArg` (`api.rs:64`, struct `CreateArg`, sem `deny_unknown_fields`) não nega campos desconhecidos. Portanto, não quebra nada, mas também não grava nada.

---

## 6. Recomendações para o cliente Android

1. Use `aead:"x"` (XChaCha20-Poly1305, padrão do host) com libsodium/Tink, ou `"a"` (AES-GCM nativo do JCA). Os dois funcionam; `"a"` dispensa uma lib nativa.
2. Guarde em armazenamento seguro (Keystore e EncryptedSharedPreferences): a X25519 persistente, o `device_id`, o `host`, o `relay` e o `pk`. **Não** guarde o `code`.
3. Ordem do handshake: hello → hello → hostAuth (verificar) → keyAuth com `id:1` ou pair. Os ids de RPC continuam a partir de 2.
4. Ponha um timeout por RPC e trate o silêncio como sessão morta (R-110), com reconexão e backoff (1 s → 60 s; o host usa o mesmo intervalo em `remote_relay.rs:37-38`).
5. Filtre `conv-event` por `conversationId`. Gere notificações com `permissionRequest`, `turnComplete`, `turnError` e `notice(level=error)`, inclusive de conversas que não estão abertas.
6. Use `interrupt`, nunca `interruptConversation`.
7. Antes de enviar uma imagem ou áudio, comprima abaixo de ~45 KB brutos, até existir um upload em pedaços.
8. Aceite `updatedAt` como número (ms) ou string RFC3339, conforme o método.
9. Use um foreground service ou WorkManager para manter o túnel e receber notificações em tempo real: o protocolo não tem push nem replay.
