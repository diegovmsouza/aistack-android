package br.com.amberwrite.aistack

import android.app.Application
import android.content.Context
import br.com.amberwrite.aistack.service.NotificationChannels
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class AiStackApplication : Application() {

    /** Grafo de dependências do processo. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Provedor Bouncy Castle completo (X25519/Ed25519) no lugar do embutido e reduzido.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        NotificationChannels.create(this)
        container = AppContainer(this)
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as AiStackApplication).container
    }
}
