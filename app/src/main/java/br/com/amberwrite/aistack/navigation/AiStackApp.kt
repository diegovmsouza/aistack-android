package br.com.amberwrite.aistack.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.amberwrite.aistack.AiStackApplication
import br.com.amberwrite.aistack.BuildConfig
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

/**
 * Raiz da interface: NavHost com todas as rotas e o tratamento de links externos.
 *
 * Os deep links são tratados aqui (e não com `navDeepLink`) porque a activity é
 * `singleTask`: o link chega também por `onNewIntent`, e assim um único caminho decide
 * se precisa confirmar um novo pareamento antes de navegar.
 */
@Composable
fun AiStackApp(deepLink: Uri?, onDeepLinkConsumed: () -> Unit) {
    val container = AiStackApplication.container(LocalContext.current)
    val nav = rememberNavController()
    val link by container.pairingStore.link.collectAsStateWithLifecycle()
    val startRoute = remember { if (container.pairingStore.isPaired) Routes.SESSIONS else Routes.PAIR }

    /** Link de pareamento a iniciar na tela `pair` (vindo de deep link confirmado). */
    var pendingPair by remember { mutableStateOf<PairLink?>(null) }
    /** Link de pareamento aguardando confirmação (já existe um desktop pareado). */
    var confirmRepair by remember { mutableStateOf<PairLink?>(null) }

    LaunchedEffect(deepLink) {
        if (deepLink == null) return@LaunchedEffect
        when (val target = DeepLink.parse(deepLink.toString())) {
            is DeepLink.Pair -> {
                if (container.pairingStore.isPaired) {
                    confirmRepair = target.link
                } else {
                    pendingPair = target.link
                    nav.goToPair()
                }
            }
            is DeepLink.Chat -> if (container.pairingStore.isPaired) {
                nav.navigate(Routes.chat(target.conversationId)) { launchSingleTop = true }
            }
            null -> Unit
        }
        onDeepLinkConsumed()
    }

    // Despareado aqui ou revogado no desktop: volta para o pareamento e limpa a pilha.
    LaunchedEffect(link) {
        if (link == null && nav.currentDestination?.route.let { it != null && it != Routes.PAIR }) {
            nav.goToPair()
        }
    }

    Box(Modifier.fillMaxSize().background(AiTheme.colors.bg)) {
        NavHost(navController = nav, startDestination = startRoute) {
            composable(Routes.PAIR) {
                PairScreen(
                    onPaired = {
                        nav.navigate(Routes.SESSIONS) {
                            popUpTo(nav.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    initialLink = pendingPair,
                    onInitialLinkConsumed = { pendingPair = null }
                )
            }
            composable(Routes.SESSIONS) {
                SessionsScreen(
                    onOpenChat = { nav.navigate(Routes.chat(it)) },
                    onNewSession = { nav.navigate(Routes.NEW_SESSION) },
                    onOpenPending = { nav.navigate(Routes.PENDING) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onRepair = { nav.navigate(Routes.PAIR) }
                )
            }
            composable(Routes.NEW_SESSION) {
                NewSessionScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { id ->
                        nav.navigate(Routes.chat(id)) { popUpTo(Routes.NEW_SESSION) { inclusive = true } }
                    }
                )
            }
            composable(Routes.CHAT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ChatScreen(
                    conversationId = id,
                    onBack = { nav.backOrSessions() },
                    onOpenFiles = { nav.navigate(Routes.files(it)) },
                    onRepair = { nav.navigate(Routes.PAIR) }
                )
            }
            composable(
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
                    onOpenFile = { nav.navigate(Routes.fileView(convId, it)) }
                )
            }
            composable(
                Routes.FILE_VIEW,
                arguments = listOf(
                    navArgument("convId") { type = NavType.StringType },
                    navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { entry ->
                FileViewScreen(
                    conversationId = entry.arguments?.getString("convId").orEmpty(),
                    path = entry.arguments?.getString("path").orEmpty(),
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.PENDING) {
                PendingScreen(onBack = { nav.popBackStack() }, onOpenChat = { nav.navigate(Routes.chat(it)) })
            }
            composable(Routes.ACCOUNTS) { AccountsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.DEVICES) { DevicesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenDevices = { nav.navigate(Routes.DEVICES) },
                    onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) },
                    onOpenDesignCatalog = { nav.navigate(Routes.DESIGN_CATALOG) },
                    onUnpaired = { nav.goToPair() }
                )
            }
            if (BuildConfig.DEBUG) {
                composable(Routes.DESIGN_CATALOG) { DesignCatalogScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }

    confirmRepair?.let { newLink ->
        ConfirmDialog(
            title = "Parear com outro desktop?",
            text = "Este celular já está pareado. Continuar troca o pareamento atual pelo do link recebido (relay ${newLink.relay}).",
            confirmLabel = "Parear",
            onConfirm = {
                confirmRepair = null
                pendingPair = newLink
                nav.navigate(Routes.PAIR) { launchSingleTop = true }
            },
            onDismiss = { confirmRepair = null }
        )
    }
}

/** Vai para o pareamento descartando toda a pilha. */
private fun NavHostController.goToPair() {
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
