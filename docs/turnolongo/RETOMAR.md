# RETOMAR — AiStack Android — app completo

> **Claude | Opus 5.5 | xhigh** — 2026-10-03 20:4x · bloco #2

Sobrescreva antes de cada pausa e ao trocar de assunto (até 60 linhas). Regras: `docs/turnolongo/CONTRATO.md` · índice: `docs/turnolongo/MAPA.md`.

## Agora
- Assunto ativo: app Android completo — CONCLUÍDO (2026-10-04 09:1x). Android main em 745766d; desktop `feat/mobile-companion` 1e644f8 (worktree `AiStack/worktrees/mobile`, sem merge no main).
- Onda 3 (E2E isolado no emulator-5556 «Pixel_10_Pro_XL_2», AISTACK_HOME=~/.aistack-mobile-dev, relay local 127.0.0.1:8787, RPC na porta 1431) validou: pareamento por link https; nova sessão pelo celular (origin=mobile); streaming; notificação de permissão com Permitir; done e Live Update; Pendências; comandos `/`; menu de anexos; painel Agentes; explorador e visualizador; câmera ponta a ponta (o modelo descreveu a foto, miniatura no chip); ditado em escuta.
- Corrigido na Onda 3: miniaturas (decodeStream só com bounds devolve null → spinner eterno), explorador pelo chat, re-pareamento falso ao reabrir a tarefa, trilha do explorador.
- Falta (fora do alcance automático): conferir a olho o ícone de celular na sidebar do desktop (validado só por dados; captura de tela falha no Wayland); merge de `feat/mobile-companion` e deploy do relay — decisão do usuário.

## Pedido do usuário (resumo fiel)
App Android bonito, com animações, SVG obrigatório para imagens; todos os recursos do AiStack: sessões do desktop (ler/interagir), iniciar sessão no celular (aparece na sidebar do desktop com ícone de celular no topo, controle total no desktop), notificações em tempo real de tarefas e pendências pareadas, sub-agentes, comandos de barra, explorador de arquivos, câmera, microfone. Pareamento atual funciona: preservar. Testar no emulador Pixel_10_Pro_XL (emulator-5554 já ligado). Máx. 5 agentes, effort ≤ high.

## Checklist
- [x] turno longo: SUSPENSO (livre, 20:46) e vigia removido a pedido do usuário — trabalhar direto, sem pausas
- [x] Entender (01–04)
- [x] Plano (00-plano.md)
- [x] Onda 1 (contrato, core, design, host) — commitada
- [x] Onda 2 (telas F1–F5)
- [x] Desktop: worktree separado no AiStack (há alteração alheia em `src-tauri/src/window_persistence.rs` — não tocar)
- [x] Android: implementação, build, instalação e testes no emulador (Onda 3)

## Riscos e armadilhas
- 21:54: matou de novo (pressão 89%). Corrigido: scripts/oom-permissivo.sh (ManagedOOMMemoryPressure=auto em user@.service; rodado pelo usuário às 22:0x). Builds com flock + MemoryMax=6G. Onda 1 relançada como we5duoj1a.
- 21:42: o systemd-oomd matou o Claude (scope app-com.anthropic.Claude, 41 processos) e às 21:31 o Android Studio. Builds pesados agora em `systemd-run --user --scope -p MemoryMax=…`; cargo -j 6; gradle --max-workers. Outra sessão investiga a causa. Loop dinâmico (/loop) ativo; Onda 1 relançada como task wxq4pyq20 (A2 em cache).
- Workflow em segundo plano consome cota mesmo durante a pausa do turno.
- AiStack tem outro turno (config em `AiStack/docs/scripts-auditoria/turnolongo.env`) e worktrees wp10-host/wp11-front.
