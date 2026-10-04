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
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.navigation.AiStackApp
import br.com.amberwrite.aistack.ui.theme.AiStackTheme

/**
 * Única activity: só hospeda [AiStackApp]. Links `aistack://` chegam por [getIntent] na
 * criação e por [onNewIntent] depois (a activity é `singleTask`).
 */
class MainActivity : ComponentActivity() {

    private var deepLink by mutableStateOf<Uri?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* sem ação: o app funciona sem */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deepLink = intent?.viewData()
        requestNotificationPermission()

        val container = AiStackApplication.container(this)
        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            AiStackTheme(darkTheme = dark) {
                AiStackApp(deepLink = deepLink, onDeepLinkConsumed = { deepLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.viewData()?.let { deepLink = it }
    }

    private fun Intent.viewData(): Uri? = if (action == Intent.ACTION_VIEW) data else null

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
