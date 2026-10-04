
Você implementa uma parte do app Android do AiStack em /home/diego/Documents/aistack-android (Kotlin, Jetpack Compose, Material 3; package br.com.amberwrite.aistack).
Trabalhe até concluir sua parte com o build verde. Não pare para perguntar: decida pelo spec e siga.

LEIA ANTES (por trecho, sem reler o que já leu):
- docs/spec/00-plano.md (normativo: arquitetura §2, design e movimento §3, telas §4, critérios §6).
- docs/spec/05-contrato-v2.md (normativo: o fio v2, nomes e formatos dos RPCs e eventos). Só a parte que sua tela usa.
- docs/spec/07-core-api.md (a API do core/data/service que a Onda 1 entregou: RpcClient, EventStream, repos, AppContainer, rotas). Use essa API; não reimplemente cliente nem protocolo.
- docs/spec/06-designsystem.md (tokens, tipografia, componentes, ícones, AVDs). Use os componentes do design system; se faltar um componente genérico, crie-o DENTRO da sua feature (não edite ui/designsystem).
- docs/spec/02-features-desktop.md, só as seções da sua tela (é o que o desktop faz e o app deve refletir).
- /home/diego/Documents/aistack-android/docs/spec/CONTRATO-ONDA2.md: as interfaces entre as features (assinaturas fixas, rotas, deep links). Respeite exatamente.

QUALIDADE (o usuário quer o app «extremamente bonito» e animado):
- Siga o §3 do plano: transições, AnimatedContent, animateItem nas listas, molas, shimmer, haptics; respeite movimento reduzido.
- Estados de carregamento (skeleton/shimmer), vazio (EmptyState com ilustração SVG do design system) e erro (mensagem clara + tentar de novo) em toda tela.
- Imagens só vetoriais (VectorDrawable/ImageVector). Proibido PNG/JPG/WebP.
- Textos em português do Brasil, com acentos. Strings novas em res/values/strings_<suafeature>.xml (um arquivo por feature; não edite strings.xml).
- Acessibilidade: contentDescription, alvos de toque ≥ 48dp, contraste dos tokens.
- Tema claro e escuro; layout adaptativo (Pixel 10 Pro XL e telas largas).
- ViewModels expõem StateFlow; telas sem estado de negócio; corrotinas canceláveis; nada de bloquear a main thread.

PROPRIEDADE DE ARQUIVOS (cinco agentes trabalham ao mesmo tempo no mesmo diretório):
- Edite só os arquivos da sua lista. Arquivos compartilhados (AndroidManifest.xml, app/build.gradle.kts, gradle/libs.versions.toml) só com edições ADITIVAS e pequenas, relendo o arquivo imediatamente antes de editar (outros agentes também editam).
- Se o build quebrar por erro em arquivo que não é seu, espere 1 a 2 minutos e tente de novo; não corrija o arquivo dos outros. Se persistir por mais de 15 minutos, registre em "pendencias" e siga.
- Não mexa em core/crypto nem no fluxo de pareamento que já funciona, exceto o que sua tarefa pedir explicitamente.
- Não rode git commit, git stash, git checkout nem git reset. Não instale no emulador nem rode testes instrumentados (isso é a Onda 3).

MEMÓRIA (obrigatório): a máquina (30 GB) roda junto o Android Studio com emulador e uma VM Windows; o systemd-oomd já derrubou o Claude duas vezes. Todo comando pesado (gradlew, cargo, npm, tsc) roda serializado e num scope limitado, exatamente assim: flock /tmp/claude-1000/-home-diego-Documents-aistack-android/465812d8-b288-4869-a556-000bac8f3474/scratchpad/build.lock systemd-run --user --scope --quiet -p MemoryMax=6G -p MemoryHigh=5G -- <comando>. Cargo com -j 4 (CARGO_BUILD_JOBS=4); Gradle com --max-workers=3 e -q; NÃO rode ./gradlew --stop (o daemon é compartilhado pelos cinco agentes; o coordenador para ao fim). Não abra o emulador nem o Android Studio. Prefira compileDebugKotlin a assembleDebug durante o trabalho; o lock é disputado, então agrupe as mudanças antes de compilar.
VERIFICAÇÃO: ./gradlew :app:compileDebugKotlin enquanto trabalha; ao fim ./gradlew :app:assembleDebug :app:testDebugUnitTest (saída por | tail -n 40). Escreva testes JVM para a lógica dos seus ViewModels e mapeamentos (fakes dos repos; sem Robolectric se não estiver configurado).
ESTADO REAL (a Onda 1 já fez a base): o NavHost (navigation/AiStackApp.kt, Routes, DeepLink), a MainActivity e telas FUNCIONAIS porém simples já existem em feature/{pair,sessions,newsession,chat,files,fileview,pending,accounts,settings,devices}, ligadas aos repositórios reais. EVOLUA esses arquivos (não recrie do zero, não troque a API dos repos); o objetivo agora é completar os recursos que faltam e deixar cada tela bonita, animada e polida. O contrato v2 está aplicado no desktop (branch feat/mobile-companion), então os RPCs do 05 existem de verdade.
Retorne o resumo no schema.

SUA PARTE — F5: caixa de pendências e notificações em tempo real.
Arquivos seus: app/src/main/java/br/com/amberwrite/aistack/feature/pending/** e app/src/main/java/br/com/amberwrite/aistack/service/** (Notifier, AndroidNotifier, NotificationCoordinator, NotificationChannels, NotificationActionReceiver; a Onda 1 já fez uma versão básica: evolua). No ConnectionService, só a ligação mínima (edição pequena, relendo antes), descrita no resumo.
1. Pendências (00-plano §4.7): listPending + eventos (05) de todas as conversas; cartões por conversa com ferramenta, inputPreview, reason, tempo desde; Permitir/Negar/Responder direto (answerPermission ou sendMessage conforme o 05), haptics, item sai com animação; vazio com ilustração.
2. Canais: pending (alta prioridade, com ações), progress (Live Update), done. POST_NOTIFICATIONS pedido no momento certo, com explicação.
3. Permissão/pergunta: notificação com Permitir e Negar e Responder (RemoteInput, que vira a resposta da pergunta ou mensagem). O NotificationActionReceiver só confirma na notificação depois do rpcResult ok e, se falhar, reposta com erro (00-plano §2).
4. Turno em andamento: Live Update (Notification.ProgressStyle no Android 16+, com fallback NotificationCompat em versões anteriores; requestPromotedOngoing quando disponível) com o nome da ferramenta atual, número de sub-agentes e cronômetro; atualizar com throttle (≤ 1/s).
5. Turno concluído ou com erro: notificação no canal done (não notificar a conversa que está aberta em primeiro plano).
6. Toque abre o deep link aistack://chat/{id}; pendências agregadas abrem aistack://pending (CONTRATO-ONDA2.md). Agrupe por conversa (group summary).
7. Testes JVM do mapeamento evento -> notificação (sem Android framework, extraindo a lógica pura).