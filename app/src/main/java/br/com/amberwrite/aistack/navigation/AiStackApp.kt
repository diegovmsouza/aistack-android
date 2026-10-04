package br.com.amberwrite.aistack.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.amberwrite.aistack.AiStackApplication
import br.com.amberwrite.aistack.BuildConfig
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.core.relay.PairLink
import br.com.amberwrite.aistack.feature.accounts.AccountsScreen
import br.com.amberwrite.aistack.feature.chat.ChatScreen
import br.com.amberwrite.aistack.feature.common.ConfirmDialog
import br.com.amberwrite.aistack.feature.devices.DevicesScreen
import br.com.amberwrite.aistack.feature.files.FilesScreen
import br.com.amberwrite.aistack.feature.fileview.FileViewScreen
import br.com.amberwrite.aistack.feature.newsession.NewSessionScreen
import br.com.amberwrite.aistack.feature.pair.PairScreen
import br.com.amberwrite.aistack.feature.pending.PendingScreen
import br.com.amberwrite.aistack.feature.sessions.SessionsScreen
import br.com.amberwrite.aistack.feature.settings.SettingsScreen
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.DesignCatalogScreen
import br.com.amberwrite.aistack.ui.designsystem.components.EmptyState
import br.com.amberwrite.aistack.ui.designsystem.illustrations.Illustration
import br.com.amberwrite.aistack.ui.designsystem.tokens.AiStackMotion

/** Largura a partir da qual a lista de sessões e o chat ficam lado a lado (window size “expanded”). */
internal val ExpandedWidth = 840.dp

/** Largura da lista no layout lista-detalhe. */
private val ListPaneWidth = 380.dp

/** Chave no SavedStateHandle do chat para uma menção vinda de Arquivos (sem recriar o chat). */
private const val MENTION_KEY = "pendingMention"

/**
 * Raiz da interface: NavHost com todas as rotas, o layout lista-detalhe em telas largas, as
 * transições (com elemento compartilhado item → chat e FAB → nova sessão) e o tratamento de
 * links externos.
 *
 * Os deep links são tratados aqui (e não com `navDeepLink`) porque a activity é
 * `singleTask` e exportada: o link chega também por `onNewIntent`, e assim um único caminho
 * valida o id da conversa, só navega quando o aparelho está pareado e pede confirmação antes
 * de trocar um pareamento existente. Veja [DeepLink] e a documentação de `MainActivity`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AiStackApp(
    deepLink: Uri?,
    onDeepLinkConsumed: () -> Unit,
    onPairRouteChange: (Boolean) -> Unit = {},
) {
    val container = AiStackApplication.container(LocalContext.current)
    val nav = rememberNavController()
    val link by container.pairingStore.link.collectAsStateWithLifecycle()
    val startRoute = remember { if (container.pairingStore.isPaired) Routes.SESSIONS else Routes.PAIR }
    val motion = AiTheme.motion

    /** Link de pareamento a iniciar na tela `pair` (vindo de deep link confirmado). */
    var pendingPair by remember { mutableStateOf<PairLink?>(null) }
    /** Link de pareamento aguardando confirmação (já existe um desktop pareado). */
    var confirmRepair by remember { mutableStateOf<PairLink?>(null) }
    /** Conversa aberta no painel de detalhe (só no layout expandido). */
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    /** Menção a inserir no chat do painel de detalhe. */
    var detailMention by rememberSaveable { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(AiTheme.colors.bg)
    ) {
        val expanded = maxWidth >= ExpandedWidth

        /** Abre uma conversa: no painel de detalhe (expandido) ou como destino próprio. */
        fun openChat(id: String, mention: String? = null) {
            if (!DeepLink.isValidConversationId(id)) return
            if (expanded) {
                selectedId = id
                detailMention = mention
                if (nav.currentDestination?.route != Routes.SESSIONS &&
                    !nav.popBackStack(Routes.SESSIONS, inclusive = false)
                ) {
                    nav.navigate(Routes.SESSIONS) { launchSingleTop = true }
                }
            } else {
                nav.navigate(Routes.chat(id, mention)) { launchSingleTop = true }
            }
        }

        /** Arquivos/visualizador pediram para mencionar [path]: volta ao chat de origem com a menção. */
        fun mentionInChat(convId: String, path: String) {
            val chatEntry = runCatching { nav.getBackStackEntry(Routes.CHAT) }.getOrNull()
                ?.takeIf { it.arguments?.getString("id") == convId }
            if (chatEntry != null) {
                chatEntry.savedStateHandle[MENTION_KEY] = path
                nav.popBackStack(chatEntry.destination.id, inclusive = false)
            } else {
                openChat(convId, path)
            }
        }

        SharedTransitionLayout(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = nav,
                    startDestination = startRoute,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = enterSpec(motion),
                    exitTransition = { fadeOut(motion.exit()) },
                    popEnterTransition = { fadeIn(motion.fade()) },
                    popExitTransition = popExitSpec(motion),
                ) {
                    screen(Routes.PAIR) { entry ->
                        val canGoBack = remember(entry) { nav.previousBackStackEntry != null }
                        PairScreen(
                            onPaired = {
                                nav.navigate(Routes.SESSIONS) {
                                    popUpTo(nav.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            initialLink = pendingPair,
                            onInitialLinkConsumed = { pendingPair = null },
                            onBack = if (canGoBack) ({ nav.popBackStack() }) else null,
                        )
                    }
                    screen(Routes.SESSIONS) {
                        if (expanded) {
                            BackHandler(enabled = selectedId != null) { selectedId = null }
                            ListDetail(
                                selectedId = selectedId,
                                detailMention = detailMention,
                                onSelect = { id ->
                                    if (DeepLink.isValidConversationId(id)) {
                                        selectedId = id
                                        detailMention = null
                                    }
                                },
                                onCloseDetail = { selectedId = null },
                                onNewSession = { nav.navigate(Routes.NEW_SESSION) { launchSingleTop = true } },
                                onOpenPending = { nav.navigate(Routes.PENDING) { launchSingleTop = true } },
                                onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                                onRepair = { nav.navigate(Routes.PAIR) { launchSingleTop = true } },
                                onOpenFiles = { id, path -> nav.navigate(Routes.files(id, path)) },
                            )
                        } else {
                            SessionsScreen(
                                onOpenChat = { openChat(it) },
                                onNewSession = { nav.navigate(Routes.NEW_SESSION) { launchSingleTop = true } },
                                onOpenPending = { nav.navigate(Routes.PENDING) { launchSingleTop = true } },
                                onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                                onRepair = { nav.navigate(Routes.PAIR) { launchSingleTop = true } },
                            )
                        }
                    }
                    screen(Routes.NEW_SESSION) {
                        NewSessionScreen(
                            onBack = { nav.popBackStack() },
                            onCreated = { id ->
                                if (expanded) {
                                    selectedId = id
                                    detailMention = null
                                    nav.popBackStack(Routes.SESSIONS, inclusive = false)
                                } else {
                                    nav.navigate(Routes.chat(id)) {
                                        popUpTo(Routes.NEW_SESSION) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }
                    screen(
                        Routes.CHAT,
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("mention") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        if (!DeepLink.isValidConversationId(id)) {
                            // Rota montada com id inválido (não deveria acontecer): não abre nada.
                            LaunchedEffect(entry) { nav.backOrSessions() }
                            return@screen
                        }
                        val handedMention by entry.savedStateHandle
                            .getStateFlow<String?>(MENTION_KEY, null)
                            .collectAsStateWithLifecycle()
                        Box(
                            Modifier
                                .fillMaxSize()
                                .aiSharedBounds(SharedKeys.conversation(id))
                        ) {
                            ChatScreen(
                                conversationId = id,
                                initialMention = handedMention ?: entry.arguments?.getString("mention"),
                                onBack = { nav.backOrSessions() },
                                onOpenFiles = { nav.navigate(Routes.files(id, it)) },
                                onRepair = { nav.navigate(Routes.PAIR) { launchSingleTop = true } }
                            )
                        }
                    }
                    screen(
                        Routes.FILES,
                        arguments = listOf(
                            navArgument("convId") { type = NavType.StringType },
                            navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) { entry ->
                        val convId = entry.arguments?.getString("convId").orEmpty()
                        FilesScreen(
                            conversationId = convId,
                            path = entry.arguments?.getString("path"),
                            onBack = { nav.popBackStack() },
                            onOpenDir = { nav.navigate(Routes.files(convId, it)) },
                            onOpenFile = { nav.navigate(Routes.fileView(convId, it)) },
                            onMention = { mentionInChat(convId, it) },
                        )
                    }
                    screen(
                        Routes.FILE_VIEW,
                        arguments = listOf(
                            navArgument("convId") { type = NavType.StringType },
                            navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) { entry ->
                        val convId = entry.arguments?.getString("convId").orEmpty()
                        FileViewScreen(
                            conversationId = convId,
                            path = entry.arguments?.getString("path").orEmpty(),
                            onBack = { nav.popBackStack() },
                            onMention = { mentionInChat(convId, it) },
                        )
                    }
                    screen(Routes.PENDING) {
                        PendingScreen(onBack = { nav.popBackStack() }, onOpenChat = { openChat(it) })
                    }
                    screen(Routes.ACCOUNTS) { AccountsScreen(onBack = { nav.popBackStack() }) }
                    screen(Routes.DEVICES) {
                        DevicesScreen(onBack = { nav.popBackStack() }, onUnpaired = { nav.goToPair() })
                    }
                    screen(Routes.SETTINGS) {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onOpenDevices = { nav.navigate(Routes.DEVICES) { launchSingleTop = true } },
                            onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) { launchSingleTop = true } },
                            onOpenDesignCatalog = { nav.navigate(Routes.DESIGN_CATALOG) { launchSingleTop = true } },
                            onUnpaired = { nav.goToPair() }
                        )
                    }
                    if (BuildConfig.DEBUG) {
                        screen(Routes.DESIGN_CATALOG) { DesignCatalogScreen(onBack = { nav.popBackStack() }) }
                    }
                }
            }
        }

        // Efeitos depois do NavHost, na mesma composição: o grafo já está definido quando rodam.
        LaunchedEffect(deepLink) {
            if (deepLink == null) return@LaunchedEffect
            val paired = container.pairingStore.isPaired
            when (val target = DeepLink.parse(deepLink.toString())) {
                is DeepLink.Pair -> {
                    if (paired) {
                        confirmRepair = target.link
                    } else {
                        pendingPair = target.link
                        nav.goToPair()
                    }
                }
                is DeepLink.Chat -> if (paired) openChat(target.conversationId)
                DeepLink.Pending -> if (paired) nav.navigate(Routes.PENDING) { launchSingleTop = true }
                null -> Unit
            }
            onDeepLinkConsumed()
        }

        val route = nav.currentBackStackEntryAsState().value?.destination?.route
        LaunchedEffect(route) { if (route != null) onPairRouteChange(route == Routes.PAIR) }

        // Despareado aqui ou revogado no desktop: volta para o pareamento e limpa a pilha.
        LaunchedEffect(link) {
            if (link == null) {
                selectedId = null
                if (nav.currentDestination?.route.let { it != null && it != Routes.PAIR }) nav.goToPair()
            }
        }
    }

    confirmRepair?.let { newLink ->
        ConfirmDialog(
            title = stringResource(R.string.pair_repair_title),
            text = stringResource(R.string.pair_repair_text, newLink.relay),
            confirmLabel = stringResource(R.string.pair_repair_confirm),
            dismissLabel = stringResource(R.string.pair_cancel),
            onConfirm = {
                confirmRepair = null
                pendingPair = newLink
                nav.navigate(Routes.PAIR) { launchSingleTop = true }
            },
            onDismiss = { confirmRepair = null }
        )
    }
}

/** Lista de sessões e chat lado a lado (telas largas e dobráveis abertos). */
@Composable
private fun ListDetail(
    selectedId: String?,
    detailMention: String?,
    onSelect: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onNewSession: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenSettings: () -> Unit,
    onRepair: () -> Unit,
    onOpenFiles: (String, String?) -> Unit,
) {
    val c = AiTheme.colors
    val motion = AiTheme.motion
    Row(Modifier.fillMaxSize()) {
        SessionsScreen(
            onOpenChat = onSelect,
            onNewSession = onNewSession,
            onOpenPending = onOpenPending,
            onOpenSettings = onOpenSettings,
            onRepair = onRepair,
            modifier = Modifier
                .width(ListPaneWidth)
                .fillMaxHeight(),
            selectedId = selectedId,
        )
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(c.line)
        )
        AnimatedContent(
            targetState = selectedId,
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.exit()) },
            contentAlignment = Alignment.Center,
            label = "detailPane",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) { id ->
            if (id == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.sessions_detail_empty_title),
                        body = stringResource(R.string.sessions_detail_empty_body),
                        illustration = Illustration.NoSessions,
                    )
                }
            } else {
                key(id) {
                    ChatScreen(
                        conversationId = id,
                        initialMention = detailMention,
                        onBack = onCloseDetail,
                        onOpenFiles = { path -> onOpenFiles(id, path) },
                        onRepair = onRepair,
                    )
                }
            }
        }
    }
}

/** Destino que expõe o seu escopo de animação para [aiSharedBounds]. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = arguments) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}

private fun enterSpec(motion: AiStackMotion): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(motion.fade()) + slideInHorizontally(motion.enter()) { it / 12 }
}

private fun popExitSpec(motion: AiStackMotion): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(motion.exit()) + slideOutHorizontally(motion.exit()) { it / 12 }
}

/**
 * Vai para o pareamento descartando toda a pilha. Já estando nele, não recria o destino:
 * uma entrada nova teria outro ViewModel, e o pareamento em curso ficaria órfão na tela.
 */
private fun NavHostController.goToPair() {
    if (currentDestination?.route == Routes.PAIR) return
    navigate(Routes.PAIR) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Voltar a partir do chat: se ele foi aberto por notificação (pilha vazia), cai na lista. */
private fun NavHostController.backOrSessions() {
    if (!popBackStack()) {
        navigate(Routes.SESSIONS) { launchSingleTop = true }
    }
}
