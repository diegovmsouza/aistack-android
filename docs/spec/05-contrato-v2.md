# 05 — Contrato v2 do fio app Android ↔ host (NORMATIVO)

Este documento é **normativo**. A1 (app Android) e D1 (host Rust, worktree `AiStack/worktrees/mobile`, branch `feat/mobile-companion`) implementam em paralelo **exatamente** o que está aqui. Quando outro documento divergir deste, vale este.

- Bases: `01-protocolo.md` (fio atual), `04-desktop-mudancas.md` §a, §c–§h e as decisões de `00-plano.md` §1.
- Código conferido por leitura (checkout principal, sem alterações):
  - `src-tauri/src/tunnel.rs`, `remote_relay.rs`, `api.rs`, `engines/mod.rs`, `engines/claude.rs`;
  - `store.rs`, `runtime.rs`, `slash_commands.rs`, `provider.rs`;
  - `src/components/layout/Main.tsx`, `src/components/chat/QuestionCard.tsx`, `src/stores/chat.ts`.
- Os números de linha citados são do `main` em `3a55fcf`.
- Palavras normativas:
  - **DEVE** e **NÃO DEVE** são obrigatórias;
  - **DEVERIA** é a recomendação forte;
  - **PODE** é opcional.
- O que não pôde ser confirmado no código está marcado **«não verificado»**.

## Índice

1. [Convenções gerais e compatibilidade](#1-convenções-gerais-e-compatibilidade)
2. [Anúncio de capacidades (`caps`)](#2-anúncio-de-capacidades-caps)
3. [Fragmentação `rpcPart`](#3-fragmentação-rpcpart)
4. [Mensagens grandes sem `frag` (fase 1) e eventos grandes](#4-mensagens-grandes-sem-frag-fase-1-e-eventos-grandes)
5. [Paginação de `getConversation` e `getBlock`](#5-paginação-de-getconversation-e-getblock)
6. [Origem da conversa (`origin`, `originDevice`)](#6-origem-da-conversa-origin-origindevice)
7. [Evento `resync`](#7-evento-resync)
8. [Atividade de sub-agente (`subagentActivity`)](#8-atividade-de-sub-agente-subagentactivity)
9. [`listPending`, `subscribe`, `listDir`, `readFile`](#9-listpending-subscribe-listdir-readfile)
10. [Alias `interrupt` / `interruptConversation`](#10-alias-interrupt--interruptconversation)
11. [`listSlashCommands`](#11-listslashcommands)
12. [Política remota: `REMOTE_ALLOWED`, `REMOTE_DENIED` e rebaixamento de bypass](#12-política-remota-remote_allowed-remote_denied-e-rebaixamento-de-bypass)
13. [`createConversation` remoto](#13-createconversation-remoto)
14. [`answerPermission` e perguntas estruturadas](#14-answerpermission-e-perguntas-estruturadas)
15. [Datas normalizadas (`updatedAt`)](#15-datas-normalizadas-updatedat)
16. [Tabela final de quadros e eventos](#16-tabela-final-de-quadros-e-eventos)
17. [Sequência de conexão do app v2](#17-sequência-de-conexão-do-app-v2)
18. [Testes de contrato obrigatórios](#18-testes-de-contrato-obrigatórios)
19. [Itens «não verificado»](#19-itens-não-verificado)
20. [Desvios e decisões da implementação D1](#20-desvios-e-decisões-da-implementação-d1)

---

## 1. Convenções gerais e compatibilidade

### 1.1 O que não muda

- Transporte: quadros binários do relay `0x01|len|device|payload`. O payload selado tem no máximo `MAX_DATA = 65536` B (`remote_relay.rs:40`).
- Handshake e chaves:
  - X25519 + HKDF-SHA256 (`"aistack-tunnel-v1 h2c"`/`"… c2h"`);
  - AEAD `"x"` (XChaCha20-Poly1305) ou `"a"` (AES-256-GCM);
  - contador a partir de 0, usado como AAD (`tunnel.rs:221-240`);
  - `hostAuth` assinando `"aistack-host-auth:" ‖ bytes crus do hello do cliente ‖ bytes do hello do host` (`remote_relay.rs`, `device_session`).
- Pareamento (`pair`/`pairResult`) e `keyAuth` (`rpc` id 1) **não mudam**. Aparelhos já pareados continuam válidos: nenhuma chave, nenhum formato de `Device` e nenhum transcrito de assinatura foi alterado.
- Quadros JSON: enum `Tun` com `#[serde(tag="t", rename_all="camelCase")]`. Os nomes no fio são `hello`, `pair`, `pairResult`, `rpc`, `rpcResult`, `event`, `close`, `hostAuth`, e `rpcPart` passa a existir (§3).

### 1.2 Regras de evolução

- Todo campo novo é **opcional** para quem lê:
  - o host DEVE usar `#[serde(default)]` nos campos de entrada novos;
  - o app DEVE ignorar campos desconhecidos e tolerar a ausência de qualquer campo marcado como «v2».
- O host NÃO DEVE usar `deny_unknown_fields` em quadros do túnel.
- **Detecção de recursos.** O app descobre se o host é v2 pelo erro do método:
  - `"método desconhecido: X"` indica host antigo. O app desativa o recurso, sem erro para o usuário.
  - `"método não permitido para aparelho remoto: X"` indica política (§12).
  - A única capacidade negociada no handshake é `frag` (§2).
- **Ordem das respostas.** Com a correção F-c (`04` §f), o host PODE responder RPCs fora de ordem. O app DEVE casar `rpcResult.id` com a chamada e NÃO DEVE assumir FIFO.
- **Ids de RPC.**
  - O `id` é `u64`, único por sessão do túnel e gerado pelo app. O `id` 1 continua reservado ao `keyAuth`.
  - O app DEVE começar as demais chamadas em 2 e incrementar.
- **Erros de RPC.**
  - O erro é sempre `rpcResult.error: string` em português, e a sessão **continua**.
  - Nenhum erro de RPC, de fragmentação ou de tamanho encerra a sessão. Só encerram: canal fechado, quadro que não decifra (anti-replay/AEAD) ou revogação.
  - O host DEVE cortar `error` em 4096 B (fronteira UTF-8) para nunca cair em §4.
- **Quem chama.**
  - Toda RPC que chega pelo relay é despachada como `Caller::Remote{device_id, device_name}` (`04` §a.2).
  - O `device_id` e o nome vêm da sessão autenticada (`keyAuth` ou `pair`), **nunca** dos params. Campos `origin`, `originDevice`, `deviceId` e similares nos params DEVEM ser ignorados.
  - HTTP/Tailscale (`server.rs`) e a janela Tauri continuam `Caller::Local`.

### 1.3 Valores de enums no fio (verificados)

| Tipo | Valores no fio | Fonte |
|---|---|---|
| `Provider` | `claude`, `codex`, `agy`, `kimi`, `deepseek`, `glm`, `qwen` (minúsculas) | `provider.rs:7-17` |
| `Slot` | `a`, `b` | `provider.rs:89-94` |
| `PermissionMode` | `ask`, `acceptEdits`, `plan`, `bypass` | `engines/mod.rs:116-125` |
| `PermissionDecision.behavior` | `allow`, `deny` | `engines/mod.rs:138-150` |
| `TurnErrorKind` | `rateLimited`, `auth`, `overloaded`, `interrupted`, `other` | `engines/mod.rs:105-113` |

**Atenção, `PermissionMode`.**

- `default` e `bypassPermissions` são os nomes da **flag do CLI do Claude** (`PermissionMode::claude_flag`), **não** do fio. Hoje o app envia `"permissionMode":"default"` (`MainActivity.kt:384`), o que falha na desserialização com «unknown variant».
- Regras v2:
  - O app DEVE enviar `ask|acceptEdits|plan` (nunca `bypass`, ver §12.3).
  - O host DEVE aceitar os apelidos na entrada: `#[serde(alias = "default")]` em `Ask` e `#[serde(alias = "bypassPermissions")]` em `Bypass`. A serialização não muda. O apelido serve só de tolerância e não é contrato de saída.

---

## 2. Anúncio de capacidades (`caps`)

### 2.1 Decisão

O anúncio vai **no próprio `hello` do cliente**, como o campo opcional `caps`. **Não** há quadro `Caps` separado nem campo `n` (nonce) no v2.

### 2.2 Por que não quebra a assinatura nem os pareados (verificado)

- `Tun::Hello` (`tunnel.rs:56-61`) não tem `deny_unknown_fields`. Um host antigo desserializa `{"t":"hello","k":…,"aead":…,"caps":[…]}` ignorando `caps`.
- O transcrito do `hostAuth` usa os **bytes crus** do primeiro quadro recebido (`first`) e não um re-serializado. Por isso, a assinatura cobre `caps` sem que o host precise conhecê-lo. Um downgrade (relay malicioso removendo `caps`) invalida a assinatura.
- O host responde com `eph.hello(aead)` re-serializado. O app já guarda `hostHelloBytes` crus (`RelayClient.kt`) e DEVE continuar verificando sobre os bytes recebidos, nunca sobre um re-serializado.
- O pareamento e o `keyAuth` vêm depois do `hello` e não leem `caps`.

### 2.3 Formato

**Cliente → host** (primeiro quadro, em claro):

```json
{"t":"hello","k":"<X25519 efêmera, base64url sem padding, 32 B>","aead":"a","caps":["frag"]}
```

- `caps: string[]` é opcional. Valores definidos no v2: só `"frag"` (§3). Valores desconhecidos DEVEM ser ignorados pelo host.
- O app v2 DEVE anunciar `["frag"]`. No Gson, o campo `caps` é declarado depois de `aead`. A ordem não importa para a assinatura, mas facilita a leitura de capturas.

**Host → cliente** (resposta em claro):

```json
{"t":"hello","k":"<X25519 efêmera do host>","aead":"a","caps":["frag"]}
```

- Rust:

  ```rust
  Hello {
      #[serde(rename = "k")] key: String,
      #[serde(default, skip_serializing_if = "Option::is_none")] aead: Option<AeadProfile>,
      #[serde(default, skip_serializing_if = "Vec::is_empty")] caps: Vec<String>,
  }
  ```

- O host DEVE ecoar em `caps` a **interseção** entre o que o cliente anunciou e o que ele suporta.
- Se o cliente não mandou `caps`, ou a interseção é vazia, o campo é **omitido**. Clientes antigos e o cliente web recebem então um hello byte a byte idêntico ao atual.
- O host DEVE serializar o próprio hello **uma vez** e usar os mesmos bytes para enviar e para o transcrito. O `serde_json::to_vec` é determinístico para a mesma struct, mas a regra evita divergência futura.
- O host guarda `peer_caps` na sessão do aparelho. Esse valor decide §3 e §4 e o padrão de `maxBytes` em §9.4.

### 2.4 Semântica

| Cliente anuncia `frag` | Hello do host traz `caps:["frag"]` | Comportamento |
|---|---|---|
| sim | sim | `rpcPart` liberado **nos dois sentidos** |
| sim | não (host antigo ou sem suporte) | o app NÃO DEVE enviar `rpcPart`; respostas grandes chegam como erro (§4) ou derrubam a sessão (host antigo, R-110) |
| não | — | o host NÃO DEVE enviar `rpcPart`; vale §4 |

**Importante.** Um host antigo que recebe `{"t":"rpcPart",…}` falha no `serde_json::from_slice` do `Tun` e **encerra a sessão** (`remote_relay.rs:539`, `"quadro cifrado inválido"`). Por isso, o app só pode enviar `rpcPart` se o eco chegou.

---

## 3. Fragmentação `rpcPart`

### 3.1 Quadro

```json
{"t":"rpcPart","id":42,"seq":0,"last":false,"data":"<base64url sem padding>"}
```

- Rust: `Tun::RpcPart { id: u64, seq: u32, last: bool, data: String }`.
- `id` é o id da RPC. No sentido c2h é o `id` do `rpc` que está sendo enviado. No sentido h2c é o `id` do `rpc` que está sendo respondido.
- `seq` começa em 0 e cresce de 1 em 1, sem lacunas.
- `last: true` marca o último pedaço. Uma mensagem de um pedaço só é `seq:0,last:true`, mas o remetente NÃO DEVE fragmentar o que cabe inteiro (§3.3).
- `data` são bytes **crus** do pedaço, em base64url sem padding (RFC 4648 §5). O receptor DEVE aceitar também com padding.

### 3.2 O que é fragmentado

A concatenação dos `data` decodificados, em ordem de `seq`, é **o JSON UTF-8 completo de um quadro `Tun`**:

- **c2h:** um `{"t":"rpc","id":42,"method":"…","params":{…}}`;
- **h2c:** um `{"t":"rpcResult","id":42,"result":…}` (ou com `error`).

Regras:

- O `id` interno DEVE ser igual ao `id` dos `rpcPart`.
- O quadro interno NÃO DEVE ser outro `rpcPart`, `event`, `hello`, `pair` ou `close`. Eventos **nunca** são fragmentados no v2 (§4.2).
- Depois de remontado, o quadro é processado exatamente como se tivesse chegado inteiro: o mesmo `dispatch_as`, a mesma política e o mesmo pós-processamento.

### 3.3 Tamanhos (constantes normativas)

| Constante | Valor | Uso |
|---|---|---|
| `FRAG_THRESHOLD` | 60 000 B | o remetente DEVE fragmentar quando o JSON do quadro, antes do selo, passa disso (e o par tem `frag`); abaixo disso, NÃO DEVE fragmentar |
| `FRAG_CHUNK` | 45 000 B | tamanho **cru** de cada pedaço; todos exceto o último DEVEM ter exatamente esse tamanho |
| `FRAG_MAX_TOTAL` | 8 388 608 B (8 MiB) | teto do JSON remontado, nos dois sentidos |
| `FRAG_MAX_PARTS` | 187 | `ceil(8 MiB / 45 000)`; `seq` máximo = 186 |
| `FRAG_TIMEOUT` | 30 s | do primeiro pedaço até o `last` |
| `FRAG_MAX_INFLIGHT` | 4 | remontagens simultâneas por sentido e por sessão |

Conta de folga de um pedaço:

- 45 000 B crus viram 60 000 caracteres de base64url;
- o envelope `{"t":"rpcPart","id":…,"seq":…,"last":…,"data":""}` soma até cerca de 90 B;
- o selo soma 16 B de tag, mais o prefixo de contador se houver;
- total de cerca de 60 110 B, abaixo de `MAX_DATA` = 65 536.

### 3.4 Envio

- Os pedaços de uma mesma mensagem DEVEM sair em ordem de `seq`.
- Pedaços de **ids diferentes** PODEM se intercalar, e quadros `event` PODEM se intercalar entre pedaços (h2c).
- O contador do AEAD continua único e serial: o selo é sempre feito no laço da sessão.
- **Fila de saída do host.**
  - O host NÃO DEVE usar `try_send` para os pedaços, porque 187 pedaços estouram a fila de 256 (`remote_relay.rs`, `send_frame`).
  - DEVE usar envio com espera (`out.send(frame).await`, ou `reserve`) e um tempo-limite de 30 s. Esgotado o tempo, descarta o resto da mensagem.
  - Isso **não** derruba a sessão, a menos que o canal esteja fechado.
- Se o JSON do `rpcResult` passa de `FRAG_MAX_TOTAL`, o host não fragmenta. Ele responde com o erro de §4.1.
- **Upload.** `saveUpload{name, mime?, data}` usa base64 **padrão** no campo `data` (`api.rs:169-174`). Com o teto de 8 MiB de JSON, o arquivo cru útil é de cerca de **6 MiB**. O limite do host, `MAX_UPLOAD` = 25 MiB, continua valendo, mas é inalcançável pelo túnel.
  - O app DEVERIA comprimir foto e áudio antes de enviar.
  - O app DEVE recusar localmente arquivos acima de 6 000 000 B crus, com mensagem clara.
  - Não há RPC nova de upload no v2.

### 3.5 Remontagem e erros

O receptor mantém, por sessão e sentido, `HashMap<id, {next_seq, buf, started_at, failed}>`.

| Situação | Host (c2h) | App (h2c) |
|---|---|---|
| `seq` ≠ `next_seq` (lacuna, repetição, fora de ordem) | descarta o buffer, marca `failed` e responde `{"t":"rpcResult","id":N,"error":"fragmento fora de ordem (id N, esperado S, recebido R)"}` | descarta e falha a chamada `N` com erro local |
| `data` não decodifica | descarta e responde `"fragmento inválido (id N): base64url"` | descarta e falha a chamada |
| soma passa de `FRAG_MAX_TOTAL`, ou `seq` > 186 | descarta e responde `"mensagem fragmentada excede o limite de 8388608 bytes (id N)"` | descarta e falha a chamada |
| mais de `FRAG_MAX_INFLIGHT` ids abertos | ignora o pedaço novo e responde `"fragmentações simultâneas demais (id N)"` | descarta e falha a chamada |
| `FRAG_TIMEOUT` sem `last` | descarta e responde `"fragmentação incompleta: tempo esgotado (id N)"` | descarta e falha a chamada com tempo esgotado |
| remontado não é JSON, não é `rpc` (c2h) ou `rpcResult` (h2c), ou o `id` interno diverge | responde `"mensagem fragmentada inválida (id N)"` | descarta e falha a chamada |
| `rpcPart` com `id` já marcado `failed` | ignora em silêncio até chegar `last:true` (e então esquece o id) | idem |
| `rpcPart` recebido sem `frag` negociado | trata como `"mensagem fragmentada inválida (id N)"` | idem |

- O host verifica o timeout a cada quadro recebido e também num `tokio::time::interval` de 5 s no `select!` da sessão.
- O app verifica com o próprio timer da chamada. O app DEVE dar à chamada fragmentada um prazo de pelo menos 60 s.
- Nenhuma linha desta tabela encerra a sessão.

### 3.6 Exemplo (h2c)

O app chama `getBlock`. A resposta tem 100 000 B de JSON:

```json
{"t":"rpcPart","id":17,"seq":0,"last":false,"data":"eyJ0IjoicnBjUmVzdWx0IiwiaWQiOjE3LCJyZXN1bHQiOnsi…"}
{"t":"event","event":"conv-event","payload":{…}}
{"t":"rpcPart","id":17,"seq":1,"last":false,"data":"…"}
{"t":"rpcPart","id":17,"seq":2,"last":true,"data":"…"}
```

Os dois primeiros pedaços têm 45 000 B crus e o terceiro tem 10 000 B. Remontado, o resultado é `{"t":"rpcResult","id":17,"result":{…}}`.

---

## 4. Mensagens grandes sem `frag` (fase 1) e eventos grandes

### 4.1 Resposta de RPC grande (fase 1, obrigatória)

O host serializa o `rpcResult` e mede `N = len(JSON)` antes do selo. Depois decide:

1. `N ≤ FRAG_THRESHOLD`: envia inteiro.
2. `N > FRAG_THRESHOLD` com `frag` negociado e `N ≤ FRAG_MAX_TOTAL`: fragmenta (§3).
3. Caso contrário, envia:

   ```json
   {"t":"rpcResult","id":7,"error":"resposta excede o limite do túnel (123456 bytes)"}
   ```

Regras:

- O texto é exato: `resposta excede o limite do túnel ({N} bytes)`, com `N` em decimal sem separador.
- O app DEVE reconhecer o prefixo `resposta excede o limite do túnel` e reagir conforme a RPC. Em `getConversation`, reduz `limitTurns` pela metade e repete (§5.1). Nas demais, mostra o erro.
- O envio do quadro, em qualquer um dos três casos, NÃO DEVE propagar erro com `?` para fora do laço. Hoje, `send_frame(…)?` em `remote_relay.rs:533` derruba a sessão (R-110).
- Só canal fechado encerra a sessão. Fila cheia vira log e descarte (eventos) ou espera (pedaços, §3.4).

### 4.2 Eventos grandes (com ou sem `frag`)

Eventos **não** são fragmentados. Antes de selar um `event`, o host mede o JSON e aplica, nesta ordem:

1. **Até 60 000 B:** envia.
2. **Corte de campos** (só `conv-event`). Os campos-alvo de `payload.event`, conforme o `type`, são:

   | `type` | campos cortados |
   |---|---|
   | `toolResult` | `output` |
   | `toolInput` | `input` |
   | `permissionRequest` | `input` |
   | `textDelta`, `thinkingDelta` | `text` |
   | `status` | `text` |
   | `turnError` | `message` |
   | `subagentActivity` | `text` |
   | `turnStarted`, `steer` | `user.text`, `text` |

   Como cada campo é cortado:
   - string: os primeiros 16 384 B, na fronteira UTF-8;
   - não string (objeto, array): vira a **string** do seu JSON, cortada da mesma forma. O tipo muda, e o app DEVE aceitar isso quando `truncated:true`.

   No objeto `payload.event`, o host acrescenta `"truncated":true` e `"originalBytes":N`, onde N é o tamanho do JSON do evento original (na implementação D1, o quadro `event` inteiro em claro; ver desvio D1-6 em §20).

3. **Se ainda não couber**, ou se o evento não é `conv-event`, o original é descartado e o host envia:

   ```json
   {"t":"event","event":"eventTooLarge","payload":{"event":"conv-event","conversationId":"c1","turn":4,"kind":"toolResult","originalBytes":812345}}
   ```

   Para eventos que não são `conv-event`, `conversationId`, `turn` e `kind` vêm `null`.

Exemplo de evento cortado:

```json
{"t":"event","event":"conv-event","payload":{"conversationId":"c1","turn":4,"event":{"type":"toolResult","id":"toolu_01","output":"…primeiros 16 KiB…","isError":false,"truncated":true,"originalBytes":203117}}}
```

- O `permissionRequest` cortado continua respondível. O host usa o `input` **completo** que guardou (`engines/claude.rs:589-611`), e o app nunca devolve o `input`.
- Depois do fim do turno, o app PODE buscar o bloco inteiro com `getBlock` (§5.2).

---

## 5. Paginação de `getConversation` e `getBlock`

### 5.1 `getConversation`

Params:

```json
{"id":"c1","beforeTurn":12,"limitTurns":20}
```

| Campo | Tipo | Regra |
|---|---|---|
| `id` | string | obrigatório |
| `beforeTurn` | integer, opcional | exclusivo: só turnos `< beforeTurn`; ausente significa a partir do mais recente |
| `limitTurns` | integer, opcional | quantos **turnos** (não blocos) devolver; o host limita a `1..=200` |

- Sem nenhum dos dois, o comportamento é o atual: todos os blocos (compatibilidade com a UI local).
- O app v2 DEVE sempre enviar `limitTurns`:
  - 20 com `frag`;
  - 5 sem `frag`;
  - em erro de §4.1, metade do valor, até 1.

Resposta:

```json
{
  "conversation": { "...": "Conversation (§6.1)" },
  "blocks": [
    {"turn":11,"seq":0,"kind":"user","content":{"text":"oi","attachments":[]},"createdAt":"2026-10-03T12:00:00+00:00"},
    {"turn":11,"seq":1,"kind":"tool","content":{"id":"toolu_1","name":"Read","input":{"file_path":"/x"},"output":"…16 KiB…","status":"done"},"createdAt":"…","truncated":true,"originalBytes":90233}
  ],
  "busy": false,
  "queue": [{"id":"q1","text":"depois faça X","attachments":[]}],
  "pending": [],
  "page": {"hasMore": true, "oldestTurn": 11}
}
```

**Seleção dos blocos.**

- O host pega os `limitTurns` turnos mais recentes com `turn < beforeTurn`, usando a SQL de `04` §e.3 (`store::list_blocks_page`).
- Os blocos saem em ordem `turn, seq` **ascendente**.
- `page.hasMore` é true se existe algum turno `< oldestTurn`.
- `page.oldestTurn` é o menor turno devolvido, ou `null` se `blocks` está vazio.

**Presença de `page`.**

- O host v2 DEVE incluir `page` sempre que `beforeTurn` ou `limitTurns` vierem nos params.
- Sem eles, PODE omitir.
- Para o app, a ausência de `page` com `limitTurns` enviado significa «host antigo»: veio tudo.

**Corte de blocos (só `Caller::Remote`).**

- Vale para qualquer bloco cujo `content` serializado passe de 16 384 B:
  - `kind:"tool"`: corta `input` e `output`;
  - `kind` `text`, `thinking`, `steer` ou `user`: corta `text`;
  - a regra de corte é a de §4.2, com 16 384 B por campo.
- O bloco ganha, **no nível do bloco**, `"truncated":true,"originalBytes":N` (N = bytes do `content` original).
- O app busca o inteiro com `getBlock`.

**`kind` de bloco.** Persistidos pelo `Assembler` (`runtime.rs:137-175`): `text`, `thinking`, `tool` (`{id,name,input,output,status:"running"|"done"|"error"|"interrupted"}`), `steer`, `error` (`{kind,message}`) e `compacted`. O bloco do usuário (`kind:"user"`) é gravado fora do `Assembler`, e o seu formato exato é **«não verificado»**. O app DEVE tratar `kind` desconhecido como texto genérico.

**Outros campos.**

- `pending`: lista de `PendingPerm` (§9.1) só daquela conversa, `[]` se não houver. É campo v2.
- `busy` e `queue` não mudam.
- **Turno em andamento:** os blocos só são gravados no fim do turno (`runtime.rs:1314-1329`). Com `busy:true`, o turno corrente **não** está em `blocks`. O app monta o parcial pelos `conv-event` ao vivo, a partir do `turnStarted`.

### 5.2 `getBlock` (nova)

```json
{"t":"rpc","id":30,"method":"getBlock","params":{"conversationId":"c1","turn":11,"seq":1}}
```

Resposta: o `Block` **inteiro**, sem corte.

```json
{"turn":11,"seq":1,"kind":"tool","content":{"id":"toolu_1","name":"Read","input":{…},"output":"…completo…","status":"done"},"createdAt":"2026-10-03T12:00:03+00:00"}
```

- Erros:
  - `"bloco não encontrado: c1/11/1"`;
  - `"conversa não encontrada: c1"`.
- Acima de 60 000 B, a resposta segue §4.1: fragmenta com `frag`, ou dá erro sem ele. O app sem `frag` mostra «conteúdo grande demais para o túnel».

---

## 6. Origem da conversa (`origin`, `originDevice`)

### 6.1 Objeto `Conversation` no fio v2

```json
{
  "id": "c1",
  "provider": "claude",
  "nativeSessionId": "5b2f…",
  "projectPath": "/home/diego/Documents/AiStack",
  "title": "Corrigir R-110",
  "model": "opus",
  "effort": "high",
  "permissionMode": "ask",
  "activeSlot": "a",
  "extraDirs": [],
  "createdAt": 1759492800000,
  "updatedAt": 1759496400000,
  "archived": false,
  "origin": "mobile",
  "originDevice": {"id": "dev_7Hq…", "name": "Pixel 8"}
}
```

**`origin`** (v2) vale `"desktop"` ou `"mobile"`.

- As linhas antigas valem `"desktop"` (migração 0003 com `DEFAULT 'desktop'`, sem backfill).
- O app DEVE tratar valor ausente ou desconhecido como `"desktop"`.

**`originDevice`** (v2) é `{id, name}` ou `null`.

- Só vem preenchido quando `origin == "mobile"`.
- O `name` é **fotografado** na criação. Renomear ou revogar o aparelho depois não altera o campo.
- A forma de guardar fica a cargo de D1: duas colunas `origin_device_id` e `origin_device_name`, ou um JSON em `origin_device`. No fio, é sempre o objeto.

**Regras de atribuição.**

- A origem vem **exclusivamente** do `Caller`: `Caller::Remote{device_id, device_name}` gera `"mobile"` e `{id, name}`; `Caller::Local` gera `"desktop"`.
- Campos de origem nos params são ignorados.
- HTTP/Tailscale é `Local`, portanto `"desktop"`.
- **Fork:** a conversa nova recebe a origem **de quem pediu o fork**, não a da conversa-mãe. Fork pedido no celular gera `"mobile"` com o aparelho que pediu; pedido no desktop gera `"desktop"`.

### 6.2 Onde aparece

| Lugar | Forma |
|---|---|
| `listConversations` → `Conversation[]` | cada item com `origin` e `originDevice` |
| `getConversation` → `.conversation` | idem |
| `createConversation`, `forkConversation`, `setConversationOptions` → `Conversation` | idem (`forkConversation` devolve a conversa nova; o retorno exato hoje é **«não verificado»**, e o app DEVE recarregar a lista se o retorno não for um objeto com `id`) |
| evento `conversations-changed` | ver §6.3 |

### 6.3 `conversations-changed` (payload v2)

```json
{"t":"event","event":"conversations-changed","payload":{"id":"c9","reason":"created","origin":"mobile","originDevice":{"id":"dev_7Hq…","name":"Pixel 8"}}}
```

- `reason` vale `"created"`, `"forked"`, `"renamed"`, `"archived"`, `"deleted"`, `"titled"` (título automático no 1º turno, `runtime.rs:892-895`) ou `"updated"` (opções).
- `id` é a conversa afetada; no fork, a **nova**.
- `origin` e `originDevice` só aparecem em `created` e `forked`.
- O payload `{}` continua válido. O host PODE emitir `{}` onde não tiver o motivo.
- O app DEVE tratar **qualquer** `conversations-changed` como «recarregar `listConversations`» e usar os campos só como dica (toast, destaque).

---

## 7. Evento `resync`

```json
{"t":"event","event":"resync","payload":{"reason":"lagged","missed":137}}
```

- **Quando:** sempre que o `broadcast::Receiver` da sessão do aparelho devolver `RecvError::Lagged(n)` (`remote_relay.rs:551-553`). Hoje isso só gera log de debug.
- Campos:
  - `reason` é sempre `"lagged"` no v2;
  - `missed` é o `n` do `Lagged`.
  - O app NÃO DEVE depender desses campos. O payload `{}` é válido.
- Envio: um `resync` por ocorrência de `Lagged`. Ele passa pelo filtro de `subscribe` em qualquer nível.
- **Reação obrigatória do app:**
  1. `listPending`;
  2. `listConversations`;
  3. para cada conversa aberta na tela, `getConversation{id, limitTurns}`, descartando o parcial ao vivo se `busy:false`.
- A mesma sequência DEVE rodar em **toda (re)conexão** (§17).

---

## 8. Atividade de sub-agente (`subagentActivity`)

### 8.1 Formato

**Não** há evento de topo chamado `subagent`. A atividade viaja **dentro do `conv-event`**, como mais um `EngineEvent`, pelo caminho comum (`runtime.rs:1373`). Assim ela herda o `conversationId`, o `turn` e o filtro de `subscribe`.

```json
{"t":"event","event":"conv-event","payload":{"conversationId":"c1","turn":4,"event":{
  "type":"subagentActivity",
  "parentToolUseId":"toolu_01ABC",
  "kind":"tool",
  "toolId":"toolu_02DEF",
  "name":"Grep",
  "text":"{\"pattern\":\"MAX_DATA\"}",
  "isError":null,
  "at":1759496401234
}}}
```

Rust (`engines/mod.rs`), serializado em camelCase como os demais:

```rust
SubagentActivity {
    parent_tool_use_id: String,
    kind: String,              // "tool" | "text" | "result"
    tool_id: Option<String>,
    name: Option<String>,
    text: Option<String>,
    is_error: Option<bool>,
    at: i64,                   // epoch ms UTC
}
```

| `kind` | `toolId` | `name` | `text` | `isError` |
|---|---|---|---|---|
| `tool` | id do `tool_use` aninhado | nome da ferramenta | prévia do `input` em JSON, ≤ 2 KiB, ou `null` | `null` |
| `text` | `null` | `null` | texto do assistente do sub-agente, ≤ 2 KiB | `null` |
| `result` | id do `tool_use` aninhado a que o resultado responde | `null` | prévia do resultado, ≤ 2 KiB | bool |

### 8.2 Regras

- `parentToolUseId` é o `id` do `toolStart` da ferramenta-pai (`Task` ou `Agent`) no fio principal. O app agrupa as atividades sob esse card.
- Campos `Option` saem como `null`. O app DEVE aceitar `null` ou ausência.
- Os cortes de 2 KiB são na fronteira UTF-8, com `…` no fim.
- **Fonte:** só o Claude, a partir das mensagens `assistant` e `user` com `parent_tool_use_id` não nulo (`engines/claude.rs:71-72,105-107`). Os `stream_event` aninhados (deltas) **não** são emitidos. Para codex, agy e openai não há equivalente («não verificado» para o codex).
- **Não persistido.** O `Assembler` ignora a variante (`_ => {}`), então ela não aparece em `getConversation` nem em `getBlock`. Depois de reconectar, a árvore de um sub-agente já encerrado é perdida, e o app mostra só o `toolStart` e o `toolResult` da ferramenta-pai.
- **`subscribe`:** só é entregue em nível `full` (§9.2).

---

## 9. `listPending`, `subscribe`, `listDir`, `readFile`

### 9.1 `listPending` (nova, somente leitura)

```json
{"t":"rpc","id":5,"method":"listPending","params":{}}
```

Resposta (só conversas **ocupadas** ou com permissão pendente, ordenadas por `lastEventAt` decrescente):

```json
[
  {
    "conversationId": "c1",
    "title": "Corrigir R-110",
    "projectPath": "/home/diego/Documents/AiStack",
    "provider": "claude",
    "busy": true,
    "turn": 7,
    "lastEventAt": 1759496401234,
    "pendingPermissions": [
      {
        "requestId": "req_9",
        "tool": "Bash",
        "toolUseId": "toolu_03",
        "input": {"command": "cargo test"},
        "inputPreview": "{\"command\":\"cargo test\"}",
        "truncated": false,
        "reason": null,
        "since": 1759496400000
      }
    ]
  }
]
```

**`PendingPerm`** (o mesmo formato em `getConversation.pending`):

| Campo | Tipo | Regra |
|---|---|---|
| `requestId` | string | o mesmo do `permissionRequest` e o que vai em `answerPermission` |
| `tool` | string | nome da ferramenta (`Bash`, `Write`, `AskUserQuestion`, …) |
| `toolUseId` | string \| null | |
| `input` | any \| null | o `input` **completo** se o seu JSON tem ≤ 8 192 B; senão `null` |
| `inputPreview` | string | JSON do `input`, cortado em 2 048 B (fronteira UTF-8) |
| `truncated` | bool | `true` se `inputPreview` foi cortado |
| `reason` | string \| null | motivo do motor (codex), se houver |
| `since` | integer | epoch ms em que o pedido chegou |

**Ciclo de vida no host** (`TurnState.pending_perms`):

- entra no `PermissionRequest`;
- sai no `PermissionCancelled`, em `answer_permission` (com sucesso), no `TurnComplete`, no `TurnError` e no `Exited`.

**Outras regras.**

- `turn` é o turno corrente. `lastEventAt` é o epoch ms do último `conv-event` daquela conversa.
- `[]` significa que nada está ativo.
- Sem erros específicos.
- Em host antigo, a chamada devolve `"método desconhecido: listPending"`. Nesse caso, o app vive só dos eventos.

### 9.2 `subscribe` (local da sessão)

Interceptada no laço da sessão do túnel (`remote_relay.rs:504-568`), **antes** de `dispatch_as`. Ela não existe em `api.rs`, não aparece para a UI local nem para o HTTP e não entra nas listas de §12. O estado vale só para aquela sessão e é perdido ao reconectar: o app DEVE chamar de novo a cada conexão.

```json
{"t":"rpc","id":3,"method":"subscribe","params":{"conversations":"all","level":"summary"}}
{"t":"rpc","id":4,"method":"subscribe","params":{"conversations":["c1"],"level":"full"}}
```

| Campo | Tipo | Regra |
|---|---|---|
| `conversations` | `"all"` \| `string[]` | `"all"` define o **nível padrão** e **apaga** os ajustes por conversa; uma lista define o nível **daquelas** conversas, substituindo ajustes anteriores delas |
| `level` | `"full"` \| `"summary"` | obrigatório |

Resposta (o estado resultante):

```json
{"default":"summary","overrides":{"c1":"full"}}
```

**Estado inicial:** `{"default":"full","overrides":{}}`, que é o comportamento de hoje, compatível com o app antigo.

**O que cada nível deixa passar:**

| Evento | `full` | `summary` |
|---|---|---|
| `conv-event` `type` ∈ {`turnStarted`, `permissionRequest`, `permissionCancelled`, `turnComplete`, `turnError`, `exited`, `rateLimit`, `rateLimitWait`, `failover`} | sim | sim |
| demais `conv-event` (`textDelta`, `thinkingDelta`, `toolStart`, `toolInput`, `toolResult`, `status`, `ready`, `mcpStatus`, `steer`, `subagentActivity`) | sim | **não** |
| `failover` com `turn:null`: aplica-se o nível da `conversationId` do payload | | |
| eventos globais (`conversations-changed`, `queue-update`, `notice`, `usage-update`, `accounts-update`, `mcp-update`, `api-accounts-changed`, `relay-status`, `devices-changed`, `resync`, `eventTooLarge`) | sim | sim |
| `pty-output`, `pty-exit` | **nunca** para aparelho remoto, em qualquer nível | |

- Erros:
  - `"parâmetros inválidos para subscribe: …"` (`level` desconhecido, `conversations` mal formado);
  - em host antigo, `"método desconhecido: subscribe"`, e então tudo chega como `full`.
- Uso recomendado:
  - `{"conversations":"all","level":"summary"}` logo após o `keyAuth`;
  - `{"conversations":[idAberta],"level":"full"}` ao abrir uma conversa;
  - `{"conversations":[idAberta],"level":"summary"}` ao sair dela.

### 9.3 `listDir` (nova)

```json
{"t":"rpc","id":40,"method":"listDir","params":{"path":"/home/diego/Documents/AiStack/src-tauri"}}
```

Resposta:

```json
{
  "path": "/home/diego/Documents/AiStack/src-tauri",
  "entries": [
    {"name":"src","kind":"dir","size":null,"mtime":1759490000000},
    {"name":"Cargo.toml","kind":"file","size":2310,"mtime":1759480000000}
  ],
  "truncated": false
}
```

**`path`.**

- Absoluto, ou começando por `~` (expandido para `HOME`).
- Relativo dá erro.
- **Ausente ou vazio:** lista as **raízes do escopo**, cada uma com `name` igual ao caminho absoluto canônico, `kind:"dir"` e `path:null` na resposta.

**`entries`.**

- Um nível só. Pastas primeiro, depois por nome, sem diferenciar maiúsculas.
- No máximo 500 entradas. `truncated: true` se houver mais.
- Ocultos (`.x`) são incluídos.

**Campos de cada entrada.**

- `kind`:
  - `"file"`;
  - `"dir"`;
  - `"other"` para link simbólico quebrado, socket, fifo ou link cujo alvo canônico sai do escopo.
- `size`: bytes, só para `file`; `null` nos demais.
- `mtime`: epoch ms, ou `null` se indisponível.
- O `path` da resposta é o canônico (`canonicalize`).

**Escopo (`resolve_in_scope`).**

- O caminho canonicalizado DEVE ser igual a uma raiz canônica ou estar dentro dela.
- As raízes são:
  1. todo `projectPath` distinto das conversas, inclusive arquivadas (o mesmo conjunto de `recentProjects`, `api.rs:565-575`);
  2. todo item de `extraDirs` dessas conversas.
- Raízes que não existem são ignoradas.
- `..` e links simbólicos são resolvidos **antes** da comparação.

**Erros:**

- `"caminho precisa ser absoluto: {path}"`;
- `"caminho fora do escopo permitido: {path}"`;
- `"caminho não encontrado: {path}"`;
- `"não é uma pasta: {path}"`;
- `"falha ao ler a pasta: {erro}"`.

### 9.4 `readFile` remoto (nova forma)

Para `Caller::Remote`, o braço `readFile` usa a forma abaixo, **com escopo**. Para `Caller::Local`, o braço atual continua igual (`{content,size,isBinary,mime?}`, `api.rs:769-789`), sem mudança para a UI do desktop.

```json
{"t":"rpc","id":41,"method":"readFile","params":{"path":"/home/diego/Documents/AiStack/README.md","maxBytes":262144}}
```

| Param | Regra |
|---|---|
| `path` | as mesmas regras e o mesmo escopo de `listDir` |
| `maxBytes` | opcional; o host limita a `1..=1048576`; o padrão é 1 048 576 com `frag` e 32 768 sem `frag` |

Resposta de texto:

```json
{"path":"/home/diego/Documents/AiStack/README.md","text":"# AiStack\n…","size":18234,"truncated":false,"language":"markdown"}
```

Resposta binária (sem conteúdo):

```json
{"path":"/home/diego/Pictures/x.png","binary":true,"size":482113,"truncated":false,"language":null,"mime":"image/png","warning":"arquivo binário: conteúdo não enviado ao aparelho"}
```

**Leitura e corte.**

- O host lê no máximo `maxBytes` bytes do início.
- Se cortou no meio de um caractere UTF-8, recua até 3 bytes até a fronteira.
- `truncated` é `size > bytes devolvidos`.

**Detecção de binário.** O arquivo é binário se:

- há um byte NUL nos primeiros 8 192 B; ou
- a parte lida não é UTF-8 válido, depois do recuo de fronteira.

Na resposta binária, `text` está ausente e `binary:true`.

**`language`.** É uma dica pela extensão, e o app DEVE aceitar qualquer string ou `null`. Tabela mínima:

| Extensões | `language` |
|---|---|
| `rs` | `rust` |
| `kt`, `kts` | `kotlin` |
| `java` | `java` |
| `ts`, `tsx` | `typescript` |
| `js`, `jsx`, `mjs`, `cjs` | `javascript` |
| `py` | `python` |
| `go` | `go` |
| `swift` | `swift` |
| `c`, `h` | `c` |
| `cpp`, `cc`, `hpp` | `cpp` |
| `md` | `markdown` |
| `json` | `json` |
| `toml` | `toml` |
| `yaml`, `yml` | `yaml` |
| `html` | `html` |
| `css` | `css` |
| `sh`, `bash` | `shell` |
| `sql` | `sql` |
| `xml` | `xml` |
| `gradle` | `groovy` |
| sem extensão conhecida | `null` |

**Erros:**

- os de escopo de §9.3;
- `"não é um arquivo: {path}"`;
- `"falha ao ler o arquivo: {erro}"`.

Acima de `FRAG_THRESHOLD` vale §4.1. Com `maxBytes` em 32 KiB sem `frag`, o JSON cabe mesmo com escapes no pior caso comum. Se não couber, o erro é o de §4.1, e o app repete com metade.

---

## 10. Alias `interrupt` / `interruptConversation`

```json
{"t":"rpc","id":12,"method":"interrupt","params":{"id":"c1"}}
```

- Resposta: `null` (`{"t":"rpcResult","id":12,"result":null}`).
- O host aceita os dois nomes no mesmo braço: `"interrupt" | "interruptConversation" =>`. Os params são os mesmos: `{"id": string}`.
- O app v2 DEVE usar `interrupt`. O nome antigo existe só por tolerância: o app atual chama `interruptConversation` em `MainActivity.kt:408`.
- Os dois nomes estão em `REMOTE_ALLOWED`.
- Interromper uma conversa sem turno ativo não é erro (comportamento de `runtime.rs:923-929`; o detalhe é «não verificado»).

---

## 11. `listSlashCommands`

```json
{"t":"rpc","id":50,"method":"listSlashCommands","params":{"provider":"claude","projectPath":"/home/diego/Documents/AiStack"}}
```

- Params (`SlashCommandsArg`, `api.rs:36-39`): `provider` é obrigatório (§1.3); `projectPath` é opcional (`null` ou ausente).
- Resposta real (`slash_commands.rs:14-22`, `Vec<SlashCommandItem>` em camelCase), mais o campo v2 `desktopOnly`:

  ```json
  [
    {"name":"/help","description":"Mostra ajuda e comandos disponíveis no Claude Code","provider":"claude","source":"builtin","desktopOnly":false},
    {"name":"/revisar","description":"…","provider":"claude","source":"custom","desktopOnly":false},
    {"name":"/grill-me","description":"…","provider":"agy","source":"builtin","desktopOnly":true}
  ]
  ```

**Campos.**

- `name` começa com `/`.
- `source` vale `"builtin"`, `"custom"` (`~/.claude/commands/*.md` ou `.claude/commands/*.md` do projeto) ou `"skill"` (skills do agy).
- `desktopOnly` (v2, opcional; ausente equivale a `false`):
  - `true` para comandos que dependem de UI ou terminal do desktop;
  - lista mínima: `/btw`, `/grill-me`;
  - o host PODE ampliar a lista.
  - O app DEVE mostrar esses comandos desabilitados, com o rótulo «só no desktop», ou omiti-los.
  - Host antigo sem o campo: o app aplica localmente a mesma lista mínima.

**Execução e erros.**

- Executar um comando é `sendMessage{id, text:"/comando args"}`, como no desktop. O comportamento por provedor é «não verificado».
- Erro: `provider` inválido dá o erro de desserialização do serde.
- O tamanho com muitas skills pode passar de 60 000 B; vale §4.1.

---

## 12. Política remota: `REMOTE_ALLOWED`, `REMOTE_DENIED` e rebaixamento de bypass

### 12.1 Mecanismo

Em `api.rs`:

```rust
pub enum Caller { Local, Remote { device_id: String, device_name: String, frag: bool } } // `frag`: desvio D1-1 (§20)
pub async fn dispatch_as(rt: &Arc<Runtime>, caller: &Caller, method: &str, p: Value) -> Result<Value>;
pub async fn dispatch(rt: &Arc<Runtime>, method: &str, p: Value) -> Result<Value> { dispatch_as(rt, &Caller::Local, method, p).await }
const REMOTE_ALLOWED: &[&str] = &[ /* §12.2 */ ];
const REMOTE_DENIED:  &[&str] = &[ /* §12.4 */ ];
```

Para `Caller::Remote`, nesta ordem:

1. Se o método está em `REMOTE_ALLOWED`, aplica as checagens por argumento (§12.3) e despacha.
2. Se está em `REMOTE_DENIED`, `bail!("método não permitido para aparelho remoto: {method}")`.
3. Caso contrário, `bail!("método desconhecido: {method}")`. Isso mantém a detecção de recursos de §1.2.

O erro volta em `rpcResult.error`, e a sessão continua. Exemplo:

```json
{"t":"rpcResult","id":60,"error":"método não permitido para aparelho remoto: ptyOpen"}
```

### 12.2 `REMOTE_ALLOWED` (lista final, 31 nomes)

| Grupo | Métodos |
|---|---|
| Leitura geral | `appInfo`, `listAccounts`, `getCatalog`, `relayStatus`, `listDevices` |
| Conversas | `listConversations`, `recentProjects`, `getConversation`, `getBlock`, `searchConversations`, `listPending`, `createConversation`, `renameConversation`, `archiveConversation`, `forkConversation`, `setConversationOptions` |
| Interação | `sendMessage`, `queueMessage`, `sendNow`, `unqueueMessage`, `interrupt`, `interruptConversation`, `answerPermission`, `listSlashCommands` |
| Contexto | `gitInfo`, `gitDiff`, `listMcp` |
| Arquivos e mídia | `saveUpload`, `listDir`, `readFile` (forma remota, §9.4) |
| Aparelho | `revokeDevice` (só o próprio) |

`subscribe` também é permitido, mas é local da sessão (§9.2) e não passa por `dispatch_as`. Total: 31 braços de `dispatch`, mais `subscribe`.

### 12.3 Checagens por argumento (só `Caller::Remote`)

| Método | Checagem | Erro |
|---|---|---|
| `createConversation` | `projectPath` e cada `extraDirs` em escopo (§13) | `"pasta do projeto fora do escopo permitido ao aparelho: {path}"` |
| `createConversation`, `setConversationOptions` | `permissionMode == bypass` (ou o apelido `bypassPermissions`) é **rebaixado** para `ask`, sem erro (§12.5) | — |
| `setConversationOptions` | `projectPath` e `extraDirs`, se vierem, em escopo | o mesmo de `createConversation` |
| `gitInfo`, `gitDiff` | `path` em escopo (`resolve_in_scope`) | `"caminho fora do escopo permitido: {path}"` |
| `listDir`, `readFile` | §9.3 e §9.4 | §9.3 |
| `revokeDevice` | `params.id` DEVE ser igual ao `device_id` da sessão | `"aparelho remoto só pode revogar a si mesmo"` |
| `saveUpload` | sem mudança: grava em `rt.paths.uploads_dir()` (`api.rs:815`), com teto efetivo de cerca de 6 MiB (§3.4) | os atuais |
| `getConversation` | corte de blocos (§5.1) | — |
| todos que devolvem `Conversation` | datas normalizadas (§15) | — |

Depois do `revokeDevice` do próprio aparelho, o host envia `{"t":"close","reason":"revoked"}` e derruba a sessão (comportamento atual). O app DEVE apagar as credenciais locais.

### 12.4 `REMOTE_DENIED` (todos os demais braços atuais, explícitos)

- `checkCli`, `installCli`, `updateCli`, `updateAllClis`;
- `refreshAccounts`, `setAccountLabel`, `warmAccount`, `setActiveSlot`, `logoutAccount`;
- `deleteConversation`, `switchConversationSlot`;
- `isDirectory`, `pickDirectories`, `pickFolder`, `readClipboardImage`, `copyFile`;
- `gitSwitch`, `gitWorktreeCreate`, `gitWorktreeDelete`, `gitCommit`, `gitDiscard`, `gitPushOrPr`;
- `openInEditor`, `scanFolder`, `unpackZip`, `cleanTempDir`, `listAssets`, `listWorkspaceFiles`;
- `setMcpEnabled`, `listApiAccounts`, `saveApiAccount`, `removeApiAccount`, `fetchApiModels`, `addOwnMcp`, `removeOwnMcp`;
- `getClaudeChrome`, `setClaudeChrome`;
- `ptyOpen`, `ptyWrite`, `ptyResize`, `ptyClose`;
- `openExternalAgyLogin`, `openExternalOauthLogin`;
- `relayPairInfo`;
- `bwsStatus`, `bwsSetToken`, `bwsDeleteToken`, `bwsSync`, `bwsListSecrets`, `bwsGetSecret`, `bwsCreateSecret`, `bwsDeleteSecret`, `bwsListProjects`;
- `cdpStatus`, `cdpLaunch`, `cdpOpenTab`, `cdpActivateTab`, `cdpCloseTab`.

**Teste de contrato (D1).** Todo literal de braço do `match` de `dispatch_as` DEVE estar em exatamente uma das duas listas, ou o teste falha. Um método novo nunca fica liberado por omissão. `switchConversationSlot` fica negado no v2 e é candidato a liberar depois. `isDirectory` fica negado porque o app usa `listDir`.

### 12.5 Rebaixamento de `bypass`

- Vale para `createConversation` e `setConversationOptions` vindos de `Caller::Remote` com `permissionMode` `"bypass"` ou `"bypassPermissions"`.
- O host troca o valor por `ask` (o `default` do CLI do Claude) e executa normalmente.
- Como o app fica sabendo:
  1. O `Conversation` devolvido traz `"permissionMode":"ask"` **e** o campo extra `"warning"`, presente só quando houve rebaixamento:

     ```json
     {"id":"c9", "...":"…", "permissionMode":"ask", "warning":"modo bypass não é permitido a partir do aparelho; a conversa usa ask"}
     ```

  2. O host emite um `notice`, que também aparece no desktop:

     ```json
     {"t":"event","event":"notice","payload":{"level":"warning","conversationId":"c9","message":"Pixel 8 pediu modo bypass; rebaixado para ask"}}
     ```

- O app DEVE mostrar `warning` ao usuário quando presente e NÃO DEVE oferecer `bypass` na UI.
- Conversas criadas no desktop em `bypass` continuam em `bypass`. O celular só não pode **ativar** o modo. Se o app enviar `setConversationOptions` sem `permissionMode`, o modo atual não muda.

---

## 13. `createConversation` remoto

### 13.1 Params

```json
{"t":"rpc","id":20,"method":"createConversation","params":{
  "provider":"claude",
  "projectPath":"/home/diego/Documents/AiStack",
  "model":"opus",
  "effort":"high",
  "permissionMode":"ask",
  "extraDirs":[]
}}
```

| Campo | Obrigatório | Regra |
|---|---|---|
| `provider` | sim | §1.3 |
| `projectPath` | sim | absoluto ou `~…`; DEVE existir como pasta (`"pasta do projeto não existe: {p}"`, `runtime.rs:837-839`) e estar em escopo |
| `model` | não | string livre, validada pelo motor depois; `null` ou ausente usa o padrão |
| `effort` | não | string livre; `null` ou ausente usa o padrão |
| `permissionMode` | **sim** (`CreateArg` não tem default) | `ask` \| `acceptEdits` \| `plan`; `bypass` é rebaixado (§12.5) |
| `extraDirs` | não (padrão `[]`) | cada item em escopo; validado por `valid_dirs` (`runtime.rs:840`) |

- **Escopo de `projectPath`.** O caminho canônico é igual a, ou está dentro de, uma raiz de §9.3. Ou seja: um projeto de `recentProjects`, um `extraDirs` conhecido, ou qualquer subpasta alcançável por `listDir`.
- Projeto totalmente novo fora dessas raízes **não** é criável pelo celular no v2. `""` (como o app atual envia) dá erro.
- Campos ignorados: `origin`, `originDevice`, `title`, `activeSlot` e qualquer outro.

### 13.2 Retorno

O `Conversation` completo (§6.1), com:

- `origin:"mobile"` e `originDevice:{id,name}` da sessão;
- `createdAt` e `updatedAt` em epoch ms (§15);
- `title` inicial, gerado pelo host (o valor exato é «não verificado»);
- `warning` opcional (§12.5).

```json
{"id":"c9","provider":"claude","nativeSessionId":null,"projectPath":"/home/diego/Documents/AiStack","title":"Nova conversa","model":"opus","effort":"high","permissionMode":"ask","activeSlot":"a","extraDirs":[],"createdAt":1759496500000,"updatedAt":1759496500000,"archived":false,"origin":"mobile","originDevice":{"id":"dev_7Hq…","name":"Pixel 8"}}
```

### 13.3 Efeitos e erros

- **Efeitos:**
  - emite `conversations-changed` `{id, reason:"created", origin:"mobile", originDevice}`;
  - **não** inicia turno;
  - o app DEVE chamar `sendMessage{id, text, attachments}` em seguida.
- **Erros:**
  - de desserialização (campo obrigatório ausente, enum inválido);
  - `"pasta do projeto não existe: …"`;
  - `"pasta do projeto fora do escopo permitido ao aparelho: …"`;
  - os de `valid_dirs`.

---

## 14. `answerPermission` e perguntas estruturadas

### 14.1 Formato (verificado em `api.rs` `PermissionArg` e `engines/mod.rs:138-150`)

```json
{"t":"rpc","id":70,"method":"answerPermission","params":{"id":"c1","requestId":"req_9","decision":{"behavior":"allow","remember":false}}}
{"t":"rpc","id":71,"method":"answerPermission","params":{"id":"c1","requestId":"req_9","decision":{"behavior":"deny","message":"não rode isso agora"}}}
```

| Campo | Regra |
|---|---|
| `id` | id da conversa |
| `requestId` | `permissionRequest.requestId`, ou `PendingPerm.requestId` |
| `decision.behavior` | `"allow"` \| `"deny"` |
| `decision.remember` | só em `allow`; padrão `false`; `true` aplica as `suggestions` do CLI («sempre permitir», `engines/claude.rs:597-600`) |
| `decision.message` | só em `deny`; opcional; padrão `"O usuário negou esta ação."` |
| `decision.answers` | **v2**, só em `allow`; ver §14.2 |

- Resposta: `null`.
- Erros:
  - `"pedido de permissão desconhecido: {requestId}"`, quando já foi respondido, cancelado ou o turno acabou;
  - desserialização.
- O app DEVE tratar o primeiro erro como «já resolvido em outro lugar» e remover o card, sem alarme.
- Depois da resposta, a pendência sai de `listPending`, e o host **não** emite `permissionCancelled`. Outros clientes (o desktop ou outro aparelho) só sabem que foi resolvida pelos eventos seguintes do turno.
- **Recomendação ao D1:** emitir `conv-event` `{"type":"permissionCancelled","requestId":…}` também ao responder, para fechar o card nos outros clientes. Isso é compatível, porque a UI já trata `permissionCancelled`.

### 14.2 Perguntas estruturadas

Há **dois caminhos**, conforme o motor. A leitura do código corrige o plano: só o Claude e o codex emitem `PermissionRequest` (`engines/claude.rs:129`, `engines/codex.rs:108`). `ask_question` e `AskFollowupQuestion` **não** passam por permissão.

**(a) `AskUserQuestion` (Claude), via `permissionRequest`.**

O evento chega assim (`input` no formato do CLI; o formato exato do `input` é «não verificado» contra a versão instalada):

```json
{"type":"permissionRequest","requestId":"req_12","tool":"AskUserQuestion","toolUseId":"toolu_7",
 "input":{"questions":[{"question":"Qual banco usar?","header":"Banco","multiSelect":false,
          "options":[{"label":"SQLite","description":"local"},{"label":"Postgres","description":"servidor"}]}]},
 "suggestions":[],"reason":null}
```

A resposta é `answerPermission` com `answers`. A chave é o texto exato de `question`. O valor é o `label` escolhido, ou os `label`s unidos por `", "` em `multiSelect`, ou texto livre («Outro»):

```json
{"id":"c1","requestId":"req_12","decision":{"behavior":"allow","answers":{"Qual banco usar?":"SQLite"}}}
```

- Host (D1): `PermissionDecision::Allow` ganha `#[serde(default)] answers: Option<serde_json::Map<String, Value>>`.
- Com `answers`, o `updatedInput` enviado ao CLI passa a ser `pending.input` com a chave `"answers"` acrescentada (`engines/claude.rs:597`).
- Sem `answers`, o comportamento é o atual.
- Em motores que não são o Claude, `answers` é ignorado.
- Que o CLI do Claude lê `updatedInput.answers` nesse formato é **«não verificado»**: vem da documentação do Agent SDK e não foi testado aqui. Se não ler, o fallback é `deny` com `message` igual à resposta em texto, e o modelo recebe a resposta pela mensagem de negação.
- Cancelar a pergunta é `{"behavior":"deny","message":"O usuário dispensou a pergunta."}`.

**(b) `ask_question` e `AskFollowupQuestion` (agy, openai e outros), via ferramenta.**

- A pergunta chega como `toolStart{name:"ask_question"|"AskFollowupQuestion"}` + `toolInput{input}`, e o turno termina.
- Formatos de `input` aceitos (`src/lib/questionParser.ts:173-210`):
  - `{"question":"…","options":["…","…"],"is_multi_select":false}`;
  - `{"questions":[{"question":"…","options":[…],"is_multi_select":false}]}`.
  - Só é pergunta se houver texto e pelo menos 2 opções.
- A resposta **não** usa `answerPermission`. Ela é uma mensagem comum, como o desktop faz (`stores/chat.ts:386-390`, `QuestionCard.tsx:40-60`):

  ```json
  {"t":"rpc","id":72,"method":"sendMessage","params":{"id":"c1","text":"2. Postgres: servidor","attachments":[]}}
  ```

- O texto segue o desktop: `"{número}. {(Recomendado) se marcado}{label}{: detalhe se houver}"`, ou o texto livre digitado.
- O app DEVE detectar essas perguntas no `toolInput` ao vivo e no bloco `tool` de `getConversation`, com `name` igual a um dos dois nomes, e só oferecer a resposta com `busy:false`.

---

## 15. Datas normalizadas (`updatedAt`)

- **Regra v2:** para `Caller::Remote`, todo objeto `Conversation` no fio traz `createdAt` e `updatedAt` como **inteiro epoch ms UTC**:
  - `listConversations`;
  - `getConversation.conversation`;
  - `createConversation`;
  - `forkConversation`;
  - `setConversationOptions`.
- A conversão sai de RFC 3339 com `chrono::DateTime::parse_from_rfc3339(..).timestamp_millis()`. Se o parse falhar, a string original é mantida.
- O pós-processamento atual, só de `updatedAt` em `listConversations` (`remote_relay.rs:515-527`), DEVE ser substituído por um normalizador único em `dispatch_as` (ou no laço), aplicado a esses retornos.
- **Campos novos já nascem em epoch ms:** `PendingPerm.since`, `listPending[].lastEventAt`, `subagentActivity.at`, `listDir.entries[].mtime`.
- **Não muda:**
  - `Block.createdAt` continua string RFC 3339;
  - `Device.pairedAt` e `Device.lastSeen` continuam epoch **segundos** (`01` §2.3);
  - `UsageReport` e `AccountStatus` como hoje.
- **App:** DEVE aceitar em `createdAt` e `updatedAt` tanto número (ms) quanto string ISO 8601 ou RFC 3339, para host antigo e para `Block.createdAt`. O commit `b5bf16c` já faz isso para `updatedAt`.

---

## 16. Tabela final de quadros e eventos

### 16.1 Quadros `Tun` (campo `t`)

| `t` | Sentido | Formato | v2? |
|---|---|---|---|
| `hello` | c2h, h2c (em claro) | `{"t":"hello","k":"…","aead":"x"\|"a"?,"caps":["frag"]?}` | `caps` novo |
| `hostAuth` | h2c (selado) | `{"t":"hostAuth","pk":"…","sig":"…"}` | não |
| `pair` | c2h | `{"t":"pair","code":"…","device":{"id","name","pk"}}` | não |
| `pairResult` | h2c | `{"t":"pairResult","ok":bool,"error"?:"…"}` | não |
| `rpc` | c2h | `{"t":"rpc","id":u64,"method":"…","params"?:{…}}` | não |
| `rpcResult` | h2c | `{"t":"rpcResult","id":u64,"result"?:…,"error"?:"…"}` | não |
| `rpcPart` | c2h, h2c | `{"t":"rpcPart","id":u64,"seq":u32,"last":bool,"data":"b64u"}` | **novo** |
| `event` | h2c | `{"t":"event","event":"…","payload":…}` | não |
| `close` | ambos | `{"t":"close","reason":"…"}` (`"revoked"` na revogação) | não |

### 16.2 Eventos (campo `event`) entregues ao aparelho

| `event` | `payload` | Nível `subscribe` | v2? |
|---|---|---|---|
| `conv-event` | `{conversationId, turn: number\|null, event: EngineEvent}` (§16.3) | depende do `type` (§9.2) | `subagentActivity`, `truncated` e `originalBytes` novos |
| `queue-update` | `{conversationId, queue:[{id,text,attachments:[{path,mime}]}]}` | sempre | não |
| `conversations-changed` | `{}` ou `{id, reason, origin?, originDevice?}` (§6.3) | sempre | payload rico novo |
| `notice` | `{level:"info"\|"warning"\|"error", message, provider?, slot?, conversationId?}` | sempre | `conversationId` usado no rebaixamento |
| `usage-update` | `{provider, slot, report: UsageReport, source}` | sempre | não |
| `accounts-update` | `AccountStatus[]` ou `{}` (`runtime.rs:639` emite `{}`, `:714,:744` emitem a lista) | sempre | não |
| `mcp-update` | `{}` | sempre | não |
| `api-accounts-changed` | `{}` | sempre | não |
| `relay-status` | `{state, relayUrl, hostId, hostPk, sessions, error?}` | sempre | não |
| `devices-changed` | `Device[]` = `[{id,name,pk,pairedAt,lastSeen,revoked}]` (epoch s) | sempre | não |
| `resync` | `{reason:"lagged", missed:n}` (ou `{}`) | sempre | **novo** |
| `eventTooLarge` | `{event, conversationId\|null, turn\|null, kind\|null, originalBytes}` | sempre | **novo** |
| `pty-output`, `pty-exit` | — | **nunca** enviados a aparelho remoto | filtro novo |

### 16.3 `EngineEvent` dentro de `conv-event` (`event.type`)

| `type` | Campos | `summary` |
|---|---|---|
| `ready` | `nativeSessionId, model?, permissionMode?` | não |
| `textDelta` | `block, text` | não |
| `thinkingDelta` | `block, text` | não |
| `toolStart` | `block, id, name, nested` | não |
| `toolInput` | `id, input` | não |
| `toolResult` | `id, output, isError` | não |
| `permissionRequest` | `requestId, tool, input, toolUseId?, suggestions, reason?` | **sim** |
| `permissionCancelled` | `requestId` | **sim** |
| `rateLimitWait` | `secondsRemaining` | **sim** |
| `rateLimit` | os campos de `UsageReport` no mesmo objeto (newtype com tag interna) | **sim** |
| `status` | `text?` | não |
| `turnComplete` | `usage:{inputTokens,outputTokens,cacheReadTokens,cacheWriteTokens}, costUsd?, durationMs?` | **sim** |
| `turnError` | `kind, message` | **sim** |
| `exited` | `code?, detail` | **sim** |
| `mcpStatus` | `servers:[{name,status,detail?}]` | não |
| `subagentActivity` | `parentToolUseId, kind, toolId?, name?, text?, isError?, at` (§8) | não |
| `turnStarted` (sintético) | `slot, user:{text, attachments}` | **sim** |
| `steer` (sintético) | `text, attachments` | não |
| `failover` (sintético, `turn:null`) | `provider, from, to, reason` | **sim** |

Qualquer um desses PODE trazer `truncated:true` e `originalBytes` (§4.2). O app DEVE ignorar `type` desconhecido.

### 16.4 RPCs novas ou alteradas no v2 (resumo)

| Método | Params | Resultado |
|---|---|---|
| `getConversation` | `{id, beforeTurn?, limitTurns?}` | `{conversation, blocks, busy, queue, pending, page?}` |
| `getBlock` | `{conversationId, turn, seq}` | `Block` |
| `listPending` | `{}` | `[{conversationId,title,projectPath,provider,busy,turn,lastEventAt,pendingPermissions:[PendingPerm]}]` |
| `subscribe` (sessão) | `{conversations:"all"\|[ids], level:"full"\|"summary"}` | `{default, overrides}` |
| `listDir` | `{path?}` | `{path, entries:[{name,kind,size,mtime}], truncated}` |
| `readFile` (remoto) | `{path, maxBytes?}` | `{path, text, size, truncated, language}` ou `{path, binary:true, size, truncated, language, mime, warning}` |
| `interrupt` / `interruptConversation` | `{id}` | `null` |
| `listSlashCommands` | `{provider, projectPath?}` | `[{name,description,provider,source,desktopOnly?}]` |
| `createConversation` | `{provider, projectPath, model?, effort?, permissionMode, extraDirs?}` | `Conversation` (+`warning?`) |
| `setConversationOptions` | `{id, model?, effort?, permissionMode?, projectPath?, extraDirs?}` | `Conversation` (+`warning?`) |
| `answerPermission` | `{id, requestId, decision:{behavior, remember?, message?, answers?}}` | `null` |

---

## 17. Sequência de conexão do app v2

1. WebSocket no relay. Envia `{"t":"hello","k":…,"aead":"a","caps":["frag"]}` e guarda os bytes crus.
2. Recebe o hello do host (bytes crus) e anota `hostFrag = caps contém "frag"`.
3. Recebe `hostAuth` e verifica a assinatura sobre `"aistack-host-auth:" ‖ meu hello cru ‖ hello do host cru`.
4. Se já pareado, `rpc id 1 keyAuth {pk}` e espera `{"ok":true}`. Senão, `pair`.
5. `subscribe {"conversations":"all","level":"summary"}`. Ignora `"método desconhecido"`.
6. `listPending` e `listConversations`, em paralelo.
7. Se há conversa aberta: `subscribe {[id],"full"}` e `getConversation {id, limitTurns}`.
8. Em `resync`: repete os passos 6 e 7. Em `close{reason:"revoked"}`: apaga as credenciais e volta ao pareamento.
9. Reconexão: tudo desde o passo 1, porque o estado de `subscribe` e de fragmentação é por sessão.

---

## 18. Testes de contrato obrigatórios

**D1 (host):**

1. Todo braço de `dispatch_as` está em exatamente um de `REMOTE_ALLOWED` ou `REMOTE_DENIED`.
2. Método negado devolve `"método não permitido para aparelho remoto: X"`. Método inexistente devolve `"método desconhecido: X"`. A sessão segue viva.
3. Resposta de 100 KiB sem `frag` devolve o erro de §4.1, e a sessão segue viva. Com `frag`, chega íntegra em 3 pedaços.
4. Evento `toolResult` de 100 KiB chega cortado com `truncated:true`, e a sessão segue viva.
5. `rpc` c2h fragmentado de 5 MiB (`saveUpload`) é remontado e despachado. Com 9 MiB, devolve o erro de teto.
6. Hello com `caps` produz uma assinatura que confere. Hello sem `caps` produz um hello do host idêntico ao atual, byte a byte.
7. `createConversation` remoto com `bypass` resulta em `permissionMode:"ask"`, com `warning` e `notice`. A conversa sai com `origin:"mobile"`, e `originDevice` vem da sessão, mesmo com `origin:"desktop"` nos params.
8. `listDir` e `readFile` fora do escopo (incluindo `..` e link simbólico para fora) devolvem erro.
9. `revokeDevice` de outro id devolve erro.
10. `Lagged` provoca o envio de `resync`.

**A1 (app):**

1. Remontagem de `rpcPart` com intercalação de eventos, fora de ordem (erro), timeout e teto.
2. `updatedAt` como número e como string.
3. Campos desconhecidos são ignorados.
4. `rpcPart` nunca é enviado sem o eco de `frag`.

---

## 19. Itens «não verificado»

- Formato exato do `input` de `AskUserQuestion` emitido pelo CLI do Claude, e se o CLI aceita `updatedInput.answers` (§14.2a). O fallback por `deny` com `message` está definido.
- Formato do bloco persistido `kind:"user"` (gravado fora do `Assembler`).
- Valor de retorno atual de `forkConversation` (se é o `Conversation` novo ou `null`) e o `title` inicial de uma conversa criada.
- Comportamento de `interrupt` sem turno ativo (se é `Ok` silencioso).
- Execução de slash commands por provedor via `sendMessage`.
- Sub-agentes no codex.
- Tamanho real de `listSlashCommands` e de `listConversations` (até 500 conversas) contra 60 000 B. Coberto por §3 e §4.1, sem medição.
- O formato `AccountStatus` de `accounts-update` vem do espelho TS (`01` §2.3).
- O payload de `relay-status` foi copiado de `01` e não foi relido nesta passagem.

---

## 20. Desvios e decisões da implementação D1

Registro do host (`AiStack/worktrees/mobile`, branch `feat/mobile-companion`). Cada item tem um rótulo **D1-n**, citado no ponto do texto que ele ajusta. Nenhum muda o fio para o app, salvo onde está dito.

- **D1-1 — `Caller::Remote.frag`.** O `Caller::Remote` carrega também `frag: bool`, a capacidade negociada na sessão. O `readFile` remoto usa esse valor para escolher o padrão de `maxBytes` (§9.4): 32 768 sem `frag`. O fio não muda.
- **D1-2 — `originDevice` guardado em JSON.** A migração `0003_origin.sql` cria as colunas `origin` (texto, padrão `desktop`) e `origin_device` (JSON `{id,name}` ou `NULL`). É a segunda opção de §6.1. No fio, é sempre o objeto.
- **D1-3 — Datas do SQLite.** O normalizador de §15 também aceita o formato `AAAA-MM-DD HH:MM:SS` do `datetime('now')` do SQLite, tratado como UTC, além de RFC 3339. Se nenhum dos dois casar, a string original segue.
- **D1-4 — `setConversationOptions` emite `reason:"updated"`.** É o motivo previsto em §6.3. O `titled` sai da geração automática do título. `renamed`, `archived` e `deleted` saem dos braços correspondentes.
- **D1-5 — Raízes do escopo sem teto.** As raízes de §9.3 vêm de **todas** as conversas, inclusive as arquivadas, e de todos os `extraDirs` delas. O `recentProjects` continua limitado a 12 itens, então o escopo pode ser maior que essa lista.
- **D1-6 — `originalBytes` dos eventos.** Para eventos (§4.2), N é o tamanho do JSON do quadro `{"t":"event",…}` inteiro, em claro, antes do corte, e não só do `payload.event`. Nos blocos (§5), N continua sendo o tamanho do bloco. O app só usa o valor para exibição.
- **D1-7 — `hello_caps`.** O `tunnel.rs` ganhou `Ephemeral::hello_caps(aead, caps)`. O `hello(aead)` continua existindo e equivale a `hello_caps(aead, [])`. O host serializa o hello **uma vez** e usa os mesmos bytes no fio e no transcrito do `hostAuth`. Sem caps negociadas, o hello é byte a byte o do v1 (teste `hello_without_caps_is_v1_bytes`).
- **D1-8 — Fila de saída das respostas.** Cada `rpcResult` ou sequência de `rpcPart` entra numa fila da sessão, com prazo de 30 s (§3.4).
  - O laço só tira o próximo quadro quando há vaga no canal de saída (`reserve`): é o «envio aguardado» de §3.3, sem bloquear o laço.
  - Eventos podem se intercalar entre as partes.
  - Passado o prazo, o restante daquela resposta é descartado e registrado em log.
  - Eventos usam envio sem espera: com a fila cheia, o evento é descartado; só canal fechado encerra a sessão (§4.1).
- **D1-9 — Ids falhos na remontagem.** Um id c2h que falhou (fora de ordem, base64 inválido, teto, timeout) fica marcado para que as partes atrasadas sejam ignoradas em silêncio. A marca é esquecida 60 s (2 × o timeout) depois do início.
- **D1-10 — RPCs em paralelo (F-c).** Cada RPC remota roda na própria task. As respostas podem sair **fora da ordem** dos pedidos. O app já casa respostas por `id`. Só o `subscribe` é respondido em linha pela própria sessão.
- **D1-11 — Controle do relay (F-a, F-b).**
  - Só `{"type":"client_joined","device"}` abre sessão.
  - `{"type":"client_left","device"}` aborta a task da sessão daquele aparelho.
  - Outros textos são ignorados.
  - Um reconnect do mesmo `device` aborta a sessão antiga.
  - A limpeza de uma sessão que termina só remove a entrada do mapa se ela ainda for a dela (`same_channel`).
- **D1-12 — Teste ponta a ponta.** `tests/relay_e2e.rs::contrato_v2_frag_e_subscribe` usa o relay real em porta local. Ele cobre:
  - o eco da interseção de `caps`;
  - o `subscribe`;
  - um `readFile` de cerca de 200 KiB remontado a partir de `rpcPart`;
  - o erro de §4.1 para um cliente sem `frag`, com a sessão seguindo viva.

