#!/usr/bin/env python3
# consumo.py — contabilidade local de tokens do Claude Code para o turnolongo (v2). Só números saem daqui.
#
# Lê os transcritos (TL_PROJECTS_DIR, padrão ~/.claude/projects/**/*.jsonl), deduplica por message.id (as linhas de uma
# mesma mensagem repetem o mesmo uso) e mantém um livro-razão incremental em TL_CONSUMO_DIR (padrão
# ~/.cache/turnolongo/_consumo): cada execução lê só os bytes novos. Converte os tokens em «custo equivalente de API» (US$,
# tabela PRECOS). Medido em 03/10/2026 em duas janelas semanais do plano Pro, o limite de uso anda proporcional a esse custo:
# ~US$ 3,5 a 4 por 1% do limite semanal. O fator real é recalibrado a cada medida da cota (calibra). Nenhum texto dos
# transcritos é gravado: o razão guarda hora, ids, entrada, família do modelo, contagens de tokens e o custo.
#
#   consumo.py atualiza
#   consumo.py custo --desde EPOCH [--ate EPOCH]
#   consumo.py calibra --u7 FRAÇÃO --fim7 EPOCH [--u5 FRAÇÃO --fim5 EPOCH]
#   consumo.py orcamento --bloco-ini EPOCH --bloco-fim EPOCH --pausa MIN [--u7 F --fim7 E --u5 F --fim5 E --reg E] ...
#   consumo.py relatorio [--horas N] [--fator7 F] [--fator5 F] [--fim7 E] [--fim5 E]
# Filtro comum: --entradas claude-desktop,cli (padrão: TL_ENTRADAS; vazio = todas).
import argparse
import fcntl
import glob
import json
import math
import os
import sys
import time
from datetime import datetime

RETENCAO = 10 * 86400
SEMANA, CINCO_H = 7 * 86400, 5 * 3600
# US$ por milhão de tokens: entrada, escrita de cache 5 min, escrita de cache 1 h, leitura de cache, saída.
# Tabela de referência (preços públicos da API para as famílias); Fable sem preço conhecido aqui: tratado como Opus.
# A escala absoluta não importa para o orçamento: o fator calibrado (US$ por 1% da cota) absorve a diferença.
PRECOS = {
    'opus': (5.0, 6.25, 10.0, 0.50, 25.0),
    'sonnet': (3.0, 3.75, 6.0, 0.30, 15.0),
    'haiku': (1.0, 1.25, 2.0, 0.10, 5.0),
    'fable': (5.0, 6.25, 10.0, 0.50, 25.0),
}
FATOR7_PADRAO = 4.0   # US$ por 1% do limite semanal (Pro): medido 3,45 (30/09–02/10) e 4,50 (03/10)
FATOR5_PADRAO = 0.55  # US$ por 1% da janela de 5 h (Pro): medido 0,59 em 03/10


def dir_razao():
    d = os.environ.get('TL_CONSUMO_DIR') or os.path.join(
        os.environ.get('XDG_CACHE_HOME') or os.path.expanduser('~/.cache'), 'turnolongo', '_consumo')
    os.makedirs(d, exist_ok=True)
    return d


def raiz_projetos():
    return os.environ.get('TL_PROJECTS_DIR') or os.path.join(
        os.environ.get('CLAUDE_CONFIG_DIR') or os.path.expanduser('~/.claude'), 'projects')


def familia(modelo):
    m = (modelo or '').lower()
    for k in ('opus', 'sonnet', 'haiku', 'fable'):
        if k in m:
            return k
    return 'sonnet'


def custo_usd(fam, inp, cw5, cw1, cr, out):
    p = PRECOS[fam]
    return (inp * p[0] + cw5 * p[1] + cw1 * p[2] + cr * p[3] + out * p[4]) / 1e6


def epoch_iso(s):
    try:
        return datetime.fromisoformat(str(s).replace('Z', '+00:00')).timestamp()
    except Exception:
        return None


def limpa(s, n):
    return ''.join(c for c in str(s) if c not in '\t\n\r')[:n]


def atualiza():
    """Lê os bytes novos dos transcritos e acrescenta ao razão. Devolve quantas mensagens novas entraram."""
    d = dir_razao()
    raz, idxf = os.path.join(d, 'razao.tsv'), os.path.join(d, 'indice.json')
    with open(os.path.join(d, '.trava'), 'w') as trava:
        fcntl.flock(trava, fcntl.LOCK_EX)
        try:
            with open(idxf) as f:
                idx = json.load(f)
        except Exception:
            idx = {}
        agora = time.time()
        corte = agora - RETENCAO
        ids, velhas = set(), 0
        if os.path.exists(raz):
            with open(raz, encoding='utf-8', errors='ignore') as f:
                for ln in f:
                    p = ln.split('\t', 2)
                    if len(p) < 3:
                        continue
                    ids.add(p[1])
                    try:
                        if float(p[0]) < corte:
                            velhas += 1
                    except ValueError:
                        pass
        raiz = raiz_projetos()
        novas = []
        for arq in glob.glob(os.path.join(raiz, '**', '*.jsonl'), recursive=True):
            try:
                st = os.stat(arq)
            except OSError:
                continue
            if st.st_mtime < corte:
                continue
            e = idx.get(arq) or {}
            off = e.get('off', 0) if e.get('ino') == st.st_ino and e.get('off', 0) <= st.st_size else 0
            if off >= st.st_size:
                continue
            try:
                with open(arq, 'rb') as fh:
                    fh.seek(off)
                    dados = fh.read()
            except OSError:
                continue
            fim = dados.rfind(b'\n')
            if fim < 0:
                continue  # só linhas completas; o resto fica para a próxima leitura
            proj = limpa((os.path.relpath(arq, raiz).split(os.sep)[0].split('-')[-1]) or '?', 24)
            for ln in dados[:fim + 1].split(b'\n'):
                if b'"usage"' not in ln:
                    continue
                try:
                    o = json.loads(ln)
                except Exception:
                    continue
                m = o.get('message') if isinstance(o, dict) else None
                if not isinstance(m, dict) or not isinstance(m.get('usage'), dict):
                    continue
                u, mod = m['usage'], m.get('model') or ''
                mid = m.get('id') or o.get('requestId') or o.get('uuid')
                if not mid or mid in ids or mod == '<synthetic>':
                    continue
                t = epoch_iso(o.get('timestamp'))
                if t is None or t < corte:
                    continue
                ids.add(mid)
                cc = u.get('cache_creation') or {}
                cw1 = int(cc.get('ephemeral_1h_input_tokens') or 0)
                cw5 = int(cc.get('ephemeral_5m_input_tokens') or 0)
                cwt = int(u.get('cache_creation_input_tokens') or 0)
                if cw1 + cw5 < cwt:
                    cw5 += cwt - cw1 - cw5  # sem a divisão por TTL: conta como 5 min (o mais barato)
                inp, cr, out = (int(u.get(k) or 0) for k in ('input_tokens', 'cache_read_input_tokens', 'output_tokens'))
                fam = familia(mod)
                novas.append('\t'.join(str(x) for x in (
                    int(t), limpa(mid, 80), limpa(o.get('sessionId') or '', 36), limpa(o.get('entrypoint') or '?', 20),
                    fam, inp, cw5, cw1, cr, out, '%.6f' % custo_usd(fam, inp, cw5, cw1, cr, out), proj)))
            idx[arq] = {'ino': st.st_ino, 'off': off + fim + 1}
        if novas:
            with open(raz, 'a', encoding='utf-8') as f:
                f.write('\n'.join(novas) + '\n')
        if velhas > 2000:  # poda o que passou da retenção, de vez em quando
            with open(raz, encoding='utf-8', errors='ignore') as f:
                manter = [ln for ln in f if ln.split('\t', 1)[0].isdigit() and int(ln.split('\t', 1)[0]) >= corte]
            with open(raz + '.tmp', 'w', encoding='utf-8') as f:
                f.writelines(manter)
            os.replace(raz + '.tmp', raz)
        idx = {k: v for k, v in idx.items() if os.path.exists(k)}
        with open(idxf + '.tmp', 'w') as f:
            json.dump(idx, f)
        os.replace(idxf + '.tmp', idxf)
    return len(novas)


def entradas_filtro(txt):
    txt = os.environ.get('TL_ENTRADAS', '') if txt is None else txt
    s = {e.strip() for e in txt.split(',') if e.strip()}
    return s or None


def linhas(desde=0.0, ate=None, entradas=None):
    raz = os.path.join(dir_razao(), 'razao.tsv')
    if not os.path.exists(raz):
        return
    with open(raz, encoding='utf-8', errors='ignore') as f:
        for ln in f:
            p = ln.rstrip('\n').split('\t')
            if len(p) < 12:
                continue
            try:
                t = int(p[0])
                nums = [int(x) for x in p[5:10]]
                usd = float(p[10])
            except ValueError:
                continue
            if t < desde or (ate is not None and t >= ate):
                continue
            if entradas and p[3] not in entradas:
                continue
            yield t, p[2], p[3], p[4], nums, usd, p[11]


def custo(desde, ate=None, entradas=None):
    return sum(r[5] for r in linhas(desde, ate, entradas))


def fracao(x):
    """'0.46' -> 46.0 (porcento); vazio ou inválido -> None."""
    try:
        return float(str(x).replace(',', '.')) * 100.0 if str(x).strip() not in ('', 'None') else None
    except ValueError:
        return None


def num(x, casas=1):
    return ('%.*f' % (casas, x)).replace('.', ',')


def quando(t):
    lt, hoje = time.localtime(t), time.localtime()
    return time.strftime('%H:%M' if lt[:3] == hoje[:3] else '%d/%m %H:%M', lt)


def duracao(s):
    s = max(0, int(s))
    d, h, m = s // 86400, s % 86400 // 3600, s % 3600 // 60
    return '%dd%02dh' % (d, h) if d else '%dh%02dm' % (h, m)


def cmd_calibra(a):
    agora, ents = time.time(), entradas_filtro(a.entradas)
    u7, u5 = fracao(a.u7), fracao(a.u5)
    # O plano informa porcentos inteiros: abaixo de 5% (semana) ou 10% (5 h) o arredondamento passaria de 10% de erro.
    if u7 is not None and a.fim7 > agora and u7 >= 5:
        f = custo(a.fim7 - SEMANA, agora, ents) / u7
        if 0.3 <= f <= 60:
            print('FATOR7=%.4f' % f)
    if u5 is not None and a.fim5 > agora and u5 >= 10:
        f = custo(a.fim5 - CINCO_H, agora, ents) / u5
        if 0.03 <= f <= 20:
            print('FATOR5=%.4f' % f)


def cmd_orcamento(a):
    """Quando a pausa deve acabar para o último bloco caber no ritmo seguro da semana (e na janela de 5 h)."""
    agora, ents = time.time(), entradas_filtro(a.entradas)
    f7 = a.fator7 if a.fator7 > 0 else FATOR7_PADRAO
    f5 = a.fator5 if a.fator5 > 0 else FATOR5_PADRAO
    ini = a.bloco_ini
    fim_b = a.bloco_fim if a.bloco_fim > 0 else agora
    dur_b = max(60.0, fim_b - ini)
    c_blk = custo(ini, fim_b, ents)
    b7, b5 = c_blk / f7, c_blk / f5
    reserva = a.reserva
    u7, fim7 = fracao(a.u7), a.fim7
    if u7 is not None and fim7 > 0:
        if fim7 <= agora:  # a semana virou depois da medida: a nova começou em fim7
            while fim7 <= agora:
                fim7 += SEMANA
            u7e = custo(fim7 - SEMANA, agora, ents) / f7
        else:
            u7e = u7 + (custo(a.reg, agora, ents) / f7 if a.reg > 0 else 0.0)
        ritmo = (100.0 - reserva - u7e) / max((fim7 - agora) / 3600.0, 0.1)
    else:
        u7e, ritmo = None, (100.0 - reserva) / 168.0  # semana desconhecida: ritmo uniforme
    p_cfg, p_max = a.pausa * 60.0, a.pausa_max * 60.0
    base = fim_b + p_cfg
    if ritmo <= 0:
        alvo, cod = fim_b + p_max, 50
    else:
        alvo = min(max(base, ini + 3600.0 * b7 / ritmo), fim_b + p_max)
        cod = 40 if alvo > base + 60 else 0
    u5, por5, u5e = fracao(a.u5), False, None
    if u5 is not None and a.fim5 > agora:
        u5e = u5 + (custo(a.reg, agora, ents) / f5 if a.reg > 0 else 0.0)
        if (u5e >= a.guarda5 or u5e + b5 >= 98.0) and a.fim5 + 60 > alvo:
            alvo, por5, cod = a.fim5 + 60, True, max(cod, 40)
    alvo = max(alvo, agora)
    partes = ['semana ~%s%% (reseta %s, em %s)' % (num(u7e), quando(fim7), duracao(fim7 - agora)) if u7e is not None
              else 'semana não medida (ritmo uniforme)',
              'ritmo seguro %s%%/h' % num(ritmo, 2) if ritmo > 0 else 'sem folga na semana',
              'bloco %s%% em %d min' % (num(b7, 2), round(dur_b / 60))]
    if u5e is not None:
        partes.append('5 h ~%s%% (reseta %s)' % (num(u5e, 0), quando(a.fim5)))
    pmin = int(math.ceil((alvo - fim_b) / 60.0))
    if cod == 0:
        concl = 'pausa normal de %d min' % a.pausa
    elif cod == 50:
        concl = ('pausa máxima de %d min: a semana chegou à reserva de %s%%; gastar a reserva é decisão do usuário'
                 % (pmin, num(reserva, 0)))
    elif por5:
        concl = 'pausa de %d min (configurada %d): espera a janela de 5 h virar às %s' % (pmin, a.pausa, quando(a.fim5 + 60))
    else:
        concl = 'pausa de %d min (configurada %d) para caber no ritmo da semana' % (pmin, a.pausa)
    print('FIM=%d' % alvo)
    print('COD=%d' % cod)
    print('BLOCO_PCT=%.3f' % b7)
    print('LINHA=ORÇAMENTO: %s → %s' % (' · '.join(partes), concl))


def cmd_relatorio(a):
    agora, ents = time.time(), entradas_filtro(a.entradas)
    f7 = a.fator7 if a.fator7 > 0 else FATOR7_PADRAO
    f5 = a.fator5 if a.fator5 > 0 else FATOR5_PADRAO

    def soma(desde, ate=None):
        tot = [0, 0, 0, 0, 0]
        usd, n, por_sessao = 0.0, 0, {}
        for t, sess, ent, fam, nums, u, proj in linhas(desde, ate, ents):
            for i in range(5):
                tot[i] += nums[i]
            usd += u
            n += 1
            k = (sess[:8], proj)
            por_sessao[k] = por_sessao.get(k, 0.0) + u
        return tot, usd, n, por_sessao

    rot = ', '.join(sorted(ents)) if ents else 'todas as entradas'
    print('CONSUMO local (custo equivalente de API; %s) — %s' % (rot, time.strftime('%d/%m %H:%M')))
    tot5, usd5, n5, _ = soma(agora - CINCO_H)
    if n5:
        inp, cw5, cw1, cr, out = tot5
        print('  últimas 5 h: US$ %s em %d chamadas (≈ %s%% de uma janela de 5 h) · contexto médio %dK · saída média %d tokens'
              % (num(usd5, 2), n5, num(usd5 / f5, 0), (cr + cw5 + cw1 + inp) / n5 / 1000, out / n5))
    else:
        print('  últimas 5 h: nada registrado')
    horas = a.horas
    totn, usdn, nn, sess = soma(agora - horas * 3600)
    print('  últimas %d h: US$ %s em %d chamadas (≈ %s%% do semanal, a US$ %s por 1%%)'
          % (horas, num(usdn, 2), nn, num(usdn / f7), num(f7, 2)))
    if a.fim7 > agora:
        _, usd7, n7, _ = soma(a.fim7 - SEMANA)
        print('  desde a virada da semana (%s): US$ %s (≈ %s%%) · reseta %s (em %s)'
              % (quando(a.fim7 - SEMANA), num(usd7, 2), num(usd7 / f7), quando(a.fim7), duracao(a.fim7 - agora)))
    if nn:
        inp, cw5, cw1, cr, out = totn
        pr = PRECOS['sonnet']
        pesos = [inp * pr[0], (cw5 * pr[1] + cw1 * pr[2]), cr * pr[3], out * pr[4]]
        tp = sum(pesos) or 1
        print('  para onde vai (peso no custo): leitura de cache %d%% · escrita de cache %d%% · saída %d%% · entrada %d%%'
              % (100 * pesos[2] / tp, 100 * pesos[1] / tp, 100 * pesos[3] / tp, 100 * pesos[0] / tp))
        top = sorted(sess.items(), key=lambda kv: -kv[1])[:5]
        print('  sessões que mais gastaram: ' + ' · '.join('%s %s US$ %s' % (k[0], k[1], num(v, 2)) for k, v in top))


def main():
    ap = argparse.ArgumentParser(prog='consumo.py')
    sub = ap.add_subparsers(dest='cmd', required=True)
    sub.add_parser('atualiza')
    p = sub.add_parser('custo')
    p.add_argument('--desde', type=float, required=True)
    p.add_argument('--ate', type=float)
    p = sub.add_parser('calibra')
    p.add_argument('--u7', default='')
    p.add_argument('--fim7', type=float, default=0)
    p.add_argument('--u5', default='')
    p.add_argument('--fim5', type=float, default=0)
    p = sub.add_parser('orcamento')
    p.add_argument('--bloco-ini', type=float, required=True)
    p.add_argument('--bloco-fim', type=float, default=0)
    p.add_argument('--pausa', type=float, required=True)
    p.add_argument('--trabalho', type=float, default=20)
    p.add_argument('--u7', default='')
    p.add_argument('--fim7', type=float, default=0)
    p.add_argument('--u5', default='')
    p.add_argument('--fim5', type=float, default=0)
    p.add_argument('--reg', type=float, default=0)
    p.add_argument('--fator7', type=float, default=0)
    p.add_argument('--fator5', type=float, default=0)
    p.add_argument('--reserva', type=float, default=10)
    p.add_argument('--pausa-max', type=float, default=240)
    p.add_argument('--guarda5', type=float, default=90)
    p = sub.add_parser('relatorio')
    p.add_argument('--horas', type=int, default=24)
    p.add_argument('--fator7', type=float, default=0)
    p.add_argument('--fator5', type=float, default=0)
    p.add_argument('--fim7', type=float, default=0)
    p.add_argument('--fim5', type=float, default=0)
    for s in sub.choices.values():
        s.add_argument('--entradas', default=None)
        s.add_argument('--sem-atualizar', action='store_true')
    a = ap.parse_args()
    if a.cmd == 'atualiza':
        print('NOVAS=%d' % atualiza())
        return 0
    if not a.sem_atualizar:
        atualiza()
    if a.cmd == 'custo':
        print('%.6f' % custo(a.desde, a.ate, entradas_filtro(a.entradas)))
    elif a.cmd == 'calibra':
        cmd_calibra(a)
    elif a.cmd == 'orcamento':
        cmd_orcamento(a)
    elif a.cmd == 'relatorio':
        cmd_relatorio(a)
    return 0


if __name__ == '__main__':
    sys.exit(main())
