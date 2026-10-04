# RETOMAR — AiStack Android — app completo

> **Claude | Opus 5.5 | xhigh** — 2026-10-03 20:4x · bloco #2

Sobrescreva antes de cada pausa e ao trocar de assunto (até 60 linhas). Regras: `docs/turnolongo/CONTRATO.md` · índice: `docs/turnolongo/MAPA.md`.

## Agora
- Assunto ativo: app Android completo (mesma sessão; spec em `docs/spec/`, plano normativo em `00-plano.md`).
- Estado em 1 linha: Onda 1 RETOMADA (run `wf_356ac38d-b19`, script `scratchpad/onda1b.js`): A1 core + A2 design + D1 host (worktree `AiStack/worktrees/mobile`). `05-contrato-v2.md` pronto (C0; decisões em `scratchpad/c0.json`). Usuário dormindo com carta branca (commit, push, build, instalar, testar) desde 21:07.
- Próximo passo exato: ao terminar, ler o journal (`.../subagents/workflows/wf_356ac38d-b19/journal.jsonl`), conferir `./gradlew assembleDebug testDebugUnitTest` e `cargo test` do worktree → workflow Onda 2 (F1–F5, 00-plano §5) → Onda 3 (emulador, E2E, capturas, revisão).
- Se o workflow morrer: `Workflow({scriptPath: "<scratchpad>/onda1b.js", resumeFromRunId: "wf_356ac38d-b19"})`.

## Pedido do usuário (resumo fiel)
App Android bonito, com animações, SVG obrigatório para imagens; todos os recursos do AiStack: sessões do desktop (ler/interagir), iniciar sessão no celular (aparece na sidebar do desktop com ícone de celular no topo, controle total no desktop), notificações em tempo real de tarefas e pendências pareadas, sub-agentes, comandos de barra, explorador de arquivos, câmera, microfone. Pareamento atual funciona: preservar. Testar no emulador Pixel_10_Pro_XL (emulator-5554 já ligado). Máx. 5 agentes, effort ≤ high.

## Checklist
- [x] turno longo: SUSPENSO (livre, 20:46) e vigia removido a pedido do usuário — trabalhar direto, sem pausas
- [x] Entender (01–04)
- [x] Plano (00-plano.md)
- [~] Onda 1 (contrato, core, design, host)
- [ ] Desktop: worktree separado no AiStack (há alteração alheia em `src-tauri/src/window_persistence.rs` — não tocar)
- [ ] Android: implementação, build, instalação e testes no emulador

## Riscos e armadilhas
- 21:54: matou de novo (pressão 89%). Corrigido: scripts/oom-permissivo.sh (ManagedOOMMemoryPressure=auto em user@.service; rodado pelo usuário às 22:0x). Builds com flock + MemoryMax=6G. Onda 1 relançada como we5duoj1a.
- 21:42: o systemd-oomd matou o Claude (scope app-com.anthropic.Claude, 41 processos) e às 21:31 o Android Studio. Builds pesados agora em `systemd-run --user --scope -p MemoryMax=…`; cargo -j 6; gradle --max-workers. Outra sessão investiga a causa. Loop dinâmico (/loop) ativo; Onda 1 relançada como task wxq4pyq20 (A2 em cache).
- Workflow em segundo plano consome cota mesmo durante a pausa do turno.
- AiStack tem outro turno (config em `AiStack/docs/scripts-auditoria/turnolongo.env`) e worktrees wp10-host/wp11-front.
