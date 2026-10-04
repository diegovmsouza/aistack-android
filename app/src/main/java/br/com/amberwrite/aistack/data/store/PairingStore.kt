package br.com.amberwrite.aistack.data.store

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import br.com.amberwrite.aistack.core.relay.PairLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Guarda o link de pareamento (sem o código de uso único) em `EncryptedSharedPreferences`,
 * com a chave-mestra no Android Keystore. Se o armazenamento cifrado falhar (aparelhos com
 * Keystore quebrado), cai para preferências privadas comuns — o link não é segredo forte:
 * a autenticação depende da chave privada do aparelho, que tem cifra própria.
 *
 * Migra na primeira leitura o formato antigo (`aistack_remote_config`).
 */
class PairingStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = openPrefs(appContext)

    private val _link = MutableStateFlow(load())
    /** Link pareado atual, ou `null` quando o aparelho não está pareado. */
    val link: StateFlow<PairLink?> = _link.asStateFlow()

    val isPaired: Boolean get() = _link.value != null

    /** Salva o link (sempre sem código). Só deve ser chamado depois do pareamento confirmado. */
    fun save(link: PairLink) {
        val clean = link.withoutCode()
        prefs.edit()
            .putString(KEY_RELAY, clean.relay)
            .putString(KEY_HOST, clean.host)
            .putString(KEY_PK, clean.pk)
            .apply()
        _link.value = clean
    }

    /** Apaga as credenciais de pareamento (revogação ou "esquecer desktop"). */
    fun clear() {
        prefs.edit().clear().apply()
        appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        _link.value = null
    }

    private fun load(): PairLink? {
        read(prefs)?.let { return it }
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val old = read(legacy) ?: return null
        prefs.edit()
            .putString(KEY_RELAY, old.relay)
            .putString(KEY_HOST, old.host)
            .putString(KEY_PK, old.pk)
            .commit()
        legacy.edit().clear().apply()
        Log.i(TAG, "Link de pareamento migrado do formato antigo.")
        return old
    }

    private fun read(sp: SharedPreferences): PairLink? {
        val relay = sp.getString(KEY_RELAY, null) ?: return null
        val host = sp.getString(KEY_HOST, null) ?: return null
        val pk = sp.getString(KEY_PK, null) ?: return null
        return PairLink(relay, host, pk, null)
    }

    companion object {
        private const val TAG = "PairingStore"
        private const val PREFS = "aistack_pairing_secure"
        private const val PREFS_FALLBACK = "aistack_pairing"
        private const val LEGACY_PREFS = "aistack_remote_config"
        private const val KEY_RELAY = "remote_relay"
        private const val KEY_HOST = "remote_host"
        private const val KEY_PK = "remote_pk"

        @Suppress("DEPRECATION")
        private fun openPrefs(context: Context): SharedPreferences = try {
            val master = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS,
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "Armazenamento cifrado indisponível; usando preferências privadas: ${e.message}")
            context.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE)
        }
    }
}
