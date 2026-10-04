#!/usr/bin/env bash
# turnolongo.sh — relógio de turnos (trabalho/pausa) para sessões longas com um modelo. O SCRIPT é o relógio; o modelo obedece.
# Motor da skill /turnolongo, generalizado do cron-cycle.sh do AiStack (contrato canônico 20/30). Configuração em turnolongo.env
# (ao lado deste arquivo ou apontado por TURNOLONGO_CONFIG); sem config: 20 min de trabalho + 30 de pausa e estado em
# ~/.cache/turnolongo/<slug>. Testado em Linux com systemd --user; macOS, WSL e Windows não foram testados (há fallbacks).
#
# CICLO
#   work [nota]    inicia bloco de trabalho (TL_WORK_MIN, padrão 20). RECUSA se houver pausa em curso ou bloco de trabalho aberto
#                  (em curso ou vencido). Sai de LIVRE e de SEM CICLO.
#   pause [nota]   inicia a pausa (TL_PAUSE_MIN, padrão 30). Avisa (não bloqueia) se a vistoria (TL_VISTORIA) está ausente ou velha.
#   pause --parada "motivo"  depois de uma PARADA (limite de uso, app suspenso): a pausa vale TL_PAUSE_MIN contados do FIM do bloco
#                  (regra autorizada pelo usuário). Exige o motivo e bloco vencido há >= 5 min; se o tempo já passou, a pausa está
#                  cumprida e o 'work' sai na hora; se faltar, a pausa só dura o que falta.
#   livre "motivo" suspende o ciclo (o usuário pediu para trabalhar sem relógio): para os alarmes; 'status' mostra LIVRE (exit 0);
#                  'work' abre o próximo bloco. Os agentes ficam liberados enquanto estiver LIVRE.
#   retomar [nota] EXCEÇÃO: só com autorização do usuário no chat. Encerra a pausa, o bloco aberto ou o LIVRE e abre um bloco NOVO.
#   compensar [min] [nota]  EXCEÇÃO: só com autorização do usuário no chat. Interrompe a pausa e reabre o bloco (mesmo N).
#   encerrar [nota]  encerra o turno longo: para alarmes e aquecimento; 'work' abre um turno novo.
# SCRIPT AUTÔNOMO (exceção autorizada pelo usuário)
#   script "rótulo" -- cmd [args...]  o tempo de um script que roda SEM o modelo (sem stdin, saída em arquivo, modelo em silêncio total,
#                  sem polling) NÃO conta como trabalho, porque não gasta tokens. O wrapper suspende o relógio do bloco, roda o comando,
#                  mede o tempo real e o credita: o prazo que restava antes é o que resta depois. Só dentro de um bloco de trabalho;
#                  limite TL_SCRIPT_MAX_MIN (padrão 120); sai com o exit do comando. Wrapper morto (kill -9, reboot) = órfão, sem crédito.
# LEITURA
#   status         1 linha: fase + tempo restante. exit 0=trabalhando, suspenso por script, LIVRE ou sem ciclo; 10=bloco vencido;
#                  20=em pausa; 30=pausa vencida. Com TL_WARM=1 pode vir uma 2ª linha de AVISO (janela de 5 h sem aquecimento).
#   wait [--max SEG] [--trabalho]  bloqueia até o fim da fase atual, acompanhando créditos de script (rodar em segundo plano).
#                  --max SEG: desiste depois de SEG segundos (exit 3). --trabalho: para AGENTES: espera um bloco de trabalho ATIVO
#                  (sem script do orquestrador em curso); LIVRE ou sem ciclo liberam na hora.
#   anuncio        imprime o quadro do turno: início, fim do bloco, fim da pausa, janela de 5 h (se medida), contexto e documentação.
#   agente         imprime o bloco de regras de relógio que todo agente recebe no prompt.
#   fase           imprime só a fase (work, pause, livre ou none); é o que o boot consulta.
#   config         imprime a configuração efetiva (CHAVE=valor): arquivo, minutos, pastas, contexto, modelo, aquecimento.
#   log "texto"    anota uma linha no log do ciclo.
# JANELA DE 5 H
#   aquecer [--teste]  uma chamada mínima ao CLI (modelo mais barato, sem ferramentas nem sessão) para a janela de 5 h estar contando;
#                  lê só o rate_limit_event, não guarda a saída. Com TL_WARM=1 arma o próximo aquecimento em resetsAt + 20 s.
#                  --teste: mede uma vez e não arma nada.
#   janela [--registrar FIM_EPOCH [UTIL [FONTE]]]  mostra a janela de 5 h conhecida; --registrar grava o fim vindo de outra fonte.
# CONTEXTO
#   autocompact XXXK [--dry] [--aplicar-settings]  "digita" /autocompact XXXK (150K, 250K, 500K, 1M; 100K a 1M): por TL_TYPER, por tmux
#                  ($TMUX_PANE) ou, só com permissão (--aplicar-settings ou TL_AUTOCOMPACT_FALLBACK=settings), gravando autoCompactWindow
#                  no settings.json. Sem canal: avisa o usuário (exit 4, «pendente»). Se o valor já vigora, não faz nada.
# INÍCIO
#   boot --work N --pause N --docs DIR --context XXXK [--slug S] [--title T] [--model M] [--effort E] [--assinatura A] [--warm 0|1]
#        [--autocompact-fallback avisar|settings] [--config ARQ] [--sem-copia] [--sem-autocompact] [--forcar] [--iniciar]
#        [--reusar]  (preenche o que faltar com a config existente: retomar uma sessão nova é «boot --config ARQ --reusar --iniciar»)
#                  grava turnolongo.env, CONTRATO.md, VISTORIA.md e vigia-prompt.txt na pasta de documentação, copia o motor para lá,
#                  digita o /autocompact e, com --iniciar, abre o primeiro bloco e imprime o anúncio. Reconfigura se já houver config.
#        v2: [--modo guerra|economia|normal] (config nova sem --modo nem --context: guerra) [--semanal 0|1] [--reserva PCT] [--vigia-cron "CRON"]: o modo dá o contexto (150K, 200K;
#            normal exige --context), o orçamento semanal, a reserva e o cron do vigia; configs novas usam RETOMAR.md como vistoria
#            e ganham MAPA.md e assuntos/.
# ECONOMIA (v2)
#   hibernar       para rodar EM SEGUNDO PLANO depois do 'pause': dorme no shell até o fim da pausa, aplica o orçamento (estende a pausa
#                  sem acordar o modelo) e só volta com o bloco seguinte aberto (ou se o ciclo mudar).
#   medir          lê a cota (5 h e semana) pela chamada mínima do CLI (~US$ 0,001); recusa se o CLI estiver em outra conta que o app.
#   uso [--registrar 5h=PCT@RESET 7d=PCT@RESET]  mostra ou registra a cota vinda de outra fonte (get_usage do app; RESET em ISO ou epoch).
#   orcamento      a pausa que o ritmo da semana e a janela de 5 h pedem para o último bloco. exit 0 normal, 40 estendida, 50 reserva.
#                  Com TL_SEMANAL=1, 'pause' e 'work' aplicam sozinhos (exit 40 do 'work' = pausa estendida; relance 'hibernar').
#   consumo [--horas N]  para onde vão os tokens (custo equivalente de API, pelos transcritos locais; consumo.py).
#   assunto novo "título" [--objetivo T] | lista | fechar SLUG [nota] | sessao SLUG ID | cli SLUG [--modelo M] [--esforco E]
#                  um arquivo por assunto em assuntos/, índice no MAPA.md e a receita para abrir a sessão do assunto no app
#                  (rotina manual: create_scheduled_task + run_scheduled_task) ou no terminal (claude --bg).
#   retomada [SLUG] [--primeira|--autonomo]  o prompt de retomada exata (o que ler, em que ordem, e o próximo passo).
#   economia [--modo M] [--projeto DIR] [--aplicar]  limites de saída no .claude/settings.local.json do projeto (só com o sim do usuário).
# INTERNOS (alarmes)
#   fire FASE N   checar FASE N [K]   aquecer --auto
# ---fim-do-uso---
set -uo pipefail

SELF="$(readlink -f "${BASH_SOURCE[0]}" 2>/dev/null || printf '%s' "${BASH_SOURCE[0]}")"
SELF_DIR="$(dirname "$SELF")"
TEMPL="${TURNOLONGO_TEMPLATES:-$SELF_DIR/../templates}"

# O que os alarmes (processos novos do systemd) precisam herdar de quem os criou.
SETENV=("--setenv=PATH=$PATH")
for v in CYCLE_STATE_DIR CYCLE_LOG WORK_MIN PAUSE_MIN SCRIPT_MAX_MIN TL_ENTRY TL_CLAUDE_BIN TL_SLUG TL_STATE_DIR TL_UNIT_PREFIX TL_CONSUMO_DIR TL_PROJECTS_DIR TL_CONSUMO_PY; do
  [ -n "${!v:-}" ] && SETENV+=("--setenv=$v=${!v}")
done

# ---------- configuração: env legado > turnolongo.env > padrão ----------
CFG="${TURNOLONGO_CONFIG:-}"
[ -n "$CFG" ] || { [ -f "$SELF_DIR/turnolongo.env" ] && CFG="$SELF_DIR/turnolongo.env"; }
ENTRY_ENV="${TL_ENTRY:-}"; TL_CONFIG_DIR=''
if [ -n "$CFG" ] && [ -f "$CFG" ]; then
  TL_CONFIG_DIR="$(cd "$(dirname "$CFG")" && pwd)"; CFG="$TL_CONFIG_DIR/$(basename "$CFG")"
  . "$CFG"; SETENV+=("--setenv=TURNOLONGO_CONFIG=$CFG")
else
  CFG=''
fi
_top="$(git -C "$SELF_DIR" rev-parse --show-toplevel 2>/dev/null || true)"
TL_SLUG="${TL_SLUG:-$(basename "${_top:-$SELF_DIR}" | tr -c 'A-Za-z0-9_\n-' '-')}"
TL_TITLE="${TL_TITLE:-Turno longo}"
TL_WORK_MIN="${TL_WORK_MIN:-20}"; TL_PAUSE_MIN="${TL_PAUSE_MIN:-30}"; TL_SCRIPT_MAX_MIN="${TL_SCRIPT_MAX_MIN:-120}"
TL_STATE_DIR="${TL_STATE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/turnolongo/$TL_SLUG}"
TL_UNIT_PREFIX="${TL_UNIT_PREFIX:-turnolongo-$TL_SLUG}"
TL_DOCS_DIR="${TL_DOCS_DIR:-$TL_STATE_DIR}" # sem config: log e vistoria ficam no estado, nunca ao lado do motor
TL_LOG="${TL_LOG:-$TL_DOCS_DIR/cycle-log.md}"
TL_VISTORIA="${TL_VISTORIA:-$TL_DOCS_DIR/VISTORIA.md}"
TL_CONTRATO="${TL_CONTRATO:-$TL_DOCS_DIR/CONTRATO.md}"
TL_CONTEXT="${TL_CONTEXT:-}"; TL_WARM="${TL_WARM:-0}"; TL_WARM_MAX="${TL_WARM_MAX:-4}"
TL_ASSINATURA="${TL_ASSINATURA:-}"; TL_MODEL="${TL_MODEL:-}"; TL_EFFORT="${TL_EFFORT:-}"
TL_AUTOCOMPACT_FALLBACK="${TL_AUTOCOMPACT_FALLBACK:-avisar}"; TL_TYPER="${TL_TYPER:-}"
TL_ENTRY="${ENTRY_ENV:-${TL_ENTRY:-$SELF}}"
# v2: modo, orçamento semanal, assuntos (configs antigas, sem estas chaves, seguem como antes: orçamento desligado)
TL_MODO="${TL_MODO:-normal}"; TL_SEMANAL="${TL_SEMANAL:-0}"; TL_RESERVA="${TL_RESERVA:-10}"; TL_PAUSA_MAX_MIN="${TL_PAUSA_MAX_MIN:-240}"
TL_GUARDA5H="${TL_GUARDA5H:-90}"; TL_MEDIR="${TL_MEDIR:-auto}"; TL_MEDIR_IDADE="${TL_MEDIR_IDADE:-1200}"
TL_FATOR7="${TL_FATOR7:-4.0}"; TL_FATOR5="${TL_FATOR5:-0.55}" # US$ por 1% da semana / da janela de 5 h (medido em 03/10/2026; recalibra)
TL_MAPA="${TL_MAPA:-$TL_DOCS_DIR/MAPA.md}"; TL_ASSUNTOS="${TL_ASSUNTOS:-$TL_DOCS_DIR/assuntos}"
TL_VIGIA_CRON="${TL_VIGIA_CRON:-17,47 * * * *}"; TL_ASSUNTO_MODELO="${TL_ASSUNTO_MODELO:-sonnet}"; TL_ASSUNTO_ESFORCO="${TL_ASSUNTO_ESFORCO:-high}"
CONSUMO_PY="${TL_CONSUMO_PY:-$SELF_DIR/consumo.py}"
WORK_MIN="${WORK_MIN:-$TL_WORK_MIN}"; PAUSE_MIN="${PAUSE_MIN:-$TL_PAUSE_MIN}"; SCRIPT_MAX_MIN="${SCRIPT_MAX_MIN:-$TL_SCRIPT_MAX_MIN}"
STATE_DIR="${CYCLE_STATE_DIR:-$TL_STATE_DIR}"; STATE="$STATE_DIR/state.env"; JANELA="$STATE_DIR/janela.env"
LOG="${CYCLE_LOG:-$TL_LOG}"
NOTIF_TITLE="${TL_NOTIFY_TITLE:-$TL_TITLE ${WORK_MIN}/${PAUSE_MIN}}"
TIMEOUT_BIN="$(command -v timeout || command -v gtimeout || true)"
[ "${1:-}" = boot ] || mkdir -p "$STATE_DIR"

# ---------- utilitários ----------
now() { date +%s; }
stamp() { date '+%Y-%m-%d %H:%M:%S'; }
dfmt() { date -d "@$1" "+$2" 2>/dev/null || date -r "$1" "+$2" 2>/dev/null; } # GNU (-d @) ou BSD (-r)
hhmm() { dfmt "$1" '%H:%M'; }
hhmmss() { dfmt "$1" '%H:%M:%S'; }
quando() { if [ "$(dfmt "$1" %Y%m%d)" = "$(date +%Y%m%d)" ]; then hhmmss "$1"; else dfmt "$1" '%d/%m %H:%M:%S'; fi; }
fmt() { local s=$1; [ "$s" -lt 0 ] && s=$((-s)); printf '%dm%02ds' $((s / 60)) $((s % 60)); }
fmt_h() { local s=$1; [ "$s" -lt 0 ] && s=0; printf '%dh%02dm' $((s / 3600)) $((s % 3600 / 60)); }
mtime() { stat -c %Y "$1" 2>/dev/null || stat -f %m "$1" 2>/dev/null || echo 0; }
sq() { local q="'"; printf "'%s'" "${1//$q/$q\\$q$q}"; } # aspas simples seguras para gravar valores no .env
log() {
  mkdir -p "$(dirname "$LOG")"
  [ -f "$LOG" ] || printf '# %s %s/%s — log do relógio\n\n| Quando | Evento | Detalhe |\n|---|---|---|\n' "$TL_TITLE" "$WORK_MIN" "$PAUSE_MIN" > "$LOG"
  local ev=${1//|//} det=${2:-}; det=${det//|//}
  printf '| %s | %s | %s |\n' "$(stamp)" "$ev" "$det" >> "$LOG"
}
notificar() { # notificar <urgência> <título> <texto>
  if command -v notify-send >/dev/null 2>&1; then notify-send -u "$1" "$2" "$3" 2>/dev/null || true
  elif command -v osascript >/dev/null 2>&1; then osascript -e "display notification \"${3//\"/}\" with title \"${2//\"/}\"" 2>/dev/null || true
  fi
  return 0
}
render() { # render <modelo> CHAVE=valor...: troca {{CHAVE}} pelo valor
  local s kv; s=$(<"$1"); shift
  for kv in "$@"; do s=${s//"{{${kv%%=*}}}"/"${kv#*=}"}; done
  printf '%s\n' "$s"
}
cfg_set() { # cfg_set <arquivo> <CHAVE> <valor>: troca a linha CHAVE= (ou acrescenta), sem tocar nas demais
  local f=$1 k=$2 v; v=$(sq "$3")
  if grep -q "^${k}=" "$f" 2>/dev/null; then
    K="$k" V="$v" awk 'BEGIN { FS = OFS = "="; k = ENVIRON["K"]; v = ENVIRON["V"] } $1 == k { print k "=" v; next } { print }' "$f" > "$f.tmp.$$" && mv "$f.tmp.$$" "$f"
  else
    printf '%s=%s\n' "$k" "$v" >> "$f"
  fi
}

# ---------- estado do ciclo ----------
load() { PHASE=none START=0 END=0 N=0 CREDIT=0 SCRIPT_T0=0 SCRIPT_PID=0 SCRIPT_LABEL='' MOTIVO='' LAST_WS=0 LAST_WE=0; [ -f "$STATE" ] && . "$STATE"; return 0; }
save() { # LAST_WS/LAST_WE: início e fim do último bloco de trabalho fechado (o orçamento mede o custo dele)
  printf 'PHASE=%s\nSTART=%s\nEND=%s\nN=%s\nCREDIT=%s\nSCRIPT_T0=%s\nSCRIPT_PID=%s\nSCRIPT_LABEL=%q\nMOTIVO=%q\nLAST_WS=%s\nLAST_WE=%s\n' \
    "$PHASE" "$START" "$END" "$N" "$CREDIT" "$SCRIPT_T0" "$SCRIPT_PID" "$SCRIPT_LABEL" "$MOTIVO" "$LAST_WS" "$LAST_WE" > "$STATE.tmp.$$" && mv "$STATE.tmp.$$" "$STATE"
}
# Despertar independente do modelo: timer do systemd --user (fallback: setsid/nohup + sleep). AccuracySec=1s: o padrão do systemd atrasa até 1 min.
agenda() { # agenda <segundos> <nome-da-unidade> <args do motor...>
  local secs=$1 unit=$2; shift 2
  systemd-run --user --quiet --on-active="${secs}s" --timer-property=AccuracySec=1s --unit="${TL_UNIT_PREFIX}-${unit}-$(now)-${RANDOM}" \
    "${SETENV[@]}" "$TL_ENTRY" "$@" >/dev/null 2>&1 && return 0
  # Fallback: o próprio processo do alarme se registra em fallback.pids (PID e unidade), para o stop_units poder pará-lo.
  local run='echo "$$ $2" >> "$3"; sleep "$1"; shift 3; exec "$@"'
  if command -v setsid >/dev/null 2>&1; then
    setsid nohup bash -c "$run" _ "$secs" "$unit" "$STATE_DIR/fallback.pids" "$TL_ENTRY" "$@" >/dev/null 2>&1 &
  else
    nohup bash -c "$run" _ "$secs" "$unit" "$STATE_DIR/fallback.pids" "$TL_ENTRY" "$@" >/dev/null 2>&1 &
  fi
  return 0
}
alarm() { agenda "$1" "$2-$3" fire "$2" "$3"; } # alarm <segundos> <fase> <n>
stop_units() { # stop_units work-18 | pause-18 | checar | aquecer: para o timer do systemd e os alarmes de fallback ainda dormindo
  systemctl --user stop "${TL_UNIT_PREFIX}-$1-*.timer" 2>/dev/null || true
  local reg="$STATE_DIR/fallback.pids" pid unit a keep=''
  [ -f "$reg" ] || return 0
  while read -r pid unit; do
    [ -n "$pid" ] || continue
    a=$(ps -o args= -p "$pid" 2>/dev/null) || a=''
    case "$a" in *'sleep "$1"'*) ;; *) continue ;; esac # já disparou, morreu ou o PID foi reaproveitado: sai do registro
    if [ "$unit" = "$1" ] || [[ "$unit" == "$1"-* ]]; then
      if command -v pkill >/dev/null 2>&1; then pkill -P "$pid" 2>/dev/null; fi
      kill "$pid" 2>/dev/null
    else
      keep+="$pid $unit"$'\n'
    fi
  done < "$reg"
  printf '%s' "$keep" > "$reg.tmp.$$" && mv "$reg.tmp.$$" "$reg"
  return 0
}
# Há um script autônomo em curso: marcador no estado + o processo do wrapper (este mesmo motor) vivo.
script_live() {
  [ "$SCRIPT_T0" -gt 0 ] && [ "$SCRIPT_PID" -gt 1 ] && kill -0 "$SCRIPT_PID" 2>/dev/null \
    && ps -o args= -p "$SCRIPT_PID" 2>/dev/null | grep -q "$(basename "$SELF")"
}
# Fecha o marcador e credita ao bloco os $1 segundos em que o modelo ficou parado (0 = sem crédito); rearma o alarme do fim do bloco.
script_fecha() {
  local dur=$1 sobra
  if [ "$PHASE" != work ] || [ "$dur" -lt 0 ]; then dur=0; fi
  END=$((END + dur)); CREDIT=$((CREDIT + dur)); SCRIPT_T0=0; SCRIPT_PID=0; SCRIPT_LABEL=''; save
  sobra=$((END - $(now)))
  if [ "$PHASE" = work ] && [ "$sobra" -gt 0 ]; then alarm "$sobra" work "$N"; fi
}
abre_bloco() { # abre o bloco de trabalho N+1 (work e retomar)
  N=$((N + 1)); PHASE=work; START=$(now); END=$((START + WORK_MIN * 60)); CREDIT=0; MOTIVO=''; save
  alarm $((WORK_MIN * 60)) work "$N"
  if [ -f "$JANELA" ] || [ "$TL_WARM" = 1 ]; then
    jload; JANELA_AUTO=0; jsave
    # aquecimento ligado e nada armado (a cadeia parou: pausa longa, limite, app fechado): rearma no fim da janela conhecida
    # ou, sem janela válida, mede daqui a 30 s (a mensagem deste bloco abre uma janela nova)
    if [ "$TL_WARM" = 1 ] && [ "$JANELA_SEMANA" != rejected ] && [ "$JANELA_PROX" -le "$(now)" ]; then
      stop_units aquecer
      if [ "$JANELA_FIM" -gt "$(now)" ]; then JANELA_PROX=$((JANELA_FIM + 20)); else JANELA_PROX=$(($(now) + 30)); fi
      jsave; agenda $((JANELA_PROX - $(now))) aquecer-0 aquecer --auto
    fi
  fi
  log "TRABALHO #$N inicia" "${1:-}"
  echo "TRABALHO #$N: ${WORK_MIN} min (até $(hhmmss "$END"))"
}
vistoria_aviso() { # a vistoria vem ANTES de cada pausa
  [ -n "$TL_VISTORIA" ] || return 0
  if [ ! -f "$TL_VISTORIA" ]; then echo "AVISO: vistoria ausente ($TL_VISTORIA): escreva-a ANTES de hibernar."; return 0; fi
  local idade=$(($(now) - $(mtime "$TL_VISTORIA")))
  [ "$idade" -gt 900 ] && echo "AVISO: vistoria velha (atualizada há $(fmt "$idade")): atualize-a antes de hibernar; é dela que a próxima sessão (ou o pós-/compact) retoma."
  return 0
}

# ---------- janela de 5 h ----------
jload() {
  JANELA_FIM=0 JANELA_UTIL='' JANELA_MEDIDA=0 JANELA_STATUS='' JANELA_PROX=0 JANELA_FONTE='' JANELA_AUTO=0 JANELA_FALHAS=0 JANELA_SEMANA=''
  JANELA_FIM7=0 JANELA_UTIL7='' JANELA_FATOR7='' JANELA_FATOR5=''; [ -f "$JANELA" ] && . "$JANELA"
  # a semana esgotada vale até o reset conhecido; passado o reset, o estado limpa sozinho
  if [ "$JANELA_SEMANA" = rejected ] && [ "$JANELA_FIM7" -gt 0 ] && [ "$JANELA_FIM7" -le "$(now)" ]; then JANELA_SEMANA=''; JANELA_UTIL7=''; fi
  return 0
}
jsave() {
  printf 'JANELA_FIM=%s\nJANELA_UTIL=%q\nJANELA_MEDIDA=%s\nJANELA_STATUS=%q\nJANELA_PROX=%s\nJANELA_FONTE=%q\nJANELA_AUTO=%s\nJANELA_FALHAS=%s\nJANELA_SEMANA=%q\nJANELA_FIM7=%s\nJANELA_UTIL7=%q\nJANELA_FATOR7=%q\nJANELA_FATOR5=%q\n' \
    "$JANELA_FIM" "$JANELA_UTIL" "$JANELA_MEDIDA" "$JANELA_STATUS" "$JANELA_PROX" "$JANELA_FONTE" "$JANELA_AUTO" "$JANELA_FALHAS" "$JANELA_SEMANA" \
    "$JANELA_FIM7" "$JANELA_UTIL7" "$JANELA_FATOR7" "$JANELA_FATOR5" > "$JANELA.tmp.$$" && mv "$JANELA.tmp.$$" "$JANELA"
}
# Conta: o app passa CLAUDE_CODE_ORGANIZATION_UUID às sessões; o CLI guarda a dele em .claude.json. Se as duas forem conhecidas e
# diferentes, a chamada mínima mede (e aquece) OUTRA cota: o motor recusa, para não misturar. Só o id da organização, nunca credencial.
conta_app() {
  if [ -n "${CLAUDE_CODE_ORGANIZATION_UUID:-}" ]; then
    printf 'CONTA_APP=%q\n' "$CLAUDE_CODE_ORGANIZATION_UUID" > "$STATE_DIR/conta.env" 2>/dev/null; printf '%s' "$CLAUDE_CODE_ORGANIZATION_UUID"; return 0
  fi
  local CONTA_APP=''; [ -f "$STATE_DIR/conta.env" ] && . "$STATE_DIR/conta.env"; printf '%s' "$CONTA_APP"
}
conta_cli() {
  python3 -c 'import json, sys
try:
    print((json.load(open(sys.argv[1])).get("oauthAccount") or {}).get("organizationUuid") or "")
except Exception:
    print("")' "${CLAUDE_CONFIG_DIR:-$HOME}/.claude.json" 2>/dev/null
}
conta_difere() { local a c; a=$(conta_app); c=$(conta_cli); [ -n "$a" ] && [ -n "$c" ] && [ "$a" != "$c" ]; }
calibra() { # recalcula os fatores (US$ por 1%) com o consumo local desde o início de cada janela; mexe só nas variáveis JANELA_*
  [ -f "$CONSUMO_PY" ] || return 0
  local k v
  while IFS='=' read -r k v; do case "$k" in FATOR7) JANELA_FATOR7=$v;; FATOR5) JANELA_FATOR5=$v;; esac; done < <(
    python3 "$CONSUMO_PY" calibra --u7 "${JANELA_UTIL7:-}" --fim7 "${JANELA_FIM7:-0}" --u5 "${JANELA_UTIL:-}" --fim5 "${JANELA_FIM:-0}" 2>/dev/null)
  return 0
}
pct() { awk -v u="${1:-}" 'BEGIN { if (u == "") print "?"; else printf "%d%%", u * 100 + 0.5 }'; }
# Uma chamada mínima ao CLI. Imprime só pares CHAVE=valor (nunca a saída bruta, que fica na memória e some).
aquecer_chamar() {
  local bin="${TL_CLAUDE_BIN:-claude}" cwd="$STATE_DIR/aquecer-cwd" raw lim=()
  command -v "$bin" >/dev/null 2>&1 || { echo "RESULT=cli_ausente"; return 3; }
  mkdir -p "$cwd"
  [ -n "$TIMEOUT_BIN" ] && lim=("$TIMEOUT_BIN" --kill-after=10 120)
  raw=$(cd "$cwd" && ${lim[@]+"${lim[@]}"} "$bin" -p "ok" --model "${TL_WARM_MODEL:-haiku}" --effort low --tools "" --no-session-persistence \
    --disable-slash-commands --strict-mcp-config --system-prompt "Responda apenas: ok" --output-format stream-json --verbose \
    --max-budget-usd 0.05 </dev/null 2>/dev/null) || true
  printf '%s' "$raw" | python3 -c '
import sys, json
info = res = None
for l in sys.stdin:
    l = l.strip()
    if not l.startswith("{"):
        continue
    try:
        o = json.loads(l)
    except Exception:
        continue
    t = o.get("type")
    if t == "rate_limit_event":
        info = o.get("rate_limit_info") or {}
    elif t == "result":
        res = o
def num(x):
    return x if isinstance(x, (int, float)) and not isinstance(x, bool) else ""
def tok(x):
    return "".join(c for c in str(x) if c.isalnum() or c in "._-")[:40]
out = {}
if info is None:
    out["EVENTO"] = "nao"
else:
    j = info.get("unifiedWindows") or {}
    w5, w7 = j.get("five_hour") or {}, j.get("seven_day") or {}
    tipo = info.get("rateLimitType", "")
    fim, util = num(w5.get("resetsAt")), num(w5.get("utilization"))
    if fim == "" and tipo == "five_hour":
        fim = num(info.get("resetsAt"))
    if util == "" and tipo == "five_hour":
        util = num(info.get("utilization"))
    out.update(EVENTO="sim", STATUS=tok(info.get("status", "")), TIPO=tok(tipo), FIM=int(fim) if fim != "" else "", UTIL=util,
               FIM7=int(num(w7.get("resetsAt"))) if num(w7.get("resetsAt")) != "" else "", UTIL7=num(w7.get("utilization")))
out["RESULT"] = "ausente" if res is None else ("erro" if res.get("is_error") else "ok")
for k, v in out.items():
    print("%s=%s" % (k, v))
'
}
# Mede a janela (uma chamada), grava o estado e, com $1=arma, arma o próximo aquecimento em resetsAt + 20 s.
aquecer_medir() {
  local arma=${1:-0} k v ev='' st='' tipo='' fim='' util='' fim7='' util7='' res='' agora prox est=0 sem=''
  if conta_difere; then # a chamada mínima mediria (e aqueceria) outra cota: não mistura
    echo "AQUECER: o CLI está logado em outra conta (organização $(conta_cli | cut -c1-8)…) que a do app ($(conta_app | cut -c1-8)…): a medida seria de outra cota. Nada medido nem gravado; registre o get_usage com 'uso --registrar' ou faça o login do CLI na conta do app."
    return 5
  fi
  while IFS='=' read -r k v; do
    case "$k" in EVENTO) ev=$v;; STATUS) st=$v;; TIPO) tipo=$v;; FIM) fim=$v;; UTIL) util=$v;; FIM7) fim7=$v;; UTIL7) util7=$v;; RESULT) res=$v;; esac
  done < <(aquecer_chamar)
  agora=$(now); jload
  if [ "$res" != ok ] && [ "$ev" != sim ]; then # sem resposta e sem evento de limite: nada a aprender
    JANELA_FALHAS=$((JANELA_FALHAS + 1)); jsave
    echo "AQUECER: sem resposta do CLI (resultado: ${res:-vazio}); falha $JANELA_FALHAS"; return 1
  fi
  JANELA_FALHAS=0; JANELA_MEDIDA=$agora; JANELA_FONTE='claude -p mínimo'; JANELA_STATUS=${st:-sem-evento}; JANELA_UTIL=$util
  if [ -n "$fim7" ] && [ "$fim7" -gt "$agora" ]; then JANELA_FIM7=$fim7; JANELA_UTIL7=$util7; fi
  [ "$tipo" = seven_day ] && [ "$st" = rejected ] && JANELA_SEMANA=rejected || JANELA_SEMANA=''
  if [ -n "$fim" ] && [ "$fim" -gt "$agora" ]; then JANELA_FIM=$fim
  else JANELA_FIM=$((agora + 18000)); est=1; JANELA_STATUS="${JANELA_STATUS}-estimada"; fi # sem dado do CLI: a janela abriu agora (estimativa, marcada como tal)
  [ -n "$JANELA_UTIL7" ] && [ "$JANELA_FIM7" -gt "$agora" ] && sem="; semana $(pct "$JANELA_UTIL7") (reseta $(quando "$JANELA_FIM7"))"
  calibra
  if [ "$JANELA_SEMANA" = rejected ]; then # sem aquecimento até o reset da semana; com o aquecimento ligado, ele volta sozinho depois
    stop_units aquecer
    if [ "$arma" = 1 ] && [ "$JANELA_FIM7" -gt "$agora" ]; then JANELA_PROX=$((JANELA_FIM7 + 20)); jsave; agenda $((JANELA_PROX - agora)) aquecer-0 aquecer --auto
    else JANELA_PROX=0; jsave; fi
    echo "AQUECER: limite SEMANAL esgotado (reseta $([ "$JANELA_FIM7" -gt "$agora" ] && quando "$JANELA_FIM7" || echo 'em hora desconhecida')); sem aquecimento até lá$([ "$JANELA_PROX" -gt 0 ] && echo "; o aquecimento volta às $(quando "$JANELA_PROX")")."
    return 0
  fi
  prox=$((JANELA_FIM + 20))
  if [ "$arma" = 1 ]; then JANELA_PROX=$prox; jsave; stop_units aquecer; agenda $((prox - agora)) aquecer-0 aquecer --auto
  else jsave; fi
  echo "AQUECER: ok; evento de limite: ${ev:-nao} (status ${st:-n/d}, tipo ${tipo:-n/d}); janela de 5 h $([ "$est" = 1 ] && echo 'ESTIMADA (o CLI não informou o fim)' || echo 'termina') às $(quando "$JANELA_FIM") (utilização $(pct "$util"))$sem$([ "$arma" = 1 ] && echo "; próximo aquecimento às $(quando "$prox")" || echo '; nada armado')"
  return 0
}
janela_aviso() { # 2ª linha do status: a janela expirou e o aquecimento não está armado
  [ "$TL_WARM" = 1 ] || return 0
  case "$PHASE" in none) return 0;; esac
  jload
  if [ "$JANELA_FIM" -eq 0 ] && [ "$JANELA_PROX" -eq 0 ]; then echo "AVISO: aquecimento ligado, mas a janela de 5 h nunca foi medida: rode 'aquecer'."
  elif [ "$JANELA_FIM" -gt 0 ] && [ "$JANELA_FIM" -lt "$(now)" ] && [ "$JANELA_PROX" -lt $(($(now) - 120)) ]; then
    echo "AVISO: a janela de 5 h expirou às $(hhmm "$JANELA_FIM") e não há aquecimento armado: rode 'aquecer' (o relógio das 5 h só começa na próxima mensagem)."
  fi
  return 0
}
fmt_d() { if [ "$1" -ge 86400 ]; then printf '%dd%02dh' $(($1 / 86400)) $(($1 % 86400 / 3600)); else fmt_h "$1"; fi; }

# ---------- orçamento: cota de 5 h e semanal (v2) ----------
medir_se_velho() { # mede pela chamada mínima quando a última medida passou de TL_MEDIR_IDADE (silencioso; registra no log)
  case "$TL_MEDIR" in off|0|nao) return 0 ;; esac
  jload
  [ $(($(now) - ${JANELA_MEDIDA:-0})) -gt "$TL_MEDIR_IDADE" ] || return 0
  conta_difere && return 0 # outra conta: só o 'uso --registrar' vale
  local s; s=$(aquecer_medir 0 2>&1)
  log "MEDIDA automática" "${s//$'\n'/ }"
  return 0
}
orcamento_calc() { # orcamento_calc <início do bloco> <fim do bloco, 0 = agora>: define ORC_FIM (epoch do fim da pausa), ORC_COD e ORC_LINHA
  ORC_FIM=0; ORC_COD=0; ORC_LINHA=''
  if [ ! -f "$CONSUMO_PY" ]; then ORC_LINHA="ORÇAMENTO: $CONSUMO_PY ausente; vale a pausa configurada."; return 1; fi
  medir_se_velho; jload
  local k v
  while IFS='=' read -r k v; do case "$k" in FIM) ORC_FIM=$v;; COD) ORC_COD=$v;; LINHA) ORC_LINHA=$v;; esac; done < <(
    python3 "$CONSUMO_PY" orcamento --bloco-ini "$1" --bloco-fim "${2:-0}" --trabalho "$WORK_MIN" --pausa "$PAUSE_MIN" \
      --u7 "${JANELA_UTIL7:-}" --fim7 "${JANELA_FIM7:-0}" --u5 "${JANELA_UTIL:-}" --fim5 "${JANELA_FIM:-0}" --reg "${JANELA_MEDIDA:-0}" \
      --fator7 "${JANELA_FATOR7:-$TL_FATOR7}" --fator5 "${JANELA_FATOR5:-$TL_FATOR5}" --reserva "$TL_RESERVA" \
      --pausa-max "$TL_PAUSA_MAX_MIN" --guarda5 "$TL_GUARDA5H" 2>/dev/null)
  if ! [[ "$ORC_FIM" =~ ^[0-9]+$ ]] || [ "$ORC_FIM" -le 0 ]; then ORC_FIM=0; ORC_COD=0; ORC_LINHA="ORÇAMENTO: cálculo indisponível; vale a pausa configurada."; return 1; fi
  return 0
}
uso_mostra() {
  jload; local ag; ag=$(now)
  if [ "$JANELA_FIM" -gt 0 ]; then
    echo "COTA 5 H: $(pct "$JANELA_UTIL") · $([ "$JANELA_FIM" -gt "$ag" ] && echo "reseta $(quando "$JANELA_FIM") (em $(fmt_h $((JANELA_FIM - ag))))" || echo "expirou $(quando "$JANELA_FIM")")"
  else echo "COTA 5 H: desconhecida"; fi
  if [ "$JANELA_FIM7" -gt 0 ]; then echo "COTA SEMANAL: $(pct "$JANELA_UTIL7") · reseta $(quando "$JANELA_FIM7") (em $(fmt_d $((JANELA_FIM7 - ag))))$([ "$JANELA_SEMANA" = rejected ] && echo ' · ESGOTADA')"
  else echo "COTA SEMANAL: desconhecida ('medir' ou 'uso --registrar')"; fi
  echo "MEDIDA: $([ "$JANELA_MEDIDA" -gt 0 ] && quando "$JANELA_MEDIDA" || echo nunca) [${JANELA_FONTE:-?}] · US\$ ${JANELA_FATOR7:-$TL_FATOR7} por 1% da semana, US\$ ${JANELA_FATOR5:-$TL_FATOR5} por 1% da janela de 5 h$([ -z "$JANELA_FATOR7" ] && echo ' (padrão; recalibra quando a semana passar de 5%)')"
  conta_difere && echo "AVISO: o CLI está em outra conta que o app: 'medir' e 'aquecer' recusam; use 'uso --registrar'."
  return 0
}
uso_registrar() { # uso --registrar 5h=PCT@RESET 7d=PCT@RESET [--fonte F]: a cota vinda do get_usage do app (ou de outra fonte)
  local k v p q ep fr u5='' f5='' u7='' f7='' fonte=get_usage
  local ajuda="uso: uso --registrar 5h=PCT@RESET 7d=PCT@RESET [--fonte F]   (PCT de 0 a 100; RESET em ISO 8601, ex. 2026-10-03T21:09:59Z, ou epoch)"
  while [ $# -gt 0 ]; do
    case "$1" in
      --fonte) fonte=${2:-get_usage}; shift 2; continue ;;
      5h=*@*|7d=*@*)
        k=${1%%=*}; v=${1#*=}; p=${v%%@*}; q=${v#*@}; p=${p/,/.}; p=${p%\%}
        [[ "$p" =~ ^[0-9]+(\.[0-9]+)?$ ]] || { echo "uso: porcentagem inválida «$p» em $1"; return 2; }
        if [[ "$q" =~ ^[0-9]+$ ]]; then ep=$q; else ep=$(date -d "$q" +%s 2>/dev/null) || { echo "uso: data inválida «$q» em $1"; return 2; }; fi
        fr=$(LC_ALL=C awk -v p="$p" 'BEGIN { printf "%.4f", p / 100 }')
        if [ "$k" = 5h ]; then u5=$fr; f5=$ep; else u7=$fr; f7=$ep; fi ;;
      *) echo "$ajuda"; return 2 ;;
    esac
    shift
  done
  [ -n "$u5$u7" ] || { echo "$ajuda"; return 2; }
  jload
  [ -z "$u5" ] || { JANELA_UTIL=$u5; JANELA_FIM=$f5; }
  if [ -n "$u7" ]; then JANELA_UTIL7=$u7; JANELA_FIM7=$f7; awk -v u="$u7" 'BEGIN { exit !(u < 1) }' && JANELA_SEMANA=''; fi
  JANELA_MEDIDA=$(now); JANELA_FONTE=$fonte; JANELA_STATUS=registrado
  calibra; conta_app >/dev/null
  if [ "$TL_WARM" = 1 ] && [ "$JANELA_FIM" -gt "$(now)" ] && [ "$JANELA_SEMANA" != rejected ]; then
    JANELA_PROX=$((JANELA_FIM + 20)); jsave; stop_units aquecer; agenda $((JANELA_PROX - $(now))) aquecer-0 aquecer --auto
  else jsave; fi
  log "USO registrado" "5 h $(pct "$JANELA_UTIL") até $(quando "$JANELA_FIM"); semana $(pct "$JANELA_UTIL7") até $(quando "$JANELA_FIM7"); fonte $JANELA_FONTE"
  uso_mostra
}

# ---------- assuntos e retomada exata (v2) ----------
slugify() { python3 -c 'import re, sys, unicodedata
s = unicodedata.normalize("NFKD", sys.argv[1]).encode("ascii", "ignore").decode().lower()
s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")[:40].strip("-")
print(s or "assunto")' "$1"; }
tarefa_id() { local s; s="tl-$(slugify "$TL_SLUG")-$1"; s=${s:0:60}; printf '%s' "${s%-}"; }
rel_mapa() { realpath -m --relative-to="$(dirname "$TL_MAPA")" "$1" 2>/dev/null || printf '%s' "$1"; }
assunto_titulo() { sed -n '1s/^# //p' "$TL_ASSUNTOS/$1.md" 2>/dev/null; }
tpl_mapa() {
  local raiz; raiz=$(git -C "$TL_DOCS_DIR" rev-parse --show-toplevel 2>/dev/null || printf '%s' "$TL_DOCS_DIR")
  cat <<EOF
# MAPA — $TL_TITLE

Índice estável de onde as coisas estão: uma linha por item, atualizado quando a estrutura muda. Não é diário (o diário é o log). Abra só a linha de que precisar.

## Turno longo
- Regras: \`$TL_CONTRATO\` · retomada: \`$TL_VISTORIA\` · log: \`$LOG\` · motor: \`$TL_ENTRY\`
- Assuntos: \`$TL_ASSUNTOS/\` (um arquivo por assunto; histórico longo em \`<slug>-historico.md\`)

## Projeto
- Raiz: \`$raiz\`
- (pasta ou arquivo — para que serve)

## Comandos
- (comando — quando usar)

## Assuntos
<!-- assuntos:inicio -->
<!-- assuntos:fim -->
EOF
}
tpl_assunto() { # tpl_assunto TÍTULO SLUG OBJETIVO
  cat <<EOF
# $1

> status: ativo · criado $(stamp) · sessões: (nenhuma) · turno: $TL_TITLE

## Objetivo
$3

## Estado (até 5 linhas)
(nada feito ainda)

## Próximo passo exato
(comando, ou arquivo:linha e o que fazer)

## Decisões (com o porquê)

## Pendências
- [ ]

## Arquivos (caminho:linha — papel)

## Comandos úteis

## Armadilhas

## Histórico (1 linha por bloco; detalhes antigos em \`$2-historico.md\`)
EOF
}
mapa_garante() {
  mkdir -p "$(dirname "$TL_MAPA")"
  if [ ! -f "$TL_MAPA" ]; then tpl_mapa > "$TL_MAPA"; return 0; fi
  grep -q '<!-- assuntos:fim -->' "$TL_MAPA" || printf '\n## Assuntos\n<!-- assuntos:inicio -->\n<!-- assuntos:fim -->\n' >> "$TL_MAPA"
}
retomada_texto() { # retomada_texto [SLUG] [primeira|autonomo|normal]: o prompt de retomada exata (só ponteiros: o conteúdo vive nos arquivos)
  local slug=${1:-} modo=${2:-normal} arq='' titulo=''
  if [ -n "$slug" ]; then arq="$TL_ASSUNTOS/$slug.md"; titulo=$(assunto_titulo "$slug"); fi
  printf 'RETOMADA EXATA — %s%s. O trabalho já está documentado: não reexplore o projeto e não leia além do necessário.\n' "$TL_TITLE" "${titulo:+ · $titulo}"
  printf '1. Leia, nesta ordem e só isto: %s (estado e próximo passo)%s. O índice %s serve para achar o resto: abra só a linha de que precisar.\n' \
    "$TL_VISTORIA" "${arq:+; depois $arq (o assunto)}" "$TL_MAPA"
  local anexo=''; [ -f "$TL_DOCS_DIR/CONTRATO-turnolongo.md" ] && anexo=" e $TL_DOCS_DIR/CONTRATO-turnolongo.md"
  printf '2. Regras do turno, numa leitura só: %s%s (a seção «Economia» vale sempre).\n' "$TL_CONTRATO" "$anexo"
  printf '3. Relógio: rode `%s status`. Com turno ativo, se você não for a sessão dona do relógio, siga `%s agente` (trabalhe só dentro do bloco).\n' "$TL_ENTRY" "$TL_ENTRY"
  case "$modo" in
    primeira) printf '4. Nesta primeira resposta, só leia o que está acima e confirme em até 5 linhas: estado, próximo passo exato e o que mais vai precisar abrir. Não edite nada; pare e espere o usuário.\n' ;;
    autonomo) printf '4. Execute o «Próximo passo» do assunto, dentro das regras do relógio, e pare no fim dele.\n' ;;
    *) printf '4. Confirme em até 5 linhas o estado e o próximo passo; depois siga por ele.\n' ;;
  esac
  printf '5. Ao parar ou trocar de assunto: atualize %s (Estado, Próximo passo, Pendências, Histórico) e %s, sem duplicar, e anote no log com `%s log "texto"`.\n' \
    "${arq:-o arquivo do assunto}" "$TL_VISTORIA" "$TL_ENTRY"
}
assunto_novo() {
  local titulo='' slug='' obj='' arq rel tid ajuda='uso: assunto novo "título" [--objetivo "texto"] [--slug s]'
  while [ $# -gt 0 ]; do
    case "$1" in
      --slug) slug=${2:-}; shift 2 ;;
      --objetivo) obj=${2:-}; shift 2 ;;
      *) [ -z "$titulo" ] || { echo "$ajuda"; return 2; }; titulo=$1; shift ;;
    esac
  done
  [ -n "$titulo" ] || { echo "$ajuda"; return 2; }
  titulo=${titulo//|//}; titulo=${titulo//$'\n'/ }; slug=$(slugify "${slug:-$titulo}")
  arq="$TL_ASSUNTOS/$slug.md"; mkdir -p "$TL_ASSUNTOS"
  if [ -f "$arq" ]; then echo "ASSUNTO: «$(assunto_titulo "$slug")» já existe em $arq (mantido)."
  else tpl_assunto "$titulo" "$slug" "${obj:-(defina em 1 a 3 linhas)}" > "$arq"; log "ASSUNTO novo" "«$titulo» → $arq"; echo "ASSUNTO: «$titulo» criado em $arq."; fi
  mapa_garante; rel=$(rel_mapa "$arq")
  if ! grep -qF "]($rel)" "$TL_MAPA"; then
    L="- [$titulo]($rel) · ativo · desde $(date '+%d/%m %H:%M')" awk '/<!-- assuntos:fim -->/ { print ENVIRON["L"] } { print }' "$TL_MAPA" > "$TL_MAPA.tmp.$$" && mv "$TL_MAPA.tmp.$$" "$TL_MAPA"
  fi
  tid=$(tarefa_id "$slug")
  cat <<EOF
MAPA: $TL_MAPA (índice atualizado)
SESSÃO DO ASSUNTO NO APP (o modelo faz, com as ferramentas do app):
  1. create_scheduled_task: taskId=$tid · title=«$TL_TITLE · $titulo» · description=«Assunto do turno longo $TL_TITLE» · sem cronExpression nem fireAt (rotina manual) · notifyOnCompletion=false · prompt = o bloco PROMPT abaixo, sem mudar nada.
  2. run_scheduled_task: taskId=$tid; depois anote a sessão: $TL_ENTRY assunto sessao $slug <session_id>
  3. A sessão nasce no modelo padrão do app e o 1º turno só lê; em seguida, set_session_model e set_session_effort nela ($TL_ASSUNTO_MODELO, $TL_ASSUNTO_ESFORCO).
  A rotina fica em «Scheduled» na barra lateral: «Run now» reabre o assunto numa sessão limpa, já com esta retomada.
  Sem as ferramentas do app (terminal): $TL_ENTRY assunto cli $slug
----- PROMPT -----
EOF
  retomada_texto "$slug" primeira
  echo "----- FIM -----"
}
assunto_lista() {
  [ -f "$TL_MAPA" ] || { echo "ASSUNTOS: nenhum (sem $TL_MAPA)."; return 0; }
  local l n=0
  while IFS= read -r l; do case "$l" in "- ["*) n=$((n + 1)); echo "$l" ;; esac; done < <(sed -n '/<!-- assuntos:inicio -->/,/<!-- assuntos:fim -->/p' "$TL_MAPA")
  [ "$n" -gt 0 ] || echo "ASSUNTOS: nenhum ainda (assunto novo \"título\")."
  return 0
}
assunto_fechar() {
  local slug=${1:-} nota=${2:-} arq rel hoje
  arq="$TL_ASSUNTOS/$slug.md"
  [ -n "$slug" ] && [ -f "$arq" ] || { echo "uso: assunto fechar SLUG [nota]   (veja 'assunto lista')"; return 2; }
  rel=$(rel_mapa "$arq"); hoje=$(date '+%d/%m')
  sed -i "1,4 s|^> status: ativo|> status: fechado $hoje|" "$arq"
  if [ -f "$TL_MAPA" ]; then
    R="]($rel)" H="$hoje" awk 'index($0, ENVIRON["R"]) { sub(/ · ativo · /, " · fechado " ENVIRON["H"] " · ") } { print }' "$TL_MAPA" > "$TL_MAPA.tmp.$$" && mv "$TL_MAPA.tmp.$$" "$TL_MAPA"
  fi
  log "ASSUNTO fechado" "$slug${nota:+: $nota}"
  echo "ASSUNTO «$(assunto_titulo "$slug")» fechado. A rotina do app ($(tarefa_id "$slug")) pode ser apagada com delete_scheduled_task, o que também arquiva a sessão dela."
}
assunto_sessao() {
  local slug=${1:-} id=${2:-} arq
  arq="$TL_ASSUNTOS/$slug.md"
  [ -n "$slug" ] && [ -f "$arq" ] && [[ "$id" =~ ^[A-Za-z0-9:_-]+$ ]] || { echo "uso: assunto sessao SLUG ID_DA_SESSÃO"; return 2; }
  if grep -q '^> .*sessões: (nenhuma)' "$arq"; then sed -i "1,4 s/sessões: (nenhuma)/sessões: $id/" "$arq"
  else sed -i -E "1,4 s/(sessões: [^·]*[^ ·])/\1, $id/" "$arq"; fi
  log "ASSUNTO sessão" "$slug → $id"
  echo "ASSUNTO «$(assunto_titulo "$slug")»: sessão $id anotada."
}
assunto_cli() { # abre a sessão do assunto no terminal (claude --bg), já no modelo e esforço do assunto; não aparece na barra do app
  local slug=${1:-} modelo=$TL_ASSUNTO_MODELO esf=$TL_ASSUNTO_ESFORCO out id dir rc titulo bin="${TL_CLAUDE_BIN:-claude}"
  shift || true
  while [ $# -gt 0 ]; do
    case "$1" in --modelo) modelo=${2:-$modelo}; shift 2 ;; --esforco) esf=${2:-$esf}; shift 2 ;; *) echo "uso: assunto cli SLUG [--modelo M] [--esforco E]"; return 2 ;; esac
  done
  [ -n "$slug" ] && [ -f "$TL_ASSUNTOS/$slug.md" ] || { echo "uso: assunto cli SLUG [--modelo M] [--esforco E]   (crie antes com 'assunto novo')"; return 2; }
  command -v "$bin" >/dev/null 2>&1 || { echo "ASSUNTO: CLI '$bin' ausente."; return 3; }
  if conta_difere; then echo "ASSUNTO: o CLI está em outra conta que o app; a sessão gastaria a outra cota. Faça o login do CLI na conta do app ou abra pela rotina do app."; return 5; fi
  titulo=$(assunto_titulo "$slug"); dir=$(git -C "$TL_DOCS_DIR" rev-parse --show-toplevel 2>/dev/null || printf '%s' "$TL_DOCS_DIR")
  out=$(cd "$dir" && "$bin" --bg -n "$TL_TITLE · $titulo" --model "$modelo" --effort "$esf" "$(retomada_texto "$slug" primeira)" 2>&1); rc=$?
  id=$(printf '%s\n' "$out" | sed -n 's/.*backgrounded · \([0-9a-f]\{6,\}\) · .*/\1/p' | head -1)
  if [ "$rc" -ne 0 ] || [ -z "$id" ]; then echo "ASSUNTO: o claude --bg falhou (exit $rc): $(printf '%s' "$out" | tail -2 | tr '\n' ' ')"; return 1; fi
  assunto_sessao "$slug" "cli:$id" >/dev/null
  log "ASSUNTO sessão no terminal" "$slug → claude --bg $id ($modelo, $esf)"
  echo "ASSUNTO «$titulo»: sessão em segundo plano $id ($modelo, esforço $esf) em $dir. Ver: claude attach $id · claude logs $id · parar: claude stop $id"
}
economia_cmd() { # limites de saída das ferramentas no .claude/settings.local.json do projeto (chave env); só grava com --aplicar
  local proj='' modo="$TL_MODO" aplicar=0 kv p alvo
  while [ $# -gt 0 ]; do
    case "$1" in
      --projeto) proj=${2:-}; shift 2 ;;
      --modo) modo=${2:-}; shift 2 ;;
      --aplicar) aplicar=1; shift ;;
      *) echo "uso: economia [--modo guerra|economia] [--projeto DIR] [--aplicar]"; return 2 ;;
    esac
  done
  [ -n "$proj" ] || proj=$(git -C "$TL_DOCS_DIR" rev-parse --show-toplevel 2>/dev/null || pwd)
  case "$modo" in
    guerra) kv='BASH_MAX_OUTPUT_LENGTH=12000 CLAUDE_CODE_FILE_READ_MAX_OUTPUT_TOKENS=12000 MAX_MCP_OUTPUT_TOKENS=10000 CLAUDE_CODE_MAX_MCP_DESCRIPTION_LENGTH=1000' ;;
    economia|normal) kv='BASH_MAX_OUTPUT_LENGTH=20000 CLAUDE_CODE_FILE_READ_MAX_OUTPUT_TOKENS=20000 MAX_MCP_OUTPUT_TOKENS=15000' ;;
    *) echo "economia: modo «$modo» desconhecido (guerra ou economia)."; return 2 ;;
  esac
  alvo="$proj/.claude/settings.local.json"
  echo "ECONOMIA ($modo): limites de saída das ferramentas para as sessões abertas em $proj, pela chave env de $alvo:"
  for p in $kv; do echo "  $p"; done
  if [ "$aplicar" != 1 ]; then echo "Nada gravado. Com o sim do usuário: $TL_ENTRY economia --modo $modo --projeto \"$proj\" --aplicar"; return 0; fi
  mkdir -p "$proj/.claude" || return 2
  # shellcheck disable=SC2086
  python3 - "$alvo" $kv <<'PY' || { echo "economia: não consegui gravar $alvo (JSON inválido?)."; return 1; }
import json, os, shutil, sys, time
alvo, pares = sys.argv[1], sys.argv[2:]
d = {}
if os.path.exists(alvo):
    with open(alvo) as f:
        d = json.load(f)
    shutil.copy2(alvo, alvo + '.bak-turnolongo-' + time.strftime('%Y%m%d-%H%M%S'))
env = d.setdefault('env', {})
for par in pares:
    k, v = par.split('=', 1)
    env[k] = v
tmp = alvo + '.tmp-turnolongo'
with open(tmp, 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
    f.write('\n')
os.replace(tmp, alvo)
PY
  log "ECONOMIA aplicada" "$modo em $alvo (autorizado pelo usuário): $kv"
  echo "ECONOMIA: gravado em $alvo (o anterior ficou ao lado, .bak-turnolongo-*). Vale para as sessões abertas depois disto."
}

# ---------- /autocompact ----------
ctx_tokens() { # 150K | 1M | 250000 -> tokens (100000..1000000); falha se inválido
  local s n; s=$(printf '%s' "$1" | tr 'km' 'KM')
  case "$s" in
    *K) n=${s%K}; [[ "$n" =~ ^[0-9]+$ ]] && n=$((n * 1000)) || return 1 ;;
    *M) n=${s%M}; [[ "$n" =~ ^[0-9]+$ ]] && n=$((n * 1000000)) || return 1 ;;
    *) [[ "$s" =~ ^[0-9]+$ ]] && n=$s || return 1 ;;
  esac
  [ "$n" -ge 100000 ] && [ "$n" -le 1000000 ] || return 1
  echo "$n"
}
ctx_label() { if [ $(($1 % 1000000)) -eq 0 ]; then echo "$(($1 / 1000000))M"; else echo "$(($1 / 1000))K"; fi; }
settings_file() { echo "${CLAUDE_CONFIG_DIR:-$HOME/.claude}/settings.json"; }
settings_get() { python3 -c 'import json, sys
try:
    v = json.load(open(sys.argv[1])).get(sys.argv[2])
except Exception:
    v = None
print("" if v is None else v)' "$(settings_file)" "$1" 2>/dev/null; }
# Devolve 0 enviado/aplicado/já vigente; 4 pendente (o usuário precisa digitar); 2 valor inválido.
autocompact() { # autocompact <XXXK> [--dry] [--aplicar-settings]
  local alvo=${1:-} dry=0 aplicar=0 tokens label atual texto a; shift || true
  for a in "$@"; do case "$a" in --dry) dry=1;; --aplicar-settings) aplicar=1;; *) echo "autocompact: opção desconhecida: $a"; return 2;; esac; done
  tokens=$(ctx_tokens "$alvo") || { echo "autocompact: valor inválido «$alvo»: use 150K, 250K, 500K, 1M (de 100K a 1M)."; return 2; }
  label=$(ctx_label "$tokens"); texto="/autocompact $label"; atual=$(settings_get autoCompactWindow)
  if [ "$atual" = "$tokens" ]; then log "AUTOCOMPACT $label já vigente" "autoCompactWindow=$atual; nada a digitar"; echo "AUTOCOMPACT: já está em $label (autoCompactWindow=$atual); nada a digitar."; return 0; fi
  [ "$TL_AUTOCOMPACT_FALLBACK" = settings ] && aplicar=1
  if [ -n "$TL_TYPER" ]; then
    [ "$dry" = 1 ] && { echo "AUTOCOMPACT (dry): digitaria «$texto» por TL_TYPER."; return 0; }
    if TL_TEXT="$texto" bash -c "$TL_TYPER"; then log "AUTOCOMPACT $label ENVIADO" "digitado por TL_TYPER"; echo "AUTOCOMPACT: «$texto» enviado por TL_TYPER."; return 0; fi
    echo "AUTOCOMPACT: TL_TYPER falhou; tentando outro canal."
  fi
  if [ -n "${TMUX_PANE:-}" ] && command -v tmux >/dev/null 2>&1; then
    [ "$dry" = 1 ] && { echo "AUTOCOMPACT (dry): digitaria «$texto» no painel tmux $TMUX_PANE."; return 0; }
    if tmux send-keys -t "$TMUX_PANE" -l "$texto" && tmux send-keys -t "$TMUX_PANE" Enter; then
      log "AUTOCOMPACT $label ENVIADO" "digitado no painel tmux $TMUX_PANE"; echo "AUTOCOMPACT: «$texto» digitado no painel tmux $TMUX_PANE."; return 0
    fi
  fi
  if [ "$aplicar" = 1 ]; then
    [ "$dry" = 1 ] && { echo "AUTOCOMPACT (dry): gravaria autoCompactWindow=$tokens em $(settings_file) (era ${atual:-ausente})."; return 0; }
    if python3 - "$(settings_file)" "$tokens" <<'PY'
import json, os, sys
p, val = sys.argv[1], int(sys.argv[2])
d = json.load(open(p))
d["autoCompactWindow"] = val
tmp = p + ".tmp-turnolongo"
with open(tmp, "w") as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
    f.write("\n")
os.chmod(tmp, os.stat(p).st_mode)
os.replace(tmp, p)
PY
    then
      log "AUTOCOMPACT $label APLICADO" "autoCompactWindow ${atual:-ausente} → $tokens em settings.json (autorizado); para desfazer, regrave ${atual:-a chave removida}"
      echo "AUTOCOMPACT: autoCompactWindow ${atual:-ausente} → $tokens gravado em $(settings_file). Vale na próxima sessão ou quando o app recarregar as configurações."; return 0
    fi
    echo "AUTOCOMPACT: não consegui gravar em $(settings_file)."
  fi
  [ "$dry" = 1 ] && { echo "AUTOCOMPACT (dry): sem canal para digitar; avisaria o usuário («$texto»)."; return 4; }
  log "AUTOCOMPACT $label PENDENTE" "sem canal para digitar (sem TL_TYPER, tmux ou permissão para o settings.json); o usuário precisa digitar «$texto»"
  notificar normal "$NOTIF_TITLE" "Digite no chat: $texto"
  echo "AUTOCOMPACT PENDENTE: não consigo digitar no app. Digite você no chat: $texto"
  return 4
}

# ---------- anúncio e regras de agente ----------
anuncio() {
  load; jload
  local ag=$(now) t2
  echo "TURNO LONGO — ${TL_TITLE}: ${WORK_MIN} min de trabalho + ${PAUSE_MIN} min de pausa (agora $(quando "$ag"))"
  case "$PHASE" in
    work)  t2=$((END + PAUSE_MIN * 60))
           echo "  Bloco #$N de trabalho: $(quando "$START") → $(quando "$END")  (restam $(fmt $((END - ag))))"
           echo "  Pausa #$N: $(quando "$END") → $(quando "$t2")  (hiberna, sem trabalho)"
           echo "  Bloco #$((N + 1)): a partir de $(quando "$t2")" ;;
    pause) echo "  Pausa #$N: $(quando "$START") → $(quando "$END")  (restam $(fmt $((END - ag))); hiberna, sem trabalho)"
           echo "  Bloco #$((N + 1)): a partir de $(quando "$END")" ;;
    livre) echo "  Ciclo LIVRE desde $(quando "$START")${MOTIVO:+ ($MOTIVO)}: sem relógio; 'work' abre o próximo bloco." ;;
    *)     echo "  Sem ciclo ativo ('work' abre o primeiro bloco)." ;;
  esac
  if [ "$JANELA_FIM" -gt "$ag" ]; then
    echo "  Sessão (janela de 5 h): termina $(quando "$JANELA_FIM") (em $(fmt_h $((JANELA_FIM - ag)))), utilização $(pct "$JANELA_UTIL"), medida $(quando "$JANELA_MEDIDA") [${JANELA_FONTE:-?}; status ${JANELA_STATUS:-?}]"
    if [ "$PHASE" = work ] || [ "$PHASE" = pause ]; then echo "  Cabem ~$(( (JANELA_FIM - ag) / ((WORK_MIN + PAUSE_MIN) * 60) )) ciclos completos até o reset da janela."; fi
  elif [ "$JANELA_FIM" -gt 0 ]; then
    echo "  Sessão (janela de 5 h): a última medida expirou $(quando "$JANELA_FIM") (a próxima mensagem abre outra janela; $([ "$TL_WARM" = 1 ] && echo "o aquecimento a mantém contando" || echo 'o aquecimento está desligado'))."
  else
    echo "  Sessão (janela de 5 h): desconhecida (o CLI ainda não informou; 'aquecer --teste' mede com uma chamada mínima)."
  fi
  if [ "$JANELA_FIM7" -gt "$ag" ]; then
    echo "  Semana: $(pct "$JANELA_UTIL7") usada (medida $(quando "$JANELA_MEDIDA")) · reseta $(quando "$JANELA_FIM7") (em $(fmt_d $((JANELA_FIM7 - ag)))) · orçamento $([ "$TL_SEMANAL" = 1 ] && echo "ligado, reserva ${TL_RESERVA}%" || echo desligado)"
  elif [ "$TL_SEMANAL" = 1 ]; then
    echo "  Semana: não medida ('medir' ou 'uso --registrar'); o orçamento usa o ritmo uniforme (reserva ${TL_RESERVA}%)."
  fi
  echo "  Contexto: $([ -n "$TL_CONTEXT" ] && echo "/autocompact $TL_CONTEXT" || echo 'não definido') · aquecimento da janela: $([ "$TL_WARM" = 1 ] && echo ligado || echo desligado)"
  echo "  Modo: $TL_MODO · vigia: $TL_VIGIA_CRON · hibernar: $TL_ENTRY hibernar (em segundo plano, depois do pause)"
  echo "  Documentação: $TL_DOCS_DIR (log: $LOG; vistoria: $TL_VISTORIA; mapa: $TL_MAPA)"
  if conta_difere; then echo "  AVISO: o CLI está em outra conta que o app: 'medir' e 'aquecer' recusam; registre a cota com 'uso --registrar'."; fi
  return 0
}
agente_texto() {
  local s; s=$(cat <<'EOF'
RELÓGIO DE TURNOS — você trabalha PRESO ao relógio do orquestrador (regra do usuário). O estado é único e compartilhado.
Comando do relógio: @ENTRY@
Agora: @STATUS@

1. Só trabalhe dentro de um bloco de TRABALHO. Antes de começar e a cada passo grande rode `@ENTRY@ status`:
   - exit 0 e «TRABALHO #N restam …»: pode trabalhar.
   - exit 10 (bloco vencido), 20 (PAUSA) ou 30 (pausa vencida, o orquestrador ainda vai retomar) ou «SUSPENSO» (o orquestrador roda
     um script autônomo): PARE e não consuma nada. Espere com `@ENTRY@ wait --trabalho --max 590` em laço (exit 3 = ainda não
     voltou, chame de novo; exit 0 = bloco de trabalho ativo, retome). O Bash em primeiro plano corta em 10 min: nunca espere mais que 590 s por chamada.
   - «LIVRE» ou «SEM CICLO»: o usuário liberou o relógio; trabalhe sem restrição.
2. Ao restar menos de 2 min no bloco: feche o item em andamento, grave um checkpoint (commit WIP no seu worktree + nota em arquivo com o
   que falta) e pare. Depois espere o próximo bloco como acima.
3. NÃO use work, pause, livre, retomar, compensar, script, encerrar nem boot: o relógio é do orquestrador.
4. Exceção: um script autônomo SEU (sem stdin, saída em arquivo, sem polling nem leitura enquanto roda) pode atravessar a pausa, porque não
   gasta tokens. Ao terminar, volte a esperar o bloco para analisar o resultado.
5. Grave o resultado final em ARQUIVO (o caminho vem na sua tarefa), não só na resposta. Se você terminar com o orquestrador em pausa, a resposta
   só será lida no bloco seguinte: deixe tudo no arquivo.
6. O limite de uso não deve te matar no meio do trabalho: o ciclo existe para evitar isso. Se estourar mesmo assim, guarde o checkpoint e espere.
EOF
)
  s=${s//@ENTRY@/"$TL_ENTRY"}; s=${s//@STATUS@/"$("$TL_ENTRY" status 2>/dev/null | head -1)"}
  printf '%s\n' "$s"
}

# ---------- boot ----------
config_dump() { # a configuração efetiva, uma CHAVE=valor por linha (a skill lê daqui)
  printf 'CONFIG=%s\nTL_SLUG=%s\nTL_TITLE=%s\nTL_WORK_MIN=%s\nTL_PAUSE_MIN=%s\nTL_DOCS_DIR=%s\nTL_LOG=%s\nTL_VISTORIA=%s\nTL_CONTRATO=%s\n' \
    "${CFG:-(nenhuma: padrões)}" "$TL_SLUG" "$TL_TITLE" "$WORK_MIN" "$PAUSE_MIN" "$TL_DOCS_DIR" "$LOG" "$TL_VISTORIA" "$TL_CONTRATO"
  printf 'TL_STATE_DIR=%s\nTL_UNIT_PREFIX=%s\nTL_ENTRY=%s\nTL_CONTEXT=%s\nTL_WARM=%s\nTL_MODEL=%s\nTL_EFFORT=%s\nTL_ASSINATURA=%s\n' \
    "$STATE_DIR" "$TL_UNIT_PREFIX" "$TL_ENTRY" "$TL_CONTEXT" "$TL_WARM" "$TL_MODEL" "$TL_EFFORT" "$TL_ASSINATURA"
  printf 'TL_MODO=%s\nTL_SEMANAL=%s\nTL_RESERVA=%s\nTL_PAUSA_MAX_MIN=%s\nTL_GUARDA5H=%s\nTL_MAPA=%s\nTL_ASSUNTOS=%s\nTL_VIGIA_CRON=%s\nCONSUMO_PY=%s\n' \
    "$TL_MODO" "$TL_SEMANAL" "$TL_RESERVA" "$TL_PAUSA_MAX_MIN" "$TL_GUARDA5H" "$TL_MAPA" "$TL_ASSUNTOS" "$TL_VIGIA_CRON" "$CONSUMO_PY"
}
copia_motor() { # copia o motor (e o consumo.py) de forma atômica (tmp + mv): um «wait» em curso segue lendo o arquivo antigo
  local dest=$1 py="$SELF_DIR/consumo.py" pyd
  pyd="$(dirname "$dest")/consumo.py"
  cp "$SELF" "$dest.tmp.$$" && chmod +x "$dest.tmp.$$" && mv -f "$dest.tmp.$$" "$dest" || return 1
  if [ -f "$py" ] && [ "$py" != "$pyd" ]; then cp "$py" "$pyd.tmp.$$" && mv -f "$pyd.tmp.$$" "$pyd"; fi
  echo "BOOT: motor copiado para $dest (a pasta de documentação leva tudo para outra sessão ou máquina)."
}
boot() {
  local b_work='' b_pause='' b_docs='' b_ctx='' b_slug='' b_title='' b_model='' b_effort='' b_warm='' b_ass='' b_fb='' b_cfg='' b_ini=0 b_copia=1 b_ac=1 b_forcar=0 b_reusar=0
  local b_modo='' b_sem='' b_res='' b_vcron=''
  while [ $# -gt 0 ]; do
    case "$1" in
      --iniciar) b_ini=1; shift; continue ;;
      --sem-copia) b_copia=0; shift; continue ;;
      --sem-autocompact) b_ac=0; shift; continue ;;
      --forcar) b_forcar=1; shift; continue ;;
      --reusar) b_reusar=1; shift; continue ;;
    esac
    [ $# -ge 2 ] || { echo "boot: a opção $1 exige um valor."; return 2; }
    case "$1" in
      --work) b_work=$2;; --pause) b_pause=$2;; --docs) b_docs=$2;; --context) b_ctx=$2;; --slug) b_slug=$2;; --title) b_title=$2;;
      --model) b_model=$2;; --effort) b_effort=$2;; --assinatura) b_ass=$2;; --warm) b_warm=$2;; --autocompact-fallback) b_fb=$2;; --config) b_cfg=$2;;
      --modo) b_modo=$2;; --semanal) b_sem=$2;; --reserva) b_res=$2;; --vigia-cron) b_vcron=$2;;
      *) echo "boot: opção desconhecida: $1"; return 2 ;;
    esac
    shift 2
  done
  if [ "$b_reusar" = 1 ]; then # o que não veio na linha de comando vem da config existente (retomar em sessão nova)
    local a0="${b_cfg:-${TURNOLONGO_CONFIG:-${CFG:-}}}" ln
    [ -f "$a0" ] || { echo "boot: --reusar exige uma config existente (--config ARQ ou TURNOLONGO_CONFIG)."; return 2; }
    while IFS= read -r ln; do
      case "${ln%%=*}" in
        TL_WORK_MIN) [ -n "$b_work" ] || b_work=${ln#*=} ;;   TL_PAUSE_MIN) [ -n "$b_pause" ] || b_pause=${ln#*=} ;;
        TL_DOCS_DIR) [ -n "$b_docs" ] || b_docs=${ln#*=} ;;   TL_CONTEXT) [ -n "$b_ctx" ] || b_ctx=${ln#*=} ;;
      esac
    done < <(env TURNOLONGO_CONFIG="$a0" "$SELF" config 2>/dev/null)
    [ -n "$b_cfg" ] || b_cfg=$a0
    [ -n "$b_ctx" ] || b_ac=0 # config antiga sem contexto: não há o que digitar
  fi
  # config nova sem --modo nem --context: o padrão é o modo guerra (pedido do usuário em 03/10/2026)
  if [ -z "$b_modo" ] && [ -z "$b_ctx" ] && [ "$b_reusar" = 0 ]; then
    local cfg_vista="${b_cfg:-${TURNOLONGO_CONFIG:-${CFG:-${b_docs:-.}/turnolongo.env}}}"
    [ -f "$cfg_vista" ] || b_modo=guerra
  fi
  if [ -n "$b_modo" ]; then # o modo preenche o que não veio explícito: contexto, orçamento semanal, reserva e cron do vigia
    case "$b_modo" in guerra|economia|normal) ;; *) echo "boot: --modo é guerra, economia ou normal."; return 2 ;; esac
    [ -n "$b_ctx" ] || case "$b_modo" in guerra) b_ctx=150K ;; economia) b_ctx=200K ;; esac
    [ -n "$b_sem" ] || { [ "$b_modo" = normal ] && b_sem=0 || b_sem=1; }
    [ -n "$b_res" ] || { [ "$b_modo" = guerra ] && b_res=10 || b_res=5; }
    [ -n "$b_vcron" ] || case "$b_modo" in guerra) b_vcron='17 */2 * * *' ;; economia) b_vcron='17 * * * *' ;; *) b_vcron='17,47 * * * *' ;; esac
  fi
  case "${b_sem:-0}" in 0|1) ;; *) echo "boot: --semanal é 0 ou 1."; return 2 ;; esac
  [ -z "$b_res" ] || { [[ "$b_res" =~ ^[0-9]+$ ]] && [ "$b_res" -le 50 ]; } || { echo "boot: --reserva é um porcento inteiro de 0 a 50."; return 2; }
  [[ "$b_work" =~ ^[0-9]+$ ]] && [ "$b_work" -ge 1 ] && [ "$b_work" -le 600 ] || { echo "boot: --work precisa ser um número de minutos (1 a 600)."; return 2; }
  [[ "$b_pause" =~ ^[0-9]+$ ]] && [ "$b_pause" -ge 1 ] && [ "$b_pause" -le 1440 ] || { echo "boot: --pause precisa ser um número de minutos (1 a 1440)."; return 2; }
  [ -n "$b_docs" ] || { echo "boot: --docs é obrigatório (pasta onde a documentação do turno persiste entre sessões)."; return 2; }
  [ "$b_ac" = 0 ] || [ -n "$b_ctx" ] || { echo "boot: --context é obrigatório (150K, 250K, 500K, 1M ou outro de 100K a 1M)."; return 2; }
  [ -z "$b_ctx" ] || ctx_tokens "$b_ctx" >/dev/null || { echo "boot: --context «$b_ctx» inválido (use 150K, 250K, 500K, 1M; de 100K a 1M)."; return 2; }
  case "${b_warm:-0}" in 0|1) ;; *) echo "boot: --warm é 0 ou 1."; return 2;; esac
  case "${b_fb:-avisar}" in avisar|settings) ;; *) echo "boot: --autocompact-fallback é avisar ou settings."; return 2;; esac
  mkdir -p "$b_docs" || return 2; b_docs="$(cd "$b_docs" && pwd)"
  [ -z "$b_ctx" ] || b_ctx=$(ctx_label "$(ctx_tokens "$b_ctx")")
  local alvo existia=0 entry_novo rc=0 tpl_dir="$TEMPL" tpl top slug title
  alvo="${b_cfg:-${TURNOLONGO_CONFIG:-${CFG:-$b_docs/turnolongo.env}}}"
  mkdir -p "$(dirname "$alvo")" || return 2; alvo="$(cd "$(dirname "$alvo")" && pwd)/$(basename "$alvo")"
  [ -f "$alvo" ] && existia=1
  top="$(git -C "$b_docs" rev-parse --show-toplevel 2>/dev/null || true)"
  slug="${b_slug:-$TL_SLUG}"; [ "$existia" = 1 ] || slug="${b_slug:-$(basename "${top:-$b_docs}" | tr -c 'A-Za-z0-9_\n-' '-')}"
  title="${b_title:-$TL_TITLE}"
  if [ "$existia" = 1 ]; then
    local cur_docs; cur_docs=$(TL_CONFIG_DIR="$(dirname "$alvo")"; . "$alvo" >/dev/null 2>&1; printf '%s' "${TL_DOCS_DIR:-}")
    cfg_set "$alvo" TL_WORK_MIN "$b_work"; cfg_set "$alvo" TL_PAUSE_MIN "$b_pause"
    [ "$cur_docs" = "$b_docs" ] || cfg_set "$alvo" TL_DOCS_DIR "$b_docs"
    [ -z "$b_ctx" ] || cfg_set "$alvo" TL_CONTEXT "$b_ctx"
    [ -z "$b_slug" ] || cfg_set "$alvo" TL_SLUG "$b_slug"; [ -z "$b_title" ] || cfg_set "$alvo" TL_TITLE "$b_title"
    [ -z "$b_model" ] || cfg_set "$alvo" TL_MODEL "$b_model"; [ -z "$b_effort" ] || cfg_set "$alvo" TL_EFFORT "$b_effort"
    [ -z "$b_ass" ] || cfg_set "$alvo" TL_ASSINATURA "$b_ass"; [ -z "$b_warm" ] || cfg_set "$alvo" TL_WARM "$b_warm"
    [ -z "$b_fb" ] || cfg_set "$alvo" TL_AUTOCOMPACT_FALLBACK "$b_fb"
    [ -z "$b_modo" ] || cfg_set "$alvo" TL_MODO "$b_modo"; [ -z "$b_sem" ] || cfg_set "$alvo" TL_SEMANAL "$b_sem"
    [ -z "$b_res" ] || cfg_set "$alvo" TL_RESERVA "$b_res"; [ -z "$b_vcron" ] || cfg_set "$alvo" TL_VIGIA_CRON "$b_vcron"
    entry_novo="$TL_ENTRY"
    echo "BOOT: configuração existente reconfigurada em $alvo (só as chaves informadas mudaram)."
    if [ "$b_copia" = 1 ] && [ -f "$(dirname "$alvo")/turnolongo.sh" ]; then
      entry_novo="$(dirname "$alvo")/turnolongo.sh"
      if [ "$b_forcar" = 1 ] && [ "$entry_novo" != "$SELF" ]; then copia_motor "$entry_novo"; fi
    fi
  else
    mkdir -p "$(dirname "$alvo")"
    {
      printf '# turnolongo.env — configuração do turno longo (gerada por «boot» em %s). Shell: só CHAVE='"'"'valor'"'"'.\n' "$(stamp)"
      printf 'TL_SLUG=%s\nTL_TITLE=%s\nTL_WORK_MIN=%s\nTL_PAUSE_MIN=%s\n' "$(sq "$slug")" "$(sq "$title")" "$b_work" "$b_pause"
      if [ "$b_docs" = "$(dirname "$alvo")" ]; then # a pasta anda junto: $TL_CONFIG_DIR vale em qualquer caminho ou máquina
        printf '%s\n' 'TL_DOCS_DIR="$TL_CONFIG_DIR"' 'TL_LOG="$TL_CONFIG_DIR/cycle-log.md"' 'TL_VISTORIA="$TL_CONFIG_DIR/RETOMAR.md"' 'TL_CONTRATO="$TL_CONFIG_DIR/CONTRATO.md"' \
          'TL_MAPA="$TL_CONFIG_DIR/MAPA.md"' 'TL_ASSUNTOS="$TL_CONFIG_DIR/assuntos"'
      else
        printf 'TL_DOCS_DIR=%s\nTL_LOG=%s\nTL_VISTORIA=%s\nTL_CONTRATO=%s\nTL_MAPA=%s\nTL_ASSUNTOS=%s\n' "$(sq "$b_docs")" "$(sq "$b_docs/cycle-log.md")" \
          "$(sq "$b_docs/RETOMAR.md")" "$(sq "$b_docs/CONTRATO.md")" "$(sq "$b_docs/MAPA.md")" "$(sq "$b_docs/assuntos")"
      fi
      printf 'TL_CONTEXT=%s\nTL_MODEL=%s\nTL_EFFORT=%s\nTL_ASSINATURA=%s\n' "$(sq "$b_ctx")" "$(sq "$b_model")" "$(sq "$b_effort")" "$(sq "${b_ass:-Claude}")"
      printf 'TL_WARM=%s\nTL_WARM_MAX=4\nTL_AUTOCOMPACT_FALLBACK=%s\nTL_SCRIPT_MAX_MIN=120\n' "${b_warm:-0}" "${b_fb:-avisar}"
      printf 'TL_MODO=%s\nTL_SEMANAL=%s\nTL_RESERVA=%s\nTL_PAUSA_MAX_MIN=240\nTL_GUARDA5H=90\nTL_VIGIA_CRON=%s\n' \
        "$(sq "${b_modo:-normal}")" "${b_sem:-0}" "${b_res:-10}" "$(sq "${b_vcron:-17,47 * * * *}")"
    } > "$alvo"
    echo "BOOT: configuração criada em $alvo."
    entry_novo="$SELF"
    if [ "$b_copia" = 1 ] && [ "$(dirname "$alvo")/turnolongo.sh" != "$SELF" ]; then
      copia_motor "$(dirname "$alvo")/turnolongo.sh" && entry_novo="$(dirname "$alvo")/turnolongo.sh"
    fi
  fi
  # recarrega as chaves gravadas, para os modelos de texto refletirem a config real
  local cdir; cdir="$(cd "$(dirname "$alvo")" && pwd)"; TL_CONFIG_DIR="$cdir"; unset TL_MAPA TL_ASSUNTOS; . "$alvo"
  local r_log="${CYCLE_LOG:-${TL_LOG:-$b_docs/cycle-log.md}}" r_vis="${TL_VISTORIA:-$b_docs/RETOMAR.md}" r_con="${TL_CONTRATO:-$b_docs/CONTRATO.md}"
  TL_MAPA="${TL_MAPA:-$b_docs/MAPA.md}"; TL_ASSUNTOS="${TL_ASSUNTOS:-$b_docs/assuntos}"; TL_MODO="${TL_MODO:-normal}"; TL_SEMANAL="${TL_SEMANAL:-0}"
  TL_DOCS_DIR=$b_docs; TL_CONTRATO=$r_con; TL_VISTORIA=$r_vis; LOG=$r_log; TL_ENTRY=$entry_novo # o MAPA nasce com os caminhos novos
  local orc_linha='Orçamento semanal desligado (`orcamento` só informa).'
  [ "$TL_SEMANAL" = 1 ] && orc_linha="Orçamento semanal ligado (reserva ${TL_RESERVA:-10}%): \`pause\` e \`hibernar\` estendem a pausa quando o ritmo da semana passa do seguro; a extensão é automática e fica no log."
  local kvs=("ENTRY=$entry_novo" "TITLE=${TL_TITLE:-$title}" "WORK=$b_work" "PAUSE=$b_pause" "DOCS=$b_docs" "LOG=$r_log" "VISTORIA=$r_vis" "CONTRATO=$r_con"
             "CONTEXT=${b_ctx:-${TL_CONTEXT:-}}" "ASSINATURA=${b_ass:-${TL_ASSINATURA:-Claude}}" "MODEL=${b_model:-${TL_MODEL:-}}" "EFFORT=${b_effort:-${TL_EFFORT:-}}" "DATA=$(stamp)"
             "MODO=$TL_MODO" "RESERVA=${TL_RESERVA:-10}" "MAPA=$TL_MAPA" "VIGIA_CRON=${TL_VIGIA_CRON:-17,47 * * * *}" "ORCAMENTO_LINHA=$orc_linha")
  local vis_tpl=VISTORIA; [ "$(basename "$r_vis")" = RETOMAR.md ] && vis_tpl=RETOMAR
  for tpl in CONTRATO:"$r_con" "$vis_tpl:$r_vis"; do
    local nome=${tpl%%:*} dest=${tpl#*:}
    if [ ! -f "$dest" ] || { [ "$b_forcar" = 1 ] && [ "$nome" = CONTRATO ]; }; then
      [ -f "$tpl_dir/$nome.md.tpl" ] || { echo "boot: modelo $tpl_dir/$nome.md.tpl não encontrado; rode o boot a partir da skill instalada (~/.claude/skills/turnolongo/scripts/turnolongo.sh)."; return 2; }
      mkdir -p "$(dirname "$dest")"; render "$tpl_dir/$nome.md.tpl" "${kvs[@]}" > "$dest"; echo "BOOT: $nome criado em $dest."
    else
      echo "BOOT: $nome já existe em $dest (mantido$([ "$nome" = CONTRATO ] && echo '; --forcar recria')$([ "$nome" != CONTRATO ] && echo '; a vistoria viva nunca é sobrescrita'))."
      if [ "$nome" = CONTRATO ]; then
        local marc; marc=$(sed -n 's/^<!-- turnolongo-contrato: \(.*\) -->$/\1/p' "$dest" | head -1)
        if [ -n "$marc" ] && [ "$marc" != "$entry_novo" ]; then
          echo "BOOT: AVISO: o CONTRATO cita o motor em $marc, mas o motor agora é $entry_novo (a pasta mudou de lugar ou de máquina?): rode o boot com --forcar para recriá-lo com os caminhos novos."
        fi
      fi
    fi
  done
  # contrato próprio do projeto (sem a marca do turnolongo, ex.: GEMINI.md): fica intacto; as regras da v2 vão para um anexo
  if [ -f "$r_con" ] && ! grep -q '^<!-- turnolongo-contrato:' "$r_con" && [ -f "$tpl_dir/CONTRATO.md.tpl" ]; then
    render "$tpl_dir/CONTRATO.md.tpl" "${kvs[@]}" > "$b_docs/CONTRATO-turnolongo.md"
    echo "BOOT: o contrato configurado ($r_con) é do projeto: mantido intacto. As regras do turno v2 (relógio, economia, vistoria) estão em $b_docs/CONTRATO-turnolongo.md: leia os dois."
  fi
  if [ -f "$tpl_dir/vigia-prompt.txt.tpl" ]; then
    render "$tpl_dir/vigia-prompt.txt.tpl" "${kvs[@]}" > "$cdir/vigia-prompt.txt"; echo "BOOT: prompt do vigia em $cdir/vigia-prompt.txt (cron do vigia: ${TL_VIGIA_CRON:-17,47 * * * *})."
  fi
  local tinha_mapa=0; [ -f "$TL_MAPA" ] && tinha_mapa=1
  mapa_garante; mkdir -p "$TL_ASSUNTOS"
  [ "$tinha_mapa" = 1 ] || echo "BOOT: MAPA criado em $TL_MAPA (um arquivo por assunto em $TL_ASSUNTOS/)."
  local TC="TURNOLONGO_CONFIG=$alvo"
  if [ "$b_ac" = 1 ]; then env "$TC" "$entry_novo" autocompact "$b_ctx"; rc=$?; [ "$rc" -eq 4 ] && echo "BOOT: o /autocompact ficou PENDENTE (rc 4): peça ao usuário para digitar."; fi
  if [ "$b_ini" = 1 ]; then
    local fase_atual; fase_atual=$(env "$TC" "$entry_novo" fase 2>/dev/null)
    case "$fase_atual" in work|pause|livre) env "$TC" "$entry_novo" retomar "novo turno longo (boot)" ;; *) env "$TC" "$entry_novo" work "início do turno longo (boot)" ;; esac
    if [ "${TL_WARM:-0}" = 1 ]; then env "$TC" "$entry_novo" aquecer
    elif [ "${TL_SEMANAL:-0}" = 1 ]; then env "$TC" "$entry_novo" medir; fi
    env "$TC" "$entry_novo" anuncio
  fi
  return 0
}

# ---------- despacho ----------
if [ "${1:-}" = boot ]; then shift; boot "$@"; exit $?; fi # o boot ainda não tem ciclo: cada fase vem de um filho já com a config nova
load
if [ "$SCRIPT_T0" -gt 0 ] && ! script_live; then # o wrapper morreu sem fechar (kill -9, reboot): sem crédito
  rot=$SCRIPT_LABEL; script_fecha 0
  log "SCRIPT «$rot» ÓRFÃO" "o processo sumiu sem fechar; sem crédito de tempo (o prazo do bloco #$N segue o que estava)"
fi
case "${1:-status}" in
  work|pause|compensar|retomar|livre|encerrar)
    if script_live; then
      echo "RECUSADO: o script «$SCRIPT_LABEL» ainda roda (relógio de trabalho suspenso, $(fmt $(($(now) - SCRIPT_T0))) de execução). Espere o fim (ou TaskStop) e só então use '$1'."
      exit 2
    fi ;;
esac

case "${1:-status}" in
  work)
    if [ "$PHASE" = pause ] && [ "$(now)" -lt "$END" ]; then
      echo "RECUSADO: PAUSA #$N em curso, faltam $(fmt $((END - $(now)))). Trabalhar na pausa é PROIBIDO — hiberne."
      exit 20
    fi
    if [ "$PHASE" = work ]; then
      if [ "$(now)" -lt "$END" ]; then
        echo "RECUSADO: TRABALHO #$N em curso, restam $(fmt $((END - $(now))))."; exit 2
      fi
      echo "RECUSADO: TRABALHO #$N vencido há $(fmt $(($(now) - END))): vistoria e 'pause' (parado há 5 min ou mais, por limite de uso etc.: 'pause --parada \"motivo\"'; 'retomar' só com autorização do usuário no chat)."; exit 10
    fi
    # fim da pausa com o orçamento ligado: se o ritmo da semana (ou a janela de 5 h) ainda não comporta o bloco, estende a pausa
    if [ "$PHASE" = pause ] && [ "$TL_SEMANAL" = 1 ] && [ "$LAST_WS" -gt 0 ] && orcamento_calc "$LAST_WS" "$LAST_WE" && [ "$ORC_FIM" -gt $(($(now) + 60)) ]; then
      stop_units "pause-$N"; END=$ORC_FIM; save; alarm $((END - $(now))) pause "$N"
      log "PAUSA #$N ESTENDIDA pelo orçamento" "até $(hhmmss "$END") · $ORC_LINHA"
      echo "PAUSA #$N ESTENDIDA até $(quando "$END") — $ORC_LINHA"
      echo "Hiberne de novo, em segundo plano: $TL_ENTRY hibernar"
      exit 40
    fi
    [ "$PHASE" = livre ] && log "CICLO LIVRE encerra" "desde $(quando "$START")${MOTIVO:+ ($MOTIVO)}; 'work' reinicia o relógio"
    abre_bloco "${2:-}" ;;
  pause)
    if [ "${2:-}" = "--parada" ]; then
      # O tempo PARADO (limite de uso, app suspenso) conta como pausa, de TL_PAUSE_MIN contados do FIM do bloco. Só depois de um
      # bloco de trabalho vencido há 5 min ou mais, e com o motivo anotado.
      motivo="${3:-}"
      [ -n "$motivo" ] || { echo "RECUSADO: --parada exige o motivo (ex.: pause --parada \"limite de uso\")."; exit 2; }
      [ "$PHASE" = work ] || { echo "RECUSADO: --parada só vale depois de um bloco de TRABALHO (fase atual: $PHASE)."; exit 2; }
      idle=$(($(now) - END))
      [ "$idle" -ge 0 ] || { echo "RECUSADO: o bloco #$N ainda corre (restam $(fmt $idle)): não é parada."; exit 2; }
      [ "$idle" -ge 300 ] || { echo "RECUSADO: o bloco #$N venceu há $(fmt $idle): menos de 5 min não é parada. Use 'pause' normal."; exit 2; }
      fim=$((END + PAUSE_MIN * 60)); LAST_WS=$START; LAST_WE=$END
      if [ "$TL_SEMANAL" = 1 ] && orcamento_calc "$LAST_WS" "$LAST_WE" && [ "$ORC_FIM" -gt "$fim" ]; then fim=$ORC_FIM; fi
      stop_units "work-$N"
      PHASE=pause; START=$END; END=$fim; save
      if [ "$fim" -le "$(now)" ]; then
        log "PAUSA #$N CUMPRIDA PELA PARADA" "parada de $(fmt $idle) desde o fim do bloco ($motivo); 'work' liberado (regra autorizada pelo usuário)"
        echo "PAUSA #$N CUMPRIDA PELA PARADA (parado há $(fmt $idle)): pode iniciar 'work' agora."
      else
        alarm $((fim - $(now))) pause "$N"
        log "PAUSA #$N inicia (descontada a parada)" "parada de $(fmt $idle) desde o fim do bloco ($motivo); faltam $(fmt $((fim - $(now))))"
        echo "PAUSA #$N: faltam $(fmt $((fim - $(now)))) (até $(hhmmss "$fim")) — HIBERNE"
      fi
      exit 0
    fi
    [ "$PHASE" = work ] || { echo "RECUSADO: 'pause' só vale depois de um bloco de TRABALHO (fase atual: $PHASE)."; exit 2; }
    vistoria_aviso
    LAST_WS=$START; LAST_WE=$(now); pfim=$((LAST_WE + PAUSE_MIN * 60)); orc=''
    if [ "$TL_SEMANAL" = 1 ] && orcamento_calc "$LAST_WS" "$LAST_WE"; then
      [ "$ORC_FIM" -gt "$pfim" ] && pfim=$ORC_FIM
      orc=$ORC_LINHA
    fi
    stop_units "work-$N" # o alarme do fim do bloco já não serve (evita o «DESPERTAR work obsoleto»)
    PHASE=pause; START=$LAST_WE; END=$pfim; save
    alarm $((END - START)) pause "$N"
    log "PAUSA #$N inicia" "${2:-}${orc:+ · $orc}"
    echo "PAUSA #$N: $(((END - START + 59) / 60)) min (até $(hhmmss "$END")) — HIBERNE: $TL_ENTRY hibernar (uma tarefa em segundo plano)"
    [ -z "$orc" ] || echo "$orc" ;;
  livre)
    motivo="${2:-}"; [ -n "$motivo" ] || { echo 'uso: livre "motivo" (ex.: usuário pediu para trabalhar sem relógio)'; exit 2; }
    if [ "$PHASE" = livre ]; then echo "LIVRE desde $(quando "$START")${MOTIVO:+ ($MOTIVO)}: já estava livre."; exit 0; fi
    left=$((END - $(now)))
    stop_units "work-$N"; stop_units "pause-$N"; stop_units checar
    if [ "$PHASE" = none ]; then ant='sem ciclo'; elif [ "$left" -gt 0 ]; then ant="$PHASE #$N, restavam $(fmt $left)"; else ant="$PHASE #$N, vencida há $(fmt $left)"; fi
    PHASE=livre; START=$(now); END=$START; MOTIVO=$motivo; save
    log "CICLO LIVRE inicia" "$motivo (antes: $ant; autorizado pelo usuário)"
    echo "LIVRE: relógio suspenso ($motivo; antes: $ant). 'work' abre o próximo bloco (#$((N + 1))); os agentes ficam liberados." ;;
  encerrar)
    stop_units "work-$N"; stop_units "pause-$N"; stop_units checar; stop_units aquecer
    jload; JANELA_PROX=0; jsave
    log "TURNO LONGO ENCERRADO" "${2:-sem nota} (fase anterior: $PHASE #$N)"
    PHASE=none; START=0; END=0; CREDIT=0; MOTIVO=''; save
    echo "TURNO LONGO ENCERRADO: alarmes e aquecimento parados. 'work' abre um turno novo (#$((N + 1)))." ;;
  script) # script "rótulo" -- comando [args...]
    rotulo="${2:-}"; rotulo="${rotulo//|//}"
    if [ -z "$rotulo" ] || [ "${3:-}" != "--" ] || [ "$#" -lt 4 ]; then echo 'uso: script "rótulo" -- comando [args...]'; exit 2; fi
    shift 3
    [ "$PHASE" = work ] || { echo "RECUSADO: script creditado só roda dentro de um bloco de TRABALHO (fase atual: $PHASE); na pausa é PROIBIDO."; exit 2; }
    t0=$(now)
    [ "$END" -gt "$t0" ] || { echo "RECUSADO: o bloco #$N venceu há $(fmt $((t0 - END))): vistoria e pausa, não há o que suspender."; exit 10; }
    left=$((END - t0))
    mkdir -p "$STATE_DIR/scripts"
    out="$STATE_DIR/scripts/$(dfmt "$t0" %Y%m%d-%H%M%S)-N${N}-$(printf '%s' "$rotulo" | tr -c 'A-Za-z0-9' '-' | cut -c1-40).log"
    lim=(); if [ -n "$TIMEOUT_BIN" ]; then lim=("$TIMEOUT_BIN" --kill-after=15 "${SCRIPT_MAX_MIN}m"); else echo "AVISO: sem 'timeout'/'gtimeout' neste sistema: o limite de ${SCRIPT_MAX_MIN} min NÃO será aplicado."; fi
    SCRIPT_T0=$t0; SCRIPT_PID=$$; SCRIPT_LABEL=$rotulo; save
    stop_units "work-$N"
    log "SCRIPT «$rotulo» inicia" "relógio do bloco #$N suspenso (restam $(fmt $left)); programa: $(basename "$1"); modelo em silêncio; saída em arquivo"
    echo "SCRIPT «$rotulo» inicia: relógio de trabalho suspenso (restam $(fmt $left)); saída em $out"
    ${lim[@]+"${lim[@]}"} "$@" </dev/null >"$out" 2>&1 & filho=$!
    trap 'kill -TERM "$filho" 2>/dev/null' TERM INT HUP
    while :; do wait "$filho"; rc=$?; kill -0 "$filho" 2>/dev/null || break; done
    trap - TERM INT HUP
    t1=$(now); load
    if [ "$PHASE" = work ] && [ "$SCRIPT_T0" = "$t0" ]; then
      script_fecha $((t1 - t0))
      extra=''; [ "$rc" -eq 124 ] && extra="; EXCEDEU o limite de ${SCRIPT_MAX_MIN} min e foi morto"
      log "SCRIPT «$rotulo» termina" "exit $rc em $(fmt $((t1 - t0)))$extra; crédito $(fmt $((t1 - t0))) (total no bloco: $(fmt $CREDIT)); bloco #$N termina às $(hhmmss "$END"); saída: $out"
      echo "SCRIPT «$rotulo» terminou (exit $rc) em $(fmt $((t1 - t0))): creditado ao bloco #$N, que termina às $(hhmmss "$END") (restam $(fmt $((END - $(now))))). Saída: $out"
    else
      log "SCRIPT «$rotulo» terminou SEM crédito" "exit $rc em $(fmt $((t1 - t0))); o ciclo mudou durante a execução (fase $PHASE #$N)"
      echo "SCRIPT «$rotulo» terminou (exit $rc) em $(fmt $((t1 - t0))), SEM crédito: o ciclo mudou durante a execução. Saída: $out"
    fi
    exit "$rc" ;;
  compensar)
    MIN="${2:-20}"
    if [ "$PHASE" != pause ]; then echo "RECUSADO: só se compensa a partir de uma PAUSA (fase atual: $PHASE)."; exit 2; fi
    left=$((END - $(now)))
    log "PAUSA #$N INTERROMPIDA" "autorizado pelo usuário no chat; $([ "$left" -gt 0 ] && echo "restavam $(fmt $left)" || echo 'já vencida')"
    stop_units "pause-$N"
    PHASE=work; START=$(now); END=$((START + MIN * 60)); CREDIT=0; save
    alarm $((MIN * 60)) work "$N"
    log "TRABALHO #$N (compensação de ${MIN} min) inicia" "${3:-}"
    echo "TRABALHO #$N (compensação): ${MIN} min (até $(hhmmss "$END"))" ;;
  retomar)
    if [ "$PHASE" = none ]; then echo "RECUSADO: não há ciclo para retomar; use 'work'."; exit 2; fi
    left=$((END - $(now)))
    case "$PHASE" in
      pause) log "PAUSA #$N INTERROMPIDA" "autorizado pelo usuário no chat; $([ "$left" -gt 0 ] && echo "restavam $(fmt $left)" || echo 'já vencida')"; stop_units "pause-$N" ;;
      livre) log "CICLO LIVRE encerra" "desde $(quando "$START")${MOTIVO:+ ($MOTIVO)}; autorizado pelo usuário no chat" ;;
      *)     log "TRABALHO #$N ENCERRADO SEM PAUSA" "autorizado pelo usuário no chat; $([ "$left" -gt 0 ] && echo "restavam $(fmt $left)" || echo "vencido há $(fmt $left)")"; stop_units "work-$N" ;;
    esac
    abre_bloco "${2:-}" ;;
  status)
    if script_live; then
      echo "TRABALHO #$N SUSPENSO: o script «$SCRIPT_LABEL» roda há $(fmt $(($(now) - SCRIPT_T0))) sem consumo do modelo (relógio parado; restam $(fmt $((END - SCRIPT_T0))) de trabalho)"
      exit 0
    fi
    left=$((END - $(now)))
    case "$PHASE" in
      work)  cred=''; [ "$CREDIT" -gt 0 ] && cred=" (+$(fmt $CREDIT) de scripts, não contados)"
             if [ "$left" -gt 0 ]; then echo "TRABALHO #$N restam $(fmt $left)$cred"; rc=0
             else echo "TRABALHO #$N VENCIDO ($(fmt $left) atrás): vistoria, /compact, pausa"; rc=10; fi ;;
      pause) if [ "$left" -gt 0 ]; then echo "PAUSA #$N restam $(fmt $left): PROIBIDO TRABALHAR"; rc=20
             else echo "PAUSA #$N ENCERRADA: pode iniciar 'work'"; rc=30; fi ;;
      livre) echo "LIVRE desde $(quando "$START")${MOTIVO:+ ($MOTIVO)}: ciclo suspenso, sem relógio; 'work' abre o próximo bloco"; rc=0 ;;
      *)     echo "SEM CICLO ATIVO"; rc=0 ;;
    esac
    janela_aviso
    exit "$rc" ;;
  wait)
    shift; max=0; modo=fase
    while [ $# -gt 0 ]; do
      case "$1" in
        --max) [[ "${2:-}" =~ ^[0-9]+$ ]] || { echo "wait: --max exige segundos inteiros."; exit 2; }; max=$2; shift 2 ;;
        --trabalho) modo=trabalho; shift ;;
        *) echo "uso: wait [--max SEG] [--trabalho]"; exit 2 ;;
      esac
    done
    t_ini=$(now)
    espera() { # dorme até 5 s, sem passar do --max; devolve 1 quando o --max acabou
      local rest=5
      if [ "$max" -gt 0 ]; then rest=$((max - ($(now) - t_ini))); [ "$rest" -gt 0 ] || return 1; [ "$rest" -gt 5 ] && rest=5; fi
      sleep "$rest"; return 0
    }
    if [ "$modo" = trabalho ]; then
      while :; do
        load
        case "$PHASE" in
          livre) echo "[cycle] LIVRE desde $(quando "$START"): sem relógio, pode trabalhar"; exit 0 ;;
          none)  echo "[cycle] SEM CICLO ATIVO: sem relógio, pode trabalhar"; exit 0 ;;
          work)  if ! script_live && [ "$(now)" -lt "$END" ]; then echo "[cycle] TRABALHO #$N ativo: restam $(fmt $((END - $(now))))"; exit 0; fi ;;
        esac
        espera || { echo "[cycle] --max esgotado: $PHASE #$N ainda não é um bloco de trabalho ativo"; exit 3; }
      done
    fi
    # Recarrega o estado a cada volta: o crédito de um script empurra o fim do bloco, e um script em curso congela o relógio.
    fase0="$PHASE"; n0="$N"
    [ "$fase0" = livre ] && { echo "[cycle] ciclo LIVRE: nada a esperar"; exit 0; }
    while :; do
      load
      [ "$PHASE" = "$fase0" ] && [ "$N" = "$n0" ] || break # outro comando mexeu no ciclo (retomar, pause, compensar, livre)
      script_live || [ "$(now)" -lt "$END" ] || break
      espera || { echo "[cycle] --max esgotado: $fase0 #$n0 segue (restam $(fmt $((END - $(now)))))"; exit 3; }
    done
    echo "[cycle] fim de $fase0 #$n0 em $(stamp)" ;;
  fase)    echo "$PHASE" ;;
  config)  config_dump ;;
  anuncio) anuncio ;;
  agente)  agente_texto ;;
  log)  log "nota" "${2:-}" ;;
  aquecer)
    shift; teste=0; auto=0
    for a in "$@"; do case "$a" in --teste) teste=1;; --auto) auto=1;; *) echo "uso: aquecer [--teste]"; exit 2;; esac; done
    if [ "$auto" = 1 ]; then
      [ "$TL_WARM" = 1 ] || { log "AQUECIMENTO ignorado" "TL_WARM não está ligado"; exit 0; }
      [ "$PHASE" != none ] || { log "AQUECIMENTO encerrado" "sem ciclo ativo; a cadeia para aqui"; exit 0; }
      jload
      if [ "$JANELA_AUTO" -ge "$TL_WARM_MAX" ]; then
        log "AQUECIMENTO PAUSADO" "$JANELA_AUTO janelas seguidas sem atividade do usuário (limite $TL_WARM_MAX); volta no próximo 'work'"
        notificar normal "$NOTIF_TITLE" "Aquecimento da janela de 5 h pausado: $JANELA_AUTO janelas sem atividade."; exit 0
      fi
      JANELA_AUTO=$((JANELA_AUTO + 1)); jsave
      saida=$(aquecer_medir 1); rc=$?
      log "AQUECIMENTO automático" "${saida//$'\n'/ }"
      if [ "$rc" -ne 0 ]; then
        jload
        if [ "$JANELA_FALHAS" -lt 4 ]; then agenda 600 aquecer-0 aquecer --auto
        else notificar critical "$NOTIF_TITLE" "O aquecimento da janela de 5 h falhou $JANELA_FALHAS vezes: confira o login do CLI."; fi
      fi
      exit 0
    fi
    if [ "$teste" = 0 ] && [ "$TL_WARM" != 1 ]; then
      echo "Aquecimento desligado (TL_WARM=0). 'aquecer --teste' mede uma vez sem armar nada."; exit 0
    fi
    aquecer_medir $([ "$teste" = 1 ] && echo 0 || echo 1); exit $? ;;
  janela)
    shift
    if [ "${1:-}" = "--registrar" ]; then
      [[ "${2:-}" =~ ^[0-9]+$ ]] || { echo 'uso: janela --registrar FIM_EPOCH [UTIL [FONTE]]'; exit 2; }
      jload; JANELA_FIM=$2; JANELA_UTIL="${3:-}"; JANELA_FONTE="${4:-registrado}"; JANELA_MEDIDA=$(now); JANELA_STATUS=registrado
      if [ "$TL_WARM" = 1 ] && [ "$JANELA_FIM" -gt "$(now)" ]; then
        JANELA_PROX=$((JANELA_FIM + 20)); jsave; stop_units aquecer; agenda $((JANELA_PROX - $(now))) aquecer-0 aquecer --auto
      else jsave; fi
      log "JANELA registrada" "fim $(quando "$JANELA_FIM"); utilização $(pct "$JANELA_UTIL"); fonte ${JANELA_FONTE}"
    fi
    jload
    if [ "$JANELA_FIM" -gt 0 ]; then
      echo "JANELA DE 5 H: $([ "$JANELA_FIM" -gt "$(now)" ] && echo "termina $(quando "$JANELA_FIM") (em $(fmt_h $((JANELA_FIM - $(now)))))" || echo "expirou $(quando "$JANELA_FIM")") · utilização $(pct "$JANELA_UTIL") · medida $(quando "$JANELA_MEDIDA") [${JANELA_FONTE:-?}; status ${JANELA_STATUS:-?}]$([ "$JANELA_PROX" -gt 0 ] && echo " · próximo aquecimento $(quando "$JANELA_PROX")")"
      if [ "$JANELA_SEMANA" = rejected ]; then echo "SEMANA: limite semanal esgotado; sem aquecimento."; fi
    else echo "JANELA DE 5 H: desconhecida (nunca medida; 'aquecer --teste' mede com uma chamada mínima)."; fi
    if [ "$JANELA_FIM7" -gt "$(now)" ]; then echo "SEMANA: $(pct "$JANELA_UTIL7") · reseta $(quando "$JANELA_FIM7") (em $(fmt_d $((JANELA_FIM7 - $(now)))))"; fi ;;
  medir) aquecer_medir 0; exit $? ;;
  uso)
    shift
    if [ "${1:-}" = --registrar ]; then shift; uso_registrar "$@"; exit $?; fi
    [ $# -eq 0 ] || { echo "uso: uso [--registrar 5h=PCT@RESET 7d=PCT@RESET]"; exit 2; }
    uso_mostra ;;
  orcamento)
    case "$PHASE" in work) oi=$START; of=$(now) ;; *) oi=$LAST_WS; of=$LAST_WE ;; esac
    if [ "$oi" -le 0 ]; then oi=$(($(now) - WORK_MIN * 60)); of=$(now); fi # sem bloco fechado: mede os últimos W minutos
    orcamento_calc "$oi" "$of" || { echo "$ORC_LINHA"; exit 1; }
    echo "$ORC_LINHA"; exit "$ORC_COD" ;;
  consumo)
    shift
    [ -f "$CONSUMO_PY" ] || { echo "consumo: $CONSUMO_PY ausente."; exit 2; }
    jload
    python3 "$CONSUMO_PY" relatorio --fator7 "${JANELA_FATOR7:-$TL_FATOR7}" --fator5 "${JANELA_FATOR5:-$TL_FATOR5}" \
      --fim7 "${JANELA_FIM7:-0}" --fim5 "${JANELA_FIM:-0}" "$@"; exit $? ;;
  hibernar)
    # Uma tarefa em segundo plano: dorme no shell (sem modelo) até o fim da pausa; se o orçamento estender a pausa, segue
    # dormindo; só termina (e acorda o modelo) com o bloco seguinte aberto, ou se outro comando mudar o ciclo.
    if [ "$PHASE" != pause ]; then
      if [ "$PHASE" = work ]; then echo "HIBERNAR: o bloco #$N de trabalho está aberto; nada a esperar."; else echo "HIBERNAR: não há pausa em curso (fase: $PHASE)."; fi
      exit 0
    fi
    log "HIBERNAÇÃO" "pausa #$N até $(hhmmss "$END"); o modelo só volta com o bloco seguinte aberto"
    while :; do
      "$SELF" wait >/dev/null 2>&1
      load
      if [ "$PHASE" != pause ]; then echo "HIBERNAR: o ciclo mudou durante a pausa:"; "$SELF" status; exit $?; fi
      [ "$(now)" -ge "$END" ] || continue
      saida=$("$SELF" work "fim da pausa (hibernar)" 2>&1); rc=$?
      if [ "$rc" -eq 40 ]; then
        load; [ "$END" -gt "$(now)" ] || { echo "HIBERNAR: a pausa foi estendida sem prazo novo; pare e confira o 'orcamento'."; exit 1; }
        continue
      fi
      printf '%s\n' "$saida"; exit "$rc"
    done ;;
  assunto)
    shift; sub=${1:-lista}; [ $# -gt 0 ] && shift
    case "$sub" in
      novo) assunto_novo "$@" ;; lista) assunto_lista ;; fechar) assunto_fechar "$@" ;; sessao) assunto_sessao "$@" ;; cli) assunto_cli "$@" ;;
      *) echo 'uso: assunto novo "título" [--objetivo T] | lista | fechar SLUG [nota] | sessao SLUG ID | cli SLUG [--modelo M] [--esforco E]'; exit 2 ;;
    esac
    exit $? ;;
  retomada)
    shift; slug=''; modo=normal
    for a in "$@"; do
      case "$a" in --primeira) modo=primeira ;; --autonomo) modo=autonomo ;; -*) echo "uso: retomada [SLUG] [--primeira|--autonomo]"; exit 2 ;; *) slug=$a ;; esac
    done
    if [ -n "$slug" ] && [ ! -f "$TL_ASSUNTOS/$slug.md" ]; then echo "retomada: o assunto «$slug» não existe (veja 'assunto lista')."; exit 2; fi
    retomada_texto "$slug" "$modo" ;;
  economia) shift; economia_cmd "$@"; exit $? ;;
  autocompact) shift; autocompact "$@"; exit $? ;;
  fire) fase="${2:-?}"; n="${3:-0}"
        if [ "$fase" = work ] && script_live; then
          log "DESPERTAR work #$n ignorado" "script «$SCRIPT_LABEL» em curso: relógio de trabalho suspenso"; exit 0
        fi
        if [ "$PHASE" != "$fase" ] || [ "$N" != "$n" ]; then
          log "DESPERTAR $fase #$n obsoleto" "o ciclo já está em $PHASE #$N"; exit 0
        fi
        if [ "$(now)" -lt $((END - 30)) ]; then
          log "DESPERTAR $fase #$n adiantado" "faltam $(fmt $((END - $(now)))) para o fim (alarme antigo; o prazo mudou)"; exit 0
        fi
        log "DESPERTAR $fase #$n" "relógio disparou"
        agenda 600 "checar-${fase}-${n}" checar "$fase" "$n" 1
        notificar normal "$NOTIF_TITLE" "Fim de $fase #$n: $([ "$fase" = work ] && echo 'vistoria + /compact + pausa' || echo 'retomar trabalho')" ;;
  checar) # checar FASE N K: 10 min depois do fim de uma fase, confere se alguém reagiu (o estado mudou)
        fase="${2:-?}"; n="${3:-0}"; k="${4:-1}"
        if [ "$PHASE" = "$fase" ] && [ "$N" = "$n" ]; then
          log "SESSÃO SEM RESPOSTA" "${fase} #${n} acabou há $(fmt $(($(now) - END))) e o estado não mudou (limite de uso? sessão fechada?); verificação ${k}/12"
          notificar critical "$TL_TITLE — sessão parada?" "Ninguém reagiu ao fim de ${fase} #${n} há $(fmt $(($(now) - END))). Provável limite de uso ou sessão fechada: mande uma mensagem no chat."
          if [ "$k" -lt 12 ]; then agenda 1800 "checar-${fase}-${n}" checar "$fase" "$n" $((k + 1)); fi
        fi ;;
  *)    sed -n '2,/^# ---fim-do-uso---/p' "$SELF" | sed '$d'; exit 2 ;;
esac
