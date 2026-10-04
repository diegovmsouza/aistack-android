
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

SUA PARTE — F1: pareamento, sessões, nova sessão e a navegação.
Arquivos seus: app/src/main/java/br/com/amberwrite/aistack/feature/pair/**, feature/sessions/**, feature/newsession/**, navigation/** e MainActivity.kt. A rota chat/{id}?mention= e o deep link aistack://pending já foram aplicados pelo coordenador (CONTRATO-ONDA2.md §1); mantenha-os. Ao fim, ligue no NavHost os callbacks novos (com valor padrão) que as outras telas acrescentarem (procure parâmetros on*: () -> Unit = {} nas telas).
1. Pareamento (00-plano §4.1): onboarding animado (AVD da marca montando), QR por CameraX + ML Kit ou colar link, estados ao vivo do RelayClient (Connecting/Handshaking/Online/AuthRejected/HostOffline) com animação, sucesso animado. Re-parear pede confirmação. PRESERVE o motor de pareamento atual (PairLink, handshake, keyAuth/pair): só troque a UI e ligue no que a Onda 1 expôs.
2. Sessões (§4.2): lista com busca, filtro por projeto, grupos por data (Hoje, Ontem, 7 dias, Antes), item com ProviderBadge, título, projeto, StatusDot ao vivo (ocupado/pendente pelos eventos), OriginBadge (celular) quando origin=="mobile", prévia da última mensagem; deslizar para arquivar e renomear (RPCs permitidos no 05); FAB «Nova sessão» com transição de container; topo com o estado da conexão e o contador de pendências (abre pending); pull-to-refresh; atualização por conversations-changed e resync.
3. Nova sessão (§4.3): projeto (recentes de listProjects/listConversations + navegar por listDir), provedor e modelo (ModelPicker com os provedores e modelos do 05/02), esforço (slider com as cores do desktop), modo de permissão (sem bypass; nunca "default" vazio — use os valores válidos do 05) e primeira mensagem. createConversation com params válidos (05); ao criar, navega para chat/{id}.
4. Navegação: rotas do CONTRATO-ONDA2.md, shared element do item da lista para o cabeçalho do chat, deep links aistack://chat/{id} e aistack://pending (já no manifest), list-detail em largura expandida (lista + chat lado a lado). Avalie se a activity precisa mesmo de exported com os intent-filters (precisa para aistack://pair vindo do navegador/câmera; documente a decisão e valide o id do chat). Mantenha a rota debug do DesignCatalogScreen.