# RETOMAR — AiStack Android — app completo

> **Claude | Opus 5.5 | xhigh** — 2026-10-03 20:4x · bloco #2

Sobrescreva antes de cada pausa e ao trocar de assunto (até 60 linhas). Regras: `docs/turnolongo/CONTRATO.md` · índice: `docs/turnolongo/MAPA.md`.

## Agora
- Assunto ativo: app Android completo — ENTREGUE (2026-10-04 10:1x). Android main 1774c02+; AiStack main 98154d2 «release: v0.4.8» (feat/mobile-companion já está no main, em fast-forward).
- Feito nesta rodada: merge no main (migração de origem renumerada para 0005), `lastMessage` no listConversations remoto + prévia no app, Markdown grande fora da thread principal, erro de escopo explicativo, deploy do relay na VM snake3000 (backup .bak-20261004, LimitNOFILE=65536), RELEASE_NOTES_v0.4.8.md.
- E2E 10:0x (emulator-5556 + dev isolado): sessão criada pelo celular, resposta ao vivo, prévia após reabrir, reconexão automática após o host reiniciar, `/` comandos, tema escuro; ícone de celular na sidebar do desktop CONFERIDO A OLHO (captura pelo portal: gdbus org.freedesktop.portal.Screenshot; gnome-screenshot trava no Wayland).
- Ícones: todos vetoriais (adaptive + monochrome), sem PNG.
- Pendente: conferir a CI do main e a publicação da v0.4.8 pelo release.yml (`gh run list -b main`). Na CI de 12:33, o teste `mcp_broker::tests::soquete_vivo_no_caminho_principal_nao_e_derrubado` falhou de forma instável (é anterior ao merge); se ele derrubar a CI, `gh run rerun --failed`.
- Banco de dev antigo: ~/.aistack-mobile-dev/aistack.db.pre-0005 (checksum da migração 3 divergia). Pasta descartável ~/aistack-e2e.

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
