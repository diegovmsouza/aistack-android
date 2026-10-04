package br.com.amberwrite.aistack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.navigation.AiStackApp
import br.com.amberwrite.aistack.navigation.NotificationRationaleDialog
import br.com.amberwrite.aistack.ui.theme.AiStackTheme

/**
 * Única activity: só hospeda [AiStackApp]. Links `aistack://` chegam por [getIntent] na
 * criação e por [onNewIntent] depois (a activity é `singleTask`).
 *
 * É `exported=true` porque é o launcher e o alvo dos links `aistack://` (QR de pareamento,
 * notificações, outros apps). Nada do intent é confiado: [AiStackApp] passa o Uri por
 * [br.com.amberwrite.aistack.navigation.DeepLink.parse], que aceita só o esquema, os hosts
 * e os formatos de id conhecidos e descarta o resto.
 *
 * A permissão de notificação (Android 13+) não é pedida ao abrir: depois do pareamento, um
 * diálogo explica o porquê e só então dispara o pedido; «Agora não» não volta a perguntar.
 */
class MainActivity : ComponentActivity() {

    private var deepLink by mutableStateOf<Uri?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* sem ação: o app funciona sem */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deepLink = intent?.viewData()

        val container = AiStackApplication.container(this)
        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
            val materialYou by container.settingsStore.materialYou.collectAsStateWithLifecycle()
            val link by container.pairingStore.link.collectAsStateWithLifecycle()
            var askNotifications by remember {
                mutableStateOf(needsNotificationPermission() && !container.settingsStore.notificationRationaleDismissed)
            }
            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            AiStackTheme(darkTheme = dark, dynamicColor = materialYou) {
                AiStackApp(deepLink = deepLink, onDeepLinkConsumed = { deepLink = null })
                if (askNotifications && link != null) {
                    NotificationRationaleDialog(
                        onAllow = {
                            askNotifications = false
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onLater = {
                            askNotifications = false
                            container.settingsStore.notificationRationaleDismissed = true
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.viewData()?.let { deepLink = it }
    }

    private fun Intent.viewData(): Uri? = if (action == Intent.ACTION_VIEW) data else null

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
}
